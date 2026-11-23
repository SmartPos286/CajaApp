package com.example.ui.suppliers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Supplier
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.bringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuppliersScreen(viewModel: PosViewModel) {
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSupplierId by remember { mutableStateOf<Int?>(null) }
    var showInvoiceDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedSupplierId by remember { mutableStateOf<Int?>(null) }

    val filteredSuppliers = suppliers.filter { s ->
        searchQuery.isBlank() ||
        s.name.contains(searchQuery, ignoreCase = true) ||
        s.documentId.contains(searchQuery, ignoreCase = true) ||
        s.phone.contains(searchQuery, ignoreCase = true) ||
        purchaseInvoices.any { it.supplierId == s.id && it.invoiceNumber.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir Proveedor")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar proveedor o factura...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .bringIntoViewOnFocus()
            )

            if (filteredSuppliers.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (suppliers.isEmpty()) "No hay proveedores registrados. Añade uno con el botón +" else "No se encontraron proveedores.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSuppliers) { supplier ->
                        val supplierInvoices = purchaseInvoices.filter { it.supplierId == supplier.id }
                        val isExpanded = expandedSupplierId == supplier.id

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                ResponsiveActionRow(
                                    breakpoint = 320.dp,
                                    stackedActionAlignment = Alignment.End,
                                    content = {
                                        Column {
                                            Text(
                                                text = supplier.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "RIF / CI: ${supplier.documentId}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    action = {
                                        Button(
                                            onClick = {
                                                selectedSupplierId = supplier.id
                                                showInvoiceDialog = true
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Cargar Factura", fontSize = 13.sp)
                                        }
                                    }
                                )

                                if (supplier.phone.isNotBlank() || supplier.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (supplier.phone.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(supplier.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (supplier.address.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(supplier.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                Spacer(modifier = Modifier.height(6.dp))

                                // Invoices header & expand toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedSupplierId = if (isExpanded) null else supplier.id
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Filled.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Facturas Registradas (${supplierInvoices.size})",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (isExpanded) "Ocultar" else "Ver facturas",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Collapsible invoices list
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (supplierInvoices.isEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "No hay facturas cargadas para este proveedor. Pulsa 'Cargar Factura' para añadir una.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        } else {
                                            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                            supplierInvoices.forEach { invoice ->
                                                val linkedProducts = products.filter {
                                                    it.invoiceNumber.trim().equals(invoice.invoiceNumber.trim(), ignoreCase = true)
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        ResponsiveActionRow(
                                                            breakpoint = 280.dp,
                                                            content = {
                                                                Text(
                                                                    "Factura #${invoice.invoiceNumber}",
                                                                    fontWeight = FontWeight.Bold,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            },
                                                            action = {
                                                                Text(
                                                                    "\$${String.format("%.2f", invoice.totalAmount)} USD",
                                                                    fontWeight = FontWeight.Bold,
                                                                    style = MaterialTheme.typography.bodyMedium
                                                                )
                                                            }
                                                        )

                                                        ResponsiveActionRow(
                                                            breakpoint = 280.dp,
                                                            stackedActionAlignment = Alignment.Start,
                                                            content = {
                                                                Text(
                                                                    dateFormat.format(Date(invoice.timestamp)),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            },
                                                            action = {
                                                                if (invoice.description.isNotBlank()) {
                                                                    Text(
                                                                        invoice.description,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        )

                                                        // Linked Products in Inventory
                                                        if (linkedProducts.isNotEmpty()) {
                                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF10B981))
                                                                Text(
                                                                    "Productos en inventario (${linkedProducts.size}): ${linkedProducts.joinToString(", ") { it.name }}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = Color(0xFF047857),
                                                                    fontWeight = FontWeight.Medium
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
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var doc by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }

        KeyboardAwareAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nuevo Proveedor", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre / Razón Social *") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = doc,
                        onValueChange = { doc = it },
                        label = { Text("RIF / CI *") },
                        placeholder = { Text("Ej: J-12345678-9") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección (Opcional)") },
                        leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && doc.isNotBlank()) {
                            viewModel.addSupplier(name.trim(), doc.trim(), phone.trim(), address.trim())
                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank() && doc.isNotBlank()
                ) {
                    Text("Guardar Proveedor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showInvoiceDialog && selectedSupplierId != null) {
        val supplier = suppliers.find { it.id == selectedSupplierId }
        var invoiceNum by remember { mutableStateOf("") }
        var subtotalAmt by remember { mutableStateOf("") }
        var isTaxIncluded by remember { mutableStateOf(true) }
        var desc by remember { mutableStateOf("") }

        // Retentions
        var applyRetentions by remember { mutableStateOf(true) }
        var ivaRetentionPercentage by remember { mutableStateOf("75") } // 75, 100
        var islrRetentionPercentage by remember { mutableStateOf("2.0") } // 0, 1, 2, 3, 5
        var voucherNumberInput by remember { mutableStateOf("") }

        val parsedSubtotal = subtotalAmt.replace(",", ".").toDoubleOrNull() ?: 0.0
        val calculatedIva = if (parsedSubtotal > 0) parsedSubtotal * 0.16 else 0.0
        val grandTotal = parsedSubtotal + calculatedIva

        val parsedIvaPerc = ivaRetentionPercentage.toDoubleOrNull() ?: 75.0
        val retencionIvaVal = if (applyRetentions && calculatedIva > 0) calculatedIva * (parsedIvaPerc / 100.0) else 0.0

        val parsedIslrPerc = islrRetentionPercentage.toDoubleOrNull() ?: 2.0
        val retencionIslrVal = if (applyRetentions && parsedSubtotal > 0) parsedSubtotal * (parsedIslrPerc / 100.0) else 0.0

        val netPayableToSupplier = maxOf(0.0, grandTotal - retencionIvaVal - retencionIslrVal)

        // Generate voucher number suggestion (Format: YYYYMM + 00000001)
        LaunchedEffect(Unit) {
            val dateStr = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
            val randomNum = String.format("%08d", (1..99999).random())
            voucherNumberInput = "$dateStr$randomNum"
        }

        KeyboardAwareAlertDialog(
            onDismissRequest = { showInvoiceDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cargar Factura de Proveedor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        supplier?.let {
                            Text(
                                "Proveedor: ${it.name} (${it.documentId})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = { showInvoiceDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = invoiceNum,
                        onValueChange = { invoiceNum = it },
                        label = { Text("Número de Factura / Control *") },
                        placeholder = { Text("Ej: FAC-10045") },
                        leadingIcon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Subtotal & Automatic IVA Calculation
                    OutlinedTextField(
                        value = subtotalAmt,
                        onValueChange = { subtotalAmt = it },
                        label = { Text("Base Imponible Subtotal ($ USD) *") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                        trailingIcon = {
                            if (parsedSubtotal > 0) {
                                Text(
                                    "+ IVA (16%): \$${String.format(Locale.US, "%.2f", calculatedIva)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (parsedSubtotal > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Monto Bruto Factura:", style = MaterialTheme.typography.labelSmall)
                                Text("\$${String.format(Locale.US, "%.2f", grandTotal)} USD", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // SENIAT Retentions Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ResponsiveActionRow(
                                breakpoint = 280.dp,
                                content = {
                                    Column {
                                        Text("Retenciones a Practicar al Proveedor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        Text("Retención de IVA e ISLR según normativa SENIAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                action = {
                                    Switch(
                                        checked = applyRetentions,
                                        onCheckedChange = { applyRetentions = it }
                                    )
                                }
                            )

                            if (applyRetentions) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                Text("% Retención IVA:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = ivaRetentionPercentage == "75",
                                        onClick = { ivaRetentionPercentage = "75" },
                                        label = { Text("75% IVA (-\$${String.format(Locale.US, "%.2f", calculatedIva * 0.75)})") }
                                    )
                                    FilterChip(
                                        selected = ivaRetentionPercentage == "100",
                                        onClick = { ivaRetentionPercentage = "100" },
                                        label = { Text("100% IVA (-\$${String.format(Locale.US, "%.2f", calculatedIva)})") }
                                    )
                                }

                                Text("% Retención ISLR:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("0.0", "1.0", "2.0", "3.0", "5.0").forEach { perc ->
                                        FilterChip(
                                            selected = islrRetentionPercentage == perc,
                                            onClick = { islrRetentionPercentage = perc },
                                            label = { Text("${perc.replace(".0", "")}% ISLR", fontSize = 11.sp) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = voucherNumberInput,
                                    onValueChange = { voucherNumberInput = it },
                                    label = { Text("N° Comprobante Retención Emitido") },
                                    placeholder = { Text("AAAAMM00000001") },
                                    leadingIcon = { Icon(Icons.Filled.ConfirmationNumber, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                                )

                                Surface(
                                    color = Color(0xFFFEF2F2),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Retención IVA ($parsedIvaPerc%):", style = MaterialTheme.typography.labelSmall, color = Color(0xFF991B1B))
                                            Text("-\$${String.format(Locale.US, "%.2f", retencionIvaVal)} USD", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Retención ISLR ($parsedIslrPerc%):", style = MaterialTheme.typography.labelSmall, color = Color(0xFF991B1B))
                                            Text("-\$${String.format(Locale.US, "%.2f", retencionIslrVal)} USD", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                        }
                                        HorizontalDivider(color = Color(0xFFFCA5A5))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Monto Neto a Pagar al Proveedor:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                            Text("\$${String.format(Locale.US, "%.2f", netPayableToSupplier)} USD", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Descripción / Concepto (Opcional)") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (invoiceNum.isNotBlank() && parsedSubtotal > 0) {
                            viewModel.addPurchaseInvoice(
                                supplierId = selectedSupplierId!!,
                                invoiceNumber = invoiceNum.trim(),
                                totalAmount = grandTotal,
                                description = desc.trim(),
                                subtotalUsd = parsedSubtotal,
                                taxAmountUsd = calculatedIva,
                                retencionIvaUsd = retencionIvaVal,
                                retencionIvaPercentage = if (applyRetentions) parsedIvaPerc else 0.0,
                                retencionIslrUsd = retencionIslrVal,
                                retencionIslrPercentage = if (applyRetentions) parsedIslrPerc else 0.0,
                                withholdingVoucherNumber = if (applyRetentions) voucherNumberInput.trim() else "",
                                withholdingStatus = if (applyRetentions && (retencionIvaVal > 0 || retencionIslrVal > 0)) "PENDIENTE_ENTERA" else "NO_APLICA"
                            )
                            showInvoiceDialog = false
                        }
                    },
                    enabled = invoiceNum.isNotBlank() && parsedSubtotal > 0
                ) {
                    Text("Guardar Factura y Retenciones", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInvoiceDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
