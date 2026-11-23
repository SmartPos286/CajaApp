package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.User
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: PosViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val win11Background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF1E1B4B),
            Color(0xFF090D16)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(win11Background),
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft glowing orbs (safe across all API levels)
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-120).dp, y = (-140).dp)
                .clip(CircleShape)
                .background(Color(0xFF6366F1).copy(alpha = 0.2f))
        )
        Box(
            modifier = Modifier
                .size(350.dp)
                .offset(x = 140.dp, y = 160.dp)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
        )

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                initialOffsetY = { it / 10 },
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                tonalElevation = 12.dp,
                shadowElevation = 24.dp
            ) {
                Box(modifier = Modifier.padding(36.dp)) {
                    AnimatedContent(
                        targetState = selectedUser,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(350)) + slideInHorizontally(
                                initialOffsetX = { if (targetState != null) it / 4 else -it / 4 },
                                animationSpec = tween(350)
                            )).togetherWith(
                                fadeOut(animationSpec = tween(250))
                            )
                        },
                        label = "LoginStepTransition"
                    ) { user ->
                        if (user == null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Bienvenido al POS",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Seleccione su perfil de usuario para continuar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(28.dp))

                                if (users.isEmpty()) {
                                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 360.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(users) { currUser ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedUser = currUser
                                                        pin = ""
                                                        error = ""
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                                tonalElevation = 4.dp
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(68.dp)
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
                                                    Spacer(modifier = Modifier.height(14.dp))
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
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { 
                                        selectedUser = null
                                        pin = ""
                                        error = ""
                                    }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Elegir otra cuenta", style = MaterialTheme.typography.bodyMedium)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
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

                                Spacer(modifier = Modifier.height(16.dp))

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

                                Spacer(modifier = Modifier.height(28.dp))

                                OutlinedTextField(
                                    value = pin,
                                    onValueChange = { pin = it },
                                    label = { Text("PIN de acceso") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (error.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = error, color = MaterialTheme.colorScheme.error)
                                }

                                Spacer(modifier = Modifier.height(28.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val success = viewModel.login(user.username, pin)
                                            if (!success) {
                                                error = "PIN incorrecto"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Text("Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
