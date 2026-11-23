package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppDialog(
    onDismissRequest: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("General", "Módulos", "Roles", "Funcionamiento")

    KeyboardAwareDialog(
        onDismissRequest = onDismissRequest,
        maxWidth = 640.dp,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 10.dp,
        modifier = Modifier.fillMaxHeight(0.88f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
                // Header Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E1B4B),
                                    Color(0xFF312E81)
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pos_286_logo),
                                    contentDescription = "SmartPOS286",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Acerca de SmartPOS286",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Sistema de Facturación y Control Comercial",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    }
                }

                // Category Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                // Content Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    when (selectedTab) {
                        0 -> GeneralOverviewTab()
                        1 -> AppModulesTab()
                        2 -> RolesAndPermissionsTab()
                        3 -> HowItWorksTab()
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer
                ResponsiveActionRow(
                    breakpoint = 360.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    content = {
                        Text(
                            text = "SmartPOS286 v2.5 • Edición Comercial",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    action = {
                        Button(
                            onClick = onDismissRequest,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Entendido")
                        }
                    }
                )
            }
    }
}

@Composable
private fun GeneralOverviewTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hero Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.PointOfSale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "¿Qué es SmartPOS286?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SmartPOS286 es una plataforma integral de facturación con inteligencia artificial, diseñada para el control total de comercios, bodegones, restaurantes, farmacias y tiendas minoristas. Permite gestionar ventas multimoneda ($ USD y Bs.), inventario en tiempo real, arqueos de caja, clientes, proveedores y estadísticas de rentabilidad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = "Capacidades Clave Incluidas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        FeatureHighlightItem(
            icon = Icons.Filled.Psychology,
            title = "Asistente de Inteligencia Artificial",
            description = "Consulta existencias, reportes de ventas y estados del negocio hablando o escribiendo de forma natural con el sistema."
        )

        FeatureHighlightItem(
            icon = Icons.Filled.Lock,
            title = "Información Encriptada y Segura",
            description = "Toda la data del negocio, facturación y clientes está protegida con algoritmos de encriptación de grado bancario (AES-256)."
        )

        FeatureHighlightItem(
            icon = Icons.Filled.CurrencyExchange,
            title = "Punto de Venta Multimoneda ($ USD / Bs.)",
            description = "Calcula precios y cobros automáticamente en dólares y bolívares según la tasa de cambio oficial configurada (BCV o personalizada)."
        )

        FeatureHighlightItem(
            icon = Icons.Filled.ReceiptLong,
            title = "Software de Facturación e Impresión Térmica",
            description = "Generación de facturas electrónicas, tickets de venta para impresoras térmicas (Bluetooth / USB) y notas de entrega en formato PDF."
        )

        FeatureHighlightItem(
            icon = Icons.Filled.Inventory,
            title = "Inventario Inteligente (Recetas y Combos)",
            description = "Soporta productos unitarios, combos en oferta e insumos con descuento automático de ingredientes/recetas por venta realizada."
        )

        FeatureHighlightItem(
            icon = Icons.Filled.Analytics,
            title = "Dashboard Gerencial y Margen de Ganancia",
            description = "Mide los ingresos mensuales, margen de ganancia real (COGS), ranking de productos más vendidos y clientes más frecuentes."
        )

        FeatureHighlightItem(
            icon = Icons.Filled.CloudDone,
            title = "Sincronización Cloud y Modo 100% Offline",
            description = "Funciona perfectamente sin conexión a internet y sincroniza automáticamente las operaciones al conectarse a la nube de Google."
        )
    }
}

