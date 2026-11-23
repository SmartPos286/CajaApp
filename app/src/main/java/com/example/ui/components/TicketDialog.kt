package com.example.ui.components

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
import android.widget.Toast
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.ui.PosViewModel
import com.example.utils.PdfExportManager

@Composable
fun TicketDialog(
    sale: Sale,
    items: List<SaleItem>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Only generate bitmap if items are loaded
    if (items.isEmpty()) return

    val bitmap = remember(sale, items) {
        TicketUtils.generateTicketBitmap(context, viewModel.prefs, sale, items)
    }

    val currentPrinterConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val currentPrinterPaperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val printerMac by viewModel.printerMacAddress.collectAsStateWithLifecycle()
    val printerName by viewModel.printerName.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()

    val matchedCustomer = remember(sale, allCustomers) {
        allCustomers.find {
            (it.documentId.isNotBlank() && it.documentId.equals(sale.customerDocument, ignoreCase = true)) ||
            (it.name.isNotBlank() && it.name.equals(sale.customerName, ignoreCase = true))
        }
    }
    val customerPhone = matchedCustomer?.phone
    val customerAddress = matchedCustomer?.address ?: ""

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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Factura #${sale.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                
                HorizontalDivider()

                // Ticket Preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.LightGray)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Ticket",
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }

                HorizontalDivider()

                // Actions Column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Thermal Print Button (USB / BT Classic / BLE)
                    if (isPrinterConfigured) {
                        Button(
                            onClick = {
                                isPrinting = true
                                printStatusText = "Conectando con $printerName..."
                                viewModel.printSaleTicket(
                                    context = context,
                                    sale = sale,
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
                                Text("Imprimir Factura ($printerName)", fontWeight = FontWeight.Bold)
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

                    if (printStatusText != null && isPrinterConfigured) {
                        Text(
                            text = printStatusText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // Secondary actions: PDF, Image, WhatsApp
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
                                        .testTag("sale_pdf_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF", fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val squareBitmap = TicketUtils.generateWhatsAppSquareTicketBitmap(context, viewModel.prefs, sale, items)
                                        val file = TicketUtils.saveBitmapToFile(context, squareBitmap, sale.id)
                                        if (file != null) {
                                            TicketUtils.shareFile(context, file, "image/png")
                                        }
                                    },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Imagen", fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = {
                                        val squareBitmap = TicketUtils.generateWhatsAppSquareTicketBitmap(context, viewModel.prefs, sale, items)
                                        val file = TicketUtils.saveBitmapToFile(context, squareBitmap, sale.id)
                                        if (file != null) {
                                            TicketUtils.shareWhatsAppFile(
                                                context = context,
                                                file = file,
                                                phone = customerPhone,
                                                captionText = "Ticket de Venta N° #${sale.id} - ${viewModel.prefs.storeName}"
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f).minimumInteractiveComponentSize(),
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
                                        .testTag("sale_pdf_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF", fontSize = 12.5.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val squareBitmap = TicketUtils.generateWhatsAppSquareTicketBitmap(context, viewModel.prefs, sale, items)
                                        val file = TicketUtils.saveBitmapToFile(context, squareBitmap, sale.id)
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
                                        val squareBitmap = TicketUtils.generateWhatsAppSquareTicketBitmap(context, viewModel.prefs, sale, items)
                                        val file = TicketUtils.saveBitmapToFile(context, squareBitmap, sale.id)
                                        if (file != null) {
                                            TicketUtils.shareWhatsAppFile(
                                                context = context,
                                                file = file,
                                                phone = customerPhone,
                                                captionText = "Ticket de Venta N° #${sale.id} - ${viewModel.prefs.storeName}"
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

    if (showScanDialog) {
        PrinterScanDialog(
            viewModel = viewModel,
            onDismiss = { showScanDialog = false }
        )
    }

    if (showPdfExportDialog) {
        PdfExportActionDialog(
            title = "Factura #${sale.id} en PDF",
            subtitle = "Comprobante de ${sale.customerName.ifBlank { "Consumidor Final" }} • Total: \$${String.format(java.util.Locale.US, "%.2f", sale.totalUsd)} USD",
            suggestedFileName = "Factura_${sale.id}.pdf",
            generatePdf = {
                PdfExportManager.generateSaleInvoicePdf(
                    context = context,
                    prefs = viewModel.prefs,
                    sale = sale,
                    items = items,
                    customerPhone = customerPhone ?: "",
                    customerAddress = customerAddress
                )
            },
            onDismiss = { showPdfExportDialog = false }
        )
    }
}
}
