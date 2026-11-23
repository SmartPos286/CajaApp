package com.example.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilidad modular para generar, guardar en almacenamiento (SAF) y compartir archivos reales
 * (.csv con BOM UTF-8 para compatibilidad con Excel/Sheets, o .json) en lugar de texto plano.
 */
object InventoryExportUtils {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun buildInventoryFileName(format: String): String {
        val ext = if (format.equals("CSV", ignoreCase = true)) "csv" else "json"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "inventario_$timestamp.$ext"
    }

    fun getMimeTypeForFormat(format: String): String {
        return if (format.equals("CSV", ignoreCase = true)) "text/csv" else "application/json"
    }

    /**
     * Escribe el contenido al OutputStream incluyendo BOM UTF-8 cuando es CSV.
     */
    fun writeContentToStream(outputStream: OutputStream, content: String, isCsv: Boolean) {
        if (isCsv) {
            outputStream.write(UTF8_BOM)
        }
        outputStream.write(content.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    /**
     * Genera un archivo físico temporal en cacheDir listo para ser compartido vía FileProvider.
     */
    fun createExportFile(context: Context, content: String, format: String): File? {
        return try {
            val isCsv = format.equals("CSV", ignoreCase = true)
            val file = File(context.cacheDir, buildInventoryFileName(format))
            FileOutputStream(file).use { out ->
                writeContentToStream(out, content, isCsv)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Guarda directamente el archivo CSV o JSON en la URI seleccionada por el usuario (SAF).
     */
    fun writeExportToUri(context: Context, uri: Uri, content: String, format: String): Boolean {
        return try {
            val isCsv = format.equals("CSV", ignoreCase = true)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeContentToStream(out, content, isCsv)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Comparte el archivo físico real mediante Intent.ACTION_SEND + EXTRA_STREAM + FileProvider
     * (adjuntando ClipData para propagar permisos de lectura a través del chooser).
     */
    fun shareExportFile(context: Context, file: File, mimeType: String, chooserTitle: String = "Compartir archivo") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