@Composable
private fun AppModulesTab() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Módulos Principales del Sistema",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ModuleDetailCard(
            title = "1. Punto de Venta (POS)",
            icon = Icons.Filled.ShoppingCart,
            badge = "Cobro Rápido",
            color = Color(0xFF2563EB),
            description = "Pantalla principal de ventas con búsqueda rápida por código de barras o teclado, carrito flotante, descuento porcentual, ventas retenidas (carritos en espera) y múltiples métodos de pago (Efectivo $, Pago Móvil, Punto de Venta / Tarjeta y Crédito)."
        )

        ModuleDetailCard(
            title = "2. Dashboard Gerencial",
            icon = Icons.Filled.Dashboard,
            badge = "Exclusivo Admin",
            color = Color(0xFF7C3AED),
            description = "Panel administrativo con resumen de ventas mensuales (1ro al último del mes), gráfico de crecimiento diario de ingresos, margen de ganancia neto, productos top vendidos y ranking de clientes frecuentes. Exportable a PDF."
        )

        ModuleDetailCard(
            title = "3. Inventario y Productos",
            icon = Icons.Filled.Category,
            badge = "Stock y Recetas",
            color = Color(0xFF059669),
            description = "Gestión de catálogo de productos. Admite fotos, variantes, costo en USD, precio de venta, alícuota de IVA, stock mínimo y creación de recetas/combos. Permite importar/exportar inventario en formato Excel / CSV."
        )

        ModuleDetailCard(
            title = "4. Histórico de Ventas y Facturas",
            icon = Icons.Filled.Receipt,
            badge = "Auditoría",
            color = Color(0xFFD97706),
            description = "Consulta detallada de todas las ventas procesadas. Permite reimprimir tickets, anular ventas con reversión automática de stock y descargar comprobantes en PDF."
        )

        ModuleDetailCard(
            title = "5. Cierre y Arqueo de Caja (Z/X)",
            icon = Icons.Filled.Calculate,
            badge = "Control Diario",
            color = Color(0xFFDC2626),
            description = "Cuadre diario de caja por turno o día. Desglosa los ingresos por tipo de pago, calcula discrepancias/sobrantes/faltantes y genera reportes de cierre e historial contable."
        )

        ModuleDetailCard(
            title = "6. Clientes y Cuentas por Cobrar (Fiado)",
            icon = Icons.Filled.People,
            badge = "Créditos",
            color = Color(0xFF0284C7),
            description = "Registro de clientes con RIF/Cédula, dirección y teléfono. Control de saldo deudor (fiado), límite de crédito y registro de abonos parciales a deuda."
        )

        ModuleDetailCard(
            title = "7. Proveedores y Compras de Mercancía",
            icon = Icons.Filled.LocalShipping,
            badge = "Abastecimiento",
            color = Color(0xFF4F46E5),
            description = "Registro de facturas de compra a proveedores. Actualiza automáticamente las existencias de mercancía en inventario y rastrea el dinero invertido."
        )

        ModuleDetailCard(
            title = "8. Gastos y Egresos Operativos",
            icon = Icons.Filled.MoneyOff,
            badge = "Egresos",
            color = Color(0xFFE11D48),
            description = "Registro de pagos de servicios, nómina o mantenimiento para restar de la utilidad bruta y obtener la ganancia líquida del negocio."
        )

        ModuleDetailCard(
            title = "9. Asistente Virtual IA",
            icon = Icons.Filled.Psychology,
            badge = "Inteligencia Artificial",
            color = Color(0xFF0891B2),
            description = "Asistente conversacional que responde dudas por voz o texto sobre existencias, precios, ventas del día y reportes del comercio."
        )

        ModuleDetailCard(
            title = "10. Ajustes y Configuración",
            icon = Icons.Filled.Settings,
            badge = "Configuración",
            color = Color(0xFF475569),
            description = "Configuración del nombre e identificación del negocio, tasa de cambio BCV, vincular impresoras térmicas Bluetooth/USB, gestión de usuarios y licencias."
        )
    }
}

