package com.example.ui.posterminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PosTerminalRecord
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.PdfExportActionDialog
import com.example.ui.components.ResponsivePair
import com.example.utils.InventoryExportUtils
import com.example.utils.PdfExportManager
import com.example.utils.PosTerminalExportUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosTerminalExportDialog(
    viewModel: PosViewModel,
    records: List<PosTerminalRecord>,
    filterLabel: String,
    generatedBy: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var exportFormat by remember { mutableStateOf("PDF") } // "PDF", "CSV", "JSON"
    var showPdfActionDialog by remember { mutableStateOf(false) }

    val exportText = remember(exportFormat, records) {
        when (exportFormat) {
            "CSV" -> PosTerminalExportUtils.buildCsv(records)
            "JSON" -> PosTerminalExportUtils.buildJson(records)
            else -> ""
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = PosTerminalExportUtils.writeExportToUri(context, uri, exportText, "CSV")
            Toast.makeText(
                context,
                if (ok) "Archivo .CSV guardado correctamente" else "Error al guardar el archivo .CSV",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = PosTerminalExportUtils.writeExportToUri(context, uri, exportText, "JSON")
            Toast.makeText(
                context,
                if (ok) "Archivo .JSON guardado correctamente" else "Error al guardar el archivo .JSON",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 600.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            "Exportar Registros de Punto de Venta",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${records.size} tickets • $filterLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = exportFormat == "PDF",
                    onClick = { exportFormat = "PDF" },
                    label = { Text("📄 PDF (Reporte)", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        if (exportFormat == "PDF") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pos_export_chip_pdf")
                )
                FilterChip(
                    selected = exportFormat == "CSV",
                    onClick = { exportFormat = "CSV" },
                    label = { Text("📊 CSV (Excel)", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        if (exportFormat == "CSV") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pos_export_chip_csv")
                )
                FilterChip(
                    selected = exportFormat == "JSON",
                    onClick = { exportFormat = "JSON" },
                    label = { Text("⚙️ JSON", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        if (exportFormat == "JSON") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pos_export_chip_json")
                )
            }

            if (exportFormat == "PDF") {
                val approved = remember(records) { records.filter { it.transactionStatus.equals("APROBADA", ignoreCase = true) } }
                val totalBs = remember(approved) { approved.filter { !it.currency.equals("USD", true) }.sumOf { it.totalAmount } }
                val totalUsd = remember(approved) { approved.filter { it.currency.equals("USD", true) }.sumOf { it.totalAmount } }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDC2626).copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626))
                            Text(
                                "Reporte de Tickets POS en PDF (A4)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Incluye fecha y hora, recibo, referencia, aprobación, tarjeta enmascarada, tipo de operación, monto total, estado y conciliación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("• Tickets incluidos: ${records.size} (${approved.size} aprobados)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "• Total aprobado Bs.: Bs. ${String.format(Locale.US, "%.2f", totalBs)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                        if (totalUsd > 0.001) {
                            Text(
                                "• Total aprobado USD: \$${String.format(Locale.US, "%.2f", totalUsd)} USD",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                        }
                    }
                }

                Button(
                    onClick = { showPdfActionDialog = true },
                    enabled = records.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pos_export_generate_pdf_button")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generar y Exportar PDF (${records.size})", fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 230.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = exportText.ifBlank { "No hay registros para exportar." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                ResponsivePair(
                    breakpoint = 340.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        Button(
                            onClick = {
                                val fileName = PosTerminalExportUtils.buildFileName(exportFormat)
                                if (exportFormat == "CSV") saveCsvLauncher.launch(fileName) else saveJsonLauncher.launch(fileName)
                            },
                            enabled = records.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = mod
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("pos_export_save_file_button")
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar .$exportFormat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    second = { mod ->
                        Button(
                            onClick = {
                                val file = PosTerminalExportUtils.createExportFile(context, exportText, exportFormat)
                                if (file != null) {
                                    InventoryExportUtils.shareExportFile(
                                        context = context,
                                        file = file,
                                        mimeType = PosTerminalExportUtils.getMimeTypeForFormat(exportFormat),
                                        chooserTitle = "Compartir Tickets POS (.$exportFormat)"
                                    )
                                } else {
                                    Toast.makeText(context, "Error al crear archivo .$exportFormat", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = records.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("pos_export_share_file_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enviar .$exportFormat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("TicketsPOS_$exportFormat", exportText))
                        Toast.makeText(context, "¡Datos $exportFormat copiados al portapapeles!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = records.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar al Portapapeles")
                }
            }
        }
    }

    if (showPdfActionDialog) {
        val fileDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        PdfExportActionDialog(
            title = "Exportar Tickets POS (PDF)",
            subtitle = "${records.size} registros • $filterLabel",
            suggestedFileName = "PuntosDeVenta_$fileDate.pdf",
            generatePdf = {
                PdfExportManager.generatePosTerminalRecordsPdf(
                    context = context,
                    prefs = viewModel.prefs,
                    records = records,
                    filterLabel = filterLabel,
                    generatedBy = generatedBy
                )
            },
            onDismiss = { showPdfActionDialog = false }
        )
    }
}
