package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.utils.PdfExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reusable M3 dialog for exporting any generated PDF document:
 * - Share via system chooser (WhatsApp / Email / Drive)
 * - Save directly to device storage using zero-permission SAF CreateDocument
 * - Open with installed PDF viewer
 */
@Composable
fun PdfExportActionDialog(
    title: String,
    subtitle: String,
    suggestedFileName: String,
    generatePdf: () -> File?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(title, suggestedFileName) {
        isGenerating = true
        hasError = false
        val file = withContext(Dispatchers.IO) { generatePdf() }
        generatedFile = file
        isGenerating = false
        hasError = file == null
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = generatedFile
        if (uri != null && file != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PdfExportManager.copyFileToUri(context, file, uri)
                }
                Toast.makeText(
                    context,
                    if (ok) "PDF guardado exitosamente" else "Error al guardar el archivo PDF",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) onDismiss()
            }
        }
    }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        maxWidth = 420.dp,
        icon = {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_pdf_export_dialog")
                ) {
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
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isGenerating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Generando documento PDF...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (hasError || generatedFile == null) {
                    Text(
                        text = "No se pudo generar el archivo PDF. Intenta nuevamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val safeFile = generatedFile!!
                    Button(
                        onClick = {
                            PdfExportManager.sharePdfFile(context, safeFile, title)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_share_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir / Enviar PDF", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val cleanName = if (suggestedFileName.endsWith(".pdf", ignoreCase = true)) {
                                suggestedFileName
                            } else {
                                "$suggestedFileName.pdf"
                            }
                            saveLauncher.launch(cleanName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_save_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar PDF en Dispositivo", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = {
                            PdfExportManager.openPdfFile(context, safeFile)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_open_button")
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir / Vista Previa PDF", fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
