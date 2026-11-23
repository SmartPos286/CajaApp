package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PosViewModel

@Composable
fun ChangePasswordDialog(
    viewModel: PosViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val cloudUsername by viewModel.cloudUsername.collectAsStateWithLifecycle()
    val activeUser = currentUser?.username?.ifBlank { null }
        ?: cloudUsername.ifBlank { null }
        ?: viewModel.prefs.cloudUsername.ifBlank { null }
        ?: "Olivtech"

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val passwordsMatch = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword == confirmPassword
    val passwordsMismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword

    KeyboardAwareDialog(
        onDismissRequest = {
            if (!isLoading) onDismissRequest()
        },
        dismissOnClickOutside = !isLoading,
        maxWidth = 500.dp,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.testTag("change_password_dialog")
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.LockReset,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Cambiar Contraseña",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Plataforma Supabase y Local",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isLoading) onDismissRequest() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                // Active Account Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Cuenta activa: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activeUser,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Current Password Input
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = ""
                    },
                    label = { Text("Contraseña Actual") },
                    placeholder = { Text("Ingrese su clave actual") },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                            Icon(
                                if (isCurrentPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isCurrentPasswordVisible) "Ocultar" else "Mostrar"
                            )
                        }
                    },
                    visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .testTag("current_password_input")
                )

                // New Password Input
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = ""
                    },
                    label = { Text("Nueva Contraseña") },
                    placeholder = { Text("Mínimo 4 caracteres") },
                    leadingIcon = {
                        Icon(Icons.Filled.VpnKey, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                            Icon(
                                if (isNewPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isNewPasswordVisible) "Ocultar" else "Mostrar"
                            )
                        }
                    },
                    visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .testTag("new_password_input")
                )

                // Confirm Password Input
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = ""
                    },
                    label = { Text("Confirmar Nueva Contraseña") },
                    placeholder = { Text("Repita la nueva contraseña") },
                    leadingIcon = {
                        Icon(Icons.Filled.CheckCircleOutline, contentDescription = null)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (passwordsMatch) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Coinciden",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    if (isConfirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isConfirmPasswordVisible) "Ocultar" else "Mostrar"
                                )
                            }
                        }
                    },
                    visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    isError = passwordsMismatch,
                    supportingText = {
                        when {
                            passwordsMatch -> Text("✓ Las contraseñas coinciden", color = Color(0xFF059669), fontSize = 12.sp)
                            passwordsMismatch -> Text("⚠ La contraseña y la confirmación deben ser iguales", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            else -> Text("La confirmación debe coincidir exactamente con la nueva contraseña", fontSize = 12.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (currentPassword.isNotBlank() && passwordsMatch && !isLoading) {
                                isLoading = true
                                viewModel.changeCloudPassword(
                                    currentPass = currentPassword,
                                    newPass = newPassword,
                                    confirmPass = confirmPassword
                                ) { success, msg ->
                                    isLoading = false
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        onDismissRequest()
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .testTag("confirm_password_input")
                )

                // Error alert banner
                AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                ResponsivePair(
                    breakpoint = 380.dp,
                    horizontalSpacing = 10.dp,
                    verticalSpacing = 10.dp,
                    firstWeight = 1f,
                    secondWeight = 1.4f,
                    first = { mod ->
                        OutlinedButton(
                            onClick = onDismissRequest,
                            enabled = !isLoading,
                            modifier = mod.defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar")
                        }
                    },
                    second = { mod ->
                        Button(
                            onClick = {
                                if (currentPassword.isBlank()) {
                                    errorMessage = "Ingrese la contraseña actual"
                                    return@Button
                                }
                                if (newPassword.isBlank()) {
                                    errorMessage = "Ingrese la nueva contraseña"
                                    return@Button
                                }
                                if (newPassword != confirmPassword) {
                                    errorMessage = "La confirmación y la nueva contraseña deben coincidir exactamente"
                                    return@Button
                                }
                                if (newPassword.length < 4) {
                                    errorMessage = "La nueva contraseña debe tener al menos 4 caracteres"
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = ""
                                viewModel.changeCloudPassword(
                                    currentPass = currentPassword,
                                    newPass = newPassword,
                                    confirmPass = confirmPassword
                                ) { success, msg ->
                                    isLoading = false
                                    if (success) {
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        onDismissRequest()
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            },
                            enabled = !isLoading && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank() && passwordsMatch,
                            modifier = mod
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("submit_change_password_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardando...")
                            } else {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cambiar Contraseña")
                            }
                        }
                    }
                )
            }
        }
    }
