package com.example.ui.summary

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CasheaSettlement
import com.example.data.local.Expense
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.ui.PosViewModel
import com.example.ui.components.DateFilterUtils
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.PdfExportActionDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsiveFlowGroup
import com.example.ui.components.ResponsivePair
import com.example.ui.components.TicketUtils
import com.example.utils.AccountingLedgerCsvExporter
import com.example.utils.PdfExportManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val deliveryNotes by viewModel.allDeliveryNotes.collectAsStateWithLifecycle()
    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val casheaSettlements by viewModel.allCasheaSettlements.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var dateFilter by remember { mutableStateOf(DateFilterUtils.today()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: General, 1: Detallado, 2: Retenciones
    var showClosureDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }

    val dateTimeSdf = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    val filteredSales = remember(sales, dateFilter) {
        sales.filter { dateFilter.matches(it.timestamp) }
    }
    val filteredDeliveryNotes = remember(deliveryNotes, dateFilter) {
        deliveryNotes.filter { dateFilter.matches(it.timestamp) }
    }
    val filteredExpenses = remember(expenses, dateFilter) {
        expenses.filter { dateFilter.matches(it.timestamp) }
    }
    val filteredPurchaseInvoices = remember(purchaseInvoices, dateFilter) {
        purchaseInvoices.filter { dateFilter.matches(it.timestamp) }
    }
    val filteredCasheaSettlements = remember(casheaSettlements, dateFilter) {
        casheaSettlements.filter { dateFilter.matches(it.settlementDate) }
    }

    val filteredSaleIds = remember(filteredSales) { filteredSales.map { it.id } }
    val filteredItems by viewModel.getItemsForSales(filteredSaleIds).collectAsStateWithLifecycle(initialValue = emptyList())

    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    val totalSalesUsd = filteredSales.sumOf { it.totalUsd }
    val totalSalesBs = filteredSales.sumOf { it.totalBs }

    val totalCashUsd = filteredSales.sumOf { it.paymentCashUsd }
    val totalMobileBs = filteredSales.sumOf { it.paymentMobileBs }
    val totalCardBs = filteredSales.sumOf { it.paymentCardBs }
    val totalCreditUsd = filteredSales.sumOf { it.paymentCreditUsd }
    val totalCasheaUsd = filteredSales.sumOf { it.paymentCasheaUsd }

    val countCashUsd = filteredSales.count { it.paymentCashUsd > 0.001 }
    val countMobileBs = filteredSales.count { it.paymentMobileBs > 0.001 }
    val countCardBs = filteredSales.count { it.paymentCardBs > 0.001 }
    val countCreditUsd = filteredSales.count { it.paymentCreditUsd > 0.001 }
    val countCasheaUsd = filteredSales.count { it.paymentCasheaUsd > 0.001 }

    val totalBaseTaxableUsd = filteredSales.sumOf { it.baseTaxableUsd }
    val totalBaseExemptUsd = filteredSales.sumOf { it.baseExemptUsd }
    val totalDiscountUsd = filteredSales.sumOf { it.discountUsd }
    val totalIvaUsd = filteredSales.sumOf { it.ivaUsd }
    val totalIgtfUsd = filteredSales.sumOf { it.igtfUsd }

    val periodExpensesUsd = filteredExpenses.sumOf { it.amountUsd }
    val periodExpensesBs = periodExpensesUsd * effectiveRate

    // Cashea commission deductions (Ingresos no recibidos por comisiones de Cashea)
    val casheaCommissionUsd = filteredCasheaSettlements.sumOf { it.commissionAmountUsd }
    val casheaCommissionBs = casheaCommissionUsd * effectiveRate
    val casheaSettledGrossUsd = filteredCasheaSettlements.sumOf { it.grossAmountUsd }
    val casheaNetReceivedUsd = filteredCasheaSettlements.sumOf { it.netReceivedUsd }

    val realIncomeUsd = totalSalesUsd - casheaCommissionUsd
    val netBalanceUsd = realIncomeUsd - periodExpensesUsd
    val netBalanceBs = netBalanceUsd * effectiveRate

    val clientWithholdings = filteredSales.filter { it.retencionIvaUsd > 0.001 || it.retencionIslrUsd > 0.001 }
    val totalClientRetentionUsd = clientWithholdings.sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val supplierWithholdings = filteredPurchaseInvoices.filter { it.retencionIvaUsd > 0.001 || it.retencionIslrUsd > 0.001 }
    val totalSupplierRetentionUsd = supplierWithholdings.sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val totalInvoicesEnteredUsd = filteredPurchaseInvoices.sumOf { it.totalAmount }

    fun triggerExportPdf() {
        showPdfExportDialog = true
    }

    fun triggerExportCsv() {
        val file = AccountingLedgerCsvExporter.exportLedgerToCsv(
            context = context,
            prefs = viewModel.prefs,
            sales = filteredSales,
            deliveryNotes = filteredDeliveryNotes,
            expenses = filteredExpenses,
            purchaseInvoices = filteredPurchaseInvoices,
            periodName = "Libro Contable - ${dateFilter.label}"
        )
        if (file != null) {
            TicketUtils.shareFile(context, file, "text/csv")
            Toast.makeText(context, "Libro contable CSV generado exitosamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Error al generar el Libro Contable CSV", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Compact Header + Tasa BCV + Date Range Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Resumen: ${dateFilter.label} • BCV Bs. ${String.format(Locale.US, "%.2f", effectiveRate)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = currentUser?.role ?: "CAJERO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            DateRangeFilterBar(
                state = dateFilter,
                onStateChange = { dateFilter = it },
                showAllOption = true,
                showYesterdayOption = true
            )

            // Compact Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("General", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Detalle (${filteredSales.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Retenciones", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            when (selectedTab) {
                0 -> GeneralSummaryTab(
                    totalSalesUsd = totalSalesUsd,
                    totalSalesBs = totalSalesBs,
                    salesCount = filteredSales.size,
                    periodExpensesUsd = periodExpensesUsd,
                    periodExpensesBs = periodExpensesBs,
                    expensesCount = filteredExpenses.size,
                    casheaCommissionUsd = casheaCommissionUsd,
                    casheaCommissionBs = casheaCommissionBs,
                    casheaSettledGrossUsd = casheaSettledGrossUsd,
                    casheaNetReceivedUsd = casheaNetReceivedUsd,
                    casheaSettlementsCount = filteredCasheaSettlements.size,
                    realIncomeUsd = realIncomeUsd,
                    netBalanceUsd = netBalanceUsd,
                    netBalanceBs = netBalanceBs,
                    totalCashUsd = totalCashUsd,
                    countCashUsd = countCashUsd,
                    totalMobileBs = totalMobileBs,
                    countMobileBs = countMobileBs,
                    totalCardBs = totalCardBs,
                    countCardBs = countCardBs,
                    totalCreditUsd = totalCreditUsd,
                    countCreditUsd = countCreditUsd,
                    totalCasheaUsd = totalCasheaUsd,
                    countCasheaUsd = countCasheaUsd,
                    totalBaseTaxableUsd = totalBaseTaxableUsd,
                    totalBaseExemptUsd = totalBaseExemptUsd,
                    totalDiscountUsd = totalDiscountUsd,
                    totalIvaUsd = totalIvaUsd,
                    totalIgtfUsd = totalIgtfUsd,
                    effectiveRate = effectiveRate,
                    isLandscape = isLandscape,
                    onOpenClosure = { showClosureDialog = true },
                    onExportPdf = { triggerExportPdf() },
                    onExportCsv = { triggerExportCsv() }
                )

                1 -> DetailedSummaryTab(
                    filteredSales = filteredSales,
                    filteredItems = filteredItems,
                    filteredExpenses = filteredExpenses,
                    filteredCasheaSettlements = filteredCasheaSettlements,
                    dateTimeSdf = dateTimeSdf
                )

                2 -> RetentionsAndInvoicesTab(
                    clientWithholdings = clientWithholdings,
                    supplierWithholdings = supplierWithholdings,
                    filteredPurchaseInvoices = filteredPurchaseInvoices,
                    suppliers = suppliers,
                    totalClientRetentionUsd = totalClientRetentionUsd,
                    totalSupplierRetentionUsd = totalSupplierRetentionUsd,
                    totalInvoicesEnteredUsd = totalInvoicesEnteredUsd,
                    effectiveRate = effectiveRate,
                    dateTimeSdf = dateTimeSdf
                )
            }
        }
    }

    if (showClosureDialog) {
        CashRegisterClosureDialog(
            sales = filteredSales,
            items = filteredItems,
            rateBcv = effectiveRate,
            onExportPdf = {
                showClosureDialog = false
                triggerExportPdf()
            },
            onDismiss = { showClosureDialog = false }
        )
    }

    if (showPdfExportDialog) {
        val safePeriod = dateFilter.label.replace(Regex("[^A-Za-z0-9_-]"), "_").take(28)
        PdfExportActionDialog(
            title = "Reporte Contable y Ventas PDF",
            subtitle = "Período: ${dateFilter.label} • ${filteredSales.size} facturas • Ingresos reales: \$${String.format(Locale.US, "%.2f", realIncomeUsd)} USD",
            suggestedFileName = "Reporte_Ventas_$safePeriod.pdf",
            generatePdf = {
                PdfExportManager.generateSalesReportPdf(
                    context = context,
                    prefs = viewModel.prefs,
                    periodLabel = dateFilter.label,
                    generatedBy = currentUser?.username ?: "Cajero",
                    effectiveRate = effectiveRate,
                    sales = filteredSales,
                    saleItems = filteredItems,
                    expenses = filteredExpenses,
                    casheaSettlements = filteredCasheaSettlements,
                    purchaseInvoices = filteredPurchaseInvoices,
                    suppliers = suppliers,
                    products = products,
                    includeAccountingSections = true
                )
            },
            onDismiss = { showPdfExportDialog = false }
        )
    }
}

