package com.example.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.ui.PosViewModel
import com.example.ui.components.ChangePasswordDialog
import com.example.ui.components.LicenseDialog
import com.example.ui.components.PrinterScanDialog
import com.example.utils.BackupManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.graphics.BitmapFactory
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    val scope = rememberCoroutineScope()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val backupMode by viewModel.backupMode.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    val isGoogleSignedIn by viewModel.isGoogleSignedIn.collectAsStateWithLifecycle()

    val isCompanyRegistered by viewModel.isCompanyRegistered.collectAsStateWithLifecycle()
    val companyGoogleAccount by viewModel.companyGoogleAccount.collectAsStateWithLifecycle()
    val companyRegisteredAt by viewModel.companyRegisteredAt.collectAsStateWithLifecycle()
    val lastPartialBackupTimestamp by viewModel.lastPartialBackupTimestamp.collectAsStateWithLifecycle()

    val currentStoreName by viewModel.storeName.collectAsStateWithLifecycle()
    val currentStoreRif by viewModel.storeRif.collectAsStateWithLifecycle()
    val currentStorePhone by viewModel.storePhone.collectAsStateWithLifecycle()
    val currentStoreAddress by viewModel.storeAddress.collectAsStateWithLifecycle()
    val storeLogoPath by viewModel.storeLogoPath.collectAsStateWithLifecycle()
    val currentCashRegisterNumber by viewModel.cashRegisterNumber.collectAsStateWithLifecycle()

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val logoFile = File(context.filesDir, "company_logo.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(logoFile).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.updateStoreLogo(logoFile.absolutePath)
                Toast.makeText(context, "¡Logo de la empresa actualizado correctamente!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar el logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isCashRegisterCommissionEnabled by viewModel.isCashRegisterCommissionEnabled.collectAsStateWithLifecycle()
    val commissionEnabledRegisters by viewModel.commissionEnabledRegisters.collectAsStateWithLifecycle()
    val isMinProfitMarginEnabled by viewModel.isMinProfitMarginEnabled.collectAsStateWithLifecycle()
    val minProfitMarginPercent by viewModel.minProfitMarginPercent.collectAsStateWithLifecycle()

    val cloudUsername by viewModel.cloudUsername.collectAsStateWithLifecycle()
    val cloudPassword by viewModel.cloudPassword.collectAsStateWithLifecycle()
    val isCloudConnected by viewModel.isCloudConnected.collectAsStateWithLifecycle()

    var inputCloudUsername by remember(cloudUsername) { mutableStateOf(cloudUsername) }
    var inputCloudPassword by remember(cloudPassword) { mutableStateOf(cloudPassword) }
    var isConnectingCloud by remember { mutableStateOf(false) }

    var editCashRegisterCommissionEnabled by remember(isCashRegisterCommissionEnabled) { mutableStateOf(isCashRegisterCommissionEnabled) }
    var editCommissionEnabledRegisters by remember(commissionEnabledRegisters) { mutableStateOf(commissionEnabledRegisters) }
    var editMinProfitMarginEnabled by remember(isMinProfitMarginEnabled) { mutableStateOf(isMinProfitMarginEnabled) }
    var editMinProfitMarginPercentStr by remember(minProfitMarginPercent) {
        mutableStateOf(
            if (minProfitMarginPercent % 1.0 == 0.0) minProfitMarginPercent.toInt().toString()
            else String.format(Locale.US, "%.1f", minProfitMarginPercent)
        )
    }

    var regName by remember(currentStoreName) {
        mutableStateOf(if (currentStoreName != "Mi Tienda") currentStoreName else "")
    }
    var regRif by remember(currentStoreRif) {
        mutableStateOf(if (currentStoreRif != "J-00000000-0") currentStoreRif else "")
    }
    var regPhone by remember(currentStorePhone) {
        mutableStateOf(if (currentStorePhone != "0000-0000000") currentStorePhone else "")
    }
    var regAddress by remember(currentStoreAddress) {
        mutableStateOf(if (currentStoreAddress != "Dirección") currentStoreAddress else "")
    }
    var regError by remember { mutableStateOf("") }

    var editName by remember(currentStoreName) { mutableStateOf(currentStoreName) }
    var editRif by remember(currentStoreRif) { mutableStateOf(currentStoreRif) }
    var editPhone by remember(currentStorePhone) { mutableStateOf(currentStorePhone) }
    var editAddress by remember(currentStoreAddress) { mutableStateOf(currentStoreAddress) }
    var editCashRegisterNumber by remember(currentCashRegisterNumber) { mutableStateOf(currentCashRegisterNumber) }

    val isLicensed by viewModel.isLicensed.collectAsStateWithLifecycle()
    val licenseKey by viewModel.licenseKey.collectAsStateWithLifecycle()
    val licenseExpiryTimestamp by viewModel.licenseExpiryTimestamp.collectAsStateWithLifecycle()
    val isUnlimitedInventory by viewModel.isUnlimitedInventory.collectAsStateWithLifecycle()
    val customProductLimit by viewModel.customProductLimit.collectAsStateWithLifecycle()
    val isUnlimitedDailyInvoices by viewModel.isUnlimitedDailyInvoices.collectAsStateWithLifecycle()
    val customDailyInvoiceLimit by viewModel.customDailyInvoiceLimit.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()

    val currentPrinterConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val currentPrinterPaperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val currentPrinterMac by viewModel.printerMacAddress.collectAsStateWithLifecycle()
    val currentPrinterName by viewModel.printerName.collectAsStateWithLifecycle()
    val currentUsbVid by viewModel.printerUsbVendorId.collectAsStateWithLifecycle()
    var showPrinterScanDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val isAdmin = currentUser == null || currentUser?.role == "ADMIN" || currentUser?.username.equals("Olivtech", ignoreCase = true)
    var showEncryptedExportDialog by remember { mutableStateOf(false) }
    var showEncryptedImportDialog by remember { mutableStateOf(false) }
    var encryptedPassphrase by remember { mutableStateOf("") }
    var useCustomPassphrase by remember { mutableStateOf(false) }
    var importAppendMode by remember { mutableStateOf(true) }
    var isProcessingEncrypted by remember { mutableStateOf(false) }

    val systemNotifications by viewModel.systemNotifications.collectAsStateWithLifecycle()
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val googleAuthUserEmail by viewModel.googleAuthUserEmail.collectAsStateWithLifecycle()
    val isGoogleAuthLoading by viewModel.isGoogleAuthLoading.collectAsStateWithLifecycle()
    val lastFirebaseBackupTimestamp by viewModel.lastFirebaseBackupTimestamp.collectAsStateWithLifecycle()
    val lastFirebaseBackupSummary by viewModel.lastFirebaseBackupSummary.collectAsStateWithLifecycle()
    val remoteEncryptedBackupInfo by viewModel.remoteEncryptedBackupInfo.collectAsStateWithLifecycle()
    val isUploadingFirebaseBackup by viewModel.isUploadingFirebaseBackup.collectAsStateWithLifecycle()
    val isRestoringFirebaseBackup by viewModel.isRestoringFirebaseBackup.collectAsStateWithLifecycle()
    var showRestoreGoogleBackupDialog by remember { mutableStateOf(false) }
    var googleRestoreAppendMode by remember { mutableStateOf(false) }

    val database = AppDatabase.getDatabase(context)
    val backupManager = remember { BackupManager(context, database) }

    val encryptedExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            isProcessingEncrypted = true
            val pass = if (useCustomPassphrase && encryptedPassphrase.isNotBlank()) encryptedPassphrase else null
            viewModel.exportEncryptedBackup(context, it, pass) { success, msg ->
                isProcessingEncrypted = false
                showEncryptedExportDialog = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val encryptedImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isProcessingEncrypted = true
            val pass = if (useCustomPassphrase && encryptedPassphrase.isNotBlank()) encryptedPassphrase else null
            viewModel.importEncryptedBackup(context, it, importAppendMode, pass) { result ->
                isProcessingEncrypted = false
                showEncryptedImportDialog = false
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = backupManager.exportToUri(it)
                Toast.makeText(context, if (success) "Copia guardada exitosamente" else "Error al guardar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = backupManager.importFromUri(it)
                Toast.makeText(context, if (success) "Datos restaurados exitosamente" else "Error al restaurar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- HEADER WITH NOTIFICATIONS BUBBLE ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Perfil de Administrador",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isAdmin) {
                    Box {
                        IconButton(onClick = { showNotificationsDialog = true }) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Notificaciones",
                                tint = if (systemNotifications.isNotEmpty()) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        if (systemNotifications.isNotEmpty()) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ) {
                                Text(systemNotifications.size.toString())
                            }
                        }
                    }
                }
            }

            // --- VISUAL THEME SECTION ---
        Text(
            "Apariencia y Tema Visual",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (appTheme == "DARK") Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            "Tema de la Aplicación",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (appTheme == "DARK") "Tema Oscuro activo (Azul Marino Plano)" else "Tema Claro activo (Azul Cielo Plano)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = (appTheme == "DARK"),
                        onClick = { viewModel.setAppTheme("DARK") },
                        label = { Text("Tema Oscuro") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )

                    FilterChip(
                        selected = (appTheme == "LIGHT"),
                        onClick = { viewModel.setAppTheme("LIGHT") },
                        label = { Text("Tema Claro") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        }

        // --- LICENSE SECTION ---
        Text(
            "Licencia del Sistema y Capacidades",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isLicensed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(
                1.dp,
                if (isLicensed) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ResponsiveActionRow(
                    breakpoint = 440.dp,
                    stackedActionAlignment = Alignment.Start,
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                if (isLicensed) Icons.Filled.VerifiedUser else Icons.Filled.LockClock,
                                contentDescription = null,
                                tint = if (isLicensed) Color(0xFF059669) else Color(0xFFD97706),
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (isLicensed) "Licencia Registrada Activa" else "Versión Sin Licencia (Límites Activos)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isLicensed) Color(0xFF059669) else Color(0xFFD97706)
                                )
                                Text(
                                    text = if (isLicensed) {
                                        if (licenseExpiryTimestamp > 0L) {
                                            val expStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(licenseExpiryTimestamp))
                                            "Clave: $licenseKey • Vence: $expStr"
                                        } else "Clave: $licenseKey"
                                    } else "Máximo 10 productos y 5 facturas al día",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    action = {
                        Button(
                            onClick = { showLicenseDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLicensed) MaterialTheme.colorScheme.primary else Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(if (isLicensed) "Gestionar" else "Activar Clave")
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        Column(modifier = mod) {
                            Text("Capacidad Inventario:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (!isLicensed) "${allProducts.size} / 10 productos (Límite Free)"
                                else if (isUnlimitedInventory) "Ilimitado (${allProducts.size} cargados)"
                                else "${allProducts.size} / $customProductLimit productos máx.",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    second = { mod ->
                        Column(modifier = mod) {
                            Text("Tope Facturas Diarias:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (!isLicensed) "Máximo 5 por día"
                                else if (isUnlimitedDailyInvoices) "Ilimitadas"
                                else "Máx. $customDailyInvoiceLimit por día",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                )
            }
        }

        Text(
            "Registro Único de la Empresa (Datos Fiscales)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!isCompanyRegistered) {
            // Company Registration Form - Immutable Once Created (No Google Sign-In required)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Notice about immutability of Name and RIF
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                "Aviso Importante: El Nombre de la empresa y el RIF serán un registro único e inmutable y NO se podrán modificar más adelante. La dirección y el número de teléfono sí podrán ser modificados posteriormente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = regName,
                        onValueChange = { regName = it; regError = "" },
                        label = { Text("Nombre de la Empresa (Registro Único)") },
                        placeholder = { Text("Ej. Inversiones Comerciales C.A.") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = regRif,
                        onValueChange = { regRif = it.uppercase(); regError = "" },
                        label = { Text("RIF / Documento Fiscal (Registro Único)") },
                        placeholder = { Text("Ej. J-12345678-9") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = regPhone,
                        onValueChange = { regPhone = it },
                        label = { Text("Número de Teléfono (Modificable)") },
                        placeholder = { Text("Ej. 0414-1234567") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = regAddress,
                        onValueChange = { regAddress = it },
                        label = { Text("Dirección de la Empresa (Modificable)") },
                        placeholder = { Text("Ej. Av. Bolívar, Local 12") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (regError.isNotBlank()) {
                        Text(regError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            if (regName.isBlank()) {
                                regError = "El nombre de la empresa no puede estar vacío."
                                return@Button
                            }
                            if (regRif.isBlank()) {
                                regError = "El RIF de la empresa no puede estar vacío."
                                return@Button
                            }
                            val result = viewModel.registerCompany(regName, regRif, regPhone, regAddress)
                            result.onSuccess {
                                Toast.makeText(context, "¡Empresa registrada exitosamente como registro único!", Toast.LENGTH_LONG).show()
                                regError = ""
                            }.onFailure { err ->
                                regError = err.message ?: "Error al registrar la empresa."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AppRegistration, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear Registro Único de la Empresa")
                    }
                }
            }
        } else {
            // Company is Already Registered: Name and RIF are strictly immutable, Phone and Address are editable
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(28.dp))
                            Column {
                                Text(
                                    "Registro Único de la Empresa Activo",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF059669)
                                )
                                val linkedAccount = companyGoogleAccount.takeIf { it.isNotBlank() }
                                    ?: if (isGoogleSignedIn) googleAccount else ""
                                Text(
                                    if (linkedAccount.isNotBlank()) "Vinculado a Google: $linkedAccount"
                                    else "Registro local activo (Sin sesión de Google requerida)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                if (companyRegisteredAt > 0L) {
                                    val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(companyRegisteredAt))
                                    Text(
                                        "Fecha de Registro: $dateStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Datos de la Empresa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre de la Empresa") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Editable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editRif,
                        onValueChange = { editRif = it.uppercase() },
                        label = { Text("RIF / Documento Fiscal") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Editable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        "Datos de Contacto Actualizables",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Teléfono de Contacto") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Editable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("Dirección") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Editable") },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editCashRegisterNumber,
                        onValueChange = { editCashRegisterNumber = it },
                        label = { Text("Número o Nombre de Caja / Terminal") },
                        supportingText = { Text("Se imprimirá en el ticket (ej: 01, Caja 1, Caja Principal)") },
                        leadingIcon = { Icon(Icons.Filled.PointOfSale, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Editable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            val res = viewModel.updateCompanyProfile(editName, editRif, editPhone, editAddress)
                            res.onSuccess {
                                viewModel.setCashRegisterNumber(editCashRegisterNumber)
                                Toast.makeText(context, "Datos de la empresa y caja actualizados correctamente", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Error al actualizar empresa", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Cambios de la Empresa y Caja")
                    }
                }
            }
        }

        // --- COMPANY LOGO UPLOAD SECTION ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Logo de la Empresa (Imagen)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    "Carga el logo oficial en formato de imagen desde tu dispositivo. Aparecerá en la esquina superior derecha del ticket cuadrado optimizado para enviar por WhatsApp e imágenes de venta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val logoFile = remember(storeLogoPath) { if (storeLogoPath.isNotBlank()) File(storeLogoPath) else null }
                    if (logoFile != null && logoFile.exists()) {
                        val logoBitmap = remember(storeLogoPath) {
                            try {
                                BitmapFactory.decodeFile(logoFile.absolutePath)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap.asImageBitmap(),
                                contentDescription = "Logo de la Empresa",
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Sin Logo", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                logoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (storeLogoPath.isNotBlank()) "Cambiar Logo" else "Cargar Logo (Imagen)")
                        }

                        if (storeLogoPath.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateStoreLogo("")
                                    Toast.makeText(context, "Logo eliminado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Eliminar Logo")
                            }
                        }
                    }
                }
            }
        }

        // --- OPTIONAL MINIMUM FIXED PROFIT PERCENTAGE ---
        if (isAdmin || currentUser?.role == "SUPERVISOR") {
            Text(
                "Ganancia Mínima Automática (Opcional)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                border = BorderStroke(
                    1.dp,
                    if (editMinProfitMarginEnabled) Color(0xFF10B981).copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Icon(
                                Icons.Filled.TrendingUp,
                                contentDescription = null,
                                tint = if (editMinProfitMarginEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Calcular Precio de Venta Automáticamente",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Si está activo, al agregar un producto (manual o por voz) indicando solo el costo, se calculará el precio de venta usando este % mínimo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = editMinProfitMarginEnabled,
                            onCheckedChange = { enabled ->
                                editMinProfitMarginEnabled = enabled
                                val parsedPct = editMinProfitMarginPercentStr.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 30.0
                                viewModel.setMinProfitMarginSettings(enabled = enabled, percent = parsedPct)
                                Toast.makeText(
                                    context,
                                    if (enabled) "Ganancia mínima automática activada ($parsedPct%)" else "Ganancia mínima automática desactivada",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.testTag("settings_min_profit_switch")
                        )
                    }

                    AnimatedVisibility(visible = editMinProfitMarginEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            OutlinedTextField(
                                value = editMinProfitMarginPercentStr,
                                onValueChange = { editMinProfitMarginPercentStr = it },
                                label = { Text("Porcentaje de Ganancia Fija Mínima (%)") },
                                placeholder = { Text("Ej: 30") },
                                leadingIcon = { Icon(Icons.Filled.Percent, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewOnFocus()
                                    .testTag("settings_min_profit_input")
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Rápido:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                listOf(15, 20, 30, 40, 50).forEach { pct ->
                                    SuggestionChip(
                                        onClick = { editMinProfitMarginPercentStr = pct.toString() },
                                        label = { Text("$pct%", fontSize = 11.sp) }
                                    )
                                }
                            }

                            val previewPct = editMinProfitMarginPercentStr.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            val exampleCost = 10.0
                            val exampleSale = exampleCost * (1.0 + previewPct / 100.0)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Ejemplo: Si un producto cuesta \$10.00 y el % mínimo es ${String.format("%.1f", previewPct).removeSuffix(".0")}%, el precio de venta sugerido será \$${String.format("%.2f", exampleSale)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    val parsedPct = editMinProfitMarginPercentStr.replace(",", ".").toDoubleOrNull()
                                    if (parsedPct == null || parsedPct < 0.0) {
                                        Toast.makeText(context, "Ingresa un porcentaje válido (>= 0)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.setMinProfitMarginSettings(
                                            enabled = editMinProfitMarginEnabled,
                                            percent = parsedPct
                                        )
                                        Toast.makeText(
                                            context,
                                            "Ganancia mínima automática guardada (${String.format("%.1f", parsedPct).removeSuffix(".0")}%)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_save_min_profit_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar Ganancia Mínima")
                            }
                        }
                    }
                }
            }
        }

        // --- COMMISSION MANAGEMENT BY CASH REGISTER (ADMIN ONLY) ---
        if (currentUser?.role == "ADMIN" || currentUser?.role == "SUPERVISOR") {
            Text(
                "Configuración de Comisiones de Venta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(28.dp))
                            Column {
                                Text("Comisiones en esta Caja / Terminal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Si está activo, las ventas de productos con comisión generarán ganancia al vendedor en este dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = editCashRegisterCommissionEnabled,
                            onCheckedChange = { editCashRegisterCommissionEnabled = it }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    OutlinedTextField(
                        value = editCommissionEnabledRegisters,
                        onValueChange = { editCommissionEnabledRegisters = it },
                        label = { Text("Cajas Autorizadas para Comisiones") },
                        placeholder = { Text("Ej: 01, 02, Caja 1 (Dejar vacío para habilitar todas)") },
                        supportingText = {
                            Text(
                                "Indica los números o nombres de cajas separados por coma. Si se deja vacío, cualquier caja con el interruptor activo generará comisiones."
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.PointOfSale, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.setCashRegisterCommissionSettings(
                                enabled = editCashRegisterCommissionEnabled,
                                registers = editCommissionEnabledRegisters
                            )
                            Toast.makeText(context, "Configuración de comisiones por caja guardada", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Reglas de Comisión por Caja")
                    }
                }
            }

            // --- ADMIN THERMAL PRINTER MANAGEMENT (BLE, CLASSIC BT, USB) ---
            val isPrinterConfigured = viewModel.isPrinterConfigured()

            Text(
                "Impresoras Térmicas y Facturación (Bluetooth / USB)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPrinterConfigured) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isPrinterConfigured) Color(0xFF059669).copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResponsiveActionRow(
                        breakpoint = 440.dp,
                        stackedActionAlignment = Alignment.Start,
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isPrinterConfigured) Color(0xFF10B981).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            when (currentPrinterConnectionType) {
                                                "USB" -> Icons.Filled.Usb
                                                "BLUETOOTH_CLASSIC" -> Icons.Filled.Bluetooth
                                                else -> Icons.Filled.Print
                                            },
                                            contentDescription = null,
                                            tint = if (isPrinterConfigured) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Column {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isPrinterConfigured)
                                                currentPrinterName
                                            else
                                                "Ninguna impresora configurada",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isPrinterConfigured) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isPrinterConfigured) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (currentPrinterConnectionType) {
                                                    "USB" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                    "BLUETOOTH_CLASSIC" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                    else -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                }
                                            ) {
                                                Text(
                                                    when (currentPrinterConnectionType) {
                                                        "USB" -> "USB OTG"
                                                        "BLUETOOTH_CLASSIC" -> "BT Clásico"
                                                        else -> "BLE FunPrint"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (currentPrinterConnectionType) {
                                                        "USB" -> Color(0xFF2563EB)
                                                        "BLUETOOTH_CLASSIC" -> Color(0xFF7C3AED)
                                                        else -> Color(0xFF059669)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isPrinterConfigured) {
                                            val idStr = if (currentPrinterConnectionType == "USB") "VID: $currentUsbVid" else currentPrinterMac
                                            "Dispositivo: $idStr • Formato: $currentPrinterPaperWidth"
                                        } else {
                                            "Compatible con USB OTG, Bluetooth Clásico (ESC/POS) y Mini BLE (MX06/C9)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        action = {
                            if (isPrinterConfigured) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.unlinkPrinter()
                                        Toast.makeText(context, "Impresora desvinculada del sistema", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Desvincular")
                                }
                            }
                        }
                    )

                    // Formato de Papel Térmico rápido
                    ResponsiveActionRow(
                        breakpoint = 380.dp,
                        verticalSpacing = 6.dp,
                        stackedActionAlignment = Alignment.Start,
                        content = {
                            Text(
                                "Ancho de papel de la factura:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        action = {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = currentPrinterPaperWidth == "58mm",
                                    onClick = { viewModel.setPrinterPaperWidth("58mm") },
                                    label = { Text("58 mm", fontSize = 11.sp) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                                FilterChip(
                                    selected = currentPrinterPaperWidth == "80mm",
                                    onClick = { viewModel.setPrinterPaperWidth("80mm") },
                                    label = { Text("80 mm", fontSize = 11.sp) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPrinterScanDialog = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPrinterConfigured) "Cambiar / Gestionar Impresora" else "Vincular Impresora (BT / USB)")
                        }

                        if (isPrinterConfigured) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.printTestTicket(
                                        context = context,
                                        onResult = { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prueba Térmica")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.printFiscalTestTicket(
                                        context = context,
                                        onStatus = { },
                                        onResult = { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prueba Fiscal")
                            }
                        }
                    }
                }
            }

            // --- ASISTENTE OLI & IA ADAPTATIVA ---
            val isLearningEnabled by viewModel.isGeminiLearningAiEnabled.collectAsStateWithLifecycle()

            Text(
                "Asistente Conversacional (Oli)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4F46E5).copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Asistente Comercial Oli",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Versiones gratuitas activas: Flash 3.5 y Flash 3.1",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Oli utiliza automáticamente Flash 3.5 y Flash 3.1 en sus versiones gratuitas para ofrecerte la máxima velocidad, consultas de conocimiento general, especificaciones técnicas y asistencia comercial en tiempo real sin necesidad de configurar claves externas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // IA de Aprendizaje Adaptativo Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLearningEnabled) Color(0xFF6366F1).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLearningEnabled) Color(0xFF6366F1).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isLearningEnabled) Color(0xFF6366F1) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "IA de Aprendizaje Adaptativo",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isLearningEnabled) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Permite al asistente comprender profundamente tus necesidades, identificar patrones de comportamiento, palabras clave y guardar instrucciones estructuradas en la nube para auto-aprendizaje futuro.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = isLearningEnabled,
                                onCheckedChange = { viewModel.setGeminiLearningAiEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF6366F1)
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- GOOGLE FIREBASE ENCRYPTED JSON CLOUD BACKUP (20 MIN AUTO-UPDATE & RESTORE) ---
        Text(
            "Respaldo en la Nube de Google (Firebase)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val isFirebaseGoogleActive = !googleAuthUserEmail.isNullOrBlank()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            border = BorderStroke(
                1.5.dp,
                if (isFirebaseGoogleActive) Color(0xFF0284C7).copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFirebaseGoogleActive) Color(0xFF0284C7).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (isFirebaseGoogleActive) Color(0xFF0284C7).copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResponsiveActionRow(
                        modifier = Modifier.padding(14.dp),
                        breakpoint = 440.dp,
                        stackedActionAlignment = Alignment.Start,
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isFirebaseGoogleActive) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                    contentDescription = "Estado de Google Firebase",
                                    tint = if (isFirebaseGoogleActive) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isFirebaseGoogleActive) "Google Firebase Activo (Cada 20 min)"
                                        else "Cuenta de Google no vinculada",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isFirebaseGoogleActive) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isFirebaseGoogleActive) "Cuenta: $googleAuthUserEmail"
                                        else "Inicia sesión con Google para activar el respaldo encriptado automático en la nube.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        action = {
                            if (!isFirebaseGoogleActive) {
                                Button(
                                    onClick = {
                                        viewModel.signInWithGoogle(context) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    enabled = !isGoogleAuthLoading,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("settings_google_sign_in_button")
                                ) {
                                    if (isGoogleAuthLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Conectar Google", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    )
                }

                Text(
                    "Cada 20 minutos se actualiza y sobrescribe el mismo archivo encriptado (AES-256 GCM) en Firebase bajo tu cuenta de Google para mantener todos tus dispositivos sincronizados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val fbSdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                        val fbTimeText = if (lastFirebaseBackupTimestamp > 0L) {
                            fbSdf.format(Date(lastFirebaseBackupTimestamp))
                        } else {
                            "Aún sin respaldo en la nube"
                        }
                        ResponsiveActionRow(
                            breakpoint = 360.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Text("Último Respaldo Encriptado:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            action = {
                                Text(fbTimeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                            }
                        )

                        val remoteSummary = lastFirebaseBackupSummary.ifBlank {
                            remoteEncryptedBackupInfo?.let {
                                "${it.productsCount} prod. • ${it.salesCount} ventas • ${it.deviceModel}"
                            }.orEmpty()
                        }
                        if (remoteSummary.isNotBlank()) {
                            ResponsiveActionRow(
                                breakpoint = 360.dp,
                                verticalSpacing = 2.dp,
                                stackedActionAlignment = Alignment.Start,
                                content = {
                                    Text("Registro en la Nube:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                action = {
                                    Text(remoteSummary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (!isFirebaseGoogleActive) {
                                viewModel.signInWithGoogle(context) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        showRestoreGoogleBackupDialog = true
                                    }
                                }
                            } else {
                                showRestoreGoogleBackupDialog = true
                            }
                        },
                        enabled = !isRestoringFirebaseBackup && !isUploadingFirebaseBackup && !isGoogleAuthLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("restore_google_backup_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        if (isRestoringFirebaseBackup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recuperando y Sincronizando...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recuperar Respaldo de Google y Sincronizar", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isFirebaseGoogleActive) {
                                Toast.makeText(context, "Primero conecta tu cuenta de Google.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.backupEncryptedJsonToFirebaseNow { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isUploadingFirebaseBackup && !isRestoringFirebaseBackup && isFirebaseGoogleActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("backup_firebase_now_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f))
                    ) {
                        if (isUploadingFirebaseBackup) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Actualizando respaldo en Firebase...")
                        } else {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Actualizar Respaldo Encriptado Ahora", color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // --- BACKUP & RED SUPABASE SECTION ---
        Text(
            "Conexión a la Red Supabase y Respaldos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, if (isCloudConnected) Color(0xFF059669).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Connection Status Header
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCloudConnected) Color(0xFF059669).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, if (isCloudConnected) Color(0xFF059669).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResponsiveActionRow(
                        modifier = Modifier.padding(14.dp),
                        breakpoint = 420.dp,
                        stackedActionAlignment = Alignment.Start,
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isCloudConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = if (isCloudConnected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isCloudConnected) "Conectado a la Red Supabase" else "Modo Offline (Solo Local)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCloudConnected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCloudConnected) "Usuario: $cloudUsername" else "Sin conexión a la red de nube",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        action = {
                            if (isCloudConnected) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.disconnectCloudNetwork()
                                        Toast.makeText(context, "Desconectado de la red (Modo Offline)", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Desconectar", fontSize = 12.sp)
                                }
                            }
                        }
                    )
                }

                // Supabase Credentials Form
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Configuración de Credenciales de Red Supabase",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ingrese su usuario y clave de red para conectarse a la nube de Supabase y sincronizar ventas, inventario y configuración en tiempo real entre múltiples cajas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputCloudUsername,
                        onValueChange = { inputCloudUsername = it },
                        label = { Text("Usuario de Red Supabase") },
                        placeholder = { Text("Ej. usuario_empresa") },
                        leadingIcon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = inputCloudPassword,
                        onValueChange = { inputCloudPassword = it },
                        label = { Text("Clave / Token de Red") },
                        placeholder = { Text("Clave de red") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ResponsivePair(
                        breakpoint = 400.dp,
                        horizontalSpacing = 10.dp,
                        verticalSpacing = 10.dp,
                        first = { mod ->
                            Button(
                                onClick = {
                                    if (inputCloudUsername.isBlank() || inputCloudPassword.isBlank()) {
                                        Toast.makeText(context, "Por favor complete usuario y clave de red", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isConnectingCloud = true
                                    viewModel.saveAndConnectCloudNetwork(inputCloudUsername, inputCloudPassword) { success, msg ->
                                        isConnectingCloud = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isConnectingCloud,
                                modifier = mod.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isConnectingCloud) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Validando...")
                                } else {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Guardar / Conectar")
                                }
                            }
                        },
                        second = { mod ->
                            OutlinedButton(
                                onClick = { showChangePasswordDialog = true },
                                modifier = mod.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Filled.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cambiar Clave", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Streaming / Device Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResponsiveActionRow(
                            breakpoint = 360.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.PhonelinkSetup, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Dispositivo Registrado:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            },
                            action = {
                                Text("${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        )

                        ResponsiveActionRow(
                            breakpoint = 360.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Text("Estado de Sincronización:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            action = {
                                Text(
                                    if (isCloudConnected) "Sincronización en Vivo Multi-Caja (Supabase)" else "Local / Sin Sincronización",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCloudConnected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        val backupSdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                        val backupTimeStr = if (lastPartialBackupTimestamp > 0) backupSdf.format(Date(lastPartialBackupTimestamp)) else "Registrado en inicio"
                        ResponsiveActionRow(
                            breakpoint = 360.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Text("Última Sincronización:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            action = {
                                Text(backupTimeStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }
                }

                if (isCloudConnected) {
                    Button(
                        onClick = {
                            viewModel.performPartialBackup()
                            Toast.makeText(context, "Sincronización enviada a Supabase exitosamente", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Forzar Sincronización Inmediata", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 10.dp,
                    verticalSpacing = 10.dp,
                    first = { mod ->
                        Button(
                            onClick = { exportLauncher.launch("pos_backup.json") },
                            modifier = mod.defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar Copia")
                        }
                    },
                    second = { mod ->
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = mod.defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurar")
                        }
                    }
                )
            }
        }

        // --- ENCRYPTED JSON DATA EXPORT / IMPORT SECTION (ADMINISTRATOR PROFILE) ---
        if (isAdmin) {
            Text(
                "Exportación Segura de Datos Encriptados (Perfil Administrador)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4F46E5).copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Archivo Encriptado Anti-Manipulación",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "AES-256 GCM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                "Protege el inventario, facturación, gastos y clientes en un archivo inalterable desde el exterior pero portable entre dispositivos con la app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                Text("Garantías de Seguridad:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text("• Cifrado simétrico AES-256 con modo de autenticación GCM (Tag 128-bit).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Inmodificable: Si alguien edita el archivo externamente, el sistema rechaza la importación automáticamente.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Interoperable: Abre y anexa la información en cualquier otro dispositivo con la aplicación.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    ResponsivePair(
                        breakpoint = 400.dp,
                        horizontalSpacing = 10.dp,
                        verticalSpacing = 10.dp,
                        first = { mod ->
                            Button(
                                onClick = {
                                    encryptedPassphrase = ""
                                    useCustomPassphrase = false
                                    showEncryptedExportDialog = true
                                },
                                modifier = mod.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Exportar Encriptado", fontSize = 13.sp)
                            }
                        },
                        second = { mod ->
                            FilledTonalButton(
                                onClick = {
                                    encryptedPassphrase = ""
                                    useCustomPassphrase = false
                                    importAppendMode = true
                                    showEncryptedImportDialog = true
                                },
                                modifier = mod.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importar / Anexar", fontSize = 13.sp)
                            }
                        }
                    )
                }
            }
        }

        // --- VERSION & SYSTEM STATUS CARD ---
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            ResponsiveActionRow(
                modifier = Modifier.padding(16.dp),
                breakpoint = 380.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "SmartPOS286",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Versión 1.0.1 BETA • Base de Datos v18",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Esquema de datos optimizado con soporte para cantidades decimales y recetas",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                action = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF059669).copy(alpha = 0.15f)
                    ) {
                        Text(
                            "ACTUALIZADO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- DIALOG EXPORTAR DATA ENCRIPTADA ---
    if (showEncryptedExportDialog) {
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val defaultFileName = "cajapos_backup_$dateStamp.cajapos"

        KeyboardAwareAlertDialog(
            onDismissRequest = { if (!isProcessingEncrypted) showEncryptedExportDialog = false },
            icon = { Icon(Icons.Filled.EnhancedEncryption, contentDescription = null, tint = Color(0xFF4F46E5)) },
            title = { Text("Exportar Data Encriptada (AES-256)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Se generará un archivo cifrado e inmodificable que contiene todo el inventario, ventas, notas de entrega, clientes y facturación.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Archivo: $defaultFileName",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Personalizar clave de cifrado:", fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(
                            checked = useCustomPassphrase,
                            onCheckedChange = { useCustomPassphrase = it }
                        )
                    }

                    if (useCustomPassphrase) {
                        OutlinedTextField(
                            value = encryptedPassphrase,
                            onValueChange = { encryptedPassphrase = it },
                            label = { Text("Clave / Passphrase personal") },
                            placeholder = { Text("Ej. MiClaveSegura2026") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Text(
                            "Nota: Deberás ingresar esta misma clave en el otro dispositivo para poder desencriptar.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Se usará la clave maestra del sistema para que cualquier otro dispositivo con la app pueda anexar la data automáticamente.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isProcessingEncrypted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Encriptando y empaquetando datos...")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        encryptedExportLauncher.launch(defaultFileName)
                    },
                    enabled = !isProcessingEncrypted,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Seleccionar Destino y Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEncryptedExportDialog = false },
                    enabled = !isProcessingEncrypted
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- DIALOG IMPORTAR Y ANEXAR DATA ENCRIPTADA ---
    if (showEncryptedImportDialog) {
        KeyboardAwareAlertDialog(
            onDismissRequest = { if (!isProcessingEncrypted) showEncryptedImportDialog = false },
            icon = { Icon(Icons.Filled.LockOpen, contentDescription = null, tint = Color(0xFF059669)) },
            title = { Text("Importar y Desencriptar Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Selecciona un archivo .cajapos o .enc para desencriptar, verificar su autenticidad y agregar los datos a la base local.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text("Modo de Integración:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = importAppendMode,
                            onClick = { importAppendMode = true },
                            label = { Text("Anexar / Sumar") }
                        )
                        FilterChip(
                            selected = !importAppendMode,
                            onClick = { importAppendMode = false },
                            label = { Text("Sobrescribir Todo") }
                        )
                    }

                    Text(
                        if (importAppendMode)
                            "• Modo Anexar: Conserva tus productos y ventas actuales; agrega los nuevos y suma stock."
                        else
                            "• Modo Sobrescribir: Reemplaza todos los datos locales con los del archivo.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("¿Fue exportado con clave personalizada?:", fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(
                            checked = useCustomPassphrase,
                            onCheckedChange = { useCustomPassphrase = it }
                        )
                    }

                    if (useCustomPassphrase) {
                        OutlinedTextField(
                            value = encryptedPassphrase,
                            onValueChange = { encryptedPassphrase = it },
                            label = { Text("Clave / Passphrase") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    if (isProcessingEncrypted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Desencriptando y verificando integridad...")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        encryptedImportLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !isProcessingEncrypted,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("Seleccionar Archivo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEncryptedImportDialog = false },
                    enabled = !isProcessingEncrypted
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showNotificationsDialog) {
        KeyboardAwareAlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("Notificaciones del Sistema", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (systemNotifications.isEmpty()) {
                        Text("No tienes notificaciones pendientes. Todo está operando normalmente.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        systemNotifications.forEach { notif ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (notif.type) {
                                        com.example.ui.NotificationType.LOW_STOCK -> Color(0xFFFEF2F2)
                                        com.example.ui.NotificationType.LICENSE_EXPIRY -> Color(0xFFFFF7ED)
                                        com.example.ui.NotificationType.SYSTEM_ALERT -> Color(0xFFEFF6FF)
                                    }
                                ),
                                border = BorderStroke(1.dp, when (notif.type) {
                                    com.example.ui.NotificationType.LOW_STOCK -> Color(0xFFFECACA)
                                    com.example.ui.NotificationType.LICENSE_EXPIRY -> Color(0xFFFED7AA)
                                    com.example.ui.NotificationType.SYSTEM_ALERT -> Color(0xFFBFDBFE)
                                })
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = when (notif.type) {
                                                com.example.ui.NotificationType.LOW_STOCK -> Icons.Filled.Inventory
                                                com.example.ui.NotificationType.LICENSE_EXPIRY -> Icons.Filled.VpnKey
                                                com.example.ui.NotificationType.SYSTEM_ALERT -> Icons.Filled.Warning
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = when (notif.type) {
                                                com.example.ui.NotificationType.LOW_STOCK -> Color(0xFFDC2626)
                                                com.example.ui.NotificationType.LICENSE_EXPIRY -> Color(0xFFD97706)
                                                com.example.ui.NotificationType.SYSTEM_ALERT -> Color(0xFF2563EB)
                                            }
                                        )
                                        Text(notif.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(notif.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showLicenseDialog) {
        LicenseDialog(
            viewModel = viewModel,
            onDismiss = { showLicenseDialog = false }
        )
    }

    if (showPrinterScanDialog) {
        PrinterScanDialog(
            viewModel = viewModel,
            onDismiss = { showPrinterScanDialog = false }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            viewModel = viewModel,
            onDismissRequest = { showChangePasswordDialog = false }
        )
    }

    if (showRestoreGoogleBackupDialog) {
        KeyboardAwareAlertDialog(
            onDismissRequest = { if (!isRestoringFirebaseBackup) showRestoreGoogleBackupDialog = false },
            icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color(0xFF0284C7)) },
            title = { Text("Recuperar Respaldo de Google", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Se descargará el último respaldo encriptado desde Firebase para la cuenta ${googleAuthUserEmail.orEmpty()}, se verificará su integridad AES-256 GCM y se sincronizará este dispositivo.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = !googleRestoreAppendMode,
                            onClick = { googleRestoreAppendMode = false },
                            label = { Text("Sincronizar Exacto") }
                        )
                        FilterChip(
                            selected = googleRestoreAppendMode,
                            onClick = { googleRestoreAppendMode = true },
                            label = { Text("Fusionar Nuevos") }
                        )
                    }

                    Text(
                        if (!googleRestoreAppendMode)
                            "• Actualiza precios/stock existentes desde la nube y agrega ventas, clientes y facturas faltantes sin duplicar."
                        else
                            "• Mantiene tus datos locales actuales e incorpora registros faltantes desde el respaldo de la nube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreGoogleBackupDialog = false
                        viewModel.restoreGoogleFirebaseBackupAndSync(appendMode = googleRestoreAppendMode) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.testTag("confirm_restore_google_backup_button")
                ) {
                    Text("Recuperar y Sincronizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreGoogleBackupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
