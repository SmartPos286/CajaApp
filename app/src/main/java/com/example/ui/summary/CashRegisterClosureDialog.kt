package com.example.ui.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CashRegisterClosureDialog(
    sales: List<Sale>,
    items: List<SaleItem>,
    rateBcv: Double,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val effectiveRate = if (rateBcv > 0.001) rateBcv else 1.0

    // Calculations
    val totalCashUsd = sales.sumOf { it.paymentCashUsd }
    val totalMobileBs = sales.sumOf { it.paymentMobileBs }
    val totalCardBs = sales.sumOf { it.paymentCardBs }
    val totalCasheaUsd = sales.sumOf { it.paymentCasheaUsd }

    val generalTotalUsd = sales.sumOf { it.totalUsd }
    val generalTotalBs = sales.sumOf { it.totalBs }

    val totalBaseTaxableUsd = sales.sumOf { it.baseTaxableUsd }
    val totalBaseExemptUsd = sales.sumOf { it.baseExemptUsd }
    val totalDiscountUsd = sales.sumOf { it.discountUsd }
    val totalIvaUsd = sales.sumOf { it.ivaUsd }
    val totalIgtfUsd = sales.sumOf { it.igtfUsd }
    val totalIgtfBs = sales.sumOf { it.igtfBs }

    val totalBaseTaxableBs = totalBaseTaxableUsd * effectiveRate
    val totalBaseExemptBs = totalBaseExemptUsd * effectiveRate
    val totalDiscountBs = totalDiscountUsd * effectiveRate
    val totalIvaBs = totalIvaUsd * effectiveRate

    // Products grouped
    data class ProductGroup(
        val name: String,
        val isTaxExempt: Boolean,
        val quantity: Double,
        val totalUsd: Double
    )

    val groupedItems = items.groupBy { it.productName }.map { (name, pList) ->
        ProductGroup(
            name = name,
            isTaxExempt = pList.any { it.isTaxExempt },
            quantity = pList.sumOf { it.quantity },
            totalUsd = pList.sumOf { it.priceUsd * it.quantity }
        )
    }.sortedByDescending { it.quantity }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
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
                    Icon(
                        Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text("Cierre de Caja del Día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val todayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        Text(todayFmt.format(Date()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Ingresos por Medio de Pago
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ingresos por Método de Pago", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo Divisas ($):", style = MaterialTheme.typography.bodySmall)
                            Text("\$${String.format(Locale.US, "%.2f", totalCashUsd)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pago Móvil (Bs):", style = MaterialTheme.typography.bodySmall)
                            Text("Bs. ${String.format(Locale.US, "%.2f", totalMobileBs)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Punto de Venta / Tarjeta (Bs):", style = MaterialTheme.typography.bodySmall)
                            Text("Bs. ${String.format(Locale.US, "%.2f", totalCardBs)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cashea ($ USD):", style = MaterialTheme.typography.bodySmall)
                            Text("\$${String.format(Locale.US, "%.2f", totalCasheaUsd)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL FACTURADO:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format(Locale.US, "%.2f", generalTotalUsd)} USD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                Text("Bs. ${String.format(Locale.US, "%.2f", generalTotalBs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Card 2: Desglose Fiscal e Impuestos
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Desglose Fiscal (SENIAT / Impuestos)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        ResponsiveActionRow(
                            breakpoint = 290.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = { Text("Base Imponible (16%):", style = MaterialTheme.typography.bodySmall) },
                            action = { Text("\$${String.format(Locale.US, "%.2f", totalBaseTaxableUsd)} (Bs. ${String.format(Locale.US, "%.2f", totalBaseTaxableBs)})", style = MaterialTheme.typography.bodySmall) }
                        )

                        if (totalBaseExemptUsd > 0.001) {
                            ResponsiveActionRow(
                                breakpoint = 290.dp,
                                verticalSpacing = 2.dp,
                                stackedActionAlignment = Alignment.Start,
                                content = { Text("Ventas Exentas (0%):", style = MaterialTheme.typography.bodySmall, color = Color(0xFF047857)) },
                                action = { Text("\$${String.format(Locale.US, "%.2f", totalBaseExemptUsd)} (Bs. ${String.format(Locale.US, "%.2f", totalBaseExemptBs)})", style = MaterialTheme.typography.bodySmall, color = Color(0xFF047857), fontWeight = FontWeight.Medium) }
                            )
                        }

                        if (totalDiscountUsd > 0.001) {
                            ResponsiveActionRow(
                                breakpoint = 290.dp,
                                verticalSpacing = 2.dp,
                                stackedActionAlignment = Alignment.Start,
                                content = { Text("Descuentos Otorgados (pre-IVA):", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE11D48)) },
                                action = { Text("-\$${String.format(Locale.US, "%.2f", totalDiscountUsd)} (-Bs. ${String.format(Locale.US, "%.2f", totalDiscountBs)})", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE11D48), fontWeight = FontWeight.Medium) }
                            )
                        }

                        ResponsiveActionRow(
                            breakpoint = 290.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = { Text("IVA Recaudado (16%):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) },
                            action = { Text("\$${String.format(Locale.US, "%.2f", totalIvaUsd)} (Bs. ${String.format(Locale.US, "%.2f", totalIvaBs)})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        )

                        if (totalIgtfUsd > 0.001) {
                            ResponsiveActionRow(
                                breakpoint = 290.dp,
                                verticalSpacing = 2.dp,
                                stackedActionAlignment = Alignment.Start,
                                content = { Text("IGTF Divisas (3%):", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB45309)) },
                                action = { Text("+\$${String.format(Locale.US, "%.2f", totalIgtfUsd)} (+Bs. ${String.format(Locale.US, "%.2f", totalIgtfBs)})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB45309)) }
                            )
                        }
                    }
                }

                // Card 3: Productos Vendidos
                Text("Productos Vendidos (${groupedItems.size} artículos)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                if (groupedItems.isEmpty()) {
                    Text("No hay productos vendidos hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (item.isTaxExempt) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "Exento",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF047857),
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "${item.quantity} un. (\$${String.format(Locale.US, "%.2f", item.totalUsd)})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExportPdf,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