@Composable
fun GeneralSummaryTab(
    totalSalesUsd: Double,
    totalSalesBs: Double,
    salesCount: Int,
    periodExpensesUsd: Double,
    periodExpensesBs: Double,
    expensesCount: Int,
    casheaCommissionUsd: Double,
    casheaCommissionBs: Double,
    casheaSettledGrossUsd: Double,
    casheaNetReceivedUsd: Double,
    casheaSettlementsCount: Int,
    realIncomeUsd: Double,
    netBalanceUsd: Double,
    netBalanceBs: Double,
    totalCashUsd: Double,
    countCashUsd: Int,
    totalMobileBs: Double,
    countMobileBs: Int,
    totalCardBs: Double,
    countCardBs: Int,
    totalCreditUsd: Double,
    countCreditUsd: Int,
    totalCasheaUsd: Double,
    countCasheaUsd: Int,
    totalBaseTaxableUsd: Double,
    totalBaseExemptUsd: Double,
    totalDiscountUsd: Double,
    totalIvaUsd: Double,
    totalIgtfUsd: Double,
    effectiveRate: Double,
    isLandscape: Boolean,
    onOpenClosure: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Streamlined Financial Flow Strip (3 compact tiles, adaptive on narrow screens)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 340.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Ingresos Reales",
                        value = "\$${String.format(Locale.US, "%.2f", realIncomeUsd)}",
                        subValue = if (casheaCommissionUsd > 0.001) {
                            "Bruto: \$${String.format(Locale.US, "%.2f", totalSalesUsd)}"
                        } else {
                            "$salesCount fac. • Bs. ${String.format(Locale.US, "%.0f", totalSalesBs)}"
                        },
                        accent = Color(0xFF047857),
                        bg = Color(0xFFECFDF5)
                    )
                    CompactMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Gastos",
                        value = "-\$${String.format(Locale.US, "%.2f", periodExpensesUsd)}",
                        subValue = "$expensesCount reg. • Bs. ${String.format(Locale.US, "%.0f", periodExpensesBs)}",
                        accent = Color(0xFFDC2626),
                        bg = Color(0xFFFEF2F2)
                    )
                    CompactMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Balance Neto",
                        value = "\$${String.format(Locale.US, "%.2f", netBalanceUsd)}",
                        subValue = "Bs. ${String.format(Locale.US, "%.2f", netBalanceBs)}",
                        accent = if (netBalanceUsd >= 0) Color(0xFF1D4ED8) else Color(0xFFE11D48),
                        bg = if (netBalanceUsd >= 0) Color(0xFFEFF6FF) else Color(0xFFFFF1F2)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResponsivePair(
                        breakpoint = 240.dp,
                        horizontalSpacing = 6.dp,
                        verticalSpacing = 6.dp,
                        first = { mod ->
                            CompactMetricTile(
                                modifier = mod,
                                label = "Ingresos Reales",
                                value = "\$${String.format(Locale.US, "%.2f", realIncomeUsd)}",
                                subValue = if (casheaCommissionUsd > 0.001) {
                                    "Bruto: \$${String.format(Locale.US, "%.2f", totalSalesUsd)}"
                                } else {
                                    "$salesCount fac. • Bs. ${String.format(Locale.US, "%.0f", totalSalesBs)}"
                                },
                                accent = Color(0xFF047857),
                                bg = Color(0xFFECFDF5)
                            )
                        },
                        second = { mod ->
                            CompactMetricTile(
                                modifier = mod,
                                label = "Gastos",
                                value = "-\$${String.format(Locale.US, "%.2f", periodExpensesUsd)}",
                                subValue = "$expensesCount reg. • Bs. ${String.format(Locale.US, "%.0f", periodExpensesBs)}",
                                accent = Color(0xFFDC2626),
                                bg = Color(0xFFFEF2F2)
                            )
                        }
                    )
                    CompactMetricTile(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Balance Neto",
                        value = "\$${String.format(Locale.US, "%.2f", netBalanceUsd)}",
                        subValue = "Bs. ${String.format(Locale.US, "%.2f", netBalanceBs)}",
                        accent = if (netBalanceUsd >= 0) Color(0xFF1D4ED8) else Color(0xFFE11D48),
                        bg = if (netBalanceUsd >= 0) Color(0xFFEFF6FF) else Color(0xFFFFF1F2)
                    )
                }
            }
        }

        // Apartado: Ingresos no recibidos por comisiones de Cashea
        if (casheaCommissionUsd > 0.001 || totalCasheaUsd > 0.001 || casheaSettlementsCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summary_cashea_unrealized_box"),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
            ) {
                ResponsiveActionRow(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    breakpoint = 340.dp,
                    content = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoneyOff,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Ingresos no recibidos (Comisiones Cashea)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "Liquidado: \$${String.format(Locale.US, "%.2f", casheaSettledGrossUsd)} • Neto recibido: \$${String.format(Locale.US, "%.2f", casheaNetReceivedUsd)} ($casheaSettlementsCount pagos)",
                                    fontSize = 10.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    },
                    action = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "-\$${String.format(Locale.US, "%.2f", casheaCommissionUsd)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                            Text(
                                text = "-Bs. ${String.format(Locale.US, "%.2f", casheaCommissionBs)}",
                                fontSize = 10.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                )
            }
        }

        // Compact Export & Closure Action Strip (adaptive on narrow screens)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 340.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenClosure,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Arqueo", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onExportCsv,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Libro CSV", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("summary_export_pdf_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reporte PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                ResponsiveFlowGroup(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                    OutlinedButton(
                        onClick = onOpenClosure,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Arqueo", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onExportCsv,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Libro CSV", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("summary_export_pdf_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reporte PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Compact 2-column Payment Methods Grid
        Text("Métodos de Pago", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        ResponsivePair(
            breakpoint = 310.dp,
            horizontalSpacing = 6.dp,
            verticalSpacing = 6.dp,
            first = { mod ->
                SummaryCard(
                    title = "Efectivo USD",
                    amount = "\$${String.format(Locale.US, "%.2f", totalCashUsd)}",
                    subAmount = "Bs. ${String.format(Locale.US, "%.0f", totalCashUsd * effectiveRate)}",
                    count = countCashUsd,
                    icon = Icons.Filled.AttachMoney,
                    modifier = mod
                )
            },
            second = { mod ->
                SummaryCard(
                    title = "Pago Móvil",
                    amount = "Bs. ${String.format(Locale.US, "%.2f", totalMobileBs)}",
                    subAmount = "\$${String.format(Locale.US, "%.2f", totalMobileBs / effectiveRate)} USD",
                    count = countMobileBs,
                    icon = Icons.Filled.PhoneAndroid,
                    modifier = mod
                )
            }
        )
        ResponsivePair(
            breakpoint = 310.dp,
            horizontalSpacing = 6.dp,
            verticalSpacing = 6.dp,
            first = { mod ->
                SummaryCard(
                    title = "Punto / Débito",
                    amount = "Bs. ${String.format(Locale.US, "%.2f", totalCardBs)}",
                    subAmount = "\$${String.format(Locale.US, "%.2f", totalCardBs / effectiveRate)} USD",
                    count = countCardBs,
                    icon = Icons.Filled.CreditCard,
                    modifier = mod
                )
            },
            second = { mod ->
                SummaryCard(
                    title = "Cashea",
                    amount = "\$${String.format(Locale.US, "%.2f", totalCasheaUsd)}",
                    subAmount = "Bs. ${String.format(Locale.US, "%.0f", totalCasheaUsd * effectiveRate)}",
                    count = countCasheaUsd,
                    icon = Icons.Filled.QrCode2,
                    modifier = mod
                )
            }
        )
        SummaryCard(
            title = "Ventas a Crédito (\$ USD)",
            amount = "\$${String.format(Locale.US, "%.2f", totalCreditUsd)}",
            subAmount = "Bs. ${String.format(Locale.US, "%.2f", totalCreditUsd * effectiveRate)}",
            count = countCreditUsd,
            icon = Icons.Filled.CreditScore,
            modifier = Modifier.fillMaxWidth()
        )

        // Compact Fiscal SENIAT Surface
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Resumen Fiscal SENIAT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                FiscalRow("Base Gravable (IVA 16%):", "\$${String.format(Locale.US, "%.2f", totalBaseTaxableUsd)} USD")
                if (totalBaseExemptUsd > 0.001) {
                    FiscalRow("Monto Exento (0%):", "\$${String.format(Locale.US, "%.2f", totalBaseExemptUsd)} USD", Color(0xFF047857))
                }
                if (totalDiscountUsd > 0.001) {
                    FiscalRow("Descuentos:", "-\$${String.format(Locale.US, "%.2f", totalDiscountUsd)} USD", Color(0xFFE11D48))
                }
                FiscalRow("IVA Recaudado (16%):", "\$${String.format(Locale.US, "%.2f", totalIvaUsd)} USD", MaterialTheme.colorScheme.primary)
                if (totalIgtfUsd > 0.001) {
                    FiscalRow("IGTF Divisas (3%):", "+\$${String.format(Locale.US, "%.2f", totalIgtfUsd)} USD", Color(0xFFB45309))
                }
            }
        }
    }
}

