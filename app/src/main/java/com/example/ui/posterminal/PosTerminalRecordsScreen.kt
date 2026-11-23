package com.example.ui.posterminal

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.example.data.local.PosTerminalRecord
import com.example.ui.PosViewModel
import com.example.ui.components.DateFilterUtils
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.bringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PosTerminalRecordsScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val allRecords by viewModel.allPosTerminalRecords.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var dateFilter by remember { mutableStateOf(DateFilterUtils.allTime()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("TODOS") }

    var showTicketDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<PosTerminalRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<PosTerminalRecord?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val filteredRecords = remember(allRecords, dateFilter, searchQuery, selectedStatusFilter) {
        val q = searchQuery.trim()
        allRecords.filter { rec ->
            val matchesDate = dateFilter.matches(rec.transactionTimestamp)
            val matchesStatus = selectedStatusFilter == "TODOS" ||
                rec.transactionStatus.equals(selectedStatusFilter, ignoreCase = true)
            val matchesQuery = q.isBlank() ||
                rec.receiptNumber.contains(q, ignoreCase = true) ||
                rec.referenceNumber.contains(q, ignoreCase = true) ||
                rec.approvalNumber.contains(q, ignoreCase = true) ||
                rec.maskedCardNumber.contains(q, ignoreCase = true) ||
                rec.operationType.contains(q, ignoreCase = true) ||
                rec.bankOrTerminal.contains(q, ignoreCase = true)
            matchesDate && matchesStatus && matchesQuery
        }
    }

    val dayKeyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val groupedByDate = remember(filteredRecords) {
        filteredRecords.groupBy { rec ->
            val dtPart = rec.transactionDateTime.trim().substringBefore(" ").trim()
            if (dtPart.matches(Regex("""\d{2}/\d{2}/\d{4}"""))) {
                dtPart
            } else {
                dayKeyFormatter.format(Date(rec.transactionTimestamp))
            }
        }
    }

    val approvedFiltered = remember(filteredRecords) {
        filteredRecords.filter { it.transactionStatus.equals("APROBADA", ignoreCase = true) }
    }
    val totalApprovedBs = remember(approvedFiltered) {
        approvedFiltered.filter { !it.currency.equals("USD", ignoreCase = true) }.sumOf { it.totalAmount }
    }
    val totalApprovedUsd = remember(approvedFiltered) {
        approvedFiltered.filter { it.currency.equals("USD", ignoreCase = true) }.sumOf { it.totalAmount }
    }
    val rejectedOrVoidCount = remember(filteredRecords) {
        filteredRecords.count {
            it.transactionStatus.equals("RECHAZADA", ignoreCase = true) ||
                it.transactionStatus.equals("ANULADA", ignoreCase = true)
        }
    }

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
            // Top Header Actions + Date Range Filter
            ResponsiveActionRow(
                breakpoint = 540.dp,
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showExportDialog = true },
                            enabled = filteredRecords.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pos_terminal_export_button")
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Exportar registros POS", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exportar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                editingRecord = null
                                showTicketDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pos_terminal_upload_ticket_button")
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = "Cargar ticket POS", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cargar Ticket POS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            // Search & Status filter chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar recibo, referencia, aprobación o tarjeta...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus()
                    .testTag("pos_terminal_search_input")
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filterChips = listOf("TODOS", "APROBADA", "RECHAZADA", "ANULADA", "PENDIENTE")
                items(filterChips) { st ->
                    FilterChip(
                        selected = selectedStatusFilter == st,
                        onClick = { selectedStatusFilter = st },
                        label = {
                            val labelTxt = when (st) {
                                "TODOS" -> "Todos (${allRecords.size})"
                                else -> st.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
                            }
                            Text(labelTxt, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    )
                }
            }

            // Compact KPI summary strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PosSummaryMiniCard(
                    title = "Tickets",
                    value = "${filteredRecords.size}",
                    subtitle = "${approvedFiltered.size} aprob.",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                PosSummaryMiniCard(
                    title = "Aprobado Bs.",
                    value = "Bs. ${String.format(Locale.US, "%.2f", totalApprovedBs)}",
                    subtitle = if (totalApprovedUsd > 0.001) "+ \$${String.format(Locale.US, "%.2f", totalApprovedUsd)} USD" else "Total en Bs.",
                    accent = Color(0xFF059669),
                    modifier = Modifier.weight(1.35f)
                )
                PosSummaryMiniCard(
                    title = "Rech. / Anul.",
                    value = "$rejectedOrVoidCount",
                    subtitle = "No aprobados",
                    accent = if (rejectedOrVoidCount > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.95f)
                )
            }

            // Grouped Records List
            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                        Text(
                            text = if (allRecords.isEmpty()) {
                                "Aún no hay tickets de punto de venta registrados"
                            } else {
                                "No se encontraron tickets con ese filtro"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Presiona \"Cargar Ticket POS\" para tomar una foto o subir una imagen y extraer recibo, referencia, aprobación, tarjeta y monto automáticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedByDate.forEach { (dateKey, dayRecords) ->
                        val dayApproved = dayRecords.filter { it.transactionStatus.equals("APROBADA", ignoreCase = true) }
                        val dayBs = dayApproved.filter { !it.currency.equals("USD", ignoreCase = true) }.sumOf { it.totalAmount }
                        val dayUsd = dayApproved.filter { it.currency.equals("USD", ignoreCase = true) }.sumOf { it.totalAmount }

                        item(key = "header_$dateKey") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$dateKey (${dayRecords.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    val daySummary = buildList {
                                        if (dayBs > 0.001 || dayUsd <= 0.001) add("Bs. ${String.format(Locale.US, "%.2f", dayBs)}")
                                        if (dayUsd > 0.001) add("\$${String.format(Locale.US, "%.2f", dayUsd)} USD")
                                    }.joinToString(" • ")
                                    Text(
                                        text = daySummary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }

                        items(dayRecords, key = { it.id }) { rec ->
                            PosTerminalRecordCard(
                                record = rec,
                                onEditClick = {
                                    editingRecord = rec
                                    showTicketDialog = true
                                },
                                onDeleteClick = { recordToDelete = rec }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTicketDialog) {
        PosTerminalTicketDialog(
            viewModel = viewModel,
            initialRecord = editingRecord,
            onDismiss = {
                showTicketDialog = false
                editingRecord = null
            }
        )
    }

    if (showExportDialog) {
        val filterSummary = buildList {
            add(dateFilter.label)
            if (selectedStatusFilter != "TODOS") add("Estado: $selectedStatusFilter")
            if (searchQuery.isNotBlank()) add("Búsqueda: \"$searchQuery\"")
        }.joinToString(" • ")

        PosTerminalExportDialog(
            viewModel = viewModel,
            records = filteredRecords,
            filterLabel = filterSummary,
            generatedBy = currentUser?.username.orEmpty(),
            onDismiss = { showExportDialog = false }
        )
    }

    recordToDelete?.let { target ->
        KeyboardAwareAlertDialog(
            onDismissRequest = { recordToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar Registro POS", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Deseas eliminar el ticket ${target.receiptNumber.ifBlank { "#${target.id}" }} (Ref: ${target.referenceNumber.ifBlank { "S/N" }})?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePosTerminalRecord(target) {
                            Toast.makeText(context, "Registro eliminado", Toast.LENGTH_SHORT).show()
                        }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PosSummaryMiniCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosTerminalRecordCard(
    record: PosTerminalRecord,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val upperStatus = record.transactionStatus.uppercase(Locale.ROOT)
    val statusColor = when (upperStatus) {
        "APROBADA" -> Color(0xFF059669)
        "RECHAZADA" -> Color(0xFFDC2626)
        "ANULADA" -> Color(0xFFD97706)
        else -> Color(0xFF64748B)
    }

    val amountLabel = if (record.currency.equals("USD", ignoreCase = true)) {
        "\$${String.format(Locale.US, "%.2f", record.totalAmount)} USD"
    } else {
        "Bs. ${String.format(Locale.US, "%.2f", record.totalAmount)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = upperStatus,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = record.operationType.ifBlank { "COMPRA" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = amountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                VoucherFieldChip("Fecha", record.transactionDateTime.ifBlank { "—" })
                VoucherFieldChip("Recibo", record.receiptNumber.ifBlank { "—" })
                VoucherFieldChip("Ref.", record.referenceNumber.ifBlank { "—" })
                VoucherFieldChip("Aprob.", record.approvalNumber.ifBlank { "—" })
                VoucherFieldChip("Tarjeta", record.maskedCardNumber.ifBlank { "—" })
                if (record.bankOrTerminal.isNotBlank()) {
                    VoucherFieldChip("Banco", record.bankOrTerminal)
                }
            }

            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar ticket", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar ticket", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Eliminar", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VoucherFieldChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
