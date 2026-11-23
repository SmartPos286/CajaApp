package com.example.ui.customers

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Customer
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.bringIntoViewOnFocus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomersScreen(viewModel: PosViewModel) {
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilterType by remember { mutableStateOf("TODOS") }

    val filteredCustomers = remember(customers, selectedFilterType) {
        if (selectedFilterType == "TODOS") customers
        else customers.filter { it.clientType == selectedFilterType }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Nuevo Cliente SENIAT", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header - Compact Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Directorio de Clientes SENIAT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "${customers.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Category Filter Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedFilterType == "TODOS",
                    onClick = { selectedFilterType = "TODOS" },
                    label = { Text("Todos los Clientes") }
                )
                FilterChip(
                    selected = selectedFilterType == "PERSONA_NATURAL",
                    onClick = { selectedFilterType = "PERSONA_NATURAL" },
                    label = { Text("Natural (V/E)") }
                )
                FilterChip(
                    selected = selectedFilterType == "PERSONA_JURIDICA",
                    onClick = { selectedFilterType = "PERSONA_JURIDICA" },
                    label = { Text("Jurídica (J)") }
                )
                FilterChip(
                    selected = selectedFilterType == "GOBIERNO",
                    onClick = { selectedFilterType = "GOBIERNO" },
                    label = { Text("Gobierno (G)") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Filled.People,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            "No hay clientes registrados en esta categoría.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { showAddDialog = true }) {
                            Text("+ Crear Cliente")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        CustomerCardItem(customer)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomerDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun CustomerCardItem(customer: Customer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ResponsiveActionRow(
                breakpoint = 300.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Text(
                        customer.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                action = {
                    // SENIAT Type Badge
                    val (badgeBg, badgeText, label) = when (customer.clientType) {
                        "PERSONA_JURIDICA" -> Triple(Color(0xFFDBEAFE), Color(0xFF1E40AF), "JURÍDICA (J)")
                        "GOBIERNO" -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "GOBIERNO (G)")
                        else -> Triple(Color(0xFFF3F4F6), Color(0xFF374151), "NATURAL (V/E)")
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text("CI / RIF: ${customer.documentId}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            if (customer.phone.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("Telf: ${customer.phone}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (customer.address.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("Dir: ${customer.address}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }

            if (customer.isWithholdingAgent) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(14.dp))
                        Text(
                            "Agente de Retención SENIAT (IVA ${customer.ivaRetentionPercentage.toInt()}% • ISLR ${customer.islrRetentionPercentage.toInt()}%)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCustomerDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onCustomerAdded: ((Customer) -> Unit)? = null
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var documentPrefix by remember { mutableStateOf("V-") }
    var documentNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    // SENIAT Classification
    var clientType by remember { mutableStateOf("PERSONA_NATURAL") } // PERSONA_NATURAL, PERSONA_JURIDICA, GOBIERNO
    var isWithholdingAgent by remember { mutableStateOf(false) }
    var ivaRetentionPercentage by remember { mutableStateOf("75") } // 75 o 100
    var islrRetentionPercentage by remember { mutableStateOf("2.0") } // 0, 1, 2, 3, 5

    // Auto-adjust prefix when client type changes
    LaunchedEffect(clientType) {
        documentPrefix = when (clientType) {
            "PERSONA_JURIDICA" -> "J-"
            "GOBIERNO" -> "G-"
            else -> "V-"
        }
        if (clientType == "GOBIERNO") {
            isWithholdingAgent = true
        }
    }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        title = {
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
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Registrar Cliente SENIAT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Client Type SENIAT Selector
                Text("Tipo de Contribuyente / Cliente SENIAT:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = clientType == "PERSONA_NATURAL",
                        onClick = { clientType = "PERSONA_NATURAL" },
                        label = { Text("Natural (V/E)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = clientType == "PERSONA_JURIDICA",
                        onClick = { clientType = "PERSONA_JURIDICA" },
                        label = { Text("Jurídica (J)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = clientType == "GOBIERNO",
                        onClick = { clientType = "GOBIERNO" },
                        label = { Text("Gobierno (G)", fontSize = 11.sp) }
                    )
                }

                // Customer Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo o Razón Social *") },
                    placeholder = { Text("Ej: Comercializadora Los Andes C.A.") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                // Document ID (RIF / Cédula) with Prefix Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val prefixes = listOf("V-", "J-", "G-", "E-", "P-")
                    var expandedPrefix by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.width(84.dp)) {
                        OutlinedButton(
                            onClick = { expandedPrefix = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(documentPrefix, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expandedPrefix,
                            onDismissRequest = { expandedPrefix = false }
                        ) {
                            prefixes.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        documentPrefix = p
                                        expandedPrefix = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = documentNumber,
                        onValueChange = { documentNumber = it.filter { char -> char.isDigit() } },
                        label = { Text("Número Cédula / RIF *") },
                        placeholder = { Text("12345678") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus()
                    )
                }

                // Phone & Address
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono de Contacto") },
                    placeholder = { Text("Ej: 0414-1234567") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección Fiscal SENIAT") },
                    placeholder = { Text("Ej: Av. Principal, Edif. Centro, Local 1") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Withholding Agent Settings (Sujetos Pasivos Especiales SENIAT)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResponsiveActionRow(
                            breakpoint = 280.dp,
                            content = {
                                Column {
                                    Text("¿Es Agente de Retención SENIAT?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Sujeto Pasivo Especial o Ente Público autorizado a retener IVA/ISLR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            action = {
                                Switch(
                                    checked = isWithholdingAgent,
                                    onCheckedChange = { isWithholdingAgent = it }
                                )
                            }
                        )

                        if (isWithholdingAgent) {
                            Text("Porcentaje Retención de IVA:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = ivaRetentionPercentage == "75",
                                    onClick = { ivaRetentionPercentage = "75" },
                                    label = { Text("75% IVA (Estándar)") }
                                )
                                FilterChip(
                                    selected = ivaRetentionPercentage == "100",
                                    onClick = { ivaRetentionPercentage = "100" },
                                    label = { Text("100% IVA (Especial/Gob)") }
                                )
                            }

                            Text("Porcentaje Retención de ISLR:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || documentNumber.isBlank()) {
                        Toast.makeText(context, "Por favor completa el nombre y cédula/RIF", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val fullDocument = "$documentPrefix${documentNumber.trim()}"
                    val ivaPerc = ivaRetentionPercentage.toDoubleOrNull() ?: 75.0
                    val islrPerc = islrRetentionPercentage.toDoubleOrNull() ?: 2.0

                    viewModel.addCustomer(
                        name = name.trim(),
                        documentId = fullDocument,
                        address = address.trim(),
                        phone = phone.trim(),
                        clientType = clientType,
                        isWithholdingAgent = isWithholdingAgent,
                        ivaRetentionPercentage = ivaPerc,
                        islrRetentionPercentage = islrPerc
                    )

                    Toast.makeText(context, "Cliente SENIAT registrado correctamente", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Guardar Cliente SENIAT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
