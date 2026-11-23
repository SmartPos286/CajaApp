package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.ui.PosViewModel
import com.example.utils.PdfExportManager
import java.util.Locale

@Composable
fun DeliveryNoteDialog(
    note: DeliveryNote,
    items: List<DeliveryNoteItem>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (items.isEmpty()) return

    val bitmap = remember(note, items) {
        TicketUtils.generateDeliveryNoteBitmap(context, viewModel.prefs, note, items)
    }
    val whatsappBitmap = remember(note, items) {
        TicketUtils.generateWhatsAppDeliveryNoteBitmap(context, viewModel.prefs, note, items)
    }

    val currentPrinterConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val currentPrinterPaperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val printerMac by viewModel.printerMacAddress.collectAsStateWithLifecycle()
    val printerName by viewModel.printerName.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()

    val customerPhone = remember(note, allCustomers) {
        val matchedCustomer = allCustomers.find {
            (it.documentId.isNotBlank() && it.documentId.equals(note.customerDocument, ignoreCase = true)) ||
            (it.name.isNotBlank() && it.name.equals(note.customerName, ignoreCase = true))
        }
        matchedCustomer?.phone
    }
    val isPrinterConfigured = viewModel.isPrinterConfigured()
    var isPrinting by remember { mutableStateOf(false) }
    var printStatusText by remember { mutableStateOf<String?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "Nota de Entrega ${note.noteNumber}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isPrinterConfigured) {
                                    val connLabel = when (currentPrinterConnectionType) {
                                        "USB" -> "USB OTG"
                                        "BLUETOOTH_CLASSIC" -> "BT Clásico"
                                        else -> "BLE"
                                    }
                                    Text(
                                        "Impresora ($connLabel • $currentPrinterPaperWidth): $printerName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF059669),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    }

                    HorizontalDivider()

                    // Ticket / Document Preview
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFE2E8F0))
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Nota de Entrega",
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    HorizontalDivider()

                    // Actions
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Thermal Print Button
                        if (isPrinterConfigured) {
                            Button(
                                onClick = {
                                    isPrinting = true
                                    printStatusText = "Conectando con $printerName..."
                                    viewModel.printDeliveryNoteTicket(
                                        context = context,
                                        note = note,
                                        items = items,
                                        onStatus = { printStatusText = it },
                                        onResult = { success, msg ->
                                            isPrinting = false
                                            printStatusText = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                enabled = !isPrinting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                if (isPrinting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(printStatusText ?: "Imprimiendo...", fontSize = 14.sp)
                                } else {
                                    Icon(Icons.Filled.Print, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Imprimir Nota ($printerName)", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showScanDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706))
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Vincular Impresora Térmica (BT / USB)", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (printStatusText != null && printerMac.isNotBlank()) {
                            Text(
                                text = printStatusText ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val wideActions = maxWidth >= 340.dp
                            if (wideActions) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showPdfExportDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("delivery_note_pdf_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF", fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val file = TicketUtils.saveDeliveryNoteBitmapToFile(context, whatsappBitmap, note.id)
                                            if (file != null) {
                                                TicketUtils.shareFile(context, file, "image/png")
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize(),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Imagen", fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    Button(
                                        onClick = {
                                            val file = TicketUtils.saveDeliveryNoteBitmapToFile(context, whatsappBitmap, note.id)
                                            if (file != null) {
                                                val noteCode = note.noteNumber.ifBlank { "NE-${note.id}" }
                                                TicketUtils.shareWhatsAppFile(
                                                    context = context,
                                                    file = file,
                                                    phone = customerPhone,
                                                    captionText = "Nota de Entrega $noteCode (No válido para crédito fiscal) - ${viewModel.prefs.storeName}"
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .minimumInteractiveComponentSize(),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("WhatsApp", fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            } else {
                                ResponsiveFlowGroup(
                                    horizontalSpacing = 8.dp,
                                    verticalSpacing = 8.dp,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    OutlinedButton(
                                        onClick = { showPdfExportDialog = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delivery_note_pdf_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF", fontSize = 12.5.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val file = TicketUtils.saveDeliveryNoteBitmapToFile(context, whatsappBitmap, note.id)
                                            if (file != null) {
                                                TicketUtils.shareFile(context, file, "image/png")
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Imagen", fontSize = 12.5.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val file = TicketUtils.saveDeliveryNoteBitmapToFile(context, whatsappBitmap, note.id)
                                            if (file != null) {
                                                val noteCode = note.noteNumber.ifBlank { "NE-${note.id}" }
                                                TicketUtils.shareWhatsAppFile(
                                                    context = context,
                                                    file = file,
                                                    phone = customerPhone,
                                                    captionText = "Nota de Entrega $noteCode (No válido para crédito fiscal) - ${viewModel.prefs.storeName}"
                                                )
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("WhatsApp", fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanDialog) {
        PrinterScanDialog(
            viewModel = viewModel,
            onDismiss = { showScanDialog = false }
        )
    }

    if (showPdfExportDialog) {
        val nCode = note.noteNumber.ifBlank { "NE-${note.id}" }
        val useBs = note.displayCurrency.equals("BS", ignoreCase = true)
        val totalSubtitle = if (useBs) {
            val estBs = if (note.totalEstimatedBs > 0.001) note.totalEstimatedBs else note.totalEstimatedUsd * note.rateBcv
            "Bs. ${String.format(Locale.US, "%.2f", estBs)}"
        } else {
            "\$${String.format(Locale.US, "%.2f", note.totalEstimatedUsd)} USD"
        }
        PdfExportActionDialog(
            title = "Nota de Entrega $nCode en PDF",
            subtitle = "Cliente: ${note.customerName} • Total: $totalSubtitle",
            suggestedFileName = "Nota_Entrega_${nCode.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf",
            generatePdf = {
                PdfExportManager.generateDeliveryNotePdf(
                    context = context,
                    prefs = viewModel.prefs,
                    note = note,
                    items = items
                )
            },
            onDismiss = { showPdfExportDialog = false }
        )
    }
}
