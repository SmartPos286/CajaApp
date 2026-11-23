package com.example.ui.inventory

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.Product
import com.example.ui.NotificationType
import com.example.ui.SystemNotification
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.utils.StockAlertNotificationHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryAlertsDialog(
    lowStockProducts: List<Product>,
    allProducts: List<Product>,
    systemNotifications: List<SystemNotification> = emptyList(),
    canManageAlerts: Boolean,
    onDisableProductAlert: (Product) -> Unit,
    onEditProduct: ((Product) -> Unit)? = null,
    onGoToInventory: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasOsNotifPermission by remember {
        mutableStateOf(StockAlertNotificationHelper.hasPostNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasOsNotifPermission = granted
    }

    val otherNotifications = remember(systemNotifications) {
        systemNotifications.filter { it.type != NotificationType.LOW_STOCK }
    }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        maxWidth = 580.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = if (lowStockProducts.isNotEmpty()) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Alertas de Inventario",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar alertas")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Android 13+ notification permission prompt
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasOsNotifPermission) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Notificaciones del dispositivo desactivadas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Activa el permiso para recibir alertas en la barra de notificaciones cuando un producto llegue a su stock mínimo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Activar notificaciones")
                            }
                        }
                    }
                }

                if (lowStockProducts.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF059669)
                            )
                            Column {
                                Text(
                                    text = "Stock bajo control",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                                Text(
                                    text = "Ningún producto con alerta activa ha alcanzado su cantidad mínima.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "${lowStockProducts.size} producto(s) en stock mínimo o agotados:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    lowStockProducts.forEach { product ->
                        val effStock = product.effectiveStockForAlert(allProducts).coerceAtLeast(0.0)
                        val isOut = effStock <= 1e-6
                        val accent = if (isOut) Color(0xFFDC2626) else Color(0xFFD97706)
                        val bg = if (isOut) Color(0xFFDC2626).copy(alpha = 0.08f) else Color(0xFFF59E0B).copy(alpha = 0.10f)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stock_alert_item_${product.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Cód: ${product.code.ifBlank { "SIN CÓD." }} • ${product.category.ifBlank { "General" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accent.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = if (isOut) {
                                                "AGOTADO (Mín: ${StockAlertNotificationHelper.formatStockQuantity(product.minStockAlertQuantity)})"
                                            } else {
                                                "Stock: ${StockAlertNotificationHelper.formatStockQuantity(effStock)} / Mín: ${StockAlertNotificationHelper.formatStockQuantity(product.minStockAlertQuantity)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (canManageAlerts || onEditProduct != null) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (canManageAlerts) {
                                            OutlinedButton(
                                                onClick = { onDisableProductAlert(product) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 40.dp)
                                                    .testTag("disable_alert_button_${product.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.NotificationsOff,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Quitar alerta (no importante)", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        if (onEditProduct != null) {
                                            TextButton(
                                                onClick = { onEditProduct(product) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Editar mínimo", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (otherNotifications.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Otros avisos del sistema:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    otherNotifications.forEach { notif ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when (notif.type) {
                                    NotificationType.LOW_STOCK -> Color(0xFFFEF2F2)
                                    NotificationType.LICENSE_EXPIRY -> Color(0xFFFFF7ED)
                                    NotificationType.SYSTEM_ALERT -> Color(0xFFEFF6FF)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                when (notif.type) {
                                    NotificationType.LOW_STOCK -> Color(0xFFFECACA)
                                    NotificationType.LICENSE_EXPIRY -> Color(0xFFFED7AA)
                                    NotificationType.SYSTEM_ALERT -> Color(0xFFBFDBFE)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (notif.type) {
                                            NotificationType.LOW_STOCK -> Icons.Filled.Inventory
                                            NotificationType.LICENSE_EXPIRY -> Icons.Filled.VpnKey
                                            NotificationType.SYSTEM_ALERT -> Icons.Filled.Warning
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = when (notif.type) {
                                            NotificationType.LOW_STOCK -> Color(0xFFDC2626)
                                            NotificationType.LICENSE_EXPIRY -> Color(0xFFD97706)
                                            NotificationType.SYSTEM_ALERT -> Color(0xFF2563EB)
                                        }
                                    )
                                    Text(notif.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(notif.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onGoToInventory != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onGoToInventory()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Ir a Inventario")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cerrar")
                }
            }
        }
    )
}
