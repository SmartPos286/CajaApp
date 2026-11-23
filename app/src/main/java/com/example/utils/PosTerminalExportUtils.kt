package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.local.PosTerminalRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PosTerminalExportUtils {

    private val prettyJson = Json { prettyPrint = true }

    fun buildFileName(format: String): String {
        val ext = if (format.equals("CSV", ignoreCase = true)) "csv" else "json"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "registros_punto_venta_$timestamp.$ext"
    }

    fun getMimeTypeForFormat(format: String): String {
        return if (format.equals("CSV", ignoreCase = true)) "text/csv" else "application/json"
    }

    fun buildCsv(records: List<PosTerminalRecord>): String {
        val sb = StringBuilder()
        sb.appendLine(
            "ID,Fecha y Hora,Numero de Recibo,Numero de Referencia,Numero de Aprobacion,Tarjeta Enmascarada,Tipo de Operacion,Monto Total,Moneda,Estado de Transaccion,Banco / Terminal,Notas,Registrado Por"
        )
        records.forEach { rec ->
            sb.appendLine(
                listOf(
                    rec.id.toString(),
                    escapeCsv(rec.transactionDateTime),
                    escapeCsv(rec.receiptNumber),
                    escapeCsv(rec.referenceNumber),
                    escapeCsv(rec.approvalNumber),
                    escapeCsv(rec.maskedCardNumber),
                    escapeCsv(rec.operationType),
                    String.format(Locale.US, "%.2f", rec.totalAmount),
                    escapeCsv(rec.currency),
                    escapeCsv(rec.transactionStatus),
                    escapeCsv(rec.bankOrTerminal),
                    escapeCsv(rec.notes),
                    escapeCsv(rec.registeredBy)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildJson(records: List<PosTerminalRecord>): String {
        return prettyJson.encodeToString(records)
    }

    fun createExportFile(context: Context, content: String, format: String): File? {
        return try {
            val isCsv = format.equals("CSV", ignoreCase = true)
            val file = File(context.cacheDir, buildFileName(format))
            FileOutputStream(file).use { out ->
                InventoryExportUtils.writeContentToStream(out, content, isCsv)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeExportToUri(context: Context, uri: Uri, content: String, format: String): Boolean {
        val isCsv = format.equals("CSV", ignoreCase = true)
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                InventoryExportUtils.writeContentToStream(out, content, isCsv)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(value: String): String {
        val sanitized = value.replace("\r", " ").replace("\n", " ")
        return if (sanitized.contains(",") || sanitized.contains("\"")) {
            "\"${sanitized.replace("\"", "\"\"")}\""
        } else {
            sanitized
        }
    }
}
