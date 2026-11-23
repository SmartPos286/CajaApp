package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.User
import com.example.ui.components.AboutAppDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.theme.WinBackground
import com.example.ui.theme.WinSkyBackground
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val isCloudConnected by viewModel.isCloudConnected.collectAsStateWithLifecycle()

    var selectedUser by remember { mutableStateOf<User?>(null) }
    var isManualLogin by remember { mutableStateOf(false) }
    var manualUsername by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isDark = (appTheme == "DARK")
    val loginBackgroundColor = if (isDark) WinBackground else WinSkyBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(loginBackgroundColor)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                initialOffsetY = { it / 10 },
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                tonalElevation = 14.dp,
                shadowElevation = 28.dp
            ) {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    AnimatedContent(
                        targetState = when {
                            isManualLogin -> "MANUAL_LOGIN"
                            selectedUser != null -> "PIN_INPUT"
                            else -> "USER_SELECTION"
                        },
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                                initialOffsetX = { if (targetState != "USER_SELECTION") it / 4 else -it / 4 },
                                animationSpec = tween(300)
                            )).togetherWith(
                                fadeOut(animationSpec = tween(200))
                            )
                        },
                        label = "LoginMultiStepTransition"
                    ) { state ->
                        when (state) {
                            "USER_SELECTION" -> {
                                UserSelectionStep(
                                    viewModel = viewModel,
                                    users = users,
                                    isCloudConnected = isCloudConnected,
                                    onSelectUser = { user ->
                                        selectedUser = user
                                        pin = ""
                                        error = ""
                                    },
                                    onManualLoginClick = {
                                        isManualLogin = true
                                        manualUsername = ""
                                        pin = ""
                                        error = ""
                                    },
                                    onAboutClick = {
                                        showAboutDialog = true
                                    }
                                )
                            }
                            "PIN_INPUT" -> {
                                val user = selectedUser
                                if (user != null) {
                                    PinInputStep(
                                        user = user,
                                        pin = pin,
                                        error = error,
                                        onPinChange = { pin = it },
                                        onBackClick = {
                                            selectedUser = null
                                            pin = ""
                                            error = ""
                                        },
                                        onLoginClick = {
                                            scope.launch {
                                                val success = viewModel.login(user.username, pin)
                                                if (!success) {
                                                    error = "PIN o clave incorrecta"
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            "MANUAL_LOGIN" -> {
                                ManualLoginStep(
                                    username = manualUsername,
                                    pin = pin,
                                    error = error,
                                    onUsernameChange = { manualUsername = it },
                                    onPinChange = { pin = it },
                                    onBackClick = {
                                        isManualLogin = false
                                        pin = ""
                                        error = ""
                                    },
                                    onLoginClick = {
                                        scope.launch {
                                            if (manualUsername.isBlank() || pin.isBlank()) {
                                                error = "Ingrese usuario y clave"
                                                return@launch
                                            }
                                            val success = viewModel.login(manualUsername, pin)
                                            if (!success) {
                                                error = "Credenciales incorrectas"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutAppDialog(
            onDismissRequest = { showAboutDialog = false }
        )
    }
}

@Composable
private fun UserSelectionStep(
    viewModel: PosViewModel,
    users: List<User>,
    isCloudConnected: Boolean,
    onSelectUser: (User) -> Unit,
    onManualLoginClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ResponsiveActionRow(
            breakpoint = 380.dp,
            verticalSpacing = 8.dp,
            stackedActionAlignment = Alignment.End,
            content = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCloudConnected) Color(0xFF059669).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, if (isCloudConnected) Color(0xFF059669).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (isCloudConnected) Icons.Filled.CloudDone else Icons.Filled.Storage,
                            contentDescription = null,
                            tint = if (isCloudConnected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isCloudConnected) "Red Supabase Conectada" else "Modo Local (Offline)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCloudConnected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            action = {
                OutlinedButton(
                    onClick = onAboutClick,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Acerca de",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Acerca de", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF312E81)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.7f),
                                Color(0xFF6366F1).copy(alpha = 0.4f)
                            )
                        )
                    ),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pos_286_logo),
                contentDescription = "SmartPOS286 Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Seleccione su Usuario",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Elige tu perfil para ingresar al punto de venta",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (users.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(users) { currUser ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectUser(currUser) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currUser.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = currUser.avatarUrl,
                                        contentDescription = "Avatar de ${currUser.username}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = currUser.username.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = currUser.username.uppercase(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currUser.role,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val isGoogleAuthLoading by viewModel.isGoogleAuthLoading.collectAsStateWithLifecycle()
        val googleAuthEmail by viewModel.googleAuthUserEmail.collectAsStateWithLifecycle()
        val isGoogleSignedIn by viewModel.isGoogleSignedIn.collectAsStateWithLifecycle()

        Button(
            onClick = {
                viewModel.signInWithGoogle(context) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            enabled = !isGoogleAuthLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E293B)
            )
        ) {
            if (isGoogleAuthLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Conectando con Google...", color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.CloudSync, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isGoogleSignedIn) "Google Conectado • ${googleAuthEmail.orEmpty()}" else "Iniciar Sesión con Google (Nube en Vivo)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onManualLoginClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ingresar con Usuario y Clave (Manual)")
        }
    }
}

@Composable
private fun PinInputStep(
    user: User,
    pin: String,
    error: String,
    onPinChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.Start),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Elegir otro usuario",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
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
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = user.username.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Rol: ${user.role}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("PIN o Clave de acceso") },
            placeholder = { Text("Ingrese su PIN") },
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (pin.isNotBlank()) {
                        onLoginClick()
                    }
                }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoginClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ManualLoginStep(
    username: String,
    pin: String,
    error: String,
    onUsernameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.Start),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Volver a la lista",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Ingreso con Credenciales",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ingrese su usuario y clave para acceder al sistema",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Usuario") },
            placeholder = { Text("Nombre de usuario") },
            leadingIcon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("Contraseña / PIN") },
            placeholder = { Text("Clave de acceso") },
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onLoginClick()
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoginClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
