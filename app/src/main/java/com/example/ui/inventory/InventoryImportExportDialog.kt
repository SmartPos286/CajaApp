package com.example.ui.inventory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.PdfExportActionDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import com.example.utils.InventoryExportUtils
import com.example.utils.PdfExportManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InventoryImportExportDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Exportar, 1 = Importar
    var exportFormat by remember { mutableStateOf("CSV") } // "CSV", "JSON" o "PDF"
    var jsonInputText by remember { mutableStateOf("") }
    var replaceAllMode by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }

    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val exportData = remember(exportFormat, products) {
        when (exportFormat) {
            "CSV" -> viewModel.exportProductsToCsv()
            "JSON" -> viewModel.exportProductsToJson()
            else -> ""
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = InventoryExportUtils.writeExportToUri(context, uri, exportData, "CSV")
            Toast.makeText(
                context,
                if (saved) "Archivo .CSV guardado correctamente" else "No se pudo guardar el archivo .CSV",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = InventoryExportUtils.writeExportToUri(context, uri, exportData, "JSON")
            Toast.makeText(
                context,
                if (saved) "Archivo .JSON guardado correctamente" else "No se pudo guardar el archivo .JSON",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 640.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
                // Header with Close 'X' button
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
                            Icons.Filled.ImportExport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Exportar e Importar Inventario",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    indicator = {},
                    divider = {},
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Exportar (${products.size})", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Cargar / Importar", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // --- EXPORT TAB ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Selecciona el formato de exportación:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = exportFormat == "CSV",
                                onClick = { exportFormat = "CSV" },
                                label = { Text("📊 CSV (Excel / Hojas)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    if (exportFormat == "CSV") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                            FilterChip(
                                selected = exportFormat == "JSON",
                                onClick = { exportFormat = "JSON" },
                                label = { Text("⚙️ JSON (Migración / Apps)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    if (exportFormat == "JSON") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                            FilterChip(
                                selected = exportFormat == "PDF",
                                onClick = { exportFormat = "PDF" },
                                label = { Text("📄 PDF (Reporte)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    if (exportFormat == "PDF") Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("export_format_pdf_chip")
                            )
                        }

                        if (exportFormat == "PDF") {
                            val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0
                            val physicalItems = remember(products) { products.filter { !it.isRecipe && !it.isCombo } }
                            val totalUnits = remember(physicalItems) { physicalItems.sumOf { it.stock.coerceAtLeast(0.0) } }
                            val totalCostUsd = remember(physicalItems) {
                                physicalItems.sumOf { it.costPriceUsd.coerceAtLeast(0.0) * it.stock.coerceAtLeast(0.0) }
                            }
                            val totalRetailUsd = remember(physicalItems) {
                                physicalItems.sumOf { it.priceUsd.coerceAtLeast(0.0) * it.stock.coerceAtLeast(0.0) }
                            }
                            val outOfStockCount = remember(physicalItems) { physicalItems.count { it.stock <= 0.001 } }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFDC2626).copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            "Reporte Ejecutivo de Inventario (A4)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        "Incluye resumen de existencias, valoración a costo y venta (USD / Bs.), código, categoría, condición de IVA y estado de stock.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        "• Productos en catálogo: ${products.size} ítems ($outOfStockCount agotados)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "• Unidades físicas totales: ${if (totalUnits % 1.0 == 0.0) totalUnits.toInt().toString() else String.format(Locale.US, "%.2f", totalUnits)} u.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "• Valoración Costo: \$${String.format(Locale.US, "%.2f", totalCostUsd)} USD (Bs. ${String.format(Locale.US, "%.2f", totalCostUsd * effectiveRate)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "• Valoración Venta: \$${String.format(Locale.US, "%.2f", totalRetailUsd)} USD (Bs. ${String.format(Locale.US, "%.2f", totalRetailUsd * effectiveRate)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }

                            Button(
                                onClick = { showPdfExportDialog = true },
                                enabled = products.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("export_inventory_pdf_dialog_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = "Generar reporte PDF", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generar y Exportar PDF (${products.size} ítems)", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Preview box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 260.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp)
                                        .horizontalScroll(rememberScrollState())
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = exportData.ifBlank { "No hay productos en inventario para exportar." },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Export Actions: Guardar archivo real (.csv/.json) o Compartir archivo real
                            ResponsivePair(
                                breakpoint = 360.dp,
                                horizontalSpacing = 8.dp,
                                verticalSpacing = 8.dp,
                                first = { mod ->
                                    Button(
                                        onClick = {
                                            if (exportData.isBlank()) {
                                                Toast.makeText(context, "No hay productos para exportar.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val suggestedName = InventoryExportUtils.buildInventoryFileName(exportFormat)
                                            if (exportFormat == "CSV") {
                                                saveCsvLauncher.launch(suggestedName)
                                            } else {
                                                saveJsonLauncher.launch(suggestedName)
                                            }
                                        },
                                        enabled = products.isNotEmpty(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        modifier = mod
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("save_inventory_export_file_button")
                                    ) {
                                        Icon(Icons.Filled.SaveAlt, contentDescription = "Guardar archivo", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Guardar .$exportFormat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                },
                                second = { mod ->
                                    Button(
                                        onClick = {
                                            if (exportData.isBlank()) {
                                                Toast.makeText(context, "No hay productos para compartir.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val file = InventoryExportUtils.createExportFile(context, exportData, exportFormat)
                                            if (file != null) {
                                                InventoryExportUtils.shareExportFile(
                                                    context = context,
                                                    file = file,
                                                    mimeType = InventoryExportUtils.getMimeTypeForFormat(exportFormat),
                                                    chooserTitle = "Enviar Archivo Inventario (.$exportFormat)"
                                                )
                                            } else {
                                                Toast.makeText(context, "Error al generar el archivo .$exportFormat", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = products.isNotEmpty(),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = mod
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("share_inventory_export_file_button")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Compartir archivo", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enviar .$exportFormat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            )

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Inventario_${exportFormat}", exportData)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "¡Inventario $exportFormat copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                },
                                enabled = products.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("copy_inventory_export_text_button")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar texto", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copiar Texto al Portapapeles")
                            }
                        }
                    }
                } else {
                    // --- IMPORT TAB ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Banner de importación CSV directa
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF059669).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ResponsiveActionRow(
                                modifier = Modifier.padding(12.dp),
                                breakpoint = 400.dp,
                                content = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.UploadFile, contentDescription = null, tint = Color(0xFF059669))
                                        Column {
                                            Text("¿Deseas importar desde un archivo .CSV?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF059669))
                                            Text("Carga tu archivo con validación de columnas y cálculo de márgenes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                action = {
                                    Button(
                                        onClick = { showCsvImportDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Cargar CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }

                        Text(
                            "O pega aquí la estructura de datos en formato JSON para actualizar tu inventario:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                        if (pasteText.isNotBlank()) {
                                            jsonInputText = pasteText
                                            Toast.makeText(context, "Texto pegado desde el portapapeles", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pegar del Portapapeles", fontSize = 12.sp)
                            }

                            if (jsonInputText.isNotBlank()) {
                                TextButton(onClick = { jsonInputText = "" }) {
                                    Text("Limpiar", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = jsonInputText,
                            onValueChange = { jsonInputText = it },
                            placeholder = {
                                Text(
                                    "[\n  {\n    \"code\": \"PROD01\",\n    \"name\": \"Harina PAN\",\n    \"category\": \"Víveres\",\n    \"priceUsd\": 1.25,\n    \"stock\": 50\n  }\n]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 220.dp)
                                .bringIntoViewOnFocus()
                        )

                        // Mode selection
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Modo de Importación:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = !replaceAllMode,
                                        onClick = { replaceAllMode = false }
                                    )
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text("Combinar y Actualizar (Recomendado)", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                        Text("Actualiza productos existentes por código y añade los nuevos.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = replaceAllMode,
                                        onClick = { replaceAllMode = true }
                                    )
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text("Reemplazar Inventario Completo", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                                        Text("Borra los productos actuales y carga únicamente los del JSON.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        if (statusMessage != null) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusMessage!!.startsWith("Se importaron")) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (jsonInputText.isBlank()) {
                                    statusMessage = "Por favor ingresa o pega el texto JSON con los productos."
                                    return@Button
                                }
                                isImporting = true
                                viewModel.importProductsFromJson(
                                    jsonString = jsonInputText,
                                    replaceAll = replaceAllMode
                                ) { success, count, msg ->
                                    isImporting = false
                                    statusMessage = msg
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isImporting && jsonInputText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Procesando y Sincronizando...")
                            } else {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cargar Inventario y Sincronizar en la Nube")
                            }
                        }
                    }
                }
            }
        }

    if (showCsvImportDialog) {
        CsvInventoryImportDialog(
            viewModel = viewModel,
            onDismiss = { showCsvImportDialog = false },
            onImportCompleted = { msg ->
                statusMessage = msg
            }
        )
    }

    if (showPdfExportDialog) {
        val sortedAll = remember(products) { products.sortedBy { it.name.lowercase() } }
        val fileDateTag = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        PdfExportActionDialog(
            title = "Exportar Inventario Completo (PDF)",
            subtitle = "${sortedAll.size} productos ordenados alfabéticamente (A-Z)",
            suggestedFileName = "Inventario_$fileDateTag.pdf",
            generatePdf = {
                PdfExportManager.generateInventoryPdf(
                    context = context,
                    prefs = viewModel.prefs,
                    products = sortedAll,
                    allProducts = products,
                    effectiveRate = bcvRate,
                    filterLabel = "Todo el inventario (Nombre A-Z)",
                    generatedBy = currentUser?.username.orEmpty()
                )
            },
            onDismiss = { showPdfExportDialog = false }
        )
    }
}
