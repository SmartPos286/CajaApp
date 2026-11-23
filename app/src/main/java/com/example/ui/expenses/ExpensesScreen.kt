package com.example.ui.expenses

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Expense
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpensesScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("TODOS") }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    val todaySdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val monthSdf = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()) }
    val fullSdf = remember { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()) }

    val todayStr = remember { todaySdf.format(Date()) }
    val monthStr = remember { monthSdf.format(Date()) }

    // Analytics
    val todayExpenses = remember(expenses, todayStr) {
        expenses.filter { todaySdf.format(Date(it.timestamp)) == todayStr }
    }
    val todayTotalUsd = todayExpenses.sumOf { it.amountUsd }
    val todayTotalBs = todayExpenses.sumOf { it.amountBs }

    val monthExpenses = remember(expenses, monthStr) {
        expenses.filter { monthSdf.format(Date(it.timestamp)) == monthStr }
    }
    val monthTotalUsd = monthExpenses.sumOf { it.amountUsd }

    val filteredExpenses = remember(expenses, selectedCategoryFilter) {
        if (selectedCategoryFilter == "TODOS") {
            expenses
        } else {
            expenses.filter { it.category.uppercase().contains(selectedCategoryFilter.uppercase()) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Registrar Gasto", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFFE11D48),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Title
            ResponsiveActionRow(
                modifier = Modifier.padding(vertical = 12.dp),
                breakpoint = 360.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Column {
                        Text(
                            "Gastos Operativos del Negocio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Renta, Electricidad, Internet, Sueldos, Mantenimiento y Servicios",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                action = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2)
                    ) {
                        Text(
                            text = "Modulo Admin/Supervisor",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            // Summary KPI Cards
            ResponsivePair(
                breakpoint = 320.dp,
                horizontalSpacing = 10.dp,
                verticalSpacing = 10.dp,
                first = { mod ->
                    // Today Expenses Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = mod
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Today, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                Text("Gastos de Hoy", style = MaterialTheme.typography.labelSmall, color = Color(0xFF991B1B))
                            }
                            Text(
                                "\$${String.format(Locale.US, "%.2f", todayTotalUsd)} USD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                            Text(
                                "Bs. ${String.format(Locale.US, "%.2f", todayTotalBs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                },
                second = { mod ->
                    // Month Expenses Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        border = BorderStroke(1.dp, Color(0xFFFDBA74)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = mod
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                                Text("Gastos del Mes ($monthStr)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A3412))
                            }
                            Text(
                                "\$${String.format(Locale.US, "%.2f", monthTotalUsd)} USD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C)
                            )
                            Text(
                                "Total Reg: ${monthExpenses.size} gastos",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9A3412)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val categories = listOf("TODOS", "RENTA", "LUZ", "INTERNET", "SUELDOS", "MANTENIMIENTO", "OTROS")
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 11.5.sp, fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expenses List
            if (filteredExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            "No se han registrado gastos operativos en esta categoría.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Registrar Primer Gasto")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        val icon = getExpenseCategoryIcon(expense.category)
                        val dateFormatted = fullSdf.format(Date(expense.timestamp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            ResponsiveActionRow(
                                modifier = Modifier.padding(14.dp),
                                breakpoint = 340.dp,
                                content = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFFE4E6),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(22.dp))
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    expense.category,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                if (expense.invoiceNumber.isNotBlank()) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            "Fact/RIF: ${expense.invoiceNumber}",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (expense.description.isNotBlank()) {
                                                Text(
                                                    expense.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    dateFormatted,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    "Método: ${expense.paymentMethod}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (expense.registeredBy.isNotBlank()) {
                                                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        "Reg: ${expense.registeredBy}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                action = {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "-\$${String.format(Locale.US, "%.2f", expense.amountUsd)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color(0xFFDC2626)
                                        )
                                        Text(
                                            "Bs. ${String.format(Locale.US, "%.2f", expense.amountBs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF991B1B)
                                        )

                                        IconButton(
                                            onClick = { expenseToDelete = expense },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Eliminar",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
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

    // Modal to add new expense
    if (showAddDialog) {
        AddExpenseDialog(
            viewModel = viewModel,
            bcvRate = effectiveRate,
            currentUser = currentUser?.username ?: "Administrador",
            onDismiss = { showAddDialog = false },
            onExpenseAdded = {
                Toast.makeText(context, "¡Gasto registrado con éxito!", Toast.LENGTH_SHORT).show()
                showAddDialog = false
            }
        )
    }

    // Modal to confirm expense deletion
    expenseToDelete?.let { expense ->
        KeyboardAwareAlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Eliminar Registro de Gasto", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar el gasto de '${expense.category}' por \$${String.format(Locale.US, "%.2f", expense.amountUsd)} USD?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(expense) {
                            Toast.makeText(context, "Gasto eliminado", Toast.LENGTH_SHORT).show()
                            expenseToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
    viewModel: PosViewModel,
    bcvRate: Double,
    currentUser: String,
    onDismiss: () -> Unit,
    onExpenseAdded: () -> Unit
) {
    val categories = listOf(
        "Renta / Alquiler",
        "Servicio Eléctrico (Luz)",
        "Internet / Telecomunicaciones",
        "Sueldos / Nómina",
        "Mantenimiento y Reparaciones",
        "Servicios Públicos (Agua/Gas)",
        "Impuestos / Tasas Municipales",
        "Otros Gastos Operativos"
    )

    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var customCategoryInput by remember { mutableStateOf("") }
    var invoiceNumberInput by remember { mutableStateOf("") }
    var amountUsdInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Efectivo USD") }
    var descriptionInput by remember { mutableStateOf("") }

    val parsedAmountUsd = amountUsdInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val calculatedAmountBs = parsedAmountUsd * bcvRate

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
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Color(0xFFE11D48))
                    Text("Registrar Gasto Operativo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                // Category Picker
                Text("Tipo / Categoría de Gasto:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                // Invoice Code / RIF Field
                OutlinedTextField(
                    value = invoiceNumberInput,
                    onValueChange = { invoiceNumberInput = it },
                    label = { Text("N° de Factura / RIF / Código Comprobante") },
                    placeholder = { Text("Ej: FAC-00912 o RIF J-12345678") },
                    leadingIcon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                // Amount USD Field with Live Bs. Conversion
                OutlinedTextField(
                    value = amountUsdInput,
                    onValueChange = { amountUsdInput = it },
                    label = { Text("Monto del Gasto ($ USD)") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("$ ", fontWeight = FontWeight.Bold, color = Color(0xFFE11D48)) },
                    trailingIcon = {
                        if (parsedAmountUsd > 0) {
                            Text(
                                "≈ Bs. ${String.format(Locale.US, "%.2f", calculatedAmountBs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                // Payment Method
                Text("Método de Pago Empleado:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Efectivo USD", "Pago Móvil", "Transferencia", "Punto").forEach { m ->
                        FilterChip(
                            selected = paymentMethod == m,
                            onClick = { paymentMethod = m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }

                // Description / Notes
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Descripción o Detalle del Gasto") },
                    placeholder = { Text("Ej: Pago mensual de alquiler local comercial...") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                Text(
                    "ℹ️ Registrando gasto como: '$currentUser' a tasa BCV Bs. ${String.format(Locale.US, "%.2f", bcvRate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedAmountUsd <= 0) {
                        return@Button
                    }
                    val finalCategory = if (selectedCategory == "Otros Gastos Operativos" && customCategoryInput.isNotBlank()) {
                        customCategoryInput
                    } else {
                        selectedCategory
                    }

                    viewModel.addExpense(
                        category = finalCategory,
                        invoiceNumber = invoiceNumberInput,
                        amountUsd = parsedAmountUsd,
                        paymentMethod = paymentMethod,
                        description = descriptionInput,
                        onSuccess = onExpenseAdded
                    )
                },
                enabled = parsedAmountUsd > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Guardar Gasto", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun getExpenseCategoryIcon(category: String): ImageVector {
    val upper = category.uppercase()
    return when {
        upper.contains("RENTA") || upper.contains("ALQUILER") -> Icons.Filled.Store
        upper.contains("LUZ") || upper.contains("ELÉCTRICO") || upper.contains("CORTE") -> Icons.Filled.Lightbulb
        upper.contains("INTERNET") || upper.contains("TELECOM") -> Icons.Filled.Wifi
        upper.contains("SUELDO") || upper.contains("NÓMINA") -> Icons.Filled.Badge
        upper.contains("MANTENIMIENTO") || upper.contains("REPARACIÓN") -> Icons.Filled.Build
        upper.contains("AGUA") || upper.contains("PÚBLICO") -> Icons.Filled.WaterDrop
        upper.contains("IMPUESTO") || upper.contains("TASA") -> Icons.Filled.AccountBalance
        else -> Icons.Filled.ReceiptLong
    }
}
