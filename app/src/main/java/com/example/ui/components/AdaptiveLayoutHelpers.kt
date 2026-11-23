package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hace que cualquier campo de texto u otro componente enfocable se desplace
 * automáticamente por encima del teclado suave (IME) cuando recibe foco.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocus(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(140L)
                    bringIntoViewRequester.bringIntoView()
                    delay(160L)
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
}

/**
 * Muestra [content] y [action] en una misma fila horizontal cuando el ancho disponible
 * es >= [breakpoint], o coloca [action] limpiamente debajo de [content] en pantallas
 * compactas, evitando que textos largos y botones se aplasten entre sí.
 */
@Composable
fun ResponsiveActionRow(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 440.dp,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 10.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    stackedActionAlignment: Alignment.Horizontal = Alignment.End,
    content: @Composable () -> Unit,
    action: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= breakpoint) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = verticalAlignment
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = horizontalSpacing)
                ) {
                    content()
                }
                Box {
                    action()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
                Box(modifier = Modifier.align(stackedActionAlignment)) {
                    action()
                }
            }
        }
    }
}

/**
 * Coloca dos elementos lado a lado en pantallas medianas/anchas (>= [breakpoint])
 * o apilados verticalmente en pantallas estrechas para evitar campos o tarjetas aplastadas.
 */
@Composable
fun ResponsivePair(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 400.dp,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    firstWeight: Float = 1f,
    secondWeight: Float = 1f,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= breakpoint) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                first(Modifier.weight(firstWeight))
                second(Modifier.weight(secondWeight))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Grupo de botones o chips adaptativos que envuelven automáticamente a una segunda línea
 * si el ancho de pantalla no alcanza, manteniendo espaciado uniforme y limpio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResponsiveFlowGroup(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing, horizontalAlignment),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        content()
    }
}

/**
 * Diálogo modal consciente del teclado suave (IME) y barras del sistema.
 * Se redimensiona dinámicamente para permanecer visible sobre el teclado al escribir.
 */
@Composable
fun KeyboardAwareDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 8.dp,
    border: BorderStroke? = null,
    dismissOnClickOutside: Boolean = true,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = dismissOnClickOutside,
                        onClick = onDismissRequest
                    )
            )
            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
                border = border,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(modifier)
            ) {
                content()
            }
        }
    }
}

/**
 * Reemplazo adaptativo para [androidx.compose.material3.AlertDialog] con desplazamiento
 * automático al abrir el teclado (IME) y botones inferiores que no se aplastan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardAwareAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 6.dp,
    maxWidth: Dp = 520.dp,
    dismissOnClickOutside: Boolean = true
) {
    KeyboardAwareDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        maxWidth = maxWidth,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        dismissOnClickOutside = dismissOnClickOutside
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        icon()
                    }
                }
            }
            if (title != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            title()
                        }
                    }
                }
            }
            if (text != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            text()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
