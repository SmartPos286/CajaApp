package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.local.Product
import com.example.data.local.Supplier
import com.example.data.repository.GeminiRepository
import com.example.ui.assistant.ConversationalAssistantEngine
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

object PurchaseInvoiceOcrScanner {

    fun createTempCameraImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.cacheDir, "invoice_ocr_$timeStamp.jpg")
    }

    suspend fun scanAndExtractInvoice(
        context: Context,
        imageUri: Uri,
        geminiRepository: GeminiRepository,
        existingProducts: List<Product>,
        existingSuppliers: List<Supplier>
    ): Result<ParsedInvoiceOcrResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Reconocimiento de texto en dispositivo con ML Kit Text Recognition
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = try {
                recognizer.process(inputImage).await()
            } finally {
                recognizer.close()
            }
            val rawOcrText = visionText.text.trim()

            // 2. Si hay clave API válida de Gemini, refinar extracción multimodal con imagen + OCR
            if (geminiRepository.hasValidApiKey()) {
                val base64Image = encodeUriToJpegBase64(context, imageUri)
                if (base64Image.isNotBlank() || rawOcrText.isNotBlank()) {
                    val aiResult = geminiRepository.extractPurchaseInvoiceFromImage(
                        imageBase64 = base64Image,
                        ocrRawText = rawOcrText,
                        suppliersSummary = ConversationalAssistantEngine.buildSuppliersSummary(existingSuppliers),
                        catalogSummary = ConversationalAssistantEngine.buildCatalogSummary(existingProducts)
                    )
                    val aiJson = aiResult.getOrNull()
                    if (!aiJson.isNullOrBlank()) {
                        val parsedFromAi = PurchaseInvoiceOcrParser.parseFromGeminiJson(
                            jsonText = aiJson,
                            rawOcrText = rawOcrText,
                            existingProducts = existingProducts,
                            existingSuppliers = existingSuppliers
                        )
                        if (parsedFromAi != null && (parsedFromAi.items.isNotEmpty() || parsedFromAi.supplierName.isNotBlank())) {
                            return@withContext Result.success(parsedFromAi)
                        }
                    }
                }
            }

            // 3. Fallback determinista 100% local a partir del texto reconocido por ML Kit
            val parsedLocal = PurchaseInvoiceOcrParser.parseFromRawText(
                rawText = rawOcrText,
                existingProducts = existingProducts,
                existingSuppliers = existingSuppliers
            )
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