@Composable
private fun CompactMetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    bg: Color
) {
    Surface(
        modifier = modifier,
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            Text(subValue, fontSize = 9.5.sp, color = accent.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FiscalRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun DetailedSummaryTab(
    filteredSales: List<Sale>,
    filteredItems: List<com.example.data.local.SaleItem>,
    filteredExpenses: List<Expense>,
    filteredCasheaSettlements: List<CasheaSettlement>,
    dateTimeSdf: SimpleDateFormat
) {
    data class ProductRanking(
        val name: String,
        val isTaxExempt: Boolean,
        val quantity: Double,
        val totalUsd: Double
    )

    val topProducts = remember(filteredItems) {
        filteredItems.groupBy { it.productName }.map { (name, pItems) ->
            val isExempt = pItems.any { it.isTaxExempt }
            val qty = pItems.sumOf { it.quantity }
            val sumUsd = pItems.sumOf { it.priceUsd * it.quantity }
            ProductRanking(name, isExempt, qty, sumUsd)
        }.sortedByDescending { it.quantity }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Productos Vendidos (${topProducts.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("${filteredItems.sumOf { it.quantity }} un.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                if (topProducts.isEmpty()) {
                    Text("No hay artículos en este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    topProducts.take(15).forEachIndexed { index, item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("#${index + 1} ${item.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${item.quantity} un. • \$${String.format(Locale.US, "%.2f", item.totalUsd)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Facturas del Período (${filteredSales.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                if (filteredSales.isEmpty()) {
                    Text("No hay facturas en este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filteredSales.forEach { sale ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Factura #${sale.id} • ${dateTimeSdf.format(Date(sale.timestamp))}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(sale.customerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format(Locale.US, "%.2f", sale.totalUsd)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Text("Bs. ${String.format(Locale.US, "%.2f", sale.totalBs)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gastos y Comisiones Cashea (${filteredExpenses.size + filteredCasheaSettlements.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                if (filteredExpenses.isEmpty() && filteredCasheaSettlements.isEmpty()) {
                    Text("No hay egresos ni comisiones en este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filteredCasheaSettlements.forEach { cs ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("[Cashea] Comisión ${String.format(Locale.US, "%.1f", cs.commissionPercentage)}% s/ \$${String.format(Locale.US, "%.2f", cs.grossAmountUsd)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("-\$${String.format(Locale.US, "%.2f", cs.commissionAmountUsd)}", fontWeight = FontWeight.Bold, color = Color(0xFFB45309), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    filteredExpenses.forEach { exp ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("[${exp.category}] ${exp.description.ifBlank { "Gasto" }}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("-\$${String.format(Locale.US, "%.2f", exp.amountUsd)}", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RetentionsAndInvoicesTab(
    clientWithholdings: List<Sale>,
    supplierWithholdings: List<PurchaseInvoice>,
    filteredPurchaseInvoices: List<PurchaseInvoice>,
    suppliers: List<com.example.data.local.Supplier>,
    totalClientRetentionUsd: Double,
    totalSupplierRetentionUsd: Double,
    totalInvoicesEnteredUsd: Double,
    effectiveRate: Double,
    dateTimeSdf: SimpleDateFormat
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 340.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Ret. Clientes",
                        value = "\$${String.format(Locale.US, "%.2f", totalClientRetentionUsd)}",
                        subValue = "Bs. ${String.format(Locale.US, "%.0f", totalClientRetentionUsd * effectiveRate)}",
                        accent = Color(0xFF1D4ED8),
                        bg = Color(0xFFEFF6FF)
                    )
                    CompactMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Ret. Proveedores",
                        value = "\$${String.format(Locale.US, "%.2f", totalSupplierRetentionUsd)}",
                        subValue = "Bs. ${String.format(Locale.US, "%.0f", totalSupplierRetentionUsd * effectiveRate)}",
                        accent = Color(0xFFDC2626),
                        bg = Color(0xFFFEF2F2)
                    )
                    CompactMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Compras",
                        value = "\$${String.format(Locale.US, "%.2f", totalInvoicesEnteredUsd)}",
                        subValue = "${filteredPurchaseInvoices.size} fac.",
                        accent = Color(0xFF166534),
                        bg = Color(0xFFF0FDF4)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResponsivePair(
                        breakpoint = 240.dp,
                        horizontalSpacing = 6.dp,
                        verticalSpacing = 6.dp,
                        first = { mod ->
                            CompactMetricTile(
                                modifier = mod,
                                label = "Ret. Clientes",
                                value = "\$${String.format(Locale.US, "%.2f", totalClientRetentionUsd)}",
                                subValue = "Bs. ${String.format(Locale.US, "%.0f", totalClientRetentionUsd * effectiveRate)}",
                                accent = Color(0xFF1D4ED8),
                                bg = Color(0xFFEFF6FF)
                            )
                        },
                        second = { mod ->
                            CompactMetricTile(
                                modifier = mod,
                                label = "Ret. Proveedores",
                                value = "\$${String.format(Locale.US, "%.2f", totalSupplierRetentionUsd)}",
                                subValue = "Bs. ${String.format(Locale.US, "%.0f", totalSupplierRetentionUsd * effectiveRate)}",
                                accent = Color(0xFFDC2626),
                                bg = Color(0xFFFEF2F2)
                            )
                        }
                    )
                    CompactMetricTile(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Compras",
                        value = "\$${String.format(Locale.US, "%.2f", totalInvoicesEnteredUsd)}",
                        subValue = "${filteredPurchaseInvoices.size} fac.",
                        accent = Color(0xFF166534),
                        bg = Color(0xFFF0FDF4)
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Retenciones de Clientes (${clientWithholdings.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                if (clientWithholdings.isEmpty()) {
                    Text("Sin retenciones de clientes en este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    clientWithholdings.forEach { sale ->
                        val retTotal = sale.retencionIvaUsd + sale.retencionIslrUsd
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${sale.customerName} (#${sale.id})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("IVA: \$${String.format(Locale.US, "%.2f", sale.retencionIvaUsd)} • ISLR: \$${String.format(Locale.US, "%.2f", sale.retencionIslrUsd)}", fontSize = 10.sp, color = Color(0xFF1D4ED8))
                            }
                            Text("\$${String.format(Locale.US, "%.2f", retTotal)}", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Retenciones a Proveedores (${supplierWithholdings.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                if (supplierWithholdings.isEmpty()) {
                    Text("Sin retenciones a proveedores en este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    supplierWithholdings.forEach { inv ->
                        val supplier = suppliers.find { it.id == inv.supplierId }
                        val retTotal = inv.retencionIvaUsd + inv.retencionIslrUsd
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${supplier?.name ?: "Proveedor"} (#${inv.invoiceNumber})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Voucher: ${inv.withholdingVoucherNumber.ifBlank { "Sin N°" }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("\$${String.format(Locale.US, "%.2f", retTotal)}", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Facturas de Compras (${filteredPurchaseInvoices.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                if (filteredPurchaseInvoices.isEmpty()) {
                    Text("Sin facturas de compras en este período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filteredPurchaseInvoices.forEach { inv ->
                        val supplier = suppliers.find { it.id == inv.supplierId }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${supplier?.name ?: "Proveedor"} - #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(dateTimeSdf.format(Date(inv.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("\$${String.format(Locale.US, "%.2f", inv.totalAmount)}", fontWeight = FontWeight.Bold, color = Color(0xFF166534), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: String,
    subAmount: String,
    count: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$count", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(amount, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(subAmount, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
