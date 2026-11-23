package com.example.ui.dashboard

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Product
import com.example.ui.PosViewModel
import com.example.ui.cashea.calculateCasheaMetrics
import com.example.ui.components.DateFilterPreset
import com.example.ui.components.DateFilterUtils
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.PdfExportActionDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val allSales by viewModel.allSales.collectAsStateWithLifecycle()
    val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val allPurchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val allCasheaSettlements by viewModel.allCasheaSettlements.collectAsStateWithLifecycle()
    val prefs = viewModel.prefs
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    var calendarState by remember { mutableStateOf(Calendar.getInstance()) }
    var dateFilter by remember { mutableStateOf(DateFilterUtils.thisMonth(calendarState.timeInMillis)) }
    var showPdfExportDialog by remember { mutableStateOf(false) }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es", "ES")) }
    val monthTitle = remember(calendarState) {
        monthFormat.format(calendarState.time).replaceFirstChar { it.uppercase() }
    }

    val isUsingCustomMonth = dateFilter.preset == DateFilterPreset.THIS_MONTH
    val periodTitle = if (isUsingCustomMonth) monthTitle else dateFilter.label

    // Filtered collections for the active date/range/month
    val periodSales = remember(allSales, dateFilter) {
        allSales.filter { dateFilter.matches(it.timestamp) }
    }
    val periodSaleIds = remember(periodSales) { periodSales.map { it.id }.toSet() }
    val periodSaleItems = remember(allSaleItems, periodSaleIds) {
        allSaleItems.filter { periodSaleIds.contains(it.saleId) }
    }
    val periodPurchaseInvoices = remember(allPurchaseInvoices, dateFilter) {
        allPurchaseInvoices.filter { dateFilter.matches(it.timestamp) }
    }

    val casheaMetrics = remember(allSales, allCasheaSettlements, dateFilter) {
        calculateCasheaMetrics(allSales, allCasheaSettlements, dateFilter)
    }
    val periodCasheaCommissionsUsd = casheaMetrics.periodCommissionUnrealizedUsd

    // Financial Metrics (subtracting Cashea commissions from net revenue & profit)
    val grossRevenueUsd = remember(periodSales) { periodSales.sumOf { it.totalUsd } }
    val grossRevenueBs = remember(periodSales, effectiveRate) {
        periodSales.sumOf { it.totalBs.let { bs -> if (bs > 0.001) bs else it.totalUsd * effectiveRate } }
    }
    val netRevenueUsd = remember(grossRevenueUsd, periodCasheaCommissionsUsd) {
        grossRevenueUsd - periodCasheaCommissionsUsd
    }
    val netRevenueBs = netRevenueUsd * effectiveRate

    val productMap = remember(allProducts) { allProducts.associateBy { it.id } }
    val totalCogsUsd = remember(periodSaleItems, productMap) {
        periodSaleItems.sumOf { item ->
            val product = productMap[item.productId]
            (product?.costPriceUsd ?: 0.0) * item.quantity
        }
    }

    val grossProfitUsd = remember(netRevenueUsd, totalCogsUsd) { netRevenueUsd - totalCogsUsd }
    val grossProfitMarginPct = remember(netRevenueUsd, grossProfitUsd) {
        if (netRevenueUsd > 0.001) (grossProfitUsd / netRevenueUsd) * 100.0 else 0.0
    }

    val purchaseInvoicesCount = remember(periodPurchaseInvoices) { periodPurchaseInvoices.size }
    val totalPurchasesSpentUsd = remember(periodPurchaseInvoices) { periodPurchaseInvoices.sumOf { it.totalAmount } }
    val merchandiseInflowUnits = remember(periodPurchaseInvoices) {
        periodPurchaseInvoices.sumOf { inv -> if (inv.description.contains("unid", ignoreCase = true)) 10.0 else 1.0 }
    }

    val inventoryValuation = remember(allProducts) { calculateInventoryValuation(allProducts) }
    val totalAvailableStockUnits = inventoryValuation.totalStockUnits
    val unitProductsCount = inventoryValuation.unitProductsCount
    val netInvestedCapitalUsd = inventoryValuation.netInvestedCapitalUsd
    val totalRetailInventoryValueUsd = inventoryValuation.totalRetailValueUsd
    val netInvestedCapitalBs = netInvestedCapitalUsd * effectiveRate
    val totalRetailInventoryValueBs = totalRetailInventoryValueUsd * effectiveRate
    val projectedInventoryGainUsd = totalRetailInventoryValueUsd - netInvestedCapitalUsd
    val projectedInventoryMarginPct = if (totalRetailInventoryValueUsd > 0.001) {
        (projectedInventoryGainUsd / totalRetailInventoryValueUsd) * 100.0
    } else 0.0

    val recipeComboCount = remember(allProducts) { allProducts.count { it.isCombo || it.isRecipe } }

    // Daily Bar Chart Buckets adapted to selected date range or month
    val chartDayBuckets = remember(periodSales, dateFilter) {
        val dayFmt = SimpleDateFormat("dd/MM", Locale.getDefault())
        if (dateFilter.startTimestamp != null && dateFilter.endTimestamp != null) {
            val startDay = DateFilterUtils.startOfDay(dateFilter.startTimestamp!!)
            val endDay = DateFilterUtils.startOfDay(dateFilter.endTimestamp!!)
            val daysCount = (((endDay - startDay) / (24L * 60 * 60 * 1000L)).toInt() + 1).coerceIn(1, 62)
            val buckets = FloatArray(daysCount)
            val labels = Array(daysCount) { "" }
            val cal = Calendar.getInstance().apply { timeInMillis = startDay }
            for (i in 0 until daysCount) {
                labels[i] = dayFmt.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            for (sale in periodSales) {
                val saleDay = DateFilterUtils.startOfDay(sale.timestamp)
                val idx = ((saleDay - startDay) / (24L * 60 * 60 * 1000L)).toInt()
                if (idx in 0 until daysCount) {
                    buckets[idx] += sale.totalUsd.toFloat()
                }
            }
            buckets.toList().zip(labels.toList())
        } else {
            val grouped = periodSales.groupBy { DateFilterUtils.startOfDay(it.timestamp) }
                .toSortedMap()
                .entries
                .toList()
                .takeLast(31)
            if (grouped.isEmpty()) {
                listOf(0f to "Sin datos")
            } else {
                grouped.map { (dayTs, daySales) ->
                    daySales.sumOf { it.totalUsd }.toFloat() to dayFmt.format(Date(dayTs))
                }
            }
        }
    }

    val topSellingProducts = remember(periodSaleItems) {
        periodSaleItems.groupBy { it.productName }
            .map { (name, items) ->
                DashboardProductRank(
                    productName = name,
                    quantitySold = items.sumOf { it.quantity },
                    totalRevenueUsd = items.sumOf { it.priceUsd * it.quantity }
                )
            }
            .sortedByDescending { it.quantitySold }
            .take(8)
    }

    val topFrequentCustomers = remember(periodSales) {
        periodSales.filter { it.customerName.isNotBlank() && !it.customerName.contains("Consumidor", ignoreCase = true) }
            .groupBy { if (it.customerDocument.isNotBlank()) it.customerDocument else it.customerName }
            .map { (_, custSales) ->
                val sample = custSales.first()
                DashboardCustomerRank(
                    customerName = sample.customerName,
                    document = sample.customerDocument,
                    transactionCount = custSales.size,
                    totalSpentUsd = custSales.sumOf { it.totalUsd }
                )
            }
            .sortedByDescending { it.transactionCount }
            .take(6)
    }

    val paymentCashUsd = remember(periodSales) { periodSales.sumOf { it.paymentCashUsd } }
    val paymentMobileBs = remember(periodSales) { periodSales.sumOf { it.paymentMobileBs } }
    val paymentCardBs = remember(periodSales) { periodSales.sumOf { it.paymentCardBs } }
    val paymentCasheaUsd = remember(periodSales) { periodSales.sumOf { it.paymentCasheaUsd } }
    val paymentCreditUsd = remember(periodSales) { periodSales.sumOf { it.paymentCreditUsd } }

    val paymentBreakdown = remember(paymentCashUsd, paymentMobileBs, paymentCardBs, paymentCasheaUsd, paymentCreditUsd, effectiveRate) {
        mapOf(
            "Efectivo USD" to paymentCashUsd,
            "Pago Móvil" to (paymentMobileBs / effectiveRate),
            "Punto de Venta" to (paymentCardBs / effectiveRate),
            "Cashea" to paymentCasheaUsd,
            "Ventas a Crédito" to paymentCreditUsd
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- COMPACT PERIOD HEADER + MONTH STEPPER + PDF EXPORT ---
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResponsiveActionRow(
                        breakpoint = 340.dp,
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val newCal = (calendarState.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                                        calendarState = newCal
                                        dateFilter = DateFilterUtils.thisMonth(newCal.timeInMillis)
                                    },
                                    modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes Anterior")
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = periodTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${periodSales.size} ventas • Tasa BCV: Bs. ${String.format(Locale.US, "%.2f", effectiveRate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val newCal = (calendarState.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                                        calendarState = newCal
                                        dateFilter = DateFilterUtils.thisMonth(newCal.timeInMillis)
                                    },
                                    modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.ChevronRight, contentDescription = "Mes Siguiente")
                                }
                            }
                        },
                        action = {
                            Button(
                                onClick = { showPdfExportDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_export_pdf_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )

                    DateRangeFilterBar(
                        state = dateFilter,
                        onStateChange = { newState ->
                            dateFilter = newState
                            if (newState.preset == DateFilterPreset.THIS_MONTH) {
                                calendarState = Calendar.getInstance()
                            }
                        },
                        showAllOption = true,
                        showYesterdayOption = true
                    )
                }
            }
        }

        // --- COMPACT 2x2 FINANCIAL PANELS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ResponsivePair(
                    breakpoint = 310.dp,
                    horizontalSpacing = 6.dp,
                    verticalSpacing = 6.dp,
                    first = { mod ->
                        FinancialKpiCard(
                            modifier = mod,
                            title = "INGRESOS NETOS",
                            valueUsd = String.format(Locale.US, "$%.2f USD", netRevenueUsd),
                            valueBs = String.format(Locale.US, "%.2f Bs.", netRevenueBs),
                            subtitle = if (periodCasheaCommissionsUsd > 0.001) {
                                "Bruto: \$${String.format(Locale.US, "%.2f", grossRevenueUsd)} - Com. Cashea"
                            } else {
                                "${periodSales.size} facturas emitidas"
                            },
                            icon = Icons.Filled.AttachMoney,
                            badgeColor = Color(0xFF10B981),
                            gradient = listOf(Color(0xFF064E3B), Color(0xFF022C22))
                        )
                    },
                    second = { mod ->
                        FinancialKpiCard(
                            modifier = mod,
                            title = "GANANCIA REAL",
                            valueUsd = String.format(Locale.US, "$%.2f USD", grossProfitUsd),
                            valueBs = String.format(Locale.US, "Margen: %.1f%%", grossProfitMarginPct),
                            subtitle = "Costo: $${String.format(Locale.US, "%.2f", totalCogsUsd)} USD",
                            icon = Icons.Filled.TrendingUp,
                            badgeColor = Color(0xFF3B82F6),
                            gradient = listOf(Color(0xFF1E3A8A), Color(0xFF172554))
                        )
                    }
                )

                ResponsivePair(
                    breakpoint = 310.dp,
                    horizontalSpacing = 6.dp,
                    verticalSpacing = 6.dp,
                    first = { mod ->
                        FinancialKpiCard(
                            modifier = mod,
                            title = "COMPRAS PROVEEDORES",
                            valueUsd = "$purchaseInvoicesCount fac.",
                            valueBs = String.format(Locale.US, "$%.2f USD", totalPurchasesSpentUsd),
                            subtitle = "Entradas en el período",
                            icon = Icons.Filled.LocalShipping,
                            badgeColor = Color(0xFF8B5CF6),
                            gradient = listOf(Color(0xFF4C1D95), Color(0xFF2E1065))
                        )
                    },
                    second = { mod ->
                        FinancialKpiCard(
                            modifier = mod,
                            title = "STOCK DISPONIBLE",
                            valueUsd = "${totalAvailableStockUnits.toInt()} un.",
                            valueBs = "$unitProductsCount ítems",
                            subtitle = "$recipeComboCount combos/recetas",
                            icon = Icons.Filled.Inventory2,
                            badgeColor = Color(0xFFF59E0B),
                            gradient = listOf(Color(0xFF78350F), Color(0xFF451A03))
                        )
                    }
                )

                // Compact Cashea Unrealized Commission & Pending Debt Card
                if (casheaMetrics.globalCasheaBilledUsd > 0.001 || casheaMetrics.globalSettledGrossUsd > 0.001) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_cashea_summary_card"),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        ResponsiveActionRow(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            breakpoint = 340.dp,
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.MoneyOff, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    Column {
                                        Text(
                                            text = "Ingresos No Recibidos (Comisiones Cashea)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF991B1B)
                                        )
                                        Text(
                                            text = "Por cobrar Cashea: \$${String.format(Locale.US, "%.2f", casheaMetrics.globalPendingDebtUsd)} USD",
                                            fontSize = 10.sp,
                                            color = Color(0xFFB45309),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            },
                            action = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "-\$${String.format(Locale.US, "%.2f", periodCasheaCommissionsUsd)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                    Text(
                                        text = "-Bs. ${String.format(Locale.US, "%.2f", periodCasheaCommissionsUsd * effectiveRate)}",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB91C1C)
                                    )
                                }
                            }
                        )
                    }
                }

                InventoryCapitalValuationCard(
                    netInvestedCapitalUsd = netInvestedCapitalUsd,
                    netInvestedCapitalBs = netInvestedCapitalBs,
                    totalRetailValueUsd = totalRetailInventoryValueUsd,
                    totalRetailValueBs = totalRetailInventoryValueBs,
                    projectedGainUsd = projectedInventoryGainUsd,
                    projectedMarginPct = projectedInventoryMarginPct,
                    valuedProductsCount = inventoryValuation.valuedProductsCount
                )
            }
        }

        // --- DAILY SALES BAR CHART ---
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ventas por Día",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = periodTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${chartDayBuckets.size} día(s)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    val maxVal = remember(chartDayBuckets) {
                        val m = chartDayBuckets.maxOfOrNull { it.first } ?: 100f
                        if (m < 1f) 100f else m * 1.15f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height - 22f
                            val count = chartDayBuckets.size.coerceAtLeast(1)
                            val stepX = w / count
                            val barWidth = (stepX * 0.65f).coerceIn(4f, 44f)

                            val gridColor = Color.Gray.copy(alpha = 0.2f)
                            for (i in 1..3) {
                                val gridY = h - (h * (i / 3f))
                                drawLine(gridColor, Offset(0f, gridY), Offset(w, gridY), strokeWidth = 1f)
                            }

                            chartDayBuckets.forEachIndexed { idx, (salesVal, _) ->
                                val barHeight = (salesVal / maxVal) * h
                                val centerX = (idx + 0.5f) * stepX
                                val barColor = if (salesVal > 0f) Color(0xFF38BDF8) else Color.Gray.copy(alpha = 0.15f)
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(centerX - barWidth / 2f, h - barHeight),
                                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val firstLabel = chartDayBuckets.firstOrNull()?.second ?: ""
                            val midLabel = chartDayBuckets.getOrNull(chartDayBuckets.size / 2)?.second ?: ""
                            val lastLabel = chartDayBuckets.lastOrNull()?.second ?: ""
                            Text(firstLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (chartDayBuckets.size > 2) {
                                Text(midLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (chartDayBuckets.size > 1) {
                                Text(lastLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // --- RESPONSIVE RANKINGS: TOP PRODUCTS & FREQUENT CUSTOMERS ---
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 600.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TopProductsRankCard(topSellingProducts, Modifier.weight(1f))
                        FrequentCustomersRankCard(topFrequentCustomers, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TopProductsRankCard(topSellingProducts, Modifier.fillMaxWidth())
                        FrequentCustomersRankCard(topFrequentCustomers, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showPdfExportDialog) {
        val safeTitle = periodTitle.replace(Regex("[^A-Za-z0-9_-]"), "_")
        PdfExportActionDialog(
            title = "Reporte Gerencial PDF",
            subtitle = "Período: $periodTitle • ${periodSales.size} ventas • Netos: \$${String.format(Locale.US, "%.2f", netRevenueUsd)} USD",
            suggestedFileName = "Dashboard_$safeTitle.pdf",
            generatePdf = {
                DashboardPdfExporter.generateMonthlyDashboardPdf(
                    context = context,
                    prefs = prefs,
                    monthYearTitle = periodTitle,
                    totalSalesUsd = netRevenueUsd,
                    totalSalesBs = netRevenueBs,
                    totalCogsUsd = totalCogsUsd,
                    grossProfitUsd = grossProfitUsd,
                    grossProfitMarginPct = grossProfitMarginPct,
                    purchaseInvoicesCount = purchaseInvoicesCount,
                    merchandiseInflowUnits = merchandiseInflowUnits,
                    availableStockUnits = totalAvailableStockUnits,
                    unitProductsCount = unitProductsCount,
                    topProducts = topSellingProducts,
                    topCustomers = topFrequentCustomers,
                    paymentBreakdownUsd = paymentBreakdown,
                    casheaCommissionUsd = periodCasheaCommissionsUsd,
                    grossSalesUsd = grossRevenueUsd
                )
            },
            onDismiss = { showPdfExportDialog = false }
        )
    }
}

@Composable
private fun TopProductsRankCard(
    topSellingProducts: List<DashboardProductRank>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Productos Más Vendidos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (topSellingProducts.isEmpty()) {
                Text("Sin ventas en este período", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                topSellingProducts.take(6).forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(item.productName, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "${item.quantitySold} un. • ${String.format(Locale.US, "$%.2f", item.totalRevenueUsd)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable
private fun FrequentCustomersRankCard(
    topFrequentCustomers: List<DashboardCustomerRank>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clientes Frecuentes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (topFrequentCustomers.isEmpty()) {
                Text("Sin clientes frecuentes en este período", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                topFrequentCustomers.forEachIndexed { idx, cust ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(cust.customerName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cust.document.ifBlank { "Sin doc." }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "${cust.transactionCount}x • ${String.format(Locale.US, "$%.2f", cust.totalSpentUsd)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable
private fun FinancialKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    valueUsd: String,
    valueBs: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    gradient: List<Color>
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradient))
                .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = valueUsd,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = valueBs,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 9.5.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

data class InventoryValuationSummary(
    val netInvestedCapitalUsd: Double,
    val totalRetailValueUsd: Double,
    val totalStockUnits: Double,
    val unitProductsCount: Int,
    val valuedProductsCount: Int
)

fun calculateInventoryValuation(products: List<Product>): InventoryValuationSummary {
    val physicalProducts = products.filter { !it.isCombo && !it.isRecipe }
    val inStockProducts = physicalProducts.filter { it.stock > 0.0 }
    val netCapitalUsd = inStockProducts.sumOf { it.stock * it.costPriceUsd }
    val retailValueUsd = inStockProducts.sumOf { it.stock * it.priceUsd }
    val totalUnits = physicalProducts.sumOf { it.stock }
    return InventoryValuationSummary(
        netInvestedCapitalUsd = netCapitalUsd,
        totalRetailValueUsd = retailValueUsd,
        totalStockUnits = totalUnits,
        unitProductsCount = physicalProducts.size,
        valuedProductsCount = inStockProducts.size
    )
}

@Composable
private fun InventoryCapitalValuationCard(
    netInvestedCapitalUsd: Double,
    netInvestedCapitalBs: Double,
    totalRetailValueUsd: Double,
    totalRetailValueBs: Double,
    projectedGainUsd: Double,
    projectedMarginPct: Double,
    valuedProductsCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_capital_valuation_card"),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResponsiveActionRow(
                    breakpoint = 310.dp,
                    stackedActionAlignment = Alignment.Start,
                    content = {
                        Text(
                            text = "VALORIZACIÓN INVENTARIO ($valuedProductsCount prod.)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    },
                    action = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = String.format(Locale.US, "+$%.2f est. (%.1f%%)", projectedGainUsd, projectedMarginPct),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                )

                ResponsivePair(
                    breakpoint = 300.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        Column(modifier = mod) {
                            Text("CAPITAL INVERTIDO", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7DD3FC))
                            Text(String.format(Locale.US, "$%.2f USD", netInvestedCapitalUsd), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(String.format(Locale.US, "Bs. %.2f", netInvestedCapitalBs), fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    },
                    second = { mod ->
                        Column(modifier = mod) {
                            Text("VALOR EN VENTA", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EE7B7))
                            Text(String.format(Locale.US, "$%.2f USD", totalRetailValueUsd), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(String.format(Locale.US, "Bs. %.2f", totalRetailValueBs), fontSize = 10.sp, color = Color(0xFF34D399))
                        }
                    }
                )
            }
        }
    }
}
