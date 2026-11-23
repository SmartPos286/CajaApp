package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechToTextManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionListener: RecognitionListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isActiveSession = false

    @Volatile
    private var isContinuousMode = false

    fun startListening(
        continuous: Boolean = false,
        silenceTimeoutMs: Long = 3500L,
        onReady: () -> Unit = {},
        onBeginning: () -> Unit = {},
        onRmsChanged: (Float) -> Unit = {},
        onPartialResults: (String) -> Unit = {},
        onResults: (String) -> Unit,
        onError: (Int) -> Unit = {}
    ) {
        stopListening()

        isActiveSession = true
        isContinuousMode = continuous

        startInternal(
            silenceTimeoutMs = silenceTimeoutMs,
            onReady = onReady,
            onBeginning = onBeginning,
            onRmsChanged = onRmsChanged,
            onPartialResults = onPartialResults,
            onResults = onResults,
            onError = onError
        )
    }

    private fun startInternal(
        silenceTimeoutMs: Long,
        onReady: () -> Unit,
        onBeginning: () -> Unit,
        onRmsChanged: (Float) -> Unit,
        onPartialResults: (String) -> Unit,
        onResults: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        if (!isActiveSession) return

        mainHandler.post {
            if (!isActiveSession) return@post
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                recognitionListener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = onReady()
                    override fun onBeginningOfSpeech() = onBeginning()
                    override fun onRmsChanged(rmsdB: Float) = onRmsChanged(rmsdB)
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.w("STT", "SpeechRecognizer error=$error continuous=$isContinuousMode")
                        val isRecoverableSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        if (isActiveSession && isContinuousMode && isRecoverableSilence) {
                            mainHandler.postDelayed({
                                if (isActiveSession && isContinuousMode) {
                                    startInternal(
                                        silenceTimeoutMs,
                                        onReady,
                                        onBeginning,
                                        onRmsChanged,
                                        onPartialResults,
                                        onResults,
                                        onError
                                    )
                                }
                            }, 180L)
                        } else {
                            if (!isContinuousMode) {
                                isActiveSession = false
                            }
                            onError(error)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val recognized = matches?.firstOrNull()?.trim().orEmpty()
                        if (recognized.isNotEmpty()) {
                            onResults(recognized)
                        }
                        if (isActiveSession && isContinuousMode) {
                            mainHandler.postDelayed({
                                if (isActiveSession && isContinuousMode) {
                                    startInternal(
                                        silenceTimeoutMs,
                                        onReady,
                                        onBeginning,
                                        onRmsChanged,
                                        onPartialResults,
                                        onResults,
                                        onError
                                    )
                                }
                            }, 200L)
                        } else {
                            isActiveSession = false
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotEmpty()) {
                            onPartialResults(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }

                speechRecognizer?.setRecognitionListener(recognitionListener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Extended silence timeouts so dictation doesn't cut off prematurely mid-sentence
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, (silenceTimeoutMs * 0.8).toLong())
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8000L)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("STT", "Failed to start SpeechRecognizer", e)
                isActiveSession = false
                onError(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    fun stopListening() {
        isActiveSession = false
        isContinuousMode = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            } finally {
                speechRecognizer = null
            }
        }
    }
}
