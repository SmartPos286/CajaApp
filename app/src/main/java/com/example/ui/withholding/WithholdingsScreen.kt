package com.example.ui.withholding

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WithholdingsScreen(viewModel: PosViewModel) {
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Retenciones de Clientes, 1: Retenciones a Proveedores

    // Sales with withholdings
    val clientWithholdingSales = remember(sales) {
        sales.filter { it.retencionIvaUsd > 0.001 || it.retencionIslrUsd > 0.001 || it.withholdingStatus != "SIN_RETENCION" }
    }

    // Purchase Invoices with withholdings
    val supplierWithholdingInvoices = remember(purchaseInvoices) {
        purchaseInvoices.filter { it.retencionIvaUsd > 0.001 || it.retencionIslrUsd > 0.001 || it.withholdingStatus != "NO_APLICA" }
    }

    // Totals calculations
    val totalCustomerRetentionPendingUsd = clientWithholdingSales
        .filter { it.withholdingStatus != "RETENIDO_OK" }
        .sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val totalCustomerRetentionCollectedUsd = clientWithholdingSales
        .filter { it.withholdingStatus == "RETENIDO_OK" }
        .sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val totalSupplierRetentionPendingUsd = supplierWithholdingInvoices
        .filter { it.withholdingStatus != "ENTERADO_SENIAT" }
        .sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val totalSupplierRetentionEnteredUsd = supplierWithholdingInvoices
        .filter { it.withholdingStatus == "ENTERADO_SENIAT" }
        .sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Deudas y Créditos por Retenciones (SENIAT)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Control fiscal de comprobantes de retención IVA e ISLR de Clientes y Proveedores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Summary Metrics Cards
            ResponsivePair(
                breakpoint = 340.dp,
                horizontalSpacing = 10.dp,
                verticalSpacing = 8.dp,
                first = { mod ->
                    // Client Withholdings Outstanding Card (Crédito a Favor)
                    Card(
                        modifier = mod,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.MoveToInbox, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                                Text("Retenciones de Clientes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                            }
                            Text(
                                "\$${String.format(Locale.US, "%.2f", totalCustomerRetentionPendingUsd)} USD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                "Por cobrar voucher (${String.format(Locale.US, "%.2f", totalCustomerRetentionPendingUsd * effectiveRate)} Bs)",
                                fontSize = 11.sp,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                },
                second = { mod ->
                    // Supplier Withholdings Pending SENIAT Card (Deuda con SENIAT)
                    Card(
                        modifier = mod,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color(0xFFB91C1C), modifier = Modifier.size(18.dp))
                                Text("Deuda Enterar al SENIAT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            }
                            Text(
                                "\$${String.format(Locale.US, "%.2f", totalSupplierRetentionPendingUsd)} USD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F1D1D)
                            )
                            Text(
                                "Pendiente de enterar (${String.format(Locale.US, "%.2f", totalSupplierRetentionPendingUsd * effectiveRate)} Bs)",
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Selector
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Retenciones Generadas por Clientes (${clientWithholdingSales.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.CallReceived, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Retenciones Aplicadas a Proveedores (${supplierWithholdingInvoices.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.CallMade, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                // Customer Withholdings View
                if (clientWithholdingSales.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("No hay facturas con retención de clientes registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(clientWithholdingSales, key = { it.id }) { sale ->
                            CustomerWithholdingCard(sale = sale, viewModel = viewModel)
                        }
                    }
                }
            } else {
                // Supplier Withholdings View
                if (supplierWithholdingInvoices.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("No hay facturas de proveedores con retención registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(supplierWithholdingInvoices, key = { it.id }) { invoice ->
                            val supplier = suppliers.find { it.id == invoice.supplierId }
                            SupplierWithholdingCard(invoice = invoice, supplierName = supplier?.name ?: "Proveedor #${invoice.supplierId}", viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerWithholdingCard(sale: Sale, viewModel: PosViewModel) {
    val context = LocalContext.current
    var showVoucherDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val totalRetainedUsd = sale.retencionIvaUsd + sale.retencionIslrUsd

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResponsiveActionRow(
                breakpoint = 330.dp,
                verticalSpacing = 6.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Column {
                        Text(sale.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("RIF/CI: ${sale.customerDocument}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                action = {
                    val (statusBg, statusText, statusLabel) = when (sale.withholdingStatus) {
                        "RETENIDO_OK" -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "COMPROBANTE RECIBIDO ✓")
                        else -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "PENDIENTE VOUCHER ⏳")
                    }

                    Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                        Text(
                            statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            ResponsiveActionRow(
                breakpoint = 310.dp,
                verticalSpacing = 4.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Column {
                        Text("Factura #${sale.id} • ${dateFormat.format(Date(sale.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Monto Total Factura: \$${String.format(Locale.US, "%.2f", sale.totalUsd)} USD", style = MaterialTheme.typography.bodySmall)
                    }
                },
                action = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Monto Retenido:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("\$${String.format(Locale.US, "%.2f", totalRetainedUsd)} USD", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), style = MaterialTheme.typography.titleMedium)
                    }
                }
            )

            // Breakdown
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Ret. IVA (${sale.retencionIvaPercentage.toInt()}%): \$${String.format(Locale.US, "%.2f", sale.retencionIvaUsd)} USD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ret. ISLR (${sale.retencionIslrPercentage.toInt()}%): \$${String.format(Locale.US, "%.2f", sale.retencionIslrUsd)} USD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            ResponsiveActionRow(
                breakpoint = 340.dp,
                verticalSpacing = 8.dp,
                stackedActionAlignment = Alignment.End,
                content = {
                    Text(
                        text = if (sale.withholdingVoucherNumber.isNotBlank()) "N° Comprobante: ${sale.withholdingVoucherNumber}" else "Sin N° comprobante asignado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                action = {
                    Button(
                        onClick = { showVoucherDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (sale.withholdingStatus == "RETENIDO_OK") "Editar Comprobante" else "Registrar Comprobante", fontSize = 12.sp)
                    }
                }
            )
        }
    }

    if (showVoucherDialog) {
        var voucherNum by remember { mutableStateOf(sale.withholdingVoucherNumber) }

        KeyboardAwareAlertDialog(
            onDismissRequest = { showVoucherDialog = false },
            title = { Text("Comprobante de Retención del Cliente", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ingresa el número de comprobante entregado por el cliente (${sale.customerName}):")
                    OutlinedTextField(
                        value = voucherNum,
                        onValueChange = { voucherNum = it },
                        label = { Text("N° Comprobante SENIAT *") },
                        placeholder = { Text("Ej: 2026090001234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (voucherNum.isNotBlank()) {
                            viewModel.updateSaleWithholding(
                                saleId = sale.id,
                                voucherNumber = voucherNum.trim(),
                                status = "RETENIDO_OK"
                            )
                            Toast.makeText(context, "Comprobante guardado y marcado como recibido", Toast.LENGTH_SHORT).show()
                            showVoucherDialog = false
                        }
                    }
                ) {
                    Text("Guardar Comprobante")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoucherDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplierWithholdingCard(invoice: PurchaseInvoice, supplierName: String, viewModel: PosViewModel) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var showEnteradoDialog by remember { mutableStateOf(false) }

    val totalRetainedUsd = invoice.retencionIvaUsd + invoice.retencionIslrUsd

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResponsiveActionRow(
                breakpoint = 330.dp,
                verticalSpacing = 6.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Column {
                        Text(supplierName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Factura Compra #${invoice.invoiceNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                action = {
                    val (statusBg, statusText, statusLabel) = when (invoice.withholdingStatus) {
                        "ENTERADO_SENIAT" -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "ENTERADO AL SENIAT ✓")
                        else -> Triple(Color(0xFFFEF2F2), Color(0xFF991B1B), "PENDIENTE ENTERAR SENIAT ⚠️")
                    }

                    Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                        Text(
                            statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            ResponsiveActionRow(
                breakpoint = 310.dp,
                verticalSpacing = 4.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Column {
                        Text("Fecha: ${dateFormat.format(Date(invoice.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Monto Total Factura: \$${String.format(Locale.US, "%.2f", invoice.totalAmount)} USD", style = MaterialTheme.typography.bodySmall)
                    }
                },
                action = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Retención Practicada:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("\$${String.format(Locale.US, "%.2f", totalRetainedUsd)} USD", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), style = MaterialTheme.typography.titleMedium)
                    }
                }
            )

            // Breakdown
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Ret. IVA (${invoice.retencionIvaPercentage.toInt()}%): \$${String.format(Locale.US, "%.2f", invoice.retencionIvaUsd)} USD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ret. ISLR (${invoice.retencionIslrPercentage.toInt()}%): \$${String.format(Locale.US, "%.2f", invoice.retencionIslrUsd)} USD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            ResponsiveActionRow(
                breakpoint = 340.dp,
                verticalSpacing = 8.dp,
                stackedActionAlignment = Alignment.End,
                content = {
                    Text(
                        text = if (invoice.withholdingVoucherNumber.isNotBlank()) "N° Comprobante Emitido: ${invoice.withholdingVoucherNumber}" else "Sin N° comprobante",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                action = {
                    if (invoice.withholdingStatus != "ENTERADO_SENIAT") {
                        Button(
                            onClick = { showEnteradoDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Marcar Enterado en SENIAT", fontSize = 12.sp)
                        }
                    }
                }
            )
        }
    }

    if (showEnteradoDialog) {
        KeyboardAwareAlertDialog(
            onDismissRequest = { showEnteradoDialog = false },
            title = { Text("Declaración / Enteramiento SENIAT", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Confirmas que la retención por \$${String.format(Locale.US, "%.2f", totalRetainedUsd)} USD ya fue declarada y enterada en el portal del SENIAT?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePurchaseInvoiceWithholding(
                            id = invoice.id,
                            voucherNumber = invoice.withholdingVoucherNumber,
                            status = "ENTERADO_SENIAT"
                        )
                        Toast.makeText(context, "Retención registrada como enterada al SENIAT", Toast.LENGTH_SHORT).show()
                        showEnteradoDialog = false
                    }
                ) {
                    Text("Confirmar Enteramiento")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnteradoDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
