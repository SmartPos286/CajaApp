package com.example.ui.inventory

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.ResponsivePair
import com.example.utils.CsvInventoryParser
import com.example.utils.CsvParseResult
import com.example.utils.ParsedCsvProductRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Diálogo integral para seleccionar, procesar, validar e importar archivos CSV
 * directamente al inventario de la aplicación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvInventoryImportDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onImportCompleted: ((resultMessage: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isParsing by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<CsvParseResult?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var importSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showAllWarnings by remember { mutableStateOf(false) }
    var showPreviewTable by remember { mutableStateOf(true) }

    // Launcher para abrir el selector de archivos del sistema Android (SAF)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isParsing = true
                parseError = null
                importSuccessMessage = null
                try {
                    val fileName = getFileName(context, uri) ?: "inventario.csv"
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        parseError = "No se pudo acceder al archivo seleccionado."
                        isParsing = false
                        return@launch
                    }

                    val result = withContext(Dispatchers.IO) {
                        inputStream.use { stream ->
                            CsvInventoryParser.parse(stream, fileName)
                        }
                    }

                    parseResult = result
                    if (result.validRows.isEmpty() && result.warnings.isNotEmpty()) {
                        parseError = "No se pudo extraer ninguna fila válida del archivo CSV."
                    }
                } catch (e: Exception) {
                    parseError = "Error al leer el archivo: ${e.localizedMessage ?: e.message}"
                } finally {
                    isParsing = false
                }
            }
        }
    }

    KeyboardAwareDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        maxWidth = 760.dp,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        dismissOnClickOutside = !isImporting,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
                // Cabecera del diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF059669).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.UploadFile,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Cargar Inventario desde CSV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Importa productos directamente al sistema",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Contenido desplazable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Zona de Carga / Selección de archivo
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isImporting && !isParsing) {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "text/comma-separated-values",
                                        "text/csv",
                                        "application/csv",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (parseResult == null) "Toca para seleccionar un archivo .CSV" else "Seleccionar otro archivo .CSV",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Busca el archivo en el almacenamiento interno de tu teléfono",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Columnas soportadas
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HeaderFormatChip(name = "producto")
                                HeaderFormatChip(name = "cantidad")
                                HeaderFormatChip(name = "precio_costo")
                                HeaderFormatChip(name = "precio_venta")
                            }
                        }
                    }

                    // Indicador de análisis en progreso
                    if (isParsing) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = "Leyendo y validando archivo CSV...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Mensaje de error al parsear
                    if (parseError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = parseError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Mensaje de éxito de la importación
                    if (importSuccessMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF059669).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF059669))
                                Column {
                                    Text(
                                        text = "¡Importación Exitosa!",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = importSuccessMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Resumen del archivo cargado y estadísticas
                    parseResult?.let { result ->
                        // Banner del archivo
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = result.fileName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${result.validRows.size} filas válidas  •  ${result.warnings.size} con errores",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (result.warnings.isEmpty()) Color(0xFF059669) else Color(0xFFD97706),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Tarjetas de Métricas Clave (2x2 en compacto, 4 en ancho)
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val wideCards = maxWidth >= 520.dp
                            if (wideCards) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricCard(
                                        title = "Válidos",
                                        value = "${result.validRows.size}",
                                        subtitle = "Productos",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCard(
                                        title = "Unidades",
                                        value = "${result.totalQuantity}",
                                        subtitle = "Stock Total",
                                        color = Color(0xFF0284C7),
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCard(
                                        title = "Costo Total",
                                        value = "$${String.format(java.util.Locale.US, "%.2f", result.totalCostValue)}",
                                        subtitle = "Inversión",
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCard(
                                        title = "Venta Total",
                                        value = "$${String.format(java.util.Locale.US, "%.2f", result.totalSaleValue)}",
                                        subtitle = "Margen: +$${String.format(java.util.Locale.US, "%.2f", result.totalMargin)}",
                                        color = Color(0xFF059669),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MetricCard(
                                            title = "Válidos",
                                            value = "${result.validRows.size}",
                                            subtitle = "Productos",
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricCard(
                                            title = "Unidades",
                                            value = "${result.totalQuantity}",
                                            subtitle = "Stock Total",
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MetricCard(
                                            title = "Costo Total",
                                            value = "$${String.format(java.util.Locale.US, "%.2f", result.totalCostValue)}",
                                            subtitle = "Inversión",
                                            color = Color(0xFFD97706),
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricCard(
                                            title = "Venta Total",
                                            value = "$${String.format(java.util.Locale.US, "%.2f", result.totalSaleValue)}",
                                            subtitle = "Margen: +$${String.format(java.util.Locale.US, "%.2f", result.totalMargin)}",
                                            color = Color(0xFF059669),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Resumen de advertencias / errores
                        if (result.warnings.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFD97706).copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "${result.warnings.size} filas omitidas por inconsistencias",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                        TextButton(
                                            onClick = { showAllWarnings = !showAllWarnings },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text(if (showAllWarnings) "Ocultar" else "Ver detalles", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    AnimatedVisibility(visible = showAllWarnings) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            result.warnings.forEach { warning ->
                                                Text(
                                                    text = "• Fila ${warning.rowNumber}: ${warning.reason}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tabla / Lista de Vista Previa de Productos Válidos
                        if (result.validRows.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vista previa de productos calculados (${result.validRows.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { showPreviewTable = !showPreviewTable },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (showPreviewTable) "Ocultar tabla" else "Mostrar tabla", fontSize = 11.sp)
                                }
                            }

                            AnimatedVisibility(visible = showPreviewTable) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .verticalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    ) {
                                        // Encabezado de tabla
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            TableHeaderCell("#", width = 35.dp)
                                            TableHeaderCell("Producto", width = 160.dp)
                                            TableHeaderCell("Cant.", width = 55.dp)
                                            TableHeaderCell("Costo ($)", width = 75.dp)
                                            TableHeaderCell("Venta ($)", width = 75.dp)
                                            TableHeaderCell("Margen ($)", width = 75.dp)
                                            TableHeaderCell("Total Costo ($)", width = 95.dp)
                                            TableHeaderCell("Total Venta ($)", width = 95.dp)
                                        }

                                        // Filas de datos
                                        result.validRows.forEachIndexed { idx, row ->
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TableCell("${idx + 1}", width = 35.dp, isBold = false)
                                                TableCell(row.productName, width = 160.dp, isBold = true)
                                                TableCell("${row.quantity}", width = 55.dp, isBold = false)
                                                TableCell("$${String.format(java.util.Locale.US, "%.2f", row.costPrice)}", width = 75.dp)
                                                TableCell("$${String.format(java.util.Locale.US, "%.2f", row.salePrice)}", width = 75.dp)
                                                TableCell(
                                                    "+$${String.format(java.util.Locale.US, "%.2f", row.unitMargin)}",
                                                    width = 75.dp,
                                                    textColor = if (row.unitMargin >= 0) Color(0xFF059669) else MaterialTheme.colorScheme.error
                                                )
                                                TableCell("$${String.format(java.util.Locale.US, "%.2f", row.totalCostValue)}", width = 95.dp)
                                                TableCell("$${String.format(java.util.Locale.US, "%.2f", row.totalSaleValue)}", width = 95.dp)
                                            }
                                            if (idx < result.validRows.size - 1) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de Acciones / Botón de Confirmación
                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 10.dp,
                    verticalSpacing = 8.dp,
                    firstWeight = 1f,
                    secondWeight = 1.6f,
                    first = { mod ->
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isImporting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = mod.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(if (importSuccessMessage != null) "Cerrar" else "Cancelar")
                        }
                    },
                    second = { mod ->
                        Button(
                            onClick = {
                                val rowsToImport = parseResult?.validRows ?: return@Button
                                if (rowsToImport.isEmpty()) return@Button

                                isImporting = true
                                parseError = null
                                viewModel.importProductsFromCsv(rowsToImport) { success, importedCount, _, _, msg ->
                                    isImporting = false
                                    if (success) {
                                        val finalNotification = "Se importaron $importedCount productos correctamente. ${parseResult?.warnings?.size ?: 0} filas tuvieron errores."
                                        importSuccessMessage = finalNotification
                                        Toast.makeText(context, finalNotification, Toast.LENGTH_LONG).show()
                                        onImportCompleted?.invoke(finalNotification)
                                    } else {
                                        parseError = msg
                                    }
                                }
                            },
                            enabled = !isImporting && !isParsing && (parseResult?.validRows?.isNotEmpty() == true) && importSuccessMessage == null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = mod.defaultMinSize(minHeight = 48.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardando...")
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (parseResult != null) "Confirmar Importación (${parseResult!!.validRows.size})" else "Confirmar e Importar",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )
            }
        }
}

@Composable
private fun HeaderFormatChip(name: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isBold: Boolean = false,
    textColor: Color = Color.Unspecified
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontSize = 11.5.sp,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            name = name?.substring(cut + 1)
        }
    }
    return name
}
