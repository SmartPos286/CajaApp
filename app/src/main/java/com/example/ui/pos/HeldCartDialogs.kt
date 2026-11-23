package com.example.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.HeldSale
import com.example.data.local.HeldSaleItem
import com.example.ui.CartItem
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.bringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HoldCartDialog(
    cart: List<CartItem>,
    customerName: String,
    customerDocument: String,
    onDismiss: () -> Unit,
    onConfirm: (note: String) -> Unit
) {
    var note by remember {
        mutableStateOf(
            if (customerName.isNotBlank() && customerName != "Cliente General") {
                "Cliente: $customerName"
            } else {
                "Factura #${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
            }
        )
    }

    val totalItems = cart.sumOf { it.quantity }
    val totalEstimatedUsd = cart.sumOf { it.product.priceUsd * it.quantity }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.PauseCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Guardar Factura en Espera", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Esta opción deja la factura en pausa para que puedas seguir cobrando a otros clientes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Artículos:", style = MaterialTheme.typography.labelMedium)
                            Text("$totalItems producto(s)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Estimado:", style = MaterialTheme.typography.labelMedium)
                            Text("\$${String.format("%.2f", totalEstimatedUsd)} USD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota de Referencia o Nombre *") },
                    placeholder = { Text("Ej. Mesa 3, Juan Pérez, etc.") },
                    leadingIcon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar en Espera")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun HeldSalesListDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onRestore: (HeldSale) -> Unit
) {
    val heldSales by viewModel.allHeldSales.collectAsStateWithLifecycle()
    var selectedHeldSale by remember { mutableStateOf<HeldSale?>(null) }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 600.dp,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
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
                        Icons.Filled.PauseCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text("Facturas en Espera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${heldSales.size} factura(s) en pausa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (heldSales.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No hay facturas en espera", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cuando un cliente pague después, guárdala aquí para seguir facturando.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(heldSales) { heldSale ->
                        HeldSaleItemCard(
                            heldSale = heldSale,
                            viewModel = viewModel,
                            onRestoreClick = {
                                onRestore(heldSale)
                                onDismiss()
                            },
                            onDeleteClick = {
                                viewModel.deleteHeldSale(heldSale.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeldSaleItemCard(
    heldSale: HeldSale,
    viewModel: PosViewModel,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val itemsFlow = remember(heldSale.id) { viewModel.getItemsForHeldSale(heldSale.id) }
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList())
    var expanded by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        heldSale.note.ifBlank { "Factura en Espera" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${heldSale.customerName} (${heldSale.customerDocument})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        sdf.format(Date(heldSale.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "\$${String.format("%.2f", heldSale.totalEstimatedUsd)} USD",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${heldSale.itemCount} producto(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expand / Items preview
            if (items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (expanded) "Ocultar detalle" else "Ver ${items.size} producto(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (expanded) {
                            Spacer(Modifier.height(4.dp))
                            items.forEach { itm ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${itm.quantity}x ${itm.productName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "\$${String.format("%.2f", itm.priceUsd * itm.quantity)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onRestoreClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cargar al Carrito", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreateDeliveryNoteDialog(
    cart: List<CartItem>,
    bcvRate: Double,
    customerName: String,
    customerDocument: String,
    initialCurrency: String = "USD",
    onDismiss: () -> Unit,
    onConfirm: (customerName: String, customerDoc: String, recipientName: String, reason: String, displayCurrency: String) -> Unit
) {
    var custName by remember { mutableStateOf(if (customerName.isNotBlank() && customerName != "Cliente General") customerName else "") }
    var custDoc by remember { mutableStateOf(if (customerDocument.isNotBlank() && customerDocument != "V-00000000") customerDocument else "") }
    var recipientName by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Despacho de mercancía - No válido para crédito fiscal") }
    var selectedCurrency by remember { mutableStateOf(if (initialCurrency.equals("BS", ignoreCase = true)) "BS" else "USD") }

    val totalItems = cart.sumOf { it.quantity }
    val totalUsd = cart.sumOf { it.effectivePrice * it.quantity }
    val totalBs = totalUsd * bcvRate
    val useBs = selectedCurrency == "BS"

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Emitir Nota de Entrega", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Descuenta automáticamente el inventario sin generar factura ni IVA. Documento: Nota de entrega (no válido para crédito fiscal).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResponsiveActionRow(
                        breakpoint = 340.dp,
                        modifier = Modifier.padding(10.dp),
                        content = {
                            Column {
                                Text("Total $totalItems producto(s)", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = if (useBs) {
                                        "Total: Bs. ${String.format(Locale.US, "%.2f", totalBs)}"
                                    } else {
                                        "Total: \$${String.format(Locale.US, "%.2f", totalUsd)} USD"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        },
                        action = {
                            Text(
                                text = if (useBs) "Tasa: Bs. ${String.format(Locale.US, "%.2f", bcvRate)}" else "Sin IVA",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                // Moneda del documento (USD o Bolívares)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Moneda a reflejar en la Nota:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !useBs,
                            onClick = { selectedCurrency = "USD" },
                            leadingIcon = if (!useBs) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text("Dólares ($ USD)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        )
                        FilterChip(
                            selected = useBs,
                            onClick = { selectedCurrency = "BS" },
                            leadingIcon = if (useBs) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text("Bolívares (Bs.)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = custName,
                    onValueChange = { custName = it },
                    label = { Text("Nombre o Razón Social Cliente *") },
                    placeholder = { Text("Ej. Distribuidora Los Andes") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                OutlinedTextField(
                    value = custDoc,
                    onValueChange = { custDoc = it },
                    label = { Text("Cédula / RIF *") },
                    placeholder = { Text("Ej. J-12345678-9") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Nombre de quien recibe (Opcional)") },
                    placeholder = { Text("Persona que firma la recepción") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo u Observación") },
                    placeholder = { Text("Ej. Despacho directo, Muestra, Consignación") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (custName.isBlank()) "Cliente General" else custName.trim()
                    val finalDoc = if (custDoc.isBlank()) "V-00000000" else custDoc.trim()
                    val finalRecip = if (recipientName.isBlank()) finalName else recipientName.trim()
                    onConfirm(finalName, finalDoc, finalRecip, reason.trim(), selectedCurrency)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generar y Despachar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text("Cancelar")
            }
        }
    )
}
