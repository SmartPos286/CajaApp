package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PosViewModel
import com.example.utils.GoogleSignInHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LicenseDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isLicensed by viewModel.isLicensed.collectAsStateWithLifecycle()
    val licenseKey by viewModel.licenseKey.collectAsStateWithLifecycle()
    val isUnlimitedInventory by viewModel.isUnlimitedInventory.collectAsStateWithLifecycle()
    val customProductLimit by viewModel.customProductLimit.collectAsStateWithLifecycle()
    val isUnlimitedDailyInvoices by viewModel.isUnlimitedDailyInvoices.collectAsStateWithLifecycle()
    val customDailyInvoiceLimit by viewModel.customDailyInvoiceLimit.collectAsStateWithLifecycle()
    val isFullTimeLicense by viewModel.isFullTimeLicense.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()

    val isGoogleSignedIn by viewModel.isGoogleSignedIn.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    val licenseGoogleAccount by viewModel.licenseGoogleAccount.collectAsStateWithLifecycle()
    val licenseActivatedAt by viewModel.licenseActivatedAt.collectAsStateWithLifecycle()
    val licenseExpiryTimestamp by viewModel.licenseExpiryTimestamp.collectAsStateWithLifecycle()
    val licenseStatus by viewModel.licenseStatus.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    val todaySalesCount = remember(viewModel.allSales) {
        viewModel.getTodaySalesCount()
    }

    var keyInput by remember(licenseKey) { mutableStateOf(licenseKey) }
    var unlimitedInv by remember(isUnlimitedInventory) { mutableStateOf(isUnlimitedInventory) }
    var prodLimitText by remember(customProductLimit) { mutableStateOf(customProductLimit.toString()) }
    var unlimitedInvoices by remember(isUnlimitedDailyInvoices) { mutableStateOf(isUnlimitedDailyInvoices) }
    var invLimitText by remember(customDailyInvoiceLimit) { mutableStateOf(customDailyInvoiceLimit.toString()) }
    var errorMessage by remember { mutableStateOf("") }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLicensed) Color(0xFF059669).copy(alpha = 0.15f) else Color(0xFFD97706).copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isLicensed) Icons.Filled.VerifiedUser else Icons.Filled.KeyOff,
                                contentDescription = null,
                                tint = if (isLicensed) Color(0xFF059669) else Color(0xFFD97706)
                            )
                        }
                    }
                    Column {
                        Text(
                            "Gestión de Licencia",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isLicensed) {
                                if (isFullTimeLicense || viewModel.isMasterKey(licenseKey)) "🌟 Licencia de Tiempo Completo Activa"
                                else "Licencia Administrador Activa"
                            } else "Versión sin Licencia (Límites activos)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLicensed) Color(0xFF059669) else Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

                // Current Status Summary Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLicensed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isLicensed) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLicensed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF059669).copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                                        Text(
                                            if (licenseExpiryTimestamp > 0L) "Licencia Temporal Activa" else "Licencia de Tiempo Completo",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF059669)
                                        )
                                    }

                                    if (licenseExpiryTimestamp > 0L) {
                                        val expiryStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(licenseExpiryTimestamp))
                                        val now = System.currentTimeMillis()
                                        val daysLeft = if (licenseExpiryTimestamp > now) {
                                            ((licenseExpiryTimestamp - now) / (1000 * 60 * 60 * 24)).toInt() + 1
                                        } else 0
                                        Text(
                                            "Vence el: $expiryStr ($daysLeft días restantes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (daysLeft <= 2) Color(0xFFDC2626) else Color(0xFF059669),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            "Validación de Tiempo: Autorización Permanente",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF059669),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (licenseActivatedAt > 0L) {
                                        val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(licenseActivatedAt))
                                        Text(
                                            "Fecha de Activación: $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.checkLicenseStatusWithServer { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Consultar Licencia en Servidor", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        ResponsiveActionRow(
                            breakpoint = 360.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Inventario Registrado:", style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                            action = {
                                Text(
                                    "${allProducts.size} / ${if (!isLicensed) "10 máx." else if (isUnlimitedInventory) "Ilimitado" else "$customProductLimit máx."}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (!isLicensed && allProducts.size >= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )

                        ResponsiveActionRow(
                            breakpoint = 360.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Facturas Emitidas Hoy:", style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                            action = {
                                Text(
                                    "$todaySalesCount / ${if (!isLicensed) "5 máx." else if (isUnlimitedDailyInvoices) "Ilimitadas" else "$customDailyInvoiceLimit máx."}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (!isLicensed && todaySalesCount >= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )

                        if (!isLicensed) {
                            Text(
                                "⚠️ Sin una clave de licencia registrada por el administrador, el sistema solo permite hasta 10 productos y 5 facturas diarias.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Register Key Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Código de Licencia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ingrese su código de activación para habilitar todas las funciones del sistema.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        errorMessage = ""
                    },
                    label = { Text("Código de Activación") },
                    placeholder = { Text("Ingrese clave...") },
                    leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
                    trailingIcon = {
                        if (keyInput.isNotBlank()) {
                            IconButton(onClick = { keyInput = ""; errorMessage = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!isLicensed) {
                    Button(
                        onClick = {
                            val cleanKey = keyInput.trim()
                            if (cleanKey.isBlank()) {
                                errorMessage = "Por favor ingresa el código de activación"
                                return@Button
                            }
                            val isMaster = viewModel.isMasterKey(cleanKey)
                            val adminName = currentUser?.username ?: "admin"
                            
                            viewModel.activateLicense(
                                key = cleanKey,
                                adminUser = adminName,
                                unlimitedInventory = isMaster || unlimitedInv,
                                productLimit = if (isMaster) 999999 else (prodLimitText.toIntOrNull() ?: 50),
                                unlimitedInvoices = isMaster || unlimitedInvoices,
                                invoiceLimit = if (isMaster) 999999 else (invLimitText.toIntOrNull() ?: 30),
                                onResultMsg = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (msg.contains("⚠️")) {
                                        errorMessage = msg
                                    } else {
                                        errorMessage = ""
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activar Licencia")
                    }
                } else {
                    // License is Active: Option to re-enter or update key
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cleanKey = keyInput.trim()
                                if (cleanKey.isBlank()) {
                                    errorMessage = "Por favor ingresa un código de activación"
                                    return@OutlinedButton
                                }
                                val isMaster = viewModel.isMasterKey(cleanKey)
                                val adminName = currentUser?.username ?: "admin"
                                viewModel.activateLicense(
                                    key = cleanKey,
                                    adminUser = adminName,
                                    unlimitedInventory = isMaster || unlimitedInv,
                                    productLimit = if (isMaster) 999999 else (prodLimitText.toIntOrNull() ?: 50),
                                    unlimitedInvoices = isMaster || unlimitedInvoices,
                                    invoiceLimit = if (isMaster) 999999 else (invLimitText.toIntOrNull() ?: 30)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Actualizar Clave")
                        }
                    }
                    // License is Active: Configuration Panel
                    Text(
                        "Configuración de Límites de Licencia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Unlimited Inventory Switch
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Inventario Ilimitado", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (unlimitedInv) "Permite registrar productos sin límite" else "Límite numérico fijado por el admin",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = unlimitedInv,
                                    onCheckedChange = { unlimitedInv = it }
                                )
                            }

                            AnimatedVisibility(visible = !unlimitedInv) {
                                OutlinedTextField(
                                    value = prodLimitText,
                                    onValueChange = { prodLimitText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Límite de productos en inventario") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Unlimited Invoices Switch
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Facturas Diarias Ilimitadas", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (unlimitedInvoices) "Permite ventas continuas sin tope diario" else "Límite diario fijado por el admin",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = unlimitedInvoices,
                                    onCheckedChange = { unlimitedInvoices = it }
                                )
                            }

                            AnimatedVisibility(visible = !unlimitedInvoices) {
                                OutlinedTextField(
                                    value = invLimitText,
                                    onValueChange = { invLimitText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Límite de facturas por día") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Save Limits and Deactivate Buttons
                    ResponsivePair(
                        breakpoint = 380.dp,
                        horizontalSpacing = 10.dp,
                        verticalSpacing = 10.dp,
                        first = { mod ->
                            Button(
                                onClick = {
                                    val pLimit = prodLimitText.toIntOrNull() ?: 50
                                    val iLimit = invLimitText.toIntOrNull() ?: 30
                                    viewModel.updateLicenseLimits(
                                        unlimitedInventory = unlimitedInv,
                                        productLimit = pLimit,
                                        unlimitedInvoices = unlimitedInvoices,
                                        invoiceLimit = iLimit
                                    )
                                    Toast.makeText(context, "Límites de licencia actualizados", Toast.LENGTH_SHORT).show()
                                },
                                modifier = mod.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar Cambios")
                            }
                        },
                        second = { mod ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.deactivateLicense()
                                    keyInput = ""
                                    Toast.makeText(context, "Licencia desactivada", Toast.LENGTH_SHORT).show()
                                },
                                modifier = mod.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quitar")
                            }
                        }
                    )
                }
            }
        }
}