@Composable
private fun RolesAndPermissionsTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Estructura de Roles y Permisos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "SmartPOS286 implementa control de acceso basado en roles para proteger la información confidencial y delimitar responsabilidades:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RoleCardItem(
            roleName = "Administrador (ADMIN)",
            icon = Icons.Filled.AdminPanelSettings,
            color = Color(0xFF7C3AED),
            permissions = listOf(
                "Acceso completo e ilimitado a todos los módulos del sistema.",
                "Visualización del Dashboard Gerencial y Margen de Ganancia.",
                "Configuración de Tasa BCV, Datos de Empresa e Impresora.",
                "Gestión de Usuarios, Asignación de Roles y PINes.",
                "Creación, Edición y Eliminación de Productos e Inventario.",
                "Aprobación de Anulaciones de Ventas y Cierre de Caja."
            )
        )

        RoleCardItem(
            roleName = "Cajero (CASHIER)",
            icon = Icons.Filled.PointOfSale,
            color = Color(0xFF2563EB),
            permissions = listOf(
                "Operación del Punto de Venta (POS) para cobro de mercancía.",
                "Retención y recuperación de carritos de compra.",
                "Consulta del catálogo de productos y precios.",
                "Realización del Cierre y Arqueo de Caja de su turno.",
                "Registro de clientes y abonos a cuentas por cobrar."
            )
        )

        RoleCardItem(
            roleName = "Mesero / Atendedor (WAITER)",
            icon = Icons.Filled.Restaurant,
            color = Color(0xFF059669),
            permissions = listOf(
                "Toma de pedidos y comandas en el Punto de Venta.",
                "Retención de ventas en espera asignadas a mesas/clientes.",
                "Consulta de existencias e insumos en inventario."
            )
        )

        RoleCardItem(
            roleName = "Gerente / Supervisor (MANAGER)",
            icon = Icons.Filled.SupervisorAccount,
            color = Color(0xFFD97706),
            permissions = listOf(
                "Supervisión del inventario y actualización de existencias.",
                "Consulta de facturas, clientes, proveedores y gastos.",
                "Verificación de cierres de caja del personal."
            )
        )
    }
}

@Composable
private fun HowItWorksTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Flujo de Trabajo Operativo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        WorkflowStepItem(
            stepNumber = "1",
            title = "Inicio de Sesión y Perfil",
            description = "El usuario selecciona su perfil (Administrador, Cajero, Mesero) e ingresa su PIN de seguridad de 4 dígitos."
        )

        WorkflowStepItem(
            stepNumber = "2",
            title = "Apertura de Turno y Tasa del Día",
            description = "Se verifica la tasa de cambio oficial ($ USD / Bs.) en Configuración para garantizar cobros exactos."
        )

        WorkflowStepItem(
            stepNumber = "3",
            title = "Procesamiento de la Venta (POS)",
            description = "Se escanean o seleccionan los productos. El sistema calcula montos en ambas monedas. Se selecciona el método de pago (Efectivo, Pago Móvil, Tarjeta o Crédito) y se presiona 'Procesar Pago'."
        )

        WorkflowStepItem(
            stepNumber = "4",
            title = "Descuento de Stock y Emisión de Ticket",
            description = "Instagráneamente se descuenta el stock de productos y/o ingredientes. Se imprime el ticket físico por Bluetooth/USB o se genera el comprobante digital PDF."
        )

        WorkflowStepItem(
            stepNumber = "5",
            title = "Cierre de Caja y Auditoría",
            description = "Al finalizar la jornada, se efectúa el Cierre de Caja para cotejar el dinero recaudado contra el sistema y generar la planilla oficial de cierre."
        )
    }
}

@Composable
private fun FeatureHighlightItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModuleDetailCard(
    title: String,
    icon: ImageVector,
    badge: String,
    color: Color,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ResponsiveActionRow(
                breakpoint = 360.dp,
                verticalSpacing = 6.dp,
                stackedActionAlignment = Alignment.Start,
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        }
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                },
                action = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoleCardItem(
    roleName: String,
    icon: ImageVector,
    color: Color,
    permissions: List<String>
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Text(roleName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = color)
            }

            Spacer(modifier = Modifier.height(10.dp))

            permissions.forEach { perm ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Text(
                        text = perm,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
