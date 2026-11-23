package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebtsAndRemindersDialog(
    viewModel: PosViewModel,
    initialTab: Int = 0, // 0 = Clientes (Cobros), 1 = Proveedores (Pagos)
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(initialTab) }
    var filterStatus by remember { mutableStateOf("PENDING") } // "ALL", "PENDING", "PAID"
    var selectedSaleForPayment by remember { mutableStateOf<Sale?>(null) }
    var selectedInvoiceForPayment by remember { mutableStateOf<PurchaseInvoice?>(null) }

    val creditSales by viewModel.allCreditSales.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()

    val supplierById = remember(suppliers) { suppliers.associateBy { it.id } }
    val now = System.currentTimeMillis()
    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1L

    // Filtered lists
    val filteredCreditSales = remember(creditSales, filterStatus) {
        when (filterStatus) {
            "PENDING" -> creditSales.filter { !it.isCreditPaid }
            "PAID" -> creditSales.filter { it.isCreditPaid }
            else -> creditSales
        }
    }

    val filteredInvoices = remember(purchaseInvoices, filterStatus) {
        when (filterStatus) {
            "PENDING" -> purchaseInvoices.filter { !it.isPaid }
            "PAID" -> purchaseInvoices.filter { it.isPaid }
            else -> purchaseInvoices
        }
    }

    // Pending counts
    val pendingCreditSalesCount = creditSales.count { !it.isCreditPaid }
    val pendingInvoicesCount = purchaseInvoices.count { !it.isPaid }

    val dueTodayCreditCount = creditSales.count { !it.isCreditPaid && it.creditDueDate in dayStart..dayEnd }
    val overdueCreditCount = creditSales.count { !it.isCreditPaid && it.creditDueDate > 0 && it.creditDueDate < dayStart }

    val dueTodayInvoiceCount = purchaseInvoices.count { !it.isPaid && it.dueDate in dayStart..dayEnd }
    val overdueInvoiceCount = purchaseInvoices.count { !it.isPaid && it.dueDate > 0 && it.dueDate < dayStart }

    val totalPendingCustomerDebtUsd = creditSales.filter { !it.isCreditPaid }.sumOf { (it.paymentCreditUsd - it.creditPaidAmountUsd).coerceAtLeast(0.0) }
    val totalPendingSupplierDebtUsd = purchaseInvoices.filter { !it.isPaid }.sumOf { it.totalAmount }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 700.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        horizontalPadding = 12.dp,
        verticalPadding = 12.dp,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with Close 'X'
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
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFFD97706)
                    )
                    Text(
                        "Control de Créditos y Deudas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                indicator = {},
                divider = {},
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.PeopleAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "Clientes ($pendingCreditSalesCount)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "Proveedores ($pendingInvoicesCount)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter chips (Pendientes, Pagadas, Todas)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterStatus == "PENDING",
                    onClick = { filterStatus = "PENDING" },
                    label = { Text("Pendientes", fontSize = 11.5.sp) },
                    leadingIcon = {
                        if (filterStatus == "PENDING") Icon(Icons.Filled.Pending, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
                FilterChip(
                    selected = filterStatus == "PAID",
                    onClick = { filterStatus = "PAID" },
                    label = { Text("Pagadas / Saldadas", fontSize = 11.5.sp) },
                    leadingIcon = {
                        if (filterStatus == "PAID") Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
                FilterChip(
                    selected = filterStatus == "ALL",
                    onClick = { filterStatus = "ALL" },
                    label = { Text("Ver Todas", fontSize = 11.5.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                // --- CLIENTS CREDITS (CUENTAS POR COBRAR) ---
                // KPI Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7).copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResponsiveActionRow(
                        modifier = Modifier.padding(12.dp),
                        breakpoint = 360.dp,
                        stackedActionAlignment = Alignment.Start,
                        content = {
                            Column {
                                Text("Total Deuda Clientes Pendiente", style = MaterialTheme.typography.labelSmall, color = Color(0xFF92400E))
                                Text(
                                    "\$${String.format(Locale.US, "%.2f", totalPendingCustomerDebtUsd)} USD",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                                Text(
                                    "≈ Bs. ${String.format(Locale.US, "%.2f", totalPendingCustomerDebtUsd * bcvRate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF78350F)
                                )
                            }
                        },
                        action = {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (dueTodayCreditCount > 0) {
                                    Surface(
                                        color = Color(0xFFEF4444),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "⏰ $dueTodayCreditCount Vencen Hoy",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (overdueCreditCount > 0) {
                                    Surface(
                                        color = Color(0xFFB91C1C),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "⚠️ $overdueCreditCount Vencidos",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredCreditSales.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay registros de créditos de clientes para este filtro.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredCreditSales, key = { it.id }) { sale ->
                                val pendingDebt = (sale.paymentCreditUsd - sale.creditPaidAmountUsd).coerceAtLeast(0.0)
                                val isOverdue = !sale.isCreditPaid && sale.creditDueDate in 1 until dayStart
                                val isDueToday = !sale.isCreditPaid && sale.creditDueDate in dayStart..dayEnd

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (sale.isCreditPaid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            sale.isCreditPaid -> Color(0xFF10B981).copy(alpha = 0.4f)
                                            isDueToday || isOverdue -> Color(0xFFEF4444).copy(alpha = 0.5f)
                                            else -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ResponsiveActionRow(
                                            breakpoint = 340.dp,
                                            stackedActionAlignment = Alignment.Start,
                                            content = {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        "Factura #${sale.id}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Surface(
                                                        color = if (sale.isCreditPaid) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            if (sale.isCreditPaid) "SALDADO" else "PENDIENTE",
                                                            color = if (sale.isCreditPaid) Color(0xFF059669) else Color(0xFFD97706),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.5.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            action = {
                                                if (sale.creditDueDate > 0) {
                                                    Surface(
                                                        color = when {
                                                            sale.isCreditPaid -> MaterialTheme.colorScheme.surfaceVariant
                                                            isOverdue -> Color(0xFFEF4444)
                                                            isDueToday -> Color(0xFFF97316)
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        },
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                sale.isCreditPaid -> "Venció: ${dateFormat.format(Date(sale.creditDueDate))}"
                                                                isOverdue -> "¡VENCIDO! (${dateFormat.format(Date(sale.creditDueDate))})"
                                                                isDueToday -> "¡VENCE HOY!"
                                                                else -> "Vence: ${dateFormat.format(Date(sale.creditDueDate))}"
                                                            },
                                                            color = if (isOverdue || isDueToday) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        ResponsiveActionRow(
                                            breakpoint = 360.dp,
                                            stackedActionAlignment = Alignment.Start,
                                            content = {
                                                Column {
                                                    Text(
                                                        "Cliente: ${sale.customerName.ifBlank { "Consumidor Final" }}",
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (sale.customerDocument.isNotBlank()) {
                                                        Text(
                                                            "Doc: ${sale.customerDocument}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            action = {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        "Crédito: \$${String.format(Locale.US, "%.2f", sale.paymentCreditUsd)} USD",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "Por Cobrar: \$${String.format(Locale.US, "%.2f", pendingDebt)} USD",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (sale.isCreditPaid) Color(0xFF059669) else Color(0xFFDC2626)
                                                    )
                                                }
                                            }
                                        )

                                        if (sale.creditNotes.isNotBlank()) {
                                            Text(
                                                "Notas/Historial: ${sale.creditNotes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (!sale.isCreditPaid) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = { selectedSaleForPayment = sale },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Registrar Abono / Pagar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- SUPPLIERS INVOICES (CUENTAS POR PAGAR) ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF).copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ResponsiveActionRow(
                            modifier = Modifier.padding(12.dp),
                            breakpoint = 360.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Column {
                                    Text("Total Deuda Proveedores Pendiente", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E40AF))
                                    Text(
                                        "\$${String.format(Locale.US, "%.2f", totalPendingSupplierDebtUsd)} USD",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                    Text(
                                        "≈ Bs. ${String.format(Locale.US, "%.2f", totalPendingSupplierDebtUsd * bcvRate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1E3A8A)
                                    )
                                }
                            },
                            action = {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (dueTodayInvoiceCount > 0) {
                                        Surface(
                                            color = Color(0xFFEF4444),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "⏰ $dueTodayInvoiceCount Por Pagar Hoy",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    if (overdueInvoiceCount > 0) {
                                        Surface(
                                            color = Color(0xFFB91C1C),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "⚠️ $overdueInvoiceCount Vencidas",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredInvoices.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay facturas de compras registradas para este filtro.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredInvoices, key = { it.id }) { invoice ->
                                val supplier = supplierById[invoice.supplierId]
                                val isOverdue = !invoice.isPaid && invoice.dueDate in 1 until dayStart
                                val isDueToday = !invoice.isPaid && invoice.dueDate in dayStart..dayEnd

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (invoice.isPaid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            invoice.isPaid -> Color(0xFF10B981).copy(alpha = 0.4f)
                                            isDueToday || isOverdue -> Color(0xFFEF4444).copy(alpha = 0.5f)
                                            else -> Color(0xFF3B82F6).copy(alpha = 0.4f)
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ResponsiveActionRow(
                                            breakpoint = 340.dp,
                                            stackedActionAlignment = Alignment.Start,
                                            content = {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        "Factura #${invoice.invoiceNumber}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Surface(
                                                        color = if (invoice.isPaid) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF3B82F6).copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            if (invoice.isPaid) "PAGADA" else "POR PAGAR",
                                                            color = if (invoice.isPaid) Color(0xFF059669) else Color(0xFF2563EB),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.5.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            action = {
                                                if (invoice.dueDate > 0) {
                                                    Surface(
                                                        color = when {
                                                            invoice.isPaid -> MaterialTheme.colorScheme.surfaceVariant
                                                            isOverdue -> Color(0xFFEF4444)
                                                            isDueToday -> Color(0xFFF97316)
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        },
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                invoice.isPaid -> "Venció: ${dateFormat.format(Date(invoice.dueDate))}"
                                                                isOverdue -> "¡VENCIDA! (${dateFormat.format(Date(invoice.dueDate))})"
                                                                isDueToday -> "¡VENCE HOY!"
                                                                else -> "Vence: ${dateFormat.format(Date(invoice.dueDate))}"
                                                            },
                                                            color = if (isOverdue || isDueToday) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        ResponsiveActionRow(
                                            breakpoint = 360.dp,
                                            stackedActionAlignment = Alignment.Start,
                                            content = {
                                                Column {
                                                    Text(
                                                        "Proveedor: ${supplier?.name ?: "Proveedor #${invoice.supplierId}"}",
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (invoice.description.isNotBlank()) {
                                                        Text(
                                                            invoice.description,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            action = {
                                                Text(
                                                    "\$${String.format(Locale.US, "%.2f", invoice.totalAmount)} USD",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (invoice.isPaid) Color(0xFF059669) else Color(0xFF1D4ED8)
                                                )
                                            }
                                        )

                                        if (invoice.paymentNotes.isNotBlank()) {
                                            Text(
                                                "Notas de Pago: ${invoice.paymentNotes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            OutlinedButton(
                                                onClick = { selectedInvoiceForPayment = invoice },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    if (invoice.isPaid) Icons.Filled.Edit else Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (invoice.isPaid) "Modificar Estado" else "Marcar como Pagada", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    // Modal to record payment for Customer Credit
    selectedSaleForPayment?.let { sale ->
        val pendingDebt = (sale.paymentCreditUsd - sale.creditPaidAmountUsd).coerceAtLeast(0.0)
        var paymentAmountInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", pendingDebt)) }
        var paymentMethod by remember { mutableStateOf("Efectivo USD") }
        var paymentNotes by remember { mutableStateOf("") }

        KeyboardAwareAlertDialog(
            onDismissRequest = { selectedSaleForPayment = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cobrar Deuda a Cliente",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { selectedSaleForPayment = null },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Cliente: ${sale.customerName} (Factura #${sale.id})",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Deuda Pendiente: \$${String.format(Locale.US, "%.2f", pendingDebt)} USD",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = paymentAmountInput,
                        onValueChange = { paymentAmountInput = it },
                        label = { Text("Monto a Abonar / Cobrar (\$ USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("Método de Cobro:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Efectivo USD", "Pago Móvil", "Transferencia / Punto").forEach { m ->
                            FilterChip(
                                selected = paymentMethod == m,
                                onClick = { paymentMethod = m },
                                label = { Text(m, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = paymentNotes,
                        onValueChange = { paymentNotes = it },
                        label = { Text("Referencia o Nota de Cobro (Opcional)") },
                        placeholder = { Text("Ej: Ref Pago Móvil #1234") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentAmountInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            viewModel.payCustomerCredit(
                                saleId = sale.id,
                                amountPaidUsd = amount,
                                paymentMethod = paymentMethod,
                                notes = paymentNotes.trim()
                            ) {
                                Toast.makeText(context, "¡Abono registrado con éxito!", Toast.LENGTH_SHORT).show()
                                selectedSaleForPayment = null
                            }
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirmar Cobro")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedSaleForPayment = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal to update supplier invoice payment status
    selectedInvoiceForPayment?.let { invoice ->
        var isPaidState by remember { mutableStateOf(!invoice.isPaid) }
        var invoiceNotes by remember { mutableStateOf(invoice.paymentNotes) }

        KeyboardAwareAlertDialog(
            onDismissRequest = { selectedInvoiceForPayment = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Estado de Factura de Compra",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { selectedInvoiceForPayment = null },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Factura #${invoice.invoiceNumber} - Monto: \$${String.format(Locale.US, "%.2f", invoice.totalAmount)} USD",
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "¿Factura Pagada al Proveedor?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Switch(
                            checked = isPaidState,
                            onCheckedChange = { isPaidState = it }
                        )
                    }

                    OutlinedTextField(
                        value = invoiceNotes,
                        onValueChange = { invoiceNotes = it },
                        label = { Text("Notas de pago / Referencia bancaria") },
                        placeholder = { Text("Ej: Pagado vía transferencia banco Banesco...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePurchaseInvoicePayment(
                            invoiceId = invoice.id,
                            isPaid = isPaidState,
                            notes = invoiceNotes.trim()
                        ) {
                            Toast.makeText(context, "Estado de factura actualizado", Toast.LENGTH_SHORT).show()
                            selectedInvoiceForPayment = null
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Guardar Cambio")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedInvoiceForPayment = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
