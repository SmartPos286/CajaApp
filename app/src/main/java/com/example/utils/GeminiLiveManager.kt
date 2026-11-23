package com.example.utils

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.LoudnessEnhancer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

enum class GeminiLiveStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_LISTENING,
    MODEL_SPEAKING,
    PROCESSING_TOOL,
    FALLBACK_CONTINUOUS,
    ERROR
}

interface GeminiLiveCallbacks {
    fun onStatusChanged(status: GeminiLiveStatus, detail: String = "")
    fun onUserTranscript(partialOrFull: String, isTurnComplete: Boolean)
    fun onModelTranscript(partialOrFull: String, isTurnComplete: Boolean)
    fun onAudioLevelChanged(level: Float)
    suspend fun onToolCall(functionName: String, args: Map<String, Any>): Map<String, Any>
    fun onError(message: String)
}

/**
 * Cliente bidireccional WebSocket para Gemini Live API (BidiGenerateContent).
 * Transmite audio PCM 16kHz continuo desde el micrófono sin cortes y reproduce
 * respuestas de audio 24kHz en tiempo real con soporte de interrupción (barge-in)
 * y ejecución de herramientas (Function Calling) en vivo.
 */
class GeminiLiveManager(
    private val callbacks: GeminiLiveCallbacks
) {
    companion object {
        private const val TAG = "GeminiLiveManager"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var scope: CoroutineScope? = null
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var recordJob: Job? = null

    @Volatile
    private var isConnected = false

    @Volatile
    private var isMuted = false

    private val userTranscriptBuffer = StringBuilder()
    private val modelTranscriptBuffer = StringBuilder()

    fun connect(
        apiKey: String,
        model: String,
        systemInstruction: String,
        toolDeclarations: JSONArray
    ) {
        disconnect()

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        userTranscriptBuffer.setLength(0)
        modelTranscriptBuffer.setLength(0)
        isMuted = false

        callbacks.onStatusChanged(GeminiLiveStatus.CONNECTING, "Conectando con Gemini Live...")

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, sending setup...")
                val setupFrame = buildSetupPayload(model, systemInstruction, toolDeclarations)
                webSocket.send(setupFrame.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: $code $reason")
                isConnected = false
                if (code != 1000 && reason.isNotBlank()) {
                    callbacks.onError("Sesión Gemini Live cerrada ($code): $reason")
                } else {
                    callbacks.onStatusChanged(GeminiLiveStatus.DISCONNECTED, "")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                val wasConnected = isConnected
                isConnected = false
                stopAudioHardware()
                val errDetail = t.localizedMessage ?: "Error de red con Gemini Live"
                callbacks.onStatusChanged(GeminiLiveStatus.ERROR, errDetail)
                callbacks.onError(
                    if (wasConnected) "Se perdió la conexión con Gemini Live: $errDetail"
                    else "No se pudo conectar a Gemini Live: $errDetail"
                )
            }
        })
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (isConnected) {
            callbacks.onStatusChanged(
                GeminiLiveStatus.CONNECTED_LISTENING,
                if (muted) "Micrófono silenciado (Gemini Live en espera)" else "Escuchando en tiempo real..."
            )
        }
    }

    fun sendTextTurn(text: String) {
        val ws = webSocket ?: return
        if (!isConnected || text.isBlank()) return
        try {
            val msg = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", text) })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            ws.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text turn", e)
        }
    }

    fun sendContextUpdate(contextNote: String) {
        val ws = webSocket ?: return
        if (!isConnected || contextNote.isBlank()) return
        try {
            val msg = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "[ACTUALIZACIÓN DE CONTEXTO EN PANTALLA]: $contextNote")
                                })
                            })
                        })
                    })
                    put("turnComplete", false)
                })
            }
            ws.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send context update", e)
        }
    }

    fun disconnect() {
        isConnected = false
        recordJob?.cancel()
        recordJob = null
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {
        } finally {
            webSocket = null
        }
        stopAudioHardware()
        scope?.cancel()
        scope = null
        callbacks.onStatusChanged(GeminiLiveStatus.DISCONNECTED, "")
    }

    private fun buildSetupPayload(
        model: String,
        systemInstruction: String,
        toolDeclarations: JSONArray
    ): JSONObject {
        return JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", model)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
                if (toolDeclarations.length() > 0) {
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", toolDeclarations)
                        })
                    })
                }
            })
        }
    }

    private fun handleIncomingMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)

            if (root.has("setupComplete")) {
                Log.d(TAG, "Gemini Live setupComplete received!")
                isConnected = true
                initAudioPlayback()
                startMicrophoneStreaming()
                callbacks.onStatusChanged(GeminiLiveStatus.CONNECTED_LISTENING, "Gemini Live activo • Habla con fluidez")
                return
            }

            if (root.has("toolCall")) {
                val toolCallObj = root.getJSONObject("toolCall")
                val functionCalls = toolCallObj.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    handleToolCalls(functionCalls)
                }
            }

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Barge-in interrupted by user")
                    flushAudioPlayback()
                    finalizeModelTranscriptIfNeeded()
                    callbacks.onStatusChanged(GeminiLiveStatus.CONNECTED_LISTENING, "Escuchando corrección...")
                }

                // Live user speech transcription
                val inputTranscription = serverContent.optJSONObject("inputTranscription")
                if (inputTranscription != null) {
                    val text = inputTranscription.optString("text", "")
                    if (text.isNotEmpty()) {
                        userTranscriptBuffer.append(text)
                        callbacks.onUserTranscript(userTranscriptBuffer.toString(), false)
                    }
                }

                // Live model speech transcription
                val outputTranscription = serverContent.optJSONObject("outputTranscription")
                if (outputTranscription != null) {
                    val text = outputTranscription.optString("text", "")
                    if (text.isNotEmpty()) {
                        // User finished speaking when model starts answering
                        finalizeUserTranscriptIfNeeded()
                        modelTranscriptBuffer.append(text)
                        callbacks.onModelTranscript(modelTranscriptBuffer.toString(), false)
                    }
                }

                // Audio & optional text chunks
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    finalizeUserTranscriptIfNeeded()
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val mime = inlineData.optString("mimeType", "")
                                val data = inlineData.optString("data", "")
                                if (mime.startsWith("audio/pcm") && data.isNotEmpty()) {
                                    callbacks.onStatusChanged(GeminiLiveStatus.MODEL_SPEAKING, "Gemini Live respondiendo...")
                                    playAudioChunk(data)
                                }
                            }
                            val textPart = part.optString("text", "")
                            if (textPart.isNotBlank() && outputTranscription == null) {
                                modelTranscriptBuffer.append(textPart)
                                callbacks.onModelTranscript(modelTranscriptBuffer.toString(), false)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    finalizeUserTranscriptIfNeeded()
                    finalizeModelTranscriptIfNeeded()
                    callbacks.onStatusChanged(
                        GeminiLiveStatus.CONNECTED_LISTENING,
                        if (isMuted) "Micrófono silenciado" else "Escuchando en tiempo real..."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Live message", e)
        }
    }

    private fun finalizeUserTranscriptIfNeeded() {
        val text = userTranscriptBuffer.toString().trim()
        if (text.isNotEmpty()) {
            userTranscriptBuffer.setLength(0)
            callbacks.onUserTranscript(text, true)
        }
    }

    private fun finalizeModelTranscriptIfNeeded() {
        val text = modelTranscriptBuffer.toString().trim()
        if (text.isNotEmpty()) {
            modelTranscriptBuffer.setLength(0)
            callbacks.onModelTranscript(text, true)
        }
    }

    private fun handleToolCalls(functionCalls: JSONArray) {
        val activeScope = scope ?: return
        activeScope.launch {
            callbacks.onStatusChanged(GeminiLiveStatus.PROCESSING_TOOL, "Razonando y ejecutando acción...")
            val responsesArray = JSONArray()

            for (i in 0 until functionCalls.length()) {
                val callObj = functionCalls.optJSONObject(i) ?: continue
                val callId = callObj.optString("id", "")
                val name = callObj.optString("name", "")
                val argsJson = callObj.optJSONObject("args")
                val argsMap = jsonObjectToMap(argsJson)

                val resultMap = try {
                    callbacks.onToolCall(name, argsMap)
                } catch (e: Exception) {
                    mapOf("status" to "error", "message" to (e.localizedMessage ?: "Error ejecutando función"))
                }

                val responseObj = JSONObject().apply {
                    if (callId.isNotBlank()) put("id", callId)
                    put("name", name)
                    put("response", JSONObject().apply {
                        put("output", mapToJsonValue(resultMap))
                    })
                }
                responsesArray.put(responseObj)
            }

            try {
                val frame = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", responsesArray)
                    })
                }
                webSocket?.send(frame.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send toolResponse", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStreaming() {
        val activeScope = scope ?: return
        recordJob?.cancel()
        recordJob = activeScope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                    ?: AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        INPUT_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2
                    )
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord creation failed", e)
                callbacks.onError("No se pudo iniciar el micrófono para Gemini Live.")
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                callbacks.onError("Micrófono no disponible.")
                return@launch
            }

            audioRecord = record
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AEC unavailable: ${e.message}")
            }

            val buffer = ByteArray(2048)
            try {
                record.startRecording()
                while (isActive && isConnected) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0 && !isMuted) {
                        val level = computeRmsNormalized(buffer, bytesRead)
                        callbacks.onAudioLevelChanged(level)

                        val base64Audio = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                        val chunkJson = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("mediaChunks", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", base64Audio)
                                    })
                                })
                            })
                        }
                        webSocket?.send(chunkJson.toString())
                    } else if (isMuted) {
                        callbacks.onAudioLevelChanged(0f)
                    }
                }
            } catch (e: Exception) {
                if (isConnected) {
                    Log.e(TAG, "Mic streaming error", e)
                }
            }
        }
    }

    private fun initAudioPlayback() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.setVolume(AudioTrack.getMaxVolume())
            try {
                val enhancer = LoudnessEnhancer(track.audioSessionId)
                enhancer.setTargetGain(1200) // +12 dB hardware loudness boost for clear speaker output
                enhancer.enabled = true
                loudnessEnhancer = enhancer
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer unavailable: ${e.message}")
            }
            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
        }
    }

    private fun playAudioChunk(base64Data: String) {
        val track = audioTrack ?: return
        try {
            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (pcmBytes.isNotEmpty()) {
                val boostedBytes = amplifyPcm16(pcmBytes, gain = 2.2f)
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                track.write(boostedBytes, 0, boostedBytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio chunk", e)
        }
    }

    private fun amplifyPcm16(pcmBytes: ByteArray, gain: Float): ByteArray {
        val out = pcmBytes.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val low = out[i].toInt() and 0xFF
            val high = out[i + 1].toInt() shl 8
            val sample = (high or low).toShort().toInt()
            val amplified = (sample * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (amplified and 0xFF).toByte()
            out[i + 1] = ((amplified shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    private fun flushAudioPlayback() {
        try {
            audioTrack?.let { track ->
                track.pause()
                track.flush()
                track.play()
            }
        } catch (_: Exception) {
        }
    }

    private fun stopAudioHardware() {
        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        } finally {
            echoCanceler = null
        }

        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        } finally {
            loudnessEnhancer = null
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        } finally {
            audioRecord = null
        }

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        } finally {
            audioTrack = null
        }
    }

    private fun computeRmsNormalized(pcmBytes: ByteArray, length: Int): Float {
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)).toShort()
            sumSquares += sample.toDouble() * sample.toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val rms = sqrt(sumSquares / samples)
        return (rms / 8000.0).toFloat().coerceIn(0f, 1f)
    }

    private fun jsonObjectToMap(obj: JSONObject?): Map<String, Any> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key) ?: continue
            if (value != JSONObject.NULL) {
                result[key] = jsonValueToKotlin(value)
            }
        }
        return result
    }

    private fun jsonValueToKotlin(value: Any): Any {
        return when (value) {
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item != null && item != JSONObject.NULL) {
                        list.add(jsonValueToKotlin(item))
                    }
                }
                list
            }
            else -> value
        }
    }

    private fun mapToJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (k, v) ->
                    if (k != null) put(k.toString(), mapToJsonValue(v))
                }
            }
            is Iterable<*> -> JSONArray().apply {
                value.forEach { put(mapToJsonValue(it)) }
            }
            else -> value
        }
    }
}
