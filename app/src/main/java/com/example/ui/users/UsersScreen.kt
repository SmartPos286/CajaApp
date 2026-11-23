package com.example.ui.users

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.User
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.navigation.MainModuleGroup
import com.example.ui.navigation.ModulePermissions
import com.example.ui.navigation.Screen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(viewModel: PosViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_user_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir Usuario")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Gestión de Usuarios y Cajeros",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    val moduleSummary = remember(user) { ModulePermissions.summarizeAccess(user) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_card_${user.id}"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = user.username.take(2).uppercase(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            user.username.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("Rol: ${user.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        selectedUser = user
                                        showEditDialog = true
                                    },
                                    modifier = Modifier.testTag("edit_user_button_${user.id}")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar Usuario")
                                }
                            }

                            // Allowed modules summary badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Widgets,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Módulos: $moduleSummary",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Commission status badge
                            val isSupervisor = user.role.equals("SUPERVISOR", ignoreCase = true)
                            val effectiveCanEarn = if (isSupervisor) false else user.canEarnCommission
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (effectiveCanEarn) Color(0xFF6366F1).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (effectiveCanEarn) Color(0xFF6366F1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        if (effectiveCanEarn) Icons.Filled.MonetizationOn else Icons.Filled.MoneyOff,
                                        contentDescription = null,
                                        tint = if (effectiveCanEarn) Color(0xFF6366F1) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isSupervisor) "Comisión de Ventas: No Autorizada (Supervisor no genera ventas)"
                                               else if (effectiveCanEarn) "Comisión de Ventas: Habilitada"
                                               else "Comisión de Ventas: No Autorizada",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (effectiveCanEarn) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && selectedUser != null) {
        val userToEdit = selectedUser!!
        var newPin by remember(userToEdit) { mutableStateOf("") }
        var newAvatarUrl by remember(userToEdit) { mutableStateOf(userToEdit.avatarUrl) }
        var selectedRole by remember(userToEdit) { mutableStateOf(userToEdit.role) }
        var canEarnCommission by remember(userToEdit, selectedRole) {
            mutableStateOf(if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) false else userToEdit.canEarnCommission)
        }
        var selectedRoutes by remember(userToEdit) {
            mutableStateOf(ModulePermissions.resolveAllowedRoutes(userToEdit))
        }
        val lockUsersForSelf = userToEdit.id == currentUser?.id &&
            selectedRole.equals("ADMIN", ignoreCase = true)
        var roleExpanded by remember { mutableStateOf(false) }
        val roles = listOf("ADMIN", "SUPERVISOR", "CASHIER")

        KeyboardAwareAlertDialog(
            onDismissRequest = { showEditDialog = false },
            maxWidth = 580.dp,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editar Usuario: ${userToEdit.username}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showEditDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("Nuevo PIN (Dejar en blanco para no cambiar)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )
                    OutlinedTextField(
                        value = newAvatarUrl,
                        onValueChange = { newAvatarUrl = it },
                        label = { Text("URL del Avatar / Logo de Empresa") },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )

                    ExposedDropdownMenuBox(
                        expanded = roleExpanded,
                        onExpandedChange = { roleExpanded = !roleExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedRole,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rol del Usuario") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = roleExpanded,
                            onDismissRequest = { roleExpanded = false }
                        ) {
                            roles.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r) },
                                    onClick = {
                                        selectedRole = r
                                        if (r.equals("SUPERVISOR", ignoreCase = true)) {
                                            canEarnCommission = false
                                        }
                                        roleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Commission Toggle / Info for Admin
                    if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    "El rol de Supervisor no genera ventas directamente en caja, por lo que no percibe comisiones por venta.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Ganar Comisiones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Permite a este cajero recibir comisiones por productos con comisión activa.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = canEarnCommission,
                                    onCheckedChange = { canEarnCommission = it }
                                )
                            }
                        }
                    }

                    UserModulesSelector(
                        selectedRoutes = if (lockUsersForSelf) selectedRoutes + Screen.Users.route else selectedRoutes,
                        roleForPreset = selectedRole,
                        lockUsersForSelf = lockUsersForSelf,
                        onSelectionChange = { updated ->
                            selectedRoutes = if (lockUsersForSelf) updated + Screen.Users.route else updated
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalAvatarUrl = if (newAvatarUrl.isNotBlank()) newAvatarUrl else userToEdit.avatarUrl
                        val finalCanEarn = if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) false else canEarnCommission
                        val finalRoutes = if (lockUsersForSelf) selectedRoutes + Screen.Users.route else selectedRoutes
                        val updatedUser = userToEdit.copy(
                            pin = if (newPin.isNotBlank()) newPin else userToEdit.pin,
                            avatarUrl = finalAvatarUrl,
                            role = selectedRole,
                            canEarnCommission = finalCanEarn,
                            allowedModules = ModulePermissions.encodeAllowedRoutes(finalRoutes)
                        )
                        viewModel.updateUser(updatedUser)
                        showEditDialog = false
                    },
                    modifier = Modifier.testTag("edit_user_save_button")
                ) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (users.size > 1 && userToEdit.id != currentUser?.id) {
                        TextButton(
                            onClick = {
                                viewModel.deleteUser(userToEdit)
                                showEditDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Eliminar")
                        }
                    }
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancelar") }
                }
            }
        )
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { username, pin, role, avatarUrl, canEarnCommission, selectedRoutes ->
                if (username.isNotBlank() && pin.isNotBlank()) {
                    val finalCanEarn = if (role.equals("SUPERVISOR", ignoreCase = true)) false else canEarnCommission
                    viewModel.insertUser(
                        User(
                            username = username.trim(),
                            pin = pin.trim(),
                            role = role,
                            avatarUrl = avatarUrl.trim(),
                            canEarnCommission = finalCanEarn,
                            allowedModules = ModulePermissions.encodeAllowedRoutes(selectedRoutes)
                        )
                    )
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        username: String,
        pin: String,
        role: String,
        avatarUrl: String,
        canEarnCommission: Boolean,
        allowedRoutes: Set<String>
    ) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("CASHIER") }
    var canEarnCommission by remember { mutableStateOf(true) }
    var selectedRoutes by remember { mutableStateOf(ModulePermissions.defaultRoutesForRole("CASHIER")) }
    var hasCustomizedModules by remember { mutableStateOf(false) }
    var roleExpanded by remember { mutableStateOf(false) }
    val roles = listOf("ADMIN", "SUPERVISOR", "CASHIER")

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        maxWidth = 580.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nuevo Usuario / Cajero", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de Usuario") },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN (Contraseña)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("URL del Avatar / Logo") },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = !roleExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedRole,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    selectedRole = role
                                    if (role.equals("SUPERVISOR", ignoreCase = true)) {
                                        canEarnCommission = false
                                    }
                                    if (!hasCustomizedModules) {
                                        selectedRoutes = ModulePermissions.defaultRoutesForRole(role)
                                    }
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                // Commission toggle / supervisor disclaimer
                if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                "El rol de Supervisor no genera ventas directamente en caja, por lo que no percibe comisiones por venta.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Ganar Comisiones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Autorizar a este usuario para generar comisiones de venta.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = canEarnCommission,
                                onCheckedChange = { canEarnCommission = it }
                            )
                        }
                    }
                }

                UserModulesSelector(
                    selectedRoutes = selectedRoutes,
                    roleForPreset = selectedRole,
                    lockUsersForSelf = false,
                    onSelectionChange = { updated ->
                        hasCustomizedModules = true
                        selectedRoutes = updated
                    }
                )
            }
        },
        confirmButton = {
            val finalCanEarn = if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) false else canEarnCommission
            TextButton(
                onClick = { onConfirm(username, pin, selectedRole, avatarUrl, finalCanEarn, selectedRoutes) },
                modifier = Modifier.testTag("add_user_save_button")
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun UserModulesSelector(
    selectedRoutes: Set<String>,
    roleForPreset: String,
    lockUsersForSelf: Boolean,
    onSelectionChange: (Set<String>) -> Unit
) {
    var expandedGroups by remember { mutableStateOf(MainModuleGroup.entries.map { it.id }.toSet()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Módulos Disponibles",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${selectedRoutes.size} de ${Screen.allSubModules.size} submódulos activos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick presets row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onSelectionChange(Screen.allSubModuleRoutes) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).testTag("modules_select_all_button")
                ) {
                    Text("Todos", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onSelectionChange(ModulePermissions.defaultRoutesForRole(roleForPreset)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).testTag("modules_preset_role_button")
                ) {
                    Text("Por Rol", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        onSelectionChange(
                            if (lockUsersForSelf) setOf(Screen.Users.route) else emptySet()
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).testTag("modules_clear_all_button")
                ) {
                    Text("Ninguno", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }

            MainModuleGroup.entries.forEach { group ->
                val groupScreens = Screen.subModulesForGroup(group)
                val groupRoutes = groupScreens.map { it.route }.toSet()
                val selectedInGroup = groupRoutes.count { it in selectedRoutes }
                val isGroupExpanded = group.id in expandedGroups

                val groupToggleState = when (selectedInGroup) {
                    0 -> ToggleableState.Off
                    groupRoutes.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, group.accentColor.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedGroups = if (isGroupExpanded) {
                                        expandedGroups - group.id
                                    } else {
                                        expandedGroups + group.id
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TriStateCheckbox(
                                state = groupToggleState,
                                onClick = {
                                    val next = if (groupToggleState == ToggleableState.On) {
                                        val removed = selectedRoutes - groupRoutes
                                        if (lockUsersForSelf && group == MainModuleGroup.CONFIG) {
                                            removed + Screen.Users.route
                                        } else {
                                            removed
                                        }
                                    } else {
                                        selectedRoutes + groupRoutes
                                    }
                                    onSelectionChange(next)
                                },
                                modifier = Modifier.testTag("module_group_checkbox_${group.id.lowercase(Locale.ROOT)}")
                            )
                            Icon(
                                imageVector = group.icon,
                                contentDescription = null,
                                tint = group.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$selectedInGroup/${groupScreens.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = group.accentColor
                            )
                            IconButton(
                                onClick = {
                                    expandedGroups = if (isGroupExpanded) {
                                        expandedGroups - group.id
                                    } else {
                                        expandedGroups + group.id
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGroupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (isGroupExpanded) "Ocultar submódulos" else "Mostrar submódulos",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = isGroupExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 10.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                groupScreens.forEach { screen ->
                                    val isLockedSelfUsers = lockUsersForSelf && screen == Screen.Users
                                    val checked = isLockedSelfUsers || (screen.route in selectedRoutes)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = !isLockedSelfUsers) {
                                                val next = if (checked) {
                                                    selectedRoutes - screen.route
                                                } else {
                                                    selectedRoutes + screen.route
                                                }
                                                onSelectionChange(next)
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            enabled = !isLockedSelfUsers,
                                            onCheckedChange = { isChecked ->
                                                if (!isLockedSelfUsers) {
                                                    val next = if (isChecked) {
                                                        selectedRoutes + screen.route
                                                    } else {
                                                        selectedRoutes - screen.route
                                                    }
                                                    onSelectionChange(next)
                                                }
                                            },
                                            modifier = Modifier.testTag("module_checkbox_${screen.route}")
                                        )
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = null,
                                            tint = if (checked) group.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isLockedSelfUsers) "${screen.title} (tu sesión)" else screen.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (group == MainModuleGroup.CONFIG) {
                                    Text(
                                        text = "• Salir siempre permanece disponible para cerrar sesión.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

