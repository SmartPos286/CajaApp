package com.example.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.Product
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import com.example.utils.ExtractedInvoiceItem
import com.example.utils.InvoiceCurrency
import com.example.utils.ParsedInvoiceOcrResult
import com.example.utils.PurchaseInvoiceOcrParser
import com.example.utils.PurchaseInvoiceOcrScanner
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceOcrImportDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val allSuppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val isMinProfitMarginEnabled by viewModel.isMinProfitMarginEnabled.collectAsStateWithLifecycle()
    val minProfitMarginPercent by viewModel.minProfitMarginPercent.collectAsStateWithLifecycle()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var isScanning by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var usedGeminiVision by remember { mutableStateOf(false) }

    var supplierName by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var totalAmountText by remember { mutableStateOf("") }
    var isTotalManuallyEdited by remember { mutableStateOf(false) }
    var rawOcrText by remember { mutableStateOf("") }
    var showRawOcrText by remember { mutableStateOf(false) }

    // Moneda (Dólares USD o Bolívares VES) y Tasa
    var selectedCurrency by remember { mutableStateOf(InvoiceCurrency.USD) }
    var exchangeRateText by remember {
        mutableStateOf(if (bcvRate > 0.0) String.format(Locale.US, "%.2f", bcvRate) else "1.00")
    }

    // IVA y tributos
    var applyIva by remember { mutableStateOf(false) }
    var ivaPercentText by remember { mutableStateOf("16") }
    var includeIvaInUnitCost by remember { mutableStateOf(true) }

    var extractedItems by remember { mutableStateOf<List<ExtractedInvoiceItem>>(emptyList()) }
    var itemBeingPriced by remember { mutableStateOf<ExtractedInvoiceItem?>(null) }

    val effectiveRateBs = remember(exchangeRateText, bcvRate) {
        val parsed = exchangeRateText.replace(",", ".").toDoubleOrNull()
        if (parsed != null && parsed > 0.001) parsed else if (bcvRate > 0.001) bcvRate else 1.0
    }

    val effectiveIvaPercent = remember(ivaPercentText, applyIva) {
        if (!applyIva) 0.0
        else (ivaPercentText.replace(",", ".").toDoubleOrNull() ?: 16.0).coerceIn(0.0, 50.0)
    }

    // Items convertidos a USD en tiempo real según moneda y tasa seleccionadas
    val convertedItemsUsd = remember(extractedItems, selectedCurrency, effectiveRateBs) {
        PurchaseInvoiceOcrParser.convertInvoiceItemsToUsd(
            items = extractedItems,
            currency = selectedCurrency,
            exchangeRateBs = effectiveRateBs
        )
    }

    val itemsSubtotalInvoiceCurrency = remember(extractedItems) {
        round2(extractedItems.sumOf { it.invoiceLineTotal })
    }
    val taxableSubtotalInvoiceCurrency = remember(extractedItems) {
        round2(extractedItems.filter { !it.isTaxExempt }.sumOf { it.invoiceLineTotal })
    }
    val calculatedIvaInvoiceCurrency = remember(applyIva, taxableSubtotalInvoiceCurrency, effectiveIvaPercent) {
        if (applyIva && effectiveIvaPercent > 0.0) {
            round2(taxableSubtotalInvoiceCurrency * (effectiveIvaPercent / 100.0))
        } else 0.0
    }
    val calculatedGrandTotalInvoiceCurrency = remember(itemsSubtotalInvoiceCurrency, calculatedIvaInvoiceCurrency) {
        round2(itemsSubtotalInvoiceCurrency + calculatedIvaInvoiceCurrency)
    }

    val subtotalUsd = remember(convertedItemsUsd) {
        round2(convertedItemsUsd.sumOf { it.lineTotalUsd })
    }
    val taxableSubtotalUsd = remember(convertedItemsUsd) {
        round2(convertedItemsUsd.filter { !it.isTaxExempt }.sumOf { it.lineTotalUsd })
    }
    val calculatedIvaUsd = remember(applyIva, taxableSubtotalUsd, effectiveIvaPercent) {
        if (applyIva && effectiveIvaPercent > 0.0) {
            round2(taxableSubtotalUsd * (effectiveIvaPercent / 100.0))
        } else 0.0
    }
    val calculatedGrandTotalUsd = remember(subtotalUsd, calculatedIvaUsd) {
        round2(subtotalUsd + calculatedIvaUsd)
    }

    // Sincronizar automáticamente el campo Total cuando cambia IVA, ítems o moneda (si no lo editó manualmente)
    LaunchedEffect(calculatedGrandTotalInvoiceCurrency, extractedItems.size, applyIva, effectiveIvaPercent) {
        if (!isTotalManuallyEdited && extractedItems.isNotEmpty()) {
            totalAmountText = fmt2(calculatedGrandTotalInvoiceCurrency)
        }
    }

    fun applyParsedResult(parsed: ParsedInvoiceOcrResult) {
        supplierName = parsed.supplierName
        invoiceNumber = parsed.invoiceNumber
        selectedCurrency = parsed.detectedCurrency
        applyIva = parsed.hasDetectedIva
        ivaPercentText = if (parsed.detectedIvaPercent > 0.0) {
            if (parsed.detectedIvaPercent == parsed.detectedIvaPercent.toInt().toDouble()) {
                parsed.detectedIvaPercent.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", parsed.detectedIvaPercent)
            }
        } else "16"
        extractedItems = parsed.items
        rawOcrText = parsed.rawText
        usedGeminiVision = parsed.usedGeminiVision
        isTotalManuallyEdited = false
        val initialTotal = if (parsed.totalAmountUsd > 0.0) parsed.totalAmountUsd else parsed.items.sumOf { it.invoiceLineTotal }
        totalAmountText = if (initialTotal > 0.0) fmt2(initialTotal) else ""
    }

    fun processImageUri(uri: Uri) {
        selectedImageUri = uri
        isScanning = true
        scanError = null
        scope.launch {
            val result = viewModel.scanPurchaseInvoiceImage(context, uri)
            isScanning = false
            result.fold(
                onSuccess = { parsed ->
                    applyParsedResult(parsed)
                    if (parsed.items.isEmpty()) {
                        scanError = if (parsed.rawText.isBlank()) {
                            "No se detectó texto legible en la imagen. Prueba con otra foto más nítida o agrega los productos manualmente."
                        } else {
                            "Se reconoció texto pero no se identificaron líneas claras de productos. Puedes revisar el texto OCR o agregar los ítems manualmente."
                        }
                    }
                },
                onFailure = { err ->
                    scanError = "Error al analizar imagen: ${err.localizedMessage ?: "Error desconocido"}"
                }
            )
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processImageUri(uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            processImageUri(uri)
        }
    }

    fun launchCameraCapture() {
        try {
            val photoFile = PurchaseInvoiceOcrScanner.createTempCameraImageFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No se pudo abrir la cámara: ${e.localizedMessage ?: "Error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(
                context,
                "Se requiere permiso de cámara para tomar la foto de la factura.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun handleTakePhotoClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun relinkProductMatch(item: ExtractedInvoiceItem, newName: String): ExtractedInvoiceItem {
        val matched: Product? = PurchaseInvoiceOcrParser.findBestMatchingProduct(newName, allProducts)
        return if (matched != null) {
            item.copy(
                name = newName,
                matchedProductId = matched.id,
                matchedProductName = matched.name,
                existingStock = matched.stock,
                existingCostUsd = matched.costPriceUsd,
                existingPriceUsd = matched.priceUsd,
                isTaxExempt = matched.isTaxExempt,
                salePriceUsd = if (item.salePriceUsd > 0.0) item.salePriceUsd else matched.priceUsd
            )
        } else {
            item.copy(
                name = newName,
                matchedProductId = null,
                matchedProductName = null,
                existingStock = 0.0,
                existingCostUsd = 0.0,
                existingPriceUsd = 0.0
            )
        }
    }

    val supplierMatched = remember(supplierName, allSuppliers) {
        val norm = PurchaseInvoiceOcrParser.normalizeText(supplierName)
        norm.isNotBlank() && allSuppliers.any {
            val sNorm = PurchaseInvoiceOcrParser.normalizeText(it.name)
            sNorm == norm || (sNorm.length >= 4 && (norm.contains(sNorm) || sNorm.contains(norm)))
        }
    }

    val totalInvoiceCurrencyVal = totalAmountText.replace(",", ".").toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?: calculatedGrandTotalInvoiceCurrency

    val effectiveGrandTotalUsd = remember(totalInvoiceCurrencyVal, selectedCurrency, effectiveRateBs) {
        if (selectedCurrency == InvoiceCurrency.VES) {
            round2(totalInvoiceCurrencyVal / effectiveRateBs)
        } else {
            round2(totalInvoiceCurrencyVal)
        }
    }

    val newItemsMissingPriceCount = remember(convertedItemsUsd) {
        convertedItemsUsd.count { !it.isExistingProduct && it.name.isNotBlank() && it.salePriceUsd <= 0.0 }
    }

    val currencySymbol = if (selectedCurrency == InvoiceCurrency.VES) "Bs." else "$"

    KeyboardAwareDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        maxWidth = 650.dp,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        horizontalPadding = 10.dp,
        verticalPadding = 10.dp,
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .testTag("invoice_ocr_dialog")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1B4B))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "Escanear Factura (OCR)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Dólares o Bolívares • Opción de IVA • Suma automática",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { if (!isSaving) onDismiss() },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color(0xFFCBD5E1))
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Body
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Source Buttons (Camera & Gallery)
                item {
                    ResponsivePair(
                        breakpoint = 340.dp,
                        horizontalSpacing = 8.dp,
                        verticalSpacing = 8.dp,
                        first = { mod ->
                            Button(
                                onClick = { handleTakePhotoClick() },
                                enabled = !isScanning && !isSaving,
                                modifier = mod
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("invoice_ocr_camera_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = "Tomar foto de factura",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Tomar Foto", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        second = { mod ->
                            Button(
                                onClick = {
                                    galleryPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isScanning && !isSaving,
                                modifier = mod
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("invoice_ocr_gallery_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Icon(
                                    Icons.Filled.PhotoLibrary,
                                    contentDescription = "Cargar imagen de galería",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Cargar Imagen", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    )
                }

                        // Preview + Scanning state
                        if (selectedImageUri != null || isScanning || scanError != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            selectedImageUri?.let { uri ->
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Vista previa de factura",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF0F172A))
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (isScanning) {
                                                    Text(
                                                        "Reconociendo texto de factura...",
                                                        color = Color(0xFF38BDF8),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Spacer(Modifier.height(6.dp))
                                                    LinearProgressIndicator(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = Color(0xFF38BDF8),
                                                        trackColor = Color(0xFF0F172A)
                                                    )
                                                } else {
                                                    Text(
                                                        text = if (usedGeminiVision) {
                                                            "Análisis completado (OCR + Gemini Visión)"
                                                        } else {
                                                            "Análisis completado (OCR en dispositivo)"
                                                        },
                                                        color = Color(0xFF34D399),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                    if (rawOcrText.isNotBlank()) {
                                                        TextButton(
                                                            onClick = { showRawOcrText = !showRawOcrText },
                                                            contentPadding = PaddingValues(0.dp),
                                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                                        ) {
                                                            Text(
                                                                if (showRawOcrText) "Ocultar texto OCR" else "Ver/Editar texto OCR",
                                                                fontSize = 11.5.sp,
                                                                color = Color(0xFF93C5FD)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (!scanError.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF7C2D12).copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = scanError!!,
                                                    color = Color(0xFFFED7AA),
                                                    fontSize = 11.5.sp,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = showRawOcrText) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedTextField(
                                                    value = rawOcrText,
                                                    onValueChange = { rawOcrText = it },
                                                    label = { Text("Texto OCR reconocido", fontSize = 11.sp) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .bringIntoViewOnFocus(),
                                                    maxLines = 6,
                                                    colors = darkFieldColors()
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val reparsed = viewModel.reparsePurchaseInvoiceText(rawOcrText)
                                                            applyParsedResult(reparsed)
                                                        }
                                                    },
                                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                                ) {
                                                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Re-extraer datos desde texto", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Moneda de la Factura (USD vs Bolívares) & Tasa BCV
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Moneda de los Precios en la Factura",
                                        color = Color(0xFFE2E8F0),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    ResponsivePair(
                                        breakpoint = 340.dp,
                                        horizontalSpacing = 8.dp,
                                        verticalSpacing = 8.dp,
                                        first = { mod ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (selectedCurrency == InvoiceCurrency.USD) Color(0xFF065F46) else Color(0xFF0F172A),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selectedCurrency == InvoiceCurrency.USD) Color(0xFF34D399) else Color(0xFF334155)
                                                ),
                                                modifier = mod
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedCurrency = InvoiceCurrency.USD }
                                                    .testTag("invoice_ocr_currency_usd")
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        "Dólares ($ USD)",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                }
                                            }
                                        },
                                        second = { mod ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (selectedCurrency == InvoiceCurrency.VES) Color(0xFF1D4ED8) else Color(0xFF0F172A),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selectedCurrency == InvoiceCurrency.VES) Color(0xFF60A5FA) else Color(0xFF334155)
                                                ),
                                                modifier = mod
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedCurrency = InvoiceCurrency.VES }
                                                    .testTag("invoice_ocr_currency_ves")
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        "Bolívares (Bs.)",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    AnimatedVisibility(visible = selectedCurrency == InvoiceCurrency.VES) {
                                        ResponsivePair(
                                            breakpoint = 360.dp,
                                            horizontalSpacing = 8.dp,
                                            verticalSpacing = 8.dp,
                                            firstWeight = 0.45f,
                                            secondWeight = 0.55f,
                                            first = { mod ->
                                                OutlinedTextField(
                                                    value = exchangeRateText,
                                                    onValueChange = { exchangeRateText = it },
                                                    label = { Text("Tasa Bs/USD", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = mod
                                                        .bringIntoViewOnFocus()
                                                        .testTag("invoice_ocr_rate_input"),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    colors = darkFieldColors()
                                                )
                                            },
                                            second = { mod ->
                                                Text(
                                                    text = "Los precios en Bs. se convierten automáticamente a USD (tasa ${fmt2(effectiveRateBs)} Bs/$) al guardar en inventario.",
                                                    color = Color(0xFF93C5FD),
                                                    fontSize = 11.sp,
                                                    modifier = mod
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Invoice Header Fields (Supplier, Invoice Number, Total)
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ResponsiveActionRow(
                                        breakpoint = 340.dp,
                                        stackedActionAlignment = Alignment.Start,
                                        content = {
                                            Text(
                                                "Datos de la Factura",
                                                color = Color(0xFFE2E8F0),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        action = {
                                            if (supplierName.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (supplierMatched) Color(0xFF065F46) else Color(0xFF1E3A8A)
                                                ) {
                                                    Text(
                                                        text = if (supplierMatched) "Proveedor registrado" else "Se creará proveedor",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    OutlinedTextField(
                                        value = supplierName,
                                        onValueChange = { supplierName = it },
                                        label = { Text("Proveedor", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bringIntoViewOnFocus()
                                            .testTag("invoice_ocr_supplier_input"),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        colors = darkFieldColors()
                                    )

                                    ResponsivePair(
                                        breakpoint = 340.dp,
                                        horizontalSpacing = 8.dp,
                                        verticalSpacing = 8.dp,
                                        first = { mod ->
                                            OutlinedTextField(
                                                value = invoiceNumber,
                                                onValueChange = { invoiceNumber = it },
                                                label = { Text("N° Factura", fontSize = 11.5.sp) },
                                                singleLine = true,
                                                modifier = mod
                                                    .bringIntoViewOnFocus()
                                                    .testTag("invoice_ocr_number_input"),
                                                colors = darkFieldColors()
                                            )
                                        },
                                        second = { mod ->
                                            OutlinedTextField(
                                                value = totalAmountText,
                                                onValueChange = {
                                                    totalAmountText = it
                                                    isTotalManuallyEdited = true
                                                },
                                                label = { Text("Total ($currencySymbol)", fontSize = 11.5.sp) },
                                                placeholder = {
                                                    Text(fmt2(calculatedGrandTotalInvoiceCurrency), color = Color(0xFF64748B))
                                                },
                                                singleLine = true,
                                                modifier = mod
                                                    .bringIntoViewOnFocus()
                                                    .testTag("invoice_ocr_total_input"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = darkFieldColors()
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // IVA Section (Añadir IVA, %, Incluir en Costo Unitario)
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                                border = BorderStroke(
                                    1.dp,
                                    if (applyIva) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color(0xFF334155)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Añadir IVA a la Factura",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = if (applyIva) {
                                                    "Base: $currencySymbol${fmt2(itemsSubtotalInvoiceCurrency)} + IVA (${fmtPct(effectiveIvaPercent)}%): $currencySymbol${fmt2(calculatedIvaInvoiceCurrency)}"
                                                } else {
                                                    "Actívalo para sumar el IVA a la factura y/o al costo unitario"
                                                },
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = applyIva,
                                            onCheckedChange = {
                                                applyIva = it
                                                isTotalManuallyEdited = false
                                            },
                                            modifier = Modifier.testTag("invoice_ocr_iva_switch"),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFF0284C7)
                                            )
                                        )
                                    }

                                    AnimatedVisibility(visible = applyIva) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ResponsiveActionRow(
                                                breakpoint = 340.dp,
                                                stackedActionAlignment = Alignment.Start,
                                                content = {
                                                    OutlinedTextField(
                                                        value = ivaPercentText,
                                                        onValueChange = {
                                                            ivaPercentText = it
                                                            isTotalManuallyEdited = false
                                                        },
                                                        label = { Text("% IVA", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .bringIntoViewOnFocus()
                                                            .testTag("invoice_ocr_iva_percent_input"),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        colors = darkFieldColors()
                                                    )
                                                },
                                                action = {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        listOf("16", "8").forEach { preset ->
                                                            val selected = ivaPercentText.trim() == preset
                                                            Surface(
                                                                shape = RoundedCornerShape(10.dp),
                                                                color = if (selected) Color(0xFF0284C7) else Color(0xFF0F172A),
                                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                                                modifier = Modifier
                                                                    .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .clickable {
                                                                        ivaPercentText = preset
                                                                        isTotalManuallyEdited = false
                                                                    }
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                                    Text(
                                                                        text = "$preset%",
                                                                        color = Color.White,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 12.5.sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                                                    .clickable { includeIvaInUnitCost = !includeIvaInUnitCost }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = includeIvaInUnitCost,
                                                    onCheckedChange = { includeIvaInUnitCost = it },
                                                    modifier = Modifier.testTag("invoice_ocr_iva_in_cost_checkbox"),
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color(0xFF10B981),
                                                        checkmarkColor = Color.White
                                                    )
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "Sumar IVA al costo unitario de productos gravados",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        "Los ítems marcados 'Exento' no suman IVA.",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 10.5.sp
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF0C4A6E).copy(alpha = 0.55f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Subtotal Base:", color = Color(0xFFBAE6FD), fontSize = 11.5.sp)
                                                        Text(
                                                            "$currencySymbol${fmt2(itemsSubtotalInvoiceCurrency)}" +
                                                                if (selectedCurrency == InvoiceCurrency.VES) " ($${fmt2(subtotalUsd)})" else "",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("IVA (${fmtPct(effectiveIvaPercent)}%):", color = Color(0xFFBAE6FD), fontSize = 11.5.sp)
                                                        Text(
                                                            "+ $currencySymbol${fmt2(calculatedIvaInvoiceCurrency)}" +
                                                                if (selectedCurrency == InvoiceCurrency.VES) " ($${fmt2(calculatedIvaUsd)})" else "",
                                                            color = Color(0xFF38BDF8),
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    HorizontalDivider(color = Color(0xFF38BDF8).copy(alpha = 0.25f))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Total Factura con IVA:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            "$currencySymbol${fmt2(calculatedGrandTotalInvoiceCurrency)}" +
                                                                if (selectedCurrency == InvoiceCurrency.VES) " ($${fmt2(calculatedGrandTotalUsd)} USD)" else " USD",
                                                            color = Color(0xFF34D399),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section Header for Extracted Items
                        item {
                            ResponsiveActionRow(
                                breakpoint = 360.dp,
                                stackedActionAlignment = Alignment.Start,
                                content = {
                                    Column {
                                        Text(
                                            text = "Productos Extraídos (${extractedItems.size})",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                        Text(
                                            text = "Los existentes suman stock y solo suben costo si el nuevo es mayor.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp
                                        )
                                    }
                                },
                                action = {
                                    TextButton(
                                        onClick = {
                                            extractedItems = extractedItems + ExtractedInvoiceItem(
                                                name = "",
                                                quantity = 1.0,
                                                unitCostUsd = 0.0,
                                                lineTotalUsd = 0.0,
                                                invoiceUnitCost = 0.0,
                                                invoiceLineTotal = 0.0
                                            )
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("invoice_ocr_add_manual_item")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Agregar Ítem", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }

                        if (extractedItems.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF1E293B).copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.ReceiptLong,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            "Toma una foto o carga la imagen de la factura para listar los productos aquí.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(convertedItemsUsd, key = { _, item -> item.id }) { index, itemUsd ->
                                ExtractedInvoiceItemCard(
                                    index = index,
                                    item = itemUsd,
                                    currency = selectedCurrency,
                                    applyIva = applyIva,
                                    ivaPercent = effectiveIvaPercent,
                                    includeIvaInCost = includeIvaInUnitCost,
                                    onNameChanged = { newName ->
                                        extractedItems = extractedItems.map { current ->
                                            if (current.id == itemUsd.id) relinkProductMatch(current, newName) else current
                                        }
                                    },
                                    onQuantityChanged = { newQty ->
                                        val qty = newQty.coerceAtLeast(0.0)
                                        extractedItems = extractedItems.map { current ->
                                            if (current.id == itemUsd.id) {
                                                current.copy(
                                                    quantity = qty,
                                                    invoiceLineTotal = round2(qty * current.invoiceUnitCost)
                                                )
                                            } else current
                                        }
                                    },
                                    onUnitCostChanged = { newUnitInvoiceCurrency ->
                                        val unit = newUnitInvoiceCurrency.coerceAtLeast(0.0)
                                        extractedItems = extractedItems.map { current ->
                                            if (current.id == itemUsd.id) {
                                                current.copy(
                                                    invoiceUnitCost = unit,
                                                    invoiceLineTotal = round2(current.quantity * unit)
                                                )
                                            } else current
                                        }
                                    },
                                    onLineTotalChanged = { newTotalInvoiceCurrency ->
                                        val tot = newTotalInvoiceCurrency.coerceAtLeast(0.0)
                                        extractedItems = extractedItems.map { current ->
                                            if (current.id == itemUsd.id) {
                                                val unit = if (current.quantity > 0.0) round2(tot / current.quantity) else current.invoiceUnitCost
                                                current.copy(
                                                    invoiceUnitCost = unit,
                                                    invoiceLineTotal = tot
                                                )
                                            } else current
                                        }
                                    },
                                    onToggleTaxExempt = {
                                        extractedItems = extractedItems.map { current ->
                                            if (current.id == itemUsd.id) current.copy(isTaxExempt = !current.isTaxExempt) else current
                                        }
                                    },
                                    onSetSalePriceClick = {
                                        itemBeingPriced = itemUsd
                                    },
                                    onDelete = {
                                        extractedItems = extractedItems.filterNot { it.id == itemUsd.id }
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (newItemsMissingPriceCount > 0) {
                            Text(
                                text = "⚠️ Hay $newItemsMissingPriceCount producto(s) nuevo(s) sin Precio de Venta. Toca 'Fijar P. Venta' al lado de cada uno.",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp
                            )
                        }

                        ResponsivePair(
                            breakpoint = 360.dp,
                            horizontalSpacing = 8.dp,
                            verticalSpacing = 8.dp,
                            firstWeight = 0.36f,
                            secondWeight = 0.64f,
                            first = { mod ->
                                OutlinedButton(
                                    onClick = { if (!isSaving) onDismiss() },
                                    enabled = !isSaving,
                                    modifier = mod.defaultMinSize(minHeight = 48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancelar", color = Color(0xFFCBD5E1))
                                }
                            },
                            second = { mod ->
                                Button(
                                    onClick = {
                                        if (convertedItemsUsd.none { it.name.isNotBlank() && it.quantity > 0.0 }) {
                                            Toast.makeText(context, "Agrega al menos un producto válido.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isSaving = true
                                        val currencyDesc = if (selectedCurrency == InvoiceCurrency.VES) {
                                            "Bs. • Tasa ${fmt2(effectiveRateBs)}"
                                        } else {
                                            "USD"
                                        }
                                        viewModel.importScannedPurchaseInvoice(
                                            supplierName = supplierName,
                                            invoiceNumber = invoiceNumber,
                                            totalAmountUsd = effectiveGrandTotalUsd,
                                            items = convertedItemsUsd,
                                            subtotalUsd = subtotalUsd,
                                            taxAmountUsd = calculatedIvaUsd,
                                            ivaPercent = effectiveIvaPercent,
                                            applyIvaToProductCost = applyIva && includeIvaInUnitCost,
                                            currencyLabel = currencyDesc
                                        ) { success, message ->
                                            isSaving = false
                                            if (success) {
                                                Toast.makeText(context, "Factura e inventario actualizados", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } else {
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = !isScanning && !isSaving && convertedItemsUsd.isNotEmpty(),
                                    modifier = mod
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("invoice_ocr_confirm_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (isSaving) {
                                            "Guardando..."
                                        } else {
                                            "Cargar al Inventario ($${fmt2(effectiveGrandTotalUsd)} USD)"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        )
                    }
        }
    }

    itemBeingPriced?.let { targetItem ->
        SetSalePriceDialog(
            item = targetItem,
            bcvRate = effectiveRateBs,
            applyIva = applyIva,
            ivaPercent = effectiveIvaPercent,
            includeIvaInCost = includeIvaInUnitCost,
            configuredMinMargin = if (isMinProfitMarginEnabled && minProfitMarginPercent > 0.0) minProfitMarginPercent else null,
            onDismiss = { itemBeingPriced = null },
            onSavePrice = { newSalePriceUsd ->
                extractedItems = extractedItems.map { current ->
                    if (current.id == targetItem.id) current.copy(salePriceUsd = newSalePriceUsd) else current
                }
                itemBeingPriced = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtractedInvoiceItemCard(
    index: Int,
    item: ExtractedInvoiceItem,
    currency: InvoiceCurrency,
    applyIva: Boolean,
    ivaPercent: Double,
    includeIvaInCost: Boolean,
    onNameChanged: (String) -> Unit,
    onQuantityChanged: (Double) -> Unit,
    onUnitCostChanged: (Double) -> Unit,
    onLineTotalChanged: (Double) -> Unit,
    onToggleTaxExempt: () -> Unit,
    onSetSalePriceClick: () -> Unit,
    onDelete: () -> Unit
) {
    var qtyText by remember(item.id) {
        mutableStateOf(fmtQty(item.quantity))
    }
    var unitCostText by remember(item.id) {
        mutableStateOf(if (item.invoiceUnitCost > 0.0) fmt2(item.invoiceUnitCost) else "")
    }
    var lineTotalText by remember(item.id) {
        mutableStateOf(if (item.invoiceLineTotal > 0.0) fmt2(item.invoiceLineTotal) else "")
    }

    LaunchedEffect(item.invoiceUnitCost) {
        val parsed = unitCostText.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (abs(parsed - item.invoiceUnitCost) > 0.015) {
            unitCostText = if (item.invoiceUnitCost > 0.0) fmt2(item.invoiceUnitCost) else ""
        }
    }
    LaunchedEffect(item.invoiceLineTotal) {
        val parsed = lineTotalText.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (abs(parsed - item.invoiceLineTotal) > 0.015) {
            lineTotalText = if (item.invoiceLineTotal > 0.0) fmt2(item.invoiceLineTotal) else ""
        }
    }

    val effectiveUnitCostUsd = item.effectiveUnitCostUsd(
        applyIva = applyIva,
        ivaPercent = ivaPercent,
        includeIvaInCost = includeIvaInCost
    )
    val willRaiseCost = item.isExistingProduct && effectiveUnitCostUsd > item.existingCostUsd + 0.0001
    val needsSalePrice = !item.isExistingProduct && item.salePriceUsd <= 0.0
    val unitSuffix = if (currency == InvoiceCurrency.VES) "Bs." else "$"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isExistingProduct) Color(0xFF10B981).copy(alpha = 0.65f)
            else if (needsSalePrice) Color(0xFFF59E0B).copy(alpha = 0.75f)
            else Color(0xFF334155)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invoice_ocr_item_card_$index")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Product Name + Set Sale Price Button + Delete Button
            ResponsiveActionRow(
                breakpoint = 360.dp,
                stackedActionAlignment = Alignment.End,
                content = {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = onNameChanged,
                        label = { Text("Producto #${index + 1}", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = darkFieldColors()
                    )
                },
                action = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = onSetSalePriceClick,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("invoice_ocr_set_sale_price_$index"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.salePriceUsd > 0.0) Color(0xFF065F46) else Color(0xFFD97706)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (item.salePriceUsd > 0.0) "P. Venta" else "Fijar",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = if (item.salePriceUsd > 0.0) "$${fmt2(item.salePriceUsd)}" else "P. Venta",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Quitar producto",
                                tint = Color(0xFFF87171)
                            )
                        }
                    }
                }
            )

            // Status Badges (Stock, Cost rule, IVA Gravado/Exento)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.isExistingProduct) Color(0xFF064E3B) else Color(0xFF312E81)
                ) {
                    Text(
                        text = if (item.isExistingProduct) {
                            "Existente: +${fmtQty(item.quantity)} (Total ${fmtQty(item.existingStock + item.quantity)})"
                        } else {
                            "Nuevo (+${fmtQty(item.quantity)} uds)"
                        },
                        color = if (item.isExistingProduct) Color(0xFF6EE7B7) else Color(0xFFC7D2FE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (item.isExistingProduct) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (willRaiseCost) Color(0xFF78350F) else Color(0xFF0C4A6E)
                    ) {
                        Text(
                            text = if (willRaiseCost) {
                                "Costo sube: $${fmt2(item.existingCostUsd)} → $${fmt2(effectiveUnitCostUsd)}"
                            } else {
                                "Costo se mantiene: $${fmt2(item.existingCostUsd)}"
                            },
                            color = if (willRaiseCost) Color(0xFFFDE68A) else Color(0xFFBAE6FD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                if (applyIva) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isTaxExempt) Color(0xFF334155) else Color(0xFF075985),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onToggleTaxExempt() }
                            .testTag("invoice_ocr_item_exempt_chip_$index")
                    ) {
                        Text(
                            text = if (item.isTaxExempt) "Exento" else "+IVA ${fmtPct(ivaPercent)}%",
                            color = if (item.isTaxExempt) Color(0xFFCBD5E1) else Color(0xFFBAE6FD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Bottom Row: Qty, Unit Cost, Line Total
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val qtyField: @Composable (Modifier) -> Unit = { mod ->
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { raw ->
                            qtyText = raw
                            raw.replace(",", ".").toDoubleOrNull()?.let { onQuantityChanged(it) }
                        },
                        label = { Text("Cant.", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = mod.bringIntoViewOnFocus(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors()
                    )
                }
                val unitCostField: @Composable (Modifier) -> Unit = { mod ->
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { raw ->
                            unitCostText = raw
                            raw.replace(",", ".").toDoubleOrNull()?.let { onUnitCostChanged(it) }
                        },
                        label = { Text("Costo Unit $unitSuffix", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = mod.bringIntoViewOnFocus(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors()
                    )
                }
                val lineTotalField: @Composable (Modifier) -> Unit = { mod ->
                    OutlinedTextField(
                        value = lineTotalText,
                        onValueChange = { raw ->
                            lineTotalText = raw
                            raw.replace(",", ".").toDoubleOrNull()?.let { onLineTotalChanged(it) }
                        },
                        label = { Text("Total $unitSuffix", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = mod.bringIntoViewOnFocus(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = darkFieldColors()
                    )
                }

                if (maxWidth >= 340.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        qtyField(Modifier.weight(0.8f))
                        unitCostField(Modifier.weight(1.1f))
                        lineTotalField(Modifier.weight(1.1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        qtyField(Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unitCostField(Modifier.weight(1f))
                            lineTotalField(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (currency == InvoiceCurrency.VES || (applyIva && includeIvaInCost && !item.isTaxExempt)) {
                Text(
                    text = buildString {
                        append("Equiv. USD: $${fmt2(item.unitCostUsd)} c/u • $${fmt2(item.lineTotalUsd)} total")
                        if (applyIva && includeIvaInCost && !item.isTaxExempt) {
                            append(" | Con IVA: $${fmt2(effectiveUnitCostUsd)} c/u")
                        }
                    },
                    color = Color(0xFF93C5FD),
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetSalePriceDialog(
    item: ExtractedInvoiceItem,
    bcvRate: Double,
    applyIva: Boolean,
    ivaPercent: Double,
    includeIvaInCost: Boolean,
    configuredMinMargin: Double?,
    onDismiss: () -> Unit,
    onSavePrice: (Double) -> Unit
) {
    val effectiveCostUsd = item.effectiveUnitCostUsd(
        applyIva = applyIva,
        ivaPercent = ivaPercent,
        includeIvaInCost = includeIvaInCost
    )
    val initialUsd = when {
        item.salePriceUsd > 0.0 -> item.salePriceUsd
        item.existingPriceUsd > 0.0 -> item.existingPriceUsd
        configuredMinMargin != null && effectiveCostUsd > 0.0 -> round2(effectiveCostUsd * (1.0 + configuredMinMargin / 100.0))
        else -> 0.0
    }

    var priceUsdText by remember(item.id) {
        mutableStateOf(if (initialUsd > 0.0) fmt2(initialUsd) else "")
    }
    var priceBsText by remember(item.id) {
        mutableStateOf(if (initialUsd > 0.0) fmt2(initialUsd * bcvRate) else "")
    }

    val parsedPriceUsd = priceUsdText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val marginPct = if (effectiveCostUsd > 0.0 && parsedPriceUsd > 0.0) {
        ((parsedPriceUsd - effectiveCostUsd) / effectiveCostUsd) * 100.0
    } else 0.0

    val suggestedMargins = remember(configuredMinMargin) {
        buildList {
            configuredMinMargin?.let { add(it.toInt()) }
            listOf(20, 30, 40, 50).forEach { if (!contains(it)) add(it) }
        }
    }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Text(
                "Precio de Venta",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = item.name.ifBlank { "Producto de factura" },
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF93C5FD),
                    fontSize = 14.sp
                )
                Text(
                    text = "Costo unitario efectivo: $${fmt2(effectiveCostUsd)} USD (Bs. ${fmt2(effectiveCostUsd * bcvRate)})" +
                        if (item.isExistingProduct && item.existingPriceUsd > 0.0) {
                            "\nPrecio actual en catálogo: $${fmt2(item.existingPriceUsd)} USD"
                        } else "",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                ResponsivePair(
                    breakpoint = 340.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        OutlinedTextField(
                            value = priceUsdText,
                            onValueChange = { raw ->
                                priceUsdText = raw
                                val usd = raw.replace(",", ".").toDoubleOrNull()
                                if (usd != null && usd >= 0.0) {
                                    priceBsText = fmt2(usd * bcvRate)
                                }
                            },
                            label = { Text("Precio ($ USD)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = mod
                                .bringIntoViewOnFocus()
                                .testTag("invoice_ocr_sale_price_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = darkFieldColors()
                        )
                    },
                    second = { mod ->
                        OutlinedTextField(
                            value = priceBsText,
                            onValueChange = { raw ->
                                priceBsText = raw
                                val bs = raw.replace(",", ".").toDoubleOrNull()
                                if (bs != null && bs >= 0.0 && bcvRate > 0.001) {
                                    priceUsdText = fmt2(bs / bcvRate)
                                }
                            },
                            label = { Text("Precio (Bs.)", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = mod
                                .bringIntoViewOnFocus()
                                .testTag("invoice_ocr_sale_price_bs_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = darkFieldColors()
                        )
                    }
                )

                if (parsedPriceUsd > 0.0 && effectiveCostUsd > 0.0) {
                    Text(
                        text = "Ganancia estimada: ${String.format(Locale.US, "%.1f", marginPct)}% (Tasa ${fmt2(bcvRate)} Bs/$)",
                        fontSize = 11.5.sp,
                        color = Color(0xFF34D399)
                    )
                }

                if (effectiveCostUsd > 0.0) {
                    Text("Calcular rápido sobre costo:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedMargins.forEach { pct ->
                            val suggestedUsd = round2(effectiveCostUsd * (1.0 + pct / 100.0))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        priceUsdText = fmt2(suggestedUsd)
                                        priceBsText = fmt2(suggestedUsd * bcvRate)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(
                                        text = "+$pct%",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF93C5FD)
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
                    val value = priceUsdText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    onSavePrice(value.coerceAtLeast(0.0))
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("invoice_ocr_save_sale_price_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Aplicar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancelar", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF38BDF8),
    unfocusedBorderColor = Color(0xFF475569),
    focusedLabelColor = Color(0xFF38BDF8),
    unfocusedLabelColor = Color(0xFF94A3B8),
    cursorColor = Color(0xFF38BDF8)
)

private fun fmt2(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun fmtPct(value: Double): String =
    if (value == value.toInt().toDouble()) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun fmtQty(qty: Double): String =
    if (qty == qty.toInt().toDouble()) qty.toInt().toString() else String.format(Locale.US, "%.2f", qty)

private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
