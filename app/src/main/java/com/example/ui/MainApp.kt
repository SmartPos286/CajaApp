package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.Product
import com.example.ui.assistant.AssistantChatDialog
import com.example.ui.cashea.CasheaScreen
import com.example.ui.customers.CustomersScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.delivery.DeliveryNotesScreen
import com.example.ui.expenses.ExpensesScreen
import com.example.ui.home.HomeModulesScreen
import com.example.ui.inventory.InventoryAlertsDialog
import com.example.ui.inventory.InventoryScreen
import com.example.ui.invoices.InvoicesScreen
import com.example.ui.navigation.MainModuleGroup
import com.example.ui.navigation.ModulePermissions
import com.example.ui.navigation.Screen
import com.example.ui.pos.PosScreen
import com.example.ui.theme.WinBackground
import com.example.ui.theme.WinSkyBackground
import com.example.ui.posterminal.PosTerminalRecordsScreen
import com.example.ui.quotes.QuotesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.summary.SummaryScreen
import com.example.ui.suppliers.SuppliersScreen
import com.example.ui.users.UsersScreen
import com.example.ui.withholding.WithholdingsScreen
import com.example.utils.StockAlertNotificationHelper
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: PosViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val backupMode by viewModel.backupMode.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()

    if (currentUser == null) {
        LoginScreen(viewModel)
        return
    }

    val context = LocalContext.current
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val lowStockAlertProducts by viewModel.lowStockAlertProducts.collectAsStateWithLifecycle()
    val systemNotifications by viewModel.systemNotifications.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    var showAssistantChat by remember { mutableStateOf(false) }
    var showStockAlertsDialog by remember { mutableStateOf(false) }
    var autoAlertBannerProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isSidebarVisible by remember { mutableStateOf(false) } // Default hidden to give maximum screen space
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var expandedSidebarGroups by remember {
        mutableStateOf(MainModuleGroup.entries.map { it.id }.toSet())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && lowStockAlertProducts.isNotEmpty()) {
            StockAlertNotificationHelper.notifyLowStockProducts(context, lowStockAlertProducts, allProducts)
        }
    }

    LaunchedEffect(currentUser?.id) {
        StockAlertNotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !StockAlertNotificationHelper.hasPostNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Automatic real-time observer: fires Android OS notification + in-app alert banner
    // whenever any product newly reaches or drops further below its defined minimum stock.
    val lastAlertedStockByProductId = remember(currentUser?.id) { mutableStateMapOf<Int, Double>() }
    LaunchedEffect(lowStockAlertProducts, allProducts) {
        val currentTriggeredIds = lowStockAlertProducts.map { it.id }.toSet()
        val staleIds = lastAlertedStockByProductId.keys.filter { it !in currentTriggeredIds }
        staleIds.forEach { lastAlertedStockByProductId.remove(it) }

        val newlyTriggered = mutableListOf<Product>()
        lowStockAlertProducts.forEach { product ->
            val eff = product.effectiveStockForAlert(allProducts)
            val prev = lastAlertedStockByProductId[product.id]
            if (prev == null || eff < prev - 1e-6) {
                newlyTriggered.add(product)
                lastAlertedStockByProductId[product.id] = eff
            }
        }

        if (newlyTriggered.isNotEmpty()) {
            StockAlertNotificationHelper.notifyLowStockProducts(context, newlyTriggered, allProducts)
            autoAlertBannerProducts = newlyTriggered
        }
    }

    LaunchedEffect(autoAlertBannerProducts) {
        if (autoAlertBannerProducts.isNotEmpty()) {
            delay(7500L)
            autoAlertBannerProducts = emptyList()
        }
    }

    val canUseAssistant = remember(currentUser?.role) {
        val r = currentUser?.role?.uppercase(Locale.ROOT).orEmpty()
        r == "ADMIN" || r == "ADMINISTRADOR" || r == "SUPERVISOR"
    }

    val storeName by viewModel.storeName.collectAsStateWithLifecycle()
    val cleanStoreName = storeName.ifBlank { "Mi Negocio" }

    val allowedRoutes = remember(currentUser) {
        ModulePermissions.resolveAllowedRoutes(currentUser!!)
    }

    val items = remember(allowedRoutes) {
        Screen.allSubModules.filter { it.route in allowedRoutes }
    }

    val startDest = Screen.Home.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isPosScreen = currentRoute == Screen.Pos.route

    val navigateToRoute: (String) -> Unit = { targetRoute ->
        if (targetRoute == Screen.Home.route || targetRoute in allowedRoutes) {
            isSidebarVisible = false
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Redirect back to Home if permissions changed and current route is no longer allowed
    LaunchedEffect(currentRoute, allowedRoutes) {
        if (currentRoute != null && currentRoute != Screen.Home.route && currentRoute !in allowedRoutes) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    val dynamicTitle = when (currentRoute) {
        Screen.Home.route -> "Inicio • \"$cleanStoreName\""
        Screen.Inventory.route -> "Inventario de \"$cleanStoreName\""
        Screen.Dashboard.route -> "Resumen gráfico de \"$cleanStoreName\""
        Screen.Invoices.route -> "Facturas de \"$cleanStoreName\""
        Screen.Cashea.route -> "Cashea de \"$cleanStoreName\""
        Screen.PosTerminals.route -> "Puntos de Venta de \"$cleanStoreName\""
        Screen.Customers.route -> "Clientes de \"$cleanStoreName\""
        Screen.Summary.route -> "Resumen diario de \"$cleanStoreName\""
        Screen.Suppliers.route -> "Proveedores de \"$cleanStoreName\""
        Screen.Expenses.route -> "Gastos de \"$cleanStoreName\""
        Screen.Withholdings.route -> "Retenciones de \"$cleanStoreName\""
        Screen.Users.route -> "Usuarios de \"$cleanStoreName\""
        Screen.Settings.route -> "Ajustes de \"$cleanStoreName\""
        Screen.Pos.route -> "SmartPOS286 de \"$cleanStoreName\""
        Screen.Quotes.route -> "Presupuestos de \"$cleanStoreName\""
        Screen.DeliveryNotes.route -> "Notas de Entrega de \"$cleanStoreName\""
        else -> items.find { it.route == currentRoute }?.title ?: "SmartPOS286"
    }

    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isDark = (appTheme == "DARK")

    // Clean flat background: slightly lighter blue for dark theme, sky blue for light theme
    val appBackgroundColor = if (isDark) WinBackground else WinSkyBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundColor)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Fully Hidable & Collapsible Hierarchical Navigation Rail
            AnimatedVisibility(
                visible = isSidebarVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                NavigationRail(
                    modifier = Modifier
                        .widthIn(min = if (isSidebarExpanded) 232.dp else 74.dp, max = if (isSidebarExpanded) 244.dp else 78.dp)
                        .fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    header = {
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 10.dp)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Close / Expand Buttons Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { isSidebarExpanded = !isSidebarExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isSidebarExpanded) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                        contentDescription = if (isSidebarExpanded) "Plegar menú" else "Desplegar menú",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { isSidebarVisible = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Ocultar menú lateral",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // SmartPOS286 Logo Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
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
                                                1.dp,
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF38BDF8).copy(alpha = 0.6f),
                                                        Color(0xFF6366F1).copy(alpha = 0.3f)
                                                    )
                                                )
                                            ),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pos_286_logo),
                                        contentDescription = "SmartPOS286 Logo",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                if (isSidebarExpanded) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "SmartPOS286",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${currentUser!!.username} • ${currentUser!!.role}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = if (isSidebarExpanded) 10.dp else 4.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = if (isSidebarExpanded) Alignment.Start else Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSidebarExpanded) {
                            // Top Home button in expanded drawer
                            val homeSelected = currentRoute == Screen.Home.route
                            Surface(
                                onClick = { navigateToRoute(Screen.Home.route) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (homeSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("sidebar_item_home")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Screen.Home.icon,
                                        contentDescription = Screen.Home.title,
                                        tint = if (homeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = Screen.Home.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (homeSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (homeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Hierarchical Main Modules & Submodules
                            MainModuleGroup.entries.forEach { group ->
                                val groupSubModules = Screen.subModulesForGroup(group).filter { it.route in allowedRoutes }
                                val hasLogout = group == MainModuleGroup.CONFIG
                                if (groupSubModules.isNotEmpty() || hasLogout) {
                                    val isGroupExpanded = group.id in expandedSidebarGroups
                                    val containsSelectedRoute = groupSubModules.any { it.route == currentRoute }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (containsSelectedRoute) {
                                            group.accentColor.copy(alpha = 0.1f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                expandedSidebarGroups = if (isGroupExpanded) {
                                                    expandedSidebarGroups - group.id
                                                } else {
                                                    expandedSidebarGroups + group.id
                                                }
                                            }
                                            .testTag("sidebar_group_${group.id.lowercase(Locale.ROOT)}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = group.icon,
                                                contentDescription = group.title,
                                                tint = group.accentColor,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = group.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (containsSelectedRoute) group.accentColor else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (isGroupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = if (isGroupExpanded) "Plegar ${group.title}" else "Desplegar ${group.title}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isGroupExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 10.dp, top = 2.dp, bottom = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            groupSubModules.forEach { screen ->
                                                val selected = currentRoute == screen.route
                                                Surface(
                                                    onClick = { navigateToRoute(screen.route) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (selected) {
                                                        group.accentColor.copy(alpha = 0.16f)
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("sidebar_item_${screen.route}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = screen.icon,
                                                            contentDescription = screen.title,
                                                            tint = if (selected) group.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = screen.title,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (selected) group.accentColor else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            if (hasLogout) {
                                                Surface(
                                                    onClick = { viewModel.logout() },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("sidebar_item_logout")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                                            contentDescription = "Salir",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = "Salir",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.error,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Collapsed compact rail mode (icons grouped cleanly)
                            NavigationRailItem(
                                selected = currentRoute == Screen.Home.route,
                                onClick = { navigateToRoute(Screen.Home.route) },
                                icon = { Icon(Screen.Home.icon, contentDescription = Screen.Home.title) },
                                label = { Text("Inicio", fontSize = 10.sp, maxLines = 1) },
                                alwaysShowLabel = currentRoute == Screen.Home.route
                            )

                            MainModuleGroup.entries.forEach { group ->
                                val groupSubModules = Screen.subModulesForGroup(group).filter { it.route in allowedRoutes }
                                val hasLogout = group == MainModuleGroup.CONFIG
                                if (groupSubModules.isNotEmpty() || hasLogout) {
                                    HorizontalDivider(
                                        color = group.accentColor.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                    groupSubModules.forEach { screen ->
                                        val selected = currentRoute == screen.route
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { navigateToRoute(screen.route) },
                                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                                            label = {
                                                Text(
                                                    screen.title,
                                                    maxLines = 1,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            alwaysShowLabel = selected,
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = group.accentColor,
                                                selectedTextColor = group.accentColor,
                                                indicatorColor = group.accentColor.copy(alpha = 0.16f),
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                    if (hasLogout) {
                                        NavigationRailItem(
                                            selected = false,
                                            onClick = { viewModel.logout() },
                                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir") },
                                            label = { Text("Salir", fontSize = 10.sp) },
                                            alwaysShowLabel = false,
                                            colors = NavigationRailItemDefaults.colors(
                                                unselectedIconColor = MaterialTheme.colorScheme.error,
                                                unselectedTextColor = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Main Content Area
            Scaffold(
                containerColor = Color.Transparent, // Let the background show through
                topBar = {
                    if (!isPosScreen) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            navigationIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { isSidebarVisible = !isSidebarVisible },
                                        modifier = Modifier.testTag("topbar_menu_button")
                                    ) {
                                        Icon(
                                            if (isSidebarVisible) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                            contentDescription = "Alternar menú principal",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (currentRoute != Screen.Home.route) {
                                        IconButton(
                                            onClick = { navigateToRoute(Screen.Home.route) },
                                            modifier = Modifier.testTag("topbar_home_button")
                                        ) {
                                            Icon(
                                                Icons.Filled.Home,
                                                contentDescription = "Ir a módulos principales",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            },
                            title = {
                                Text(
                                    text = dynamicTitle,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                val nonStockAlertsCount = systemNotifications.count { it.type != NotificationType.LOW_STOCK }
                                val totalAlertBadge = lowStockAlertProducts.size + nonStockAlertsCount
                                Box(modifier = Modifier.padding(end = 4.dp)) {
                                    IconButton(
                                        onClick = { showStockAlertsDialog = true },
                                        modifier = Modifier.testTag("topbar_notifications_button")
                                    ) {
                                        Icon(
                                            imageVector = if (lowStockAlertProducts.isNotEmpty()) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                            contentDescription = "Alertas de stock e inventario",
                                            tint = if (lowStockAlertProducts.isNotEmpty()) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (totalAlertBadge > 0) {
                                        Badge(
                                            containerColor = if (lowStockAlertProducts.isNotEmpty()) Color(0xFFDC2626) else Color(0xFFD97706),
                                            contentColor = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 4.dp, end = 4.dp)
                                        ) {
                                            Text(if (totalAlertBadge > 99) "99+" else totalAlertBadge.toString())
                                        }
                                    }
                                }

                                // Backup Mode status pill
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (backupMode == "GOOGLE_CLOUD")
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            if (backupMode == "GOOGLE_CLOUD") Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                            contentDescription = null,
                                            tint = if (backupMode == "GOOGLE_CLOUD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (backupMode == "GOOGLE_CLOUD") "Nube Google" else "Offline",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    modifier = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(
                            end = if (isPosScreen) 0.dp else 12.dp,
                            bottom = if (isPosScreen) 0.dp else 12.dp
                        )
                        .imePadding() // Adjust layout dynamically when software keyboard opens
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                ) {
                    composable(Screen.Home.route) {
                        HomeModulesScreen(
                            storeName = cleanStoreName,
                            currentUser = currentUser!!,
                            bcvRate = bcvRate,
                            allowedRoutes = allowedRoutes,
                            onNavigateToSubModule = { route -> navigateToRoute(route) },
                            onLogout = { viewModel.logout() }
                        )
                    }
                    composable(Screen.Pos.route) {
                        PosScreen(
                            viewModel = viewModel,
                            isSidebarVisible = isSidebarVisible,
                            onToggleMenu = { isSidebarVisible = !isSidebarVisible },
                            onNavigateHome = { navigateToRoute(Screen.Home.route) }
                        )
                    }
                    composable(Screen.Quotes.route) {
                        QuotesScreen(
                            viewModel = viewModel,
                            onNavigateToPos = {
                                if (Screen.Pos.route in allowedRoutes) {
                                    navigateToRoute(Screen.Pos.route)
                                }
                            }
                        )
                    }
                    composable(Screen.DeliveryNotes.route) {
                        DeliveryNotesScreen(
                            viewModel = viewModel,
                            onNavigateToPos = {
                                if (Screen.Pos.route in allowedRoutes) {
                                    navigateToRoute(Screen.Pos.route)
                                }
                            }
                        )
                    }
                    composable(Screen.Dashboard.route) { DashboardScreen(viewModel) }
                    composable(Screen.Inventory.route) { InventoryScreen(viewModel) }
                    composable(Screen.Invoices.route) { InvoicesScreen(viewModel) }
                    composable(Screen.Cashea.route) { CasheaScreen(viewModel) }
                    composable(Screen.PosTerminals.route) { PosTerminalRecordsScreen(viewModel) }
                    composable(Screen.Expenses.route) { ExpensesScreen(viewModel) }
                    composable(Screen.Withholdings.route) { WithholdingsScreen(viewModel) }
                    composable(Screen.Summary.route) { SummaryScreen(viewModel) }
                    composable(Screen.Customers.route) { CustomersScreen(viewModel) }
                    composable(Screen.Suppliers.route) { SuppliersScreen(viewModel) }
                    composable(Screen.Users.route) { UsersScreen(viewModel) }
                    composable(Screen.Settings.route) { SettingsScreen(viewModel) }
                }
            }
        }

        // Automatic floating in-app alert banner for real-time low-stock triggers
        AnimatedVisibility(
            visible = autoAlertBannerProducts.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val firstProduct = autoAlertBannerProducts.firstOrNull()
            if (firstProduct != null) {
                val eff = firstProduct.effectiveStockForAlert(allProducts).coerceAtLeast(0.0)
                val summaryText = if (autoAlertBannerProducts.size == 1) {
                    "${firstProduct.name}: ${StockAlertNotificationHelper.formatStockQuantity(eff)} u. (Mín: ${StockAlertNotificationHelper.formatStockQuantity(firstProduct.minStockAlertQuantity)})"
                } else {
                    "${firstProduct.name} y ${autoAlertBannerProducts.size - 1} producto(s) más alcanzaron su mínimo"
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .testTag("auto_low_stock_floating_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alerta de Stock Mínimo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                autoAlertBannerProducts = emptyList()
                                showStockAlertsDialog = true
                            }
                        ) {
                            Text("Ver", fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { autoAlertBannerProducts = emptyList() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Ocultar alerta", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Draggable floating assistant chat bubble positioned comfortably above interface buttons
        DraggableAssistantBubble(
            canUseAssistant = canUseAssistant,
            onOpenChat = { showAssistantChat = true }
        )
    }

    if (showStockAlertsDialog) {
        val roleUpper = currentUser?.role?.uppercase(Locale.ROOT).orEmpty()
        val canManageAlerts = (roleUpper == "ADMIN" || roleUpper == "ADMINISTRADOR" || roleUpper == "SUPERVISOR") &&
            (Screen.Inventory.route in allowedRoutes)
        InventoryAlertsDialog(
            lowStockProducts = lowStockAlertProducts,
            allProducts = allProducts,
            systemNotifications = systemNotifications,
            canManageAlerts = canManageAlerts,
            onDisableProductAlert = { p ->
                viewModel.setProductStockAlert(p, enabled = false)
            },
            onGoToInventory = if (Screen.Inventory.route in allowedRoutes && currentRoute != Screen.Inventory.route) {
                { navigateToRoute(Screen.Inventory.route) }
            } else null,
            onDismiss = { showStockAlertsDialog = false }
        )
    }

    if (showAssistantChat) {
        AssistantChatDialog(
            viewModel = viewModel,
            onNavigateToRoute = { route -> navigateToRoute(route) },
            onDismiss = { showAssistantChat = false }
        )
    }
}

@Composable
fun DraggableAssistantBubble(
    canUseAssistant: Boolean,
    onOpenChat: () -> Unit
) {
    if (!canUseAssistant) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bubbleSizeDp = 56.dp
        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }

        val maxPxX = (constraints.maxWidth - bubbleSizePx - with(density) { 16.dp.toPx() }).coerceAtLeast(0f)
        val maxPxY = (constraints.maxHeight - bubbleSizePx - with(density) { 60.dp.toPx() }).coerceAtLeast(0f)
        val minPxX = with(density) { 16.dp.toPx() }
        val minPxY = with(density) { 64.dp.toPx() } // Avoid overlapping TopAppBar completely

        // Default position elevated above standard action buttons (e.g. ~240dp from top or 35% height)
        val defaultX = maxPxX
        val defaultY = with(density) { 240.dp.toPx() }.coerceIn(minPxY, maxPxY)

        var offsetX by remember { mutableFloatStateOf(defaultX) }
        var offsetY by remember { mutableFloatStateOf(defaultY) }

        // Keep inside bounds if screen rotates or changes size
        LaunchedEffect(maxPxX, maxPxY) {
            if (offsetX > maxPxX) offsetX = maxPxX
            if (offsetY > maxPxY) offsetY = maxPxY
            if (offsetX < minPxX) offsetX = minPxX
            if (offsetY < minPxY) offsetY = minPxY
        }

        Surface(
            shape = CircleShape,
            color = Color(0xFF4F46E5),
            contentColor = Color.White,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(bubbleSizeDp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isDrag = false
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.isConsumed) break
                            val dragDistance = (change.position - down.position).getDistance()
                            if (!isDrag && dragDistance > 8f) {
                                isDrag = true
                            }
                            if (isDrag) {
                                change.consume()
                                val delta = change.position - change.previousPosition
                                offsetX = (offsetX + delta.x).coerceIn(minPxX, maxPxX)
                                offsetY = (offsetY + delta.y).coerceIn(minPxY, maxPxY)
                            }
                            if (!change.pressed) {
                                if (!isDrag) {
                                    onOpenChat()
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "Asistente Conversacional (Arrastrar para mover)",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

