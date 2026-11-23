package com.example.ui.cashea

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CasheaSettlement
import com.example.data.local.Sale
import com.example.ui.PosViewModel
import com.example.ui.components.DateFilterUtils
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.TicketDialog
import com.example.ui.components.bringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CasheaSummaryMetrics(
    val globalCasheaBilledUsd: Double,
    val globalSettledGrossUsd: Double,
    val globalPendingDebtUsd: Double,
    val globalOverpaidUsd: Double,
    val globalCommissionUnrealizedUsd: Double,
    val globalNetReceivedUsd: Double,
    val periodCasheaBilledUsd: Double,
    val periodCasheaSalesCount: Int,
    val periodSettledGrossUsd: Double,
    val periodCommissionUnrealizedUsd: Double,
    val periodNetReceivedUsd: Double,
    val periodAvgCommissionPct: Double
)

fun calculateCasheaMetrics(
    allSales: List<Sale>,
    allSettlements: List<CasheaSettlement>,
    filter: com.example.ui.components.DateRangeFilterState
): CasheaSummaryMetrics {
    val casheaSales = allSales.filter { it.paymentCasheaUsd > 0.001 }
    val globalBilled = casheaSales.sumOf { it.paymentCasheaUsd }
    val globalSettledGross = allSettlements.sumOf { it.grossAmountUsd }
    val globalPending = maxOf(0.0, globalBilled - globalSettledGross)
    val globalOverpaid = maxOf(0.0, globalSettledGross - globalBilled)
    val globalCommission = allSettlements.sumOf { it.commissionAmountUsd }
    val globalNet = allSettlements.sumOf { it.netReceivedUsd }

    val periodSales = casheaSales.filter { filter.matches(it.timestamp) }
    val periodSettlements = allSettlements.filter { filter.matches(it.settlementDate) }

    val periodBilled = periodSales.sumOf { it.paymentCasheaUsd }
    val periodGross = periodSettlements.sumOf { it.grossAmountUsd }
    val periodCommission = periodSettlements.sumOf { it.commissionAmountUsd }
    val periodNet = periodSettlements.sumOf { it.netReceivedUsd }
    val avgPct = if (periodGross > 0.001) (periodCommission / periodGross) * 100.0 else 0.0

    return CasheaSummaryMetrics(
        globalCasheaBilledUsd = globalBilled,
        globalSettledGrossUsd = globalSettledGross,
        globalPendingDebtUsd = globalPending,
        globalOverpaidUsd = globalOverpaid,
        globalCommissionUnrealizedUsd = globalCommission,
        globalNetReceivedUsd = globalNet,
        periodCasheaBilledUsd = periodBilled,
        periodCasheaSalesCount = periodSales.size,
        periodSettledGrossUsd = periodGross,
        periodCommissionUnrealizedUsd = periodCommission,
        periodNetReceivedUsd = periodNet,
        periodAvgCommissionPct = avgPct
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CasheaScreen(viewModel: PosViewModel) {
    val allSales by viewModel.allSales.collectAsStateWithLifecycle()
    val allSettlements by viewModel.allCasheaSettlements.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    var dateFilter by remember { mutableStateOf(DateFilterUtils.allTime()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Liquidaciones, 1: Ventas Cashea
    var showAddDialog by remember { mutableStateOf(false) }
    var settlementToDelete by remember { mutableStateOf<CasheaSettlement?>(null) }
    var selectedSale by remember { mutableStateOf<Sale?>(null) }

    val metrics = remember(allSales, allSettlements, dateFilter) {
        calculateCasheaMetrics(allSales, allSettlements, dateFilter)
    }

    val filteredSettlements = remember(allSettlements, dateFilter) {
        allSettlements.filter { dateFilter.matches(it.settlementDate) }
    }
    val filteredCasheaSales = remember(allSales, dateFilter) {
        allSales.filter { it.paymentCasheaUsd > 0.001 && dateFilter.matches(it.timestamp) }
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Filter Bar + Primary Register Action (responsive stacking on phones)
            ResponsiveActionRow(
                breakpoint = 520.dp,
                stackedActionAlignment = Alignment.End,
                content = {
                    DateRangeFilterBar(
                        state = dateFilter,
                        onStateChange = { dateFilter = it },
                        showAllOption = true,
                        showYesterdayOption = true
                    )
                },
                action = {
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFCA8A04),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cashea_add_settlement_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Registrar Pago", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )

            // Compact 2x2 KPI Strip
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ResponsivePair(
                    breakpoint = 310.dp,
                    horizontalSpacing = 6.dp,
                    verticalSpacing = 6.dp,
                    first = { mod ->
                        CasheaCompactKpi(
                            modifier = mod,
                            title = "Facturado Cashea",
                            amountUsd = metrics.periodCasheaBilledUsd,
                            subText = "${metrics.periodCasheaSalesCount} ventas • Bs. ${String.format(Locale.US, "%.2f", metrics.periodCasheaBilledUsd * effectiveRate)}",
                            accentColor = Color(0xFFB45309),
                            containerColor = Color(0xFFFEF3C7).copy(alpha = 0.7f),
                            icon = Icons.Filled.QrCode2
                        )
                    },
                    second = { mod ->
                        CasheaCompactKpi(
                            modifier = mod,
                            title = "Deuda Pendiente Cashea",
                            amountUsd = metrics.globalPendingDebtUsd,
                            subText = if (metrics.globalOverpaidUsd > 0.009) {
                                "Saldo a favor: +\$${String.format(Locale.US, "%.2f", metrics.globalOverpaidUsd)}"
                            } else {
                                "Abonado total: \$${String.format(Locale.US, "%.2f", metrics.globalSettledGrossUsd)}"
                            },
                            accentColor = if (metrics.globalPendingDebtUsd > 0.009) Color(0xFFD97706) else Color(0xFF059669),
                            containerColor = if (metrics.globalPendingDebtUsd > 0.009) {
                                Color(0xFFFFFBEB)
                            } else {
                                Color(0xFFECFDF5)
                            },
                            icon = Icons.Filled.AccountBalanceWallet
                        )
                    }
                )

                ResponsivePair(
                    breakpoint = 310.dp,
                    horizontalSpacing = 6.dp,
                    verticalSpacing = 6.dp,
                    first = { mod ->
                        CasheaCompactKpi(
                            modifier = mod,
                            title = "Recibido Neto",
                            amountUsd = metrics.periodNetReceivedUsd,
                            subText = "Liquidado: \$${String.format(Locale.US, "%.2f", metrics.periodSettledGrossUsd)} USD",
                            accentColor = Color(0xFF047857),
                            containerColor = Color(0xFFECFDF5).copy(alpha = 0.8f),
                            icon = Icons.Filled.CheckCircle
                        )
                    },
                    second = { mod ->
                        // Dedicated Section: Ingresos no recibidos por comisiones de Cashea
                        Surface(
                            modifier = mod.testTag("cashea_unrealized_commissions_card"),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.MoneyOff,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Ingresos No Recibidos (Comisiones)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "-\$${String.format(Locale.US, "%.2f", metrics.periodCommissionUnrealizedUsd)} USD",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                                Text(
                                    text = "Prom: ${String.format(Locale.US, "%.1f", metrics.periodAvgCommissionPct)}% • Restado de ingresos",
                                    fontSize = 10.sp,
                                    color = Color(0xFFB91C1C),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                )
            }

            // Compact Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Pagos Cashea (${filteredSettlements.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Ventas Cashea (${filteredCasheaSales.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    if (filteredSettlements.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Payments,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "No hay pagos de Cashea registrados en este período.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Usa \"Registrar Pago\" para descontar lo liquidado y su comisión.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredSettlements, key = { it.id }) { settlement ->
                                CasheaSettlementCompactRow(
                                    settlement = settlement,
                                    dateText = dateFormat.format(Date(settlement.settlementDate)),
                                    effectiveRate = effectiveRate,
                                    onDelete = { settlementToDelete = settlement }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    if (filteredCasheaSales.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "No hay facturas pagadas con Cashea en este período.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredCasheaSales, key = { it.id }) { sale ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSale = sale },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                ) {
                                    ResponsiveActionRow(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        breakpoint = 310.dp,
                                        content = {
                                            Column {
                                                Text(
                                                    "Factura #${sale.id} • ${sale.customerName}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "${dateTimeFormat.format(Date(sale.timestamp))} • ${sale.customerDocument}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        action = {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "\$${String.format(Locale.US, "%.2f", sale.paymentCasheaUsd)} USD",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309)
                                                )
                                                Text(
                                                    "Total fac: \$${String.format(Locale.US, "%.2f", sale.totalUsd)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RegisterCasheaSettlementDialog(
            pendingDebtUsd = metrics.globalPendingDebtUsd,
            effectiveRate = effectiveRate,
            onDismiss = { showAddDialog = false },
            onSave = { settlementDate, grossAmountUsd, commissionPct, notes ->
                viewModel.addCasheaSettlement(
                    settlementDate = settlementDate,
                    grossAmountUsd = grossAmountUsd,
                    commissionPercentage = commissionPct,
                    notes = notes
                ) {
                    showAddDialog = false
                }
            }
        )
    }

    settlementToDelete?.let { settlement ->
        KeyboardAwareAlertDialog(
            onDismissRequest = { settlementToDelete = null },
            title = { Text("Eliminar Pago Cashea", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Deseas eliminar la liquidación del ${dateFormat.format(Date(settlement.settlementDate))} por \$${String.format(Locale.US, "%.2f", settlement.grossAmountUsd)} USD? El monto volverá a sumarse a la deuda pendiente de Cashea."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCasheaSettlement(settlement)
                        settlementToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { settlementToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    selectedSale?.let { sale ->
        val items by viewModel.getItemsForSale(sale.id).collectAsStateWithLifecycle(initialValue = emptyList())
        TicketDialog(
            sale = sale,
            items = items,
            viewModel = viewModel,
            onDismiss = { selectedSale = null }
        )
    }
}

@Composable
private fun CasheaCompactKpi(
    modifier: Modifier = Modifier,
    title: String,
    amountUsd: Double,
    subText: String,
    accentColor: Color,
    containerColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                Text(
                    text = title,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "\$${String.format(Locale.US, "%.2f", amountUsd)} USD",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = subText,
                fontSize = 10.sp,
                color = accentColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CasheaSettlementCompactRow(
    settlement: CasheaSettlement,
    dateText: String,
    effectiveRate: Double,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        ResponsiveActionRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            breakpoint = 330.dp,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = "-${String.format(Locale.US, "%.1f", settlement.commissionPercentage)}% com.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = "Liquidado: \$${String.format(Locale.US, "%.2f", settlement.grossAmountUsd)} • No recibido: -\$${String.format(Locale.US, "%.2f", settlement.commissionAmountUsd)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (settlement.notes.isNotBlank()) {
                        Text(
                            text = settlement.notes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            action = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "+\$${String.format(Locale.US, "%.2f", settlement.netReceivedUsd)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                        Text(
                            text = "Bs. ${String.format(Locale.US, "%.2f", settlement.netReceivedUsd * effectiveRate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar liquidación",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RegisterCasheaSettlementDialog(
    pendingDebtUsd: Double,
    effectiveRate: Double,
    onDismiss: () -> Unit,
    onSave: (settlementDate: Long, grossAmountUsd: Double, commissionPct: Double, notes: String) -> Unit
) {
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var grossAmountStr by remember { mutableStateOf("") }
    var commissionPctStr by remember { mutableStateOf("5") }
    var notesStr by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val grossAmount = grossAmountStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val commissionPct = (commissionPctStr.replace(",", ".").toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    val commissionUsd = grossAmount * (commissionPct / 100.0)
    val netReceivedUsd = maxOf(0.0, grossAmount - commissionUsd)

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color(0xFFB45309))
                Text("Registrar Pago de Cashea", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fecha de liquidación
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashea_settlement_date_button")
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fecha de pago: ${dateFormat.format(Date(selectedDateMillis))}")
                }

                // Monto liquidado (descontar de deuda Cashea)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ResponsiveActionRow(
                        breakpoint = 300.dp,
                        stackedActionAlignment = Alignment.Start,
                        content = {
                            Text(
                                "Monto bruto pagado por Cashea",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        action = {
                            if (pendingDebtUsd > 0.009) {
                                TextButton(
                                    onClick = {
                                        grossAmountStr = String.format(Locale.US, "%.2f", pendingDebtUsd)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Usar deuda (\$${String.format(Locale.US, "%.2f", pendingDebtUsd)})", fontSize = 11.sp)
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = grossAmountStr,
                        onValueChange = { grossAmountStr = it },
                        placeholder = { Text("0.00") },
                        prefix = { Text("$ ", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("cashea_settlement_amount_input")
                    )
                }

                // Porcentaje de comisión quitado por Cashea
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Comisión que quitó Cashea (%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("4", "5", "8", "10").forEach { pctPreset ->
                            FilterChip(
                                selected = commissionPctStr == pctPreset,
                                onClick = { commissionPctStr = pctPreset },
                                label = { Text("$pctPreset%", fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = commissionPctStr,
                        onValueChange = { commissionPctStr = it },
                        placeholder = { Text("Ej: 5.0") },
                        suffix = { Text("%", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("cashea_settlement_commission_input")
                    )
                }

                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    label = { Text("Referencia / Nota (opcional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                )

                // Live Preview
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Se resta a deuda Cashea:", fontSize = 11.5.sp)
                            Text("-\$${String.format(Locale.US, "%.2f", grossAmount)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Comisión (Ingreso no recibido):", fontSize = 11.5.sp, color = Color(0xFFDC2626))
                            Text(
                                "-\$${String.format(Locale.US, "%.2f", commissionUsd)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Neto recibido en cuenta:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                            Text(
                                "\$${String.format(Locale.US, "%.2f", netReceivedUsd)} (Bs. ${String.format(Locale.US, "%.2f", netReceivedUsd * effectiveRate)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = grossAmount > 0.001,
                onClick = {
                    onSave(selectedDateMillis, grossAmount, commissionPct, notesStr)
                },
                modifier = Modifier.testTag("cashea_settlement_confirm_button")
            ) {
                Text("Guardar Pago")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            selectedDateMillis = DateFilterUtils.utcPickerMillisToLocalDayMillis(utcMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
