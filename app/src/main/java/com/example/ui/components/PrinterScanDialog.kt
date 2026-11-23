package com.example.ui.components

import android.Manifest
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.PosViewModel
import com.example.utils.DiscoveredPrinterDevice
import com.example.utils.PaperWidthOption
import com.example.utils.PrinterConnectionType
import com.example.utils.ThermalPrinterManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterScanDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { ThermalPrinterManager(context) }

    val isScanning by printerManager.isScanning.collectAsStateWithLifecycle()
    val discoveredDevices by printerManager.discoveredDevices.collectAsStateWithLifecycle()

    val currentPrinterConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val currentPrinterPaperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val currentPrinterMac by viewModel.printerMacAddress.collectAsStateWithLifecycle()
    val currentPrinterName by viewModel.printerName.collectAsStateWithLifecycle()
    val currentUsbVid by viewModel.printerUsbVendorId.collectAsStateWithLifecycle()

    var selectedFilterType by remember { mutableStateOf<PrinterConnectionType?>(null) }
    var hasPermission by remember { mutableStateOf(printerManager.hasBluetoothPermissions()) }
    var testPrintStatus by remember { mutableStateOf<String?>(null) }
    var isTestingPrint by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        hasPermission = allGranted || printerManager.hasBluetoothPermissions()
        if (hasPermission) {
            printerManager.startScan()
        } else {
            Toast.makeText(context, "Permisos de Bluetooth necesarios para buscar impresoras inalámbricas", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            printerManager.startScan()
        } else {
            val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            permissionsLauncher.launch(required)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            printerManager.stopScan()
        }
    }

    val filteredDevices = remember(discoveredDevices, selectedFilterType) {
        if (selectedFilterType == null) discoveredDevices
        else discoveredDevices.filter { it.connectionType == selectedFilterType }
    }

    val isConfigured = viewModel.isPrinterConfigured()

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 580.dp,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Print,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                "Configurar Impresora Térmica",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Bluetooth BLE • Bluetooth Clásico • USB OTG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Estado de impresora actual activa
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isConfigured)
                        Color(0xFF10B981).copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isConfigured) Color(0xFF10B981).copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ResponsiveActionRow(
                            breakpoint = 480.dp,
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        if (isConfigured) Icons.Filled.CheckCircle else Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = if (isConfigured) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (isConfigured)
                                                "Impresora Activa: $currentPrinterName"
                                            else
                                                "Ninguna impresora configurada",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isConfigured) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                                        )
                                        val connLabel = when (currentPrinterConnectionType) {
                                            "USB" -> "USB OTG Directo"
                                            "BLUETOOTH_CLASSIC" -> "Bluetooth Clásico ESC/POS ($currentPrinterMac)"
                                            else -> "Bluetooth BLE FunPrint ($currentPrinterMac)"
                                        }
                                        Text(
                                            text = if (isConfigured)
                                                "Modo: $connLabel • Papel: $currentPrinterPaperWidth"
                                            else
                                                "Conecta una impresora por USB o enciende tu Bluetooth",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            action = {
                                if (isConfigured) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                isTestingPrint = true
                                                testPrintStatus = "Enviando prueba térmica..."
                                                viewModel.printTestTicket(
                                                    context = context,
                                                    onStatus = { testPrintStatus = it },
                                                    onResult = { _, msg ->
                                                        isTestingPrint = false
                                                        testPrintStatus = msg
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            },
                                            enabled = !isTestingPrint
                                        ) {
                                            Text("Probar Térmica", fontSize = 11.sp)
                                        }

                                        TextButton(
                                            onClick = {
                                                isTestingPrint = true
                                                testPrintStatus = "Probando protocolo fiscal ${viewModel.fiscalPrinterBrand.value}..."
                                                viewModel.printFiscalTestTicket(
                                                    context = context,
                                                    onStatus = { testPrintStatus = it },
                                                    onResult = { _, msg ->
                                                        isTestingPrint = false
                                                        testPrintStatus = msg
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            },
                                            enabled = !isTestingPrint
                                        ) {
                                            Text("Probar Fiscal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }

                                        TextButton(
                                            onClick = {
                                                viewModel.unlinkPrinter()
                                                Toast.makeText(context, "Impresora desvinculada", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Desvincular", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        )

                        if (testPrintStatus != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = testPrintStatus ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selector de Ancho de Papel / Formato de Facturación
                Text(
                    "Formato de Papel Térmico:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val is58 = currentPrinterPaperWidth == "58mm"
                    val is80 = currentPrinterPaperWidth == "80mm"

                    FilterChip(
                        selected = is58,
                        onClick = {
                            viewModel.setPrinterPaperWidth("58mm")
                            Toast.makeText(context, "Formato ajustado a 58 mm (384 px)", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("58 mm (Estándar / Portátil)") },
                        leadingIcon = {
                            Icon(
                                if (is58) Icons.Filled.Check else Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    FilterChip(
                        selected = is80,
                        onClick = {
                            viewModel.setPrinterPaperWidth("80mm")
                            Toast.makeText(context, "Formato ajustado a 80 mm (576 px)", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("80 mm (Mostrador / Ancho)") },
                        leadingIcon = {
                            Icon(
                                if (is80) Icons.Filled.Check else Icons.Filled.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Configuración de Modo Fiscal (Venezuela SENIAT)
                val isFiscalPrinter by viewModel.isFiscalPrinter.collectAsStateWithLifecycle()
                val fiscalPrinterBrand by viewModel.fiscalPrinterBrand.collectAsStateWithLifecycle()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFiscalPrinter) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, if (isFiscalPrinter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AccountBalance,
                                    contentDescription = null,
                                    tint = if (isFiscalPrinter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Impresora Fiscal (Venezuela SENIAT)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isFiscalPrinter) "Emite facturas fiscales sin aviso no fiscal" else "Genera recibos internos de control de ventas",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isFiscalPrinter,
                                onCheckedChange = { viewModel.setIsFiscalPrinter(it) }
                            )
                        }

                        if (isFiscalPrinter) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Marca / Protocolo Fiscal:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val brands = listOf("The Factory HKA", "Bixolon Fiscal", "Dascom Fiscal", "Genérica Fiscal")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                brands.forEach { brand ->
                                    val isSelected = fiscalPrinterBrand == brand
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setFiscalPrinterBrand(brand) },
                                        label = { Text(brand, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filtros de Tipo de Conexión (Tabs rápidos)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilterType == null,
                        onClick = { selectedFilterType = null },
                        label = { Text("Todos", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilterType == PrinterConnectionType.USB,
                        onClick = { selectedFilterType = PrinterConnectionType.USB },
                        label = { Text("USB OTG", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.Usb, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilterType == PrinterConnectionType.BLUETOOTH_CLASSIC,
                        onClick = { selectedFilterType = PrinterConnectionType.BLUETOOTH_CLASSIC },
                        label = { Text("BT Clásico", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilterType == PrinterConnectionType.BLE_FUNPRINT,
                        onClick = { selectedFilterType = PrinterConnectionType.BLE_FUNPRINT },
                        label = { Text("BLE FunPrint", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scan Action Bar
                ResponsiveActionRow(
                    breakpoint = 360.dp,
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Dispositivos detectados (${filteredDevices.size}):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                        }
                    },
                    action = {
                        FilledTonalButton(
                            onClick = {
                                if (isScanning) {
                                    printerManager.stopScan()
                                } else {
                                    if (hasPermission) {
                                        printerManager.startScan()
                                    } else {
                                        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                                        } else {
                                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                        permissionsLauncher.launch(required)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (isScanning) Icons.Filled.Stop else Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isScanning) "Detener" else "Actualizar / Buscar", fontSize = 12.sp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Lista de Dispositivos Descubiertos
                if (filteredDevices.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.PrintDisabled,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (isScanning) "Buscando impresoras cercanas o USB..." else "No se detectaron dispositivos",
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• Conecta tu impresora por cable USB OTG\n• O empareja tu impresora Bluetooth en Ajustes del teléfono\n• O enciende tu impresora BLE portátil (MX06, C9)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDevices) { dev ->
                            val isCurrent = when (dev.connectionType) {
                                PrinterConnectionType.USB -> currentPrinterConnectionType == "USB" && dev.usbVendorId == currentUsbVid
                                else -> dev.addressOrId.equals(currentPrinterMac, ignoreCase = true)
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isCurrent -> Color(0xFF10B981).copy(alpha = 0.12f)
                                        dev.isLikelyPrinter -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isCurrent -> Color(0xFF10B981).copy(alpha = 0.5f)
                                        dev.isLikelyPrinter -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setPrinterConfig(
                                            connectionType = dev.connectionType.id,
                                            macOrId = dev.addressOrId,
                                            name = dev.name,
                                            usbVendorId = dev.usbVendorId,
                                            usbProductId = dev.usbProductId,
                                            paperWidth = currentPrinterPaperWidth
                                        )
                                        Toast.makeText(context, "Impresora vinculada: ${dev.name}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = when {
                                                isCurrent -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                dev.isLikelyPrinter -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    when (dev.connectionType) {
                                                        PrinterConnectionType.USB -> Icons.Filled.Usb
                                                        PrinterConnectionType.BLUETOOTH_CLASSIC -> Icons.Filled.Bluetooth
                                                        PrinterConnectionType.BLE_FUNPRINT -> Icons.Filled.Print
                                                    },
                                                    contentDescription = null,
                                                    tint = when {
                                                        isCurrent -> Color(0xFF059669)
                                                        dev.isLikelyPrinter -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    dev.name,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = when (dev.connectionType) {
                                                        PrinterConnectionType.USB -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                        PrinterConnectionType.BLUETOOTH_CLASSIC -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                        PrinterConnectionType.BLE_FUNPRINT -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                    }
                                                ) {
                                                    Text(
                                                        when (dev.connectionType) {
                                                            PrinterConnectionType.USB -> "USB"
                                                            PrinterConnectionType.BLUETOOTH_CLASSIC -> "BT Clásico"
                                                            PrinterConnectionType.BLE_FUNPRINT -> "BLE"
                                                        },
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when (dev.connectionType) {
                                                            PrinterConnectionType.USB -> Color(0xFF2563EB)
                                                            PrinterConnectionType.BLUETOOTH_CLASSIC -> Color(0xFF7C3AED)
                                                            PrinterConnectionType.BLE_FUNPRINT -> Color(0xFF059669)
                                                        },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                dev.details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF059669),
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                "Activa",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.setPrinterConfig(
                                                    connectionType = dev.connectionType.id,
                                                    macOrId = dev.addressOrId,
                                                    name = dev.name,
                                                    usbVendorId = dev.usbVendorId,
                                                    usbProductId = dev.usbProductId,
                                                    paperWidth = currentPrinterPaperWidth
                                                )
                                                Toast.makeText(context, "Impresora vinculada: ${dev.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Vincular", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Actions
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar y Listo")
                }
            }
    }
}
