package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.repository.GeminiRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PosTicketOcrScanner {

    fun createTempCameraImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.cacheDir, "pos_ticket_ocr_$timeStamp.jpg")
    }

    suspend fun scanAndExtractTicket(
        context: Context,
        imageUri: Uri,
        geminiRepository: GeminiRepository
    ): Result<ParsedPosTicketResult> = withContext(Dispatchers.IO) {
        try {
            // 1. On-device OCR via ML Kit Text Recognition
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = try {
                recognizer.process(inputImage).await()
            } finally {
                recognizer.close()
            }
            val rawOcrText = visionText.text.trim()

            // 2. Multimodal refinement with Gemini if a valid API key is configured
            if (geminiRepository.hasValidApiKey()) {
                val base64Image = encodeUriToJpegBase64(context, imageUri)
                if (base64Image.isNotBlank() || rawOcrText.isNotBlank()) {
                    val aiResult = geminiRepository.extractPosTicketFromImage(
                        imageBase64 = base64Image,
                        ocrRawText = rawOcrText
                    )
                    val aiJson = aiResult.getOrNull()
                    if (!aiJson.isNullOrBlank()) {
                        val parsedAi = PosTicketOcrParser.parseFromGeminiJson(
                            jsonText = aiJson,
                            rawOcrText = rawOcrText
                        )
                        if (parsedAi != null) {
                            return@withContext Result.success(parsedAi)
                        }
                    }
                }
            }

            // 3. Deterministic local fallback parser from ML Kit text
            val parsedLocal = PosTicketOcrParser.parseFromRawText(rawOcrText)
            Result.success(parsedLocal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encodeUriToJpegBase64(context: Context, uri: Uri): String {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = 1600
            var sampleSize = 1
            val w = boundsOpts.outWidth
            val h = boundsOpts.outHeight
            if (w > maxDim || h > maxDim) {
                val halfW = w / 2
                val halfH = h / 2
                while ((halfW / sampleSize) >= maxDim || (halfH / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return ""

            try {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            ""
        }
    }
}
