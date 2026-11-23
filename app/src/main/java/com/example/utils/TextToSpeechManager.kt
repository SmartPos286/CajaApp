package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                isInitialized = true
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            // Clean text for speech: remove markdown symbols, emojis and other characters not needed for audio
            val cleanText = text
                .replace("*", "")
                .replace("_", "")
                .replace("#", "")
                .replace("`", "")
                .replace("~", "")
                .replace(">", "")
                // Remove most emojis/special symbols but keep basic punctuation
                .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")
                .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // Keep link text, remove URL
                .trim()

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "pos_tts_utterance")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
