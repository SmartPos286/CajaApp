package com.example.ui.invoices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.Quote
import com.example.data.local.Sale
import com.example.ui.PosViewModel
import com.example.ui.components.DateFilterUtils
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.DebtsAndRemindersDialog
import com.example.ui.components.DeliveryNoteDialog
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.PdfExportActionDialog
import com.example.ui.components.QuoteTicketDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.TicketDialog
import com.example.ui.pos.HeldSaleItemCard
import com.example.utils.PdfExportManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)

@Composable
fun InvoicesScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val allCasheaSettlements by viewModel.allCasheaSettlements.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val allSuppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val deliveryNotes by viewModel.allDeliveryNotes.collectAsStateWithLifecycle()
    val heldSales by viewModel.allHeldSales.collectAsStateWithLifecycle()
    val quotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val creditSales by viewModel.allCreditSales.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()

    var dateFilter by remember { mutableStateOf(DateFilterUtils.allTime()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Facturas, 1: Notas de Entrega, 2: En Espera, 3: Presupuestos
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var saleForQuickPdf by remember { mutableStateOf<Sale?>(null) }
    var showPeriodReportPdfDialog by remember { mutableStateOf(false) }
    var selectedDeliveryNote by remember { mutableStateOf<DeliveryNote?>(null) }
    var selectedQuote by remember { mutableStateOf<Quote?>(null) }
    var noteToCancel by remember { mutableStateOf<DeliveryNote?>(null) }
    var showDebtsDialog by remember { mutableStateOf(false) }
    var debtsInitialTab by remember { mutableIntStateOf(0) }

    val filteredSales = remember(sales, dateFilter) {
        sales.filter { dateFilter.matches(it.timestamp) }
    }
    val filteredDeliveryNotes = remember(deliveryNotes, dateFilter) {
        deliveryNotes.filter { dateFilter.matches(it.timestamp) }
    }
    val filteredQuotes = remember(quotes, dateFilter) {
        quotes.filter { dateFilter.matches(it.timestamp) }
    }

    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1L

    val pendingCustomerCredits = creditSales.filter { !it.isCreditPaid }
    val dueTodayCreditCount = pendingCustomerCredits.count { it.creditDueDate in dayStart..dayEnd }
    val overdueCreditCount = pendingCustomerCredits.count { it.creditDueDate in 1 until dayStart }

    val pendingSupplierInvoices = purchaseInvoices.filter { !it.isPaid }
    val dueTodayInvoiceCount = pendingSupplierInvoices.count { it.dueDate in dayStart..dayEnd }
    val overdueInvoiceCount = pendingSupplierInvoices.count { it.dueDate in 1 until dayStart }

    val dateTimeSdf = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
    val dateOnlySdf = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Compact Reminders Banner
            if (dueTodayCreditCount > 0 || overdueCreditCount > 0 || dueTodayInvoiceCount > 0 || overdueInvoiceCount > 0) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            debtsInitialTab = if (dueTodayCreditCount > 0 || overdueCreditCount > 0) 0 else 1
                            showDebtsDialog = true
                        }
                ) {
                    ResponsiveActionRow(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        breakpoint = 340.dp,
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                val remindersList = mutableListOf<String>()
                                if (dueTodayCreditCount > 0) remindersList.add("$dueTodayCreditCount cobro(s) hoy")
                                if (overdueCreditCount > 0) remindersList.add("$overdueCreditCount cobro(s) atrasados")
                                if (dueTodayInvoiceCount > 0) remindersList.add("$dueTodayInvoiceCount prov. hoy")
                                if (overdueInvoiceCount > 0) remindersList.add("$overdueInvoiceCount prov. vencidos")
                                Text(
                                    text = remindersList.joinToString(" • "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF991B1B),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        action = {
                            TextButton(
                                onClick = {
                                    debtsInitialTab = if (dueTodayCreditCount > 0 || overdueCreditCount > 0) 0 else 1
                                    showDebtsDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Ver Deudas", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        }
                    )
                }
            }

            // Compact Date / Range Filter Bar
            DateRangeFilterBar(
                state = dateFilter,
                onStateChange = { dateFilter = it },
                showAllOption = true,
                showYesterdayOption = true
            )

            // Compact Navigation Tabs + Filtered Period Total Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
            ) {
                Column {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 6.dp,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("invoices_tab_sales"),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("Facturas (${filteredSales.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("invoices_tab_notes"),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("Notas (${filteredDeliveryNotes.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("Espera (${heldSales.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.RequestQuote, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("Presupuestos (${filteredQuotes.size})", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                                }
                            }
                        )
                        Tab(
                            selected = false,
                            onClick = {
                                debtsInitialTab = 0
                                showDebtsDialog = true
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                                    Text("Deudas (${pendingCustomerCredits.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD97706))
                                }
                            }
                        )
                    }

                    // Slim summary line for the current filtered tab
                    if (selectedTab == 0 || selectedTab == 1 || selectedTab == 3) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        val (countLabel, totalUsd, totalBs) = when (selectedTab) {
                            0 -> Triple(
                                "${filteredSales.size} factura(s)",
                                filteredSales.sumOf { it.totalUsd },
                                filteredSales.sumOf { it.totalBs }
                            )
                            1 -> Triple(
                                "${filteredDeliveryNotes.size} nota(s)",
                                filteredDeliveryNotes.filter { it.status != "ANULADO" }.sumOf { it.totalEstimatedUsd },
                                filteredDeliveryNotes.filter { it.status != "ANULADO" }.sumOf { it.totalEstimatedBs }
                            )
                            else -> Triple(
                                "${filteredQuotes.size} presupuesto(s)",
                                filteredQuotes.sumOf { it.totalUsd },
                                filteredQuotes.sumOf { it.totalBs }
                            )
                        }
                        ResponsiveActionRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            breakpoint = 310.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Column {
                                    Text(
                                        text = countLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Total: \$${String.format(Locale.US, "%.2f", totalUsd)} USD (Bs. ${String.format(Locale.US, "%.2f", totalBs)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            action = {
                                if (selectedTab == 0 && filteredSales.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { showPeriodReportPdfDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .minimumInteractiveComponentSize()
                                            .testTag("invoices_export_report_pdf_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.PictureAsPdf,
                                            contentDescription = "Exportar reporte de ventas PDF",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reporte PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (filteredSales.isEmpty()) {
                        EmptyPeriodBox(Icons.Filled.ReceiptLong, "No hay facturas para el período seleccionado.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredSales, key = { it.id }) { sale ->
                                val paidBs = sale.paymentMobileBs + sale.paymentCardBs
                                val paidUsd = sale.paymentCashUsd
                                val paidCashea = sale.paymentCasheaUsd
                                val hasBs = paidBs > 0.001
                                val hasCashUsd = paidUsd > 0.001
                                val hasCashea = paidCashea > 0.001
                                val hasCredit = sale.paymentCreditUsd > 0.001
                                val methodsCount = listOf(hasBs, hasCashUsd, hasCashea, hasCredit).count { it }
                                val isPaidCasheaOnly = hasCashea && methodsCount == 1
                                val isPaidInBs = (hasBs && methodsCount == 1) || methodsCount == 0
                                val isPaidInUsd = hasCashUsd && methodsCount == 1
                                val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSale = sale },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ResponsiveActionRow(
                                            breakpoint = 290.dp,
                                            content = {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        "#${sale.id}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = when {
                                                            isPaidCasheaOnly -> Color(0xFFFACC15).copy(alpha = 0.28f)
                                                            isPaidInBs -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                            isPaidInUsd -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                            else -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                isPaidCasheaOnly -> "Cashea"
                                                                isPaidInBs -> "Bs"
                                                                isPaidInUsd -> if (sale.igtfUsd > 0.001) "USD+IGTF" else "USD"
                                                                else -> "Mixto"
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when {
                                                                isPaidCasheaOnly -> Color(0xFF854D0E)
                                                                isPaidInBs -> MaterialTheme.colorScheme.onPrimaryContainer
                                                                isPaidInUsd -> Color(0xFF047857)
                                                                else -> Color(0xFFB45309)
                                                            },
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = dateTimeSdf.format(Date(sale.timestamp)),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            },
                                            action = {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = if (isPaidInBs) {
                                                            "Bs. ${String.format(Locale.US, "%.2f", sale.totalBs)}"
                                                        } else {
                                                            "\$${String.format(Locale.US, "%.2f", sale.totalUsd)}"
                                                        },
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (!isPaidInBs && !isPaidInUsd) {
                                                        Text(
                                                            text = "Bs. ${String.format(Locale.US, "%.2f", sale.totalBs)}",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        Text(
                                            text = "${sale.customerName} (${sale.customerDocument})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (isPaidInBs) {
                                                Text(
                                                    "Base: Bs. ${String.format(Locale.US, "%.2f", sale.baseTaxableUsd * rate)}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "IVA: Bs. ${String.format(Locale.US, "%.2f", sale.ivaUsd * rate)}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else {
                                                Text(
                                                    "Base: \$${String.format(Locale.US, "%.2f", sale.baseTaxableUsd)}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "IVA: \$${String.format(Locale.US, "%.2f", sale.ivaUsd)}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (sale.igtfUsd > 0.001) {
                                                    Text(
                                                        "IGTF: \$${String.format(Locale.US, "%.2f", sale.igtfUsd)}",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFB45309),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            if (sale.discountUsd > 0.001) {
                                                Text(
                                                    "Desc: -\$${String.format(Locale.US, "%.2f", sale.discountUsd)}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFE11D48),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = { saleForQuickPdf = sale },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("invoice_card_pdf_${sale.id}")
                                            ) {
                                                Icon(
                                                    Icons.Filled.PictureAsPdf,
                                                    contentDescription = "Exportar Factura #${sale.id} a PDF",
                                                    tint = Color(0xFFDC2626),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                            }
                                            TextButton(
                                                onClick = { selectedSale = sale },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .minimumInteractiveComponentSize()
                                            ) {
                                                Text("Ver Ticket", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (filteredDeliveryNotes.isEmpty()) {
                        EmptyPeriodBox(Icons.Filled.LocalShipping, "No hay notas de entrega en este período.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredDeliveryNotes, key = { it.id }) { note ->
                                val isCancelled = note.status == "ANULADO"
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDeliveryNote = note },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ResponsiveActionRow(
                                            breakpoint = 290.dp,
                                            content = {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(note.noteNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF0284C7))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = when (note.status) {
                                                            "ENTREGADO" -> Color(0xFF059669).copy(alpha = 0.15f)
                                                            "FACTURADO" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = note.status,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when (note.status) {
                                                                "ENTREGADO" -> Color(0xFF059669)
                                                                "FACTURADO" -> MaterialTheme.colorScheme.primary
                                                                else -> MaterialTheme.colorScheme.error
                                                            },
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            action = {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("\$${String.format(Locale.US, "%.2f", note.totalEstimatedUsd)} USD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                                    Text("Bs. ${String.format(Locale.US, "%.2f", note.totalEstimatedBs)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        )

                                        Text("${note.customerName} (${note.customerDocument})", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                        ResponsiveActionRow(
                                            breakpoint = 290.dp,
                                            content = {
                                                Text(dateTimeSdf.format(Date(note.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            },
                                            action = {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    TextButton(
                                                        onClick = { selectedDeliveryNote = note },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text("Ver", fontSize = 11.sp)
                                                    }
                                                    if (!isCancelled) {
                                                        TextButton(
                                                            onClick = { noteToCancel = note },
                                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(30.dp)
                                                        ) {
                                                            Text("Anular", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (heldSales.isEmpty()) {
                        EmptyPeriodBox(Icons.Filled.PauseCircle, "No hay facturas en espera.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(heldSales, key = { it.id }) { heldSale ->
                                HeldSaleItemCard(
                                    heldSale = heldSale,
                                    viewModel = viewModel,
                                    onRestoreClick = {
                                        viewModel.restoreHeldCart(heldSale.id) {}
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteHeldSale(heldSale.id)
                                    }
                                )
                            }
                        }
                    }
                }

                3 -> {
                    if (filteredQuotes.isEmpty()) {
                        EmptyPeriodBox(Icons.Filled.RequestQuote, "No hay presupuestos en este período.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredQuotes, key = { it.id }) { quote ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedQuote = quote },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ResponsiveActionRow(
                                            breakpoint = 290.dp,
                                            content = {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(quote.quoteNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF4F46E5))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = when (quote.status) {
                                                            "ACEPTADO" -> Color(0xFF059669).copy(alpha = 0.15f)
                                                            "FACTURADO" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                            "ANULADO" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                            else -> Color(0xFF0284C7).copy(alpha = 0.15f)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = quote.status,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when (quote.status) {
                                                                "ACEPTADO" -> Color(0xFF059669)
                                                                "FACTURADO" -> MaterialTheme.colorScheme.primary
                                                                "ANULADO" -> MaterialTheme.colorScheme.error
                                                                else -> Color(0xFF0284C7)
                                                            },
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            action = {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("\$${String.format(Locale.US, "%.2f", quote.totalUsd)} USD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                                    Text("Bs. ${String.format(Locale.US, "%.2f", quote.totalBs)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        )
                                        Text("${quote.customerName} (${quote.customerDocument})", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        ResponsiveActionRow(
                                            breakpoint = 290.dp,
                                            content = {
                                                Text(
                                                    "Emitido: ${dateTimeSdf.format(Date(quote.timestamp))} • Válido: ${dateOnlySdf.format(Date(quote.validUntil))}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            action = {
                                                TextButton(
                                                    onClick = { selectedQuote = quote },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Ticket", fontSize = 11.sp)
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

    selectedDeliveryNote?.let { note ->
        val items by viewModel.getItemsForDeliveryNote(note.id).collectAsStateWithLifecycle(initialValue = emptyList())
        DeliveryNoteDialog(
            note = note,
            items = items,
            viewModel = viewModel,
            onDismiss = { selectedDeliveryNote = null }
        )
    }

    selectedQuote?.let { quote ->
        val items by viewModel.getItemsForQuote(quote.id).collectAsStateWithLifecycle(initialValue = emptyList())
        QuoteTicketDialog(
            quote = quote,
            items = items,
            viewModel = viewModel,
            onDismiss = { selectedQuote = null }
        )
    }

    noteToCancel?.let { note ->
        KeyboardAwareAlertDialog(
            onDismissRequest = { noteToCancel = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("¿Anular Nota de Entrega?", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { noteToCancel = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Text("Al anular la nota ${note.noteNumber}, los productos entregados se reintegrarán al stock del inventario automáticamente.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelDeliveryNote(note.id)
                        noteToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Anular y Devolver Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToCancel = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDebtsDialog) {
        DebtsAndRemindersDialog(
            viewModel = viewModel,
            initialTab = debtsInitialTab,
            onDismiss = { showDebtsDialog = false }
        )
    }

    saleForQuickPdf?.let { sale ->
        val matchedCustomer = remember(sale, allCustomers) {
            allCustomers.find {
                (it.documentId.isNotBlank() && it.documentId.equals(sale.customerDocument, ignoreCase = true)) ||
                    (it.name.isNotBlank() && it.name.equals(sale.customerName, ignoreCase = true))
            }
        }
        val items = remember(sale.id, allSaleItems) {
            allSaleItems.filter { it.saleId == sale.id }
        }
        PdfExportActionDialog(
            title = "Factura #${sale.id} en PDF",
            subtitle = "Comprobante de ${sale.customerName.ifBlank { "Consumidor Final" }} • Total: \$${String.format(Locale.US, "%.2f", sale.totalUsd)} USD",
            suggestedFileName = "Factura_${sale.id}.pdf",
            generatePdf = {
                PdfExportManager.generateSaleInvoicePdf(
                    context = context,
                    prefs = viewModel.prefs,
                    sale = sale,
                    items = items,
                    customerPhone = matchedCustomer?.phone ?: "",
                    customerAddress = matchedCustomer?.address ?: ""
                )
            },
            onDismiss = { saleForQuickPdf = null }
        )
    }

    if (showPeriodReportPdfDialog) {
        val periodSaleIds = remember(filteredSales) { filteredSales.map { it.id }.toSet() }
        val periodItems = remember(allSaleItems, periodSaleIds) {
            allSaleItems.filter { periodSaleIds.contains(it.saleId) }
        }
        val periodExpenses = remember(allExpenses, dateFilter) {
            allExpenses.filter { dateFilter.matches(it.timestamp) }
        }
        val periodCashea = remember(allCasheaSettlements, dateFilter) {
            allCasheaSettlements.filter { dateFilter.matches(it.settlementDate) }
        }
        val periodPurchases = remember(purchaseInvoices, dateFilter) {
            purchaseInvoices.filter { dateFilter.matches(it.timestamp) }
        }
        val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0
        val safePeriod = dateFilter.label.replace(Regex("[^A-Za-z0-9_-]"), "_").take(28)

        PdfExportActionDialog(
            title = "Reporte de Ventas PDF",
            subtitle = "Período: ${dateFilter.label} • ${filteredSales.size} facturas • Total: \$${String.format(Locale.US, "%.2f", filteredSales.sumOf { it.totalUsd })} USD",
            suggestedFileName = "Reporte_Ventas_$safePeriod.pdf",
            generatePdf = {
                PdfExportManager.generateSalesReportPdf(
                    context = context,
                    prefs = viewModel.prefs,
                    periodLabel = dateFilter.label,
                    generatedBy = currentUser?.username ?: "Operador",
                    effectiveRate = effectiveRate,
                    sales = filteredSales,
                    saleItems = periodItems,
                    expenses = periodExpenses,
                    casheaSettlements = periodCashea,
                    purchaseInvoices = periodPurchases,
                    suppliers = allSuppliers,
                    products = allProducts,
                    includeAccountingSections = true
                )
            },
            onDismiss = { showPeriodReportPdfDialog = false }
        )
    }
}

@Composable
private fun EmptyPeriodBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
