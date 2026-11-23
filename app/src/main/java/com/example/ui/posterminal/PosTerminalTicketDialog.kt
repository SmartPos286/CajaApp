package com.example.ui.posterminal

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.PosTerminalRecord
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import com.example.utils.ParsedPosTicketResult
import com.example.utils.PosTicketOcrParser
import com.example.utils.PosTicketOcrScanner
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosTerminalTicketDialog(
    viewModel: PosViewModel,
    initialRecord: PosTerminalRecord? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = initialRecord != null

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var usedGeminiVision by remember { mutableStateOf(false) }
    var showRawOcr by remember { mutableStateOf(false) }

    var transactionDateTime by remember {
        mutableStateOf(
            initialRecord?.transactionDateTime?.ifBlank { PosTicketOcrParser.formatMillis(System.currentTimeMillis()) }
                ?: PosTicketOcrParser.formatMillis(System.currentTimeMillis())
        )
    }
    var receiptNumber by remember { mutableStateOf(initialRecord?.receiptNumber.orEmpty()) }
    var referenceNumber by remember { mutableStateOf(initialRecord?.referenceNumber.orEmpty()) }
    var approvalNumber by remember { mutableStateOf(initialRecord?.approvalNumber.orEmpty()) }
    var maskedCardNumber by remember { mutableStateOf(initialRecord?.maskedCardNumber.orEmpty()) }
    var operationType by remember { mutableStateOf(initialRecord?.operationType?.ifBlank { "COMPRA" } ?: "COMPRA") }
    var totalAmountText by remember {
        mutableStateOf(
            if (initialRecord != null && initialRecord.totalAmount > 0.0) {
                String.format(Locale.US, "%.2f", initialRecord.totalAmount)
            } else ""
        )
    }
    var currency by remember { mutableStateOf(initialRecord?.currency?.ifBlank { "VES" } ?: "VES") }
    var transactionStatus by remember {
        mutableStateOf(initialRecord?.transactionStatus?.ifBlank { "APROBADA" } ?: "APROBADA")
    }
    var bankOrTerminal by remember { mutableStateOf(initialRecord?.bankOrTerminal.orEmpty()) }
    var notes by remember { mutableStateOf(initialRecord?.notes.orEmpty()) }
    var rawOcrText by remember { mutableStateOf(initialRecord?.rawOcrText.orEmpty()) }

    fun applyParsed(parsed: ParsedPosTicketResult) {
        if (parsed.transactionDateTime.isNotBlank()) transactionDateTime = parsed.transactionDateTime
        if (parsed.receiptNumber.isNotBlank()) receiptNumber = parsed.receiptNumber
        if (parsed.referenceNumber.isNotBlank()) referenceNumber = parsed.referenceNumber
        if (parsed.approvalNumber.isNotBlank()) approvalNumber = parsed.approvalNumber
        if (parsed.maskedCardNumber.isNotBlank()) maskedCardNumber = parsed.maskedCardNumber
        if (parsed.operationType.isNotBlank()) operationType = parsed.operationType
        if (parsed.totalAmount > 0.0) {
            totalAmountText = String.format(Locale.US, "%.2f", parsed.totalAmount)
        }
        currency = parsed.currency
        transactionStatus = parsed.transactionStatus
        if (parsed.bankOrTerminal.isNotBlank()) bankOrTerminal = parsed.bankOrTerminal
        rawOcrText = parsed.rawText
        usedGeminiVision = parsed.usedGeminiVision
    }

    fun processImageUri(uri: Uri) {
        selectedImageUri = uri
        isScanning = true
        scanError = null
        scope.launch {
            val result = viewModel.scanPosTicketImage(context, uri)
            isScanning = false
            result.fold(
                onSuccess = { parsed ->
                    applyParsed(parsed)
                    if (parsed.rawText.isBlank() && parsed.totalAmount <= 0.0 && parsed.referenceNumber.isBlank()) {
                        scanError = "No se detectó texto legible en el ticket. Toma una foto más nítida o completa los datos manualmente."
                    }
                },
                onFailure = { err ->
                    scanError = "Error al analizar la imagen: ${err.localizedMessage ?: "Error desconocido"}"
                }
            )
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) processImageUri(uri)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) processImageUri(uri)
    }

    fun launchCameraCapture() {
        try {
            val photoFile = PosTicketOcrScanner.createTempCameraImageFile(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir la cámara: ${e.localizedMessage ?: "Error"}", Toast.LENGTH_LONG).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara para fotografiar el ticket.", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleTakePhotoClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 620.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
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
                        Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (isEditing) "Editar Registro de Ticket POS" else "Cargar Ticket de Punto de Venta",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Captura por cámara, galería o ingreso manual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_pos_ticket_dialog")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Scrollable form body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Capture actions (Camera & Photo Picker)
                ResponsivePair(
                    breakpoint = 340.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        Button(
                            onClick = { handleTakePhotoClick() },
                            enabled = !isScanning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = mod
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("pos_ticket_camera_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Tomar foto", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tomar Foto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    second = { mod ->
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isScanning,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("pos_ticket_gallery_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Seleccionar imagen", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Subir Imagen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                )

                // Image preview + scanning status
                if (selectedImageUri != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Vista previa del ticket POS",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (isScanning) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("Extrayendo datos del ticket...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Text(
                                        text = if (usedGeminiVision) "Extraído con OCR + IA Visión" else "Extraído con OCR local",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                    Text(
                                        text = "Revisa y corrige cualquier campo antes de guardar.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Analizando ticket...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (scanError != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scanError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Transaction status selector
                Text("Estado de la Transacción", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statuses = listOf(
                        "APROBADA" to Color(0xFF059669),
                        "RECHAZADA" to Color(0xFFDC2626),
                        "ANULADA" to Color(0xFFD97706),
                        "PENDIENTE" to Color(0xFF64748B)
                    )
                    statuses.forEach { (st, tint) ->
                        val selected = transactionStatus.equals(st, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { transactionStatus = st },
                            label = { Text(st, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tint.copy(alpha = 0.16f),
                                selectedLabelColor = tint
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        )
                    }
                }

                // Amount & Currency
                ResponsivePair(
                    breakpoint = 360.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        OutlinedTextField(
                            value = totalAmountText,
                            onValueChange = { totalAmountText = it },
                            label = { Text("Monto Total *") },
                            placeholder = { Text("Ej: 450.00") },
                            leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus().testTag("pos_ticket_amount_input")
                        )
                    },
                    second = { mod ->
                        Column(modifier = mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Moneda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = currency == "VES",
                                    onClick = { currency = "VES" },
                                    label = { Text("Bs. (VES)", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                )
                                FilterChip(
                                    selected = currency == "USD",
                                    onClick = { currency = "USD" },
                                    label = { Text("USD ($)", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                )
                            }
                        }
                    }
                )

                // Date/Time & Masked Card
                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        OutlinedTextField(
                            value = transactionDateTime,
                            onValueChange = { transactionDateTime = it },
                            label = { Text("Fecha y Hora") },
                            placeholder = { Text("dd/MM/yyyy HH:mm") },
                            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus().testTag("pos_ticket_datetime_input")
                        )
                    },
                    second = { mod ->
                        OutlinedTextField(
                            value = maskedCardNumber,
                            onValueChange = { maskedCardNumber = it },
                            label = { Text("Tarjeta Enmascarada") },
                            placeholder = { Text("Ej: **** 4521") },
                            leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus().testTag("pos_ticket_card_input")
                        )
                    }
                )

                // Receipt, Reference, Approval
                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        OutlinedTextField(
                            value = receiptNumber,
                            onValueChange = { receiptNumber = it },
                            label = { Text("Nro. de Recibo") },
                            leadingIcon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus().testTag("pos_ticket_receipt_input")
                        )
                    },
                    second = { mod ->
                        OutlinedTextField(
                            value = referenceNumber,
                            onValueChange = { referenceNumber = it },
                            label = { Text("Nro. de Referencia") },
                            leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus().testTag("pos_ticket_reference_input")
                        )
                    }
                )

                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        OutlinedTextField(
                            value = approvalNumber,
                            onValueChange = { approvalNumber = it },
                            label = { Text("Nro. de Aprobación") },
                            leadingIcon = { Icon(Icons.Filled.Verified, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus().testTag("pos_ticket_approval_input")
                        )
                    },
                    second = { mod ->
                        OutlinedTextField(
                            value = bankOrTerminal,
                            onValueChange = { bankOrTerminal = it },
                            label = { Text("Banco / Terminal") },
                            placeholder = { Text("Ej: Banesco • Term 01") },
                            leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus()
                        )
                    }
                )

                // Operation type chips + text
                Text("Tipo de Operación", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("COMPRA", "COMPRA DÉBITO", "COMPRA CRÉDITO", "ANULACIÓN", "DEVOLUCIÓN").forEach { op ->
                        FilterChip(
                            selected = operationType.equals(op, ignoreCase = true),
                            onClick = { operationType = op },
                            label = { Text(op, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = operationType,
                    onValueChange = { operationType = it },
                    label = { Text("Tipo de Operación (editable)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("pos_ticket_operation_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones / Lote (opcional)") },
                    maxLines = 2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                if (rawOcrText.isNotBlank()) {
                    TextButton(
                        onClick = { showRawOcr = !showRawOcr },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (showRawOcr) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showRawOcr) "Ocultar texto OCR" else "Ver/Editar texto OCR detectado")
                    }
                    AnimatedVisibility(visible = showRawOcr) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = rawOcrText,
                                onValueChange = { rawOcrText = it },
                                label = { Text("Texto OCR sin procesar") },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                            )
                            OutlinedButton(
                                onClick = {
                                    applyParsed(viewModel.reparsePosTicketText(rawOcrText))
                                    Toast.makeText(context, "Campos recalculados desde texto OCR", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Re-extraer campos")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Footer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val parsedAmount = PosTicketOcrParser.parseNumberToken(totalAmountText)
                            ?: totalAmountText.replace(",", ".").toDoubleOrNull()
                            ?: 0.0
                        if (parsedAmount <= 0.0 && receiptNumber.isBlank() && referenceNumber.isBlank()) {
                            Toast.makeText(
                                context,
                                "Ingresa al menos el monto total o un número de recibo/referencia.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (isEditing && initialRecord != null) {
                            viewModel.updatePosTerminalRecord(
                                initialRecord.copy(
                                    transactionDateTime = transactionDateTime.trim(),
                                    receiptNumber = receiptNumber.trim(),
                                    referenceNumber = referenceNumber.trim(),
                                    approvalNumber = approvalNumber.trim(),
                                    maskedCardNumber = maskedCardNumber.trim(),
                                    operationType = operationType.trim().ifBlank { "COMPRA" },
                                    totalAmount = maxOf(0.0, parsedAmount),
                                    currency = currency,
                                    transactionStatus = transactionStatus,
                                    bankOrTerminal = bankOrTerminal.trim(),
                                    notes = notes.trim(),
                                    rawOcrText = rawOcrText
                                )
                            ) {
                                Toast.makeText(context, "Registro actualizado", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        } else {
                            viewModel.addPosTerminalRecord(
                                transactionDateTime = transactionDateTime,
                                receiptNumber = receiptNumber,
                                referenceNumber = referenceNumber,
                                approvalNumber = approvalNumber,
                                maskedCardNumber = maskedCardNumber,
                                operationType = operationType,
                                totalAmount = parsedAmount,
                                currency = currency,
                                transactionStatus = transactionStatus,
                                bankOrTerminal = bankOrTerminal,
                                notes = notes,
                                rawOcrText = rawOcrText
                            ) {
                                Toast.makeText(context, "Ticket guardado en Puntos de Venta", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    },
                    enabled = !isScanning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pos_ticket_save_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEditing) "Actualizar" else "Guardar Ticket", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
