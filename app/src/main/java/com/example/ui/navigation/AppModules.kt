package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.local.User
import java.util.Locale

enum class MainModuleGroup(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    VENTAS(
        id = "VENTAS",
        title = "VENTAS",
        description = "Facturación, presupuestos, notas de entrega y facturas",
        icon = Icons.Filled.Storefront,
        accentColor = Color(0xFF4F46E5)
    ),
    ADMINISTRATIVO(
        id = "ADMINISTRATIVO",
        title = "ADMINISTRATIVO",
        description = "Inventario, retenciones, proveedores, clientes, puntos de venta y gastos",
        icon = Icons.Filled.BusinessCenter,
        accentColor = Color(0xFF0284C7)
    ),
    INFO_GENERAL(
        id = "INFO_GENERAL",
        title = "INFO GENERAL",
        description = "Cashea, dashboard y resumen operativo",
        icon = Icons.Filled.Assessment,
        accentColor = Color(0xFF059669)
    ),
    CONFIG(
        id = "CONFIG",
        title = "CONFIG",
        description = "Usuarios, ajustes del sistema y cierre de sesión",
        icon = Icons.Filled.Tune,
        accentColor = Color(0xFF7C3AED)
    )
}

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val group: MainModuleGroup? = null
) {
    object Home : Screen("home", "Inicio", Icons.Filled.Home, null)

    // VENTAS
    object Pos : Screen("pos", "Facturación", Icons.Filled.PointOfSale, MainModuleGroup.VENTAS)
    object Quotes : Screen("quotes", "Presupuestos", Icons.Filled.RequestQuote, MainModuleGroup.VENTAS)
    object DeliveryNotes : Screen("delivery_notes", "Notas de Entrega", Icons.Filled.Assignment, MainModuleGroup.VENTAS)
    object Invoices : Screen("invoices", "Facturas", Icons.Filled.Receipt, MainModuleGroup.VENTAS)

    // ADMINISTRATIVO
    object Inventory : Screen("inventory", "Inventario", Icons.Filled.Inventory, MainModuleGroup.ADMINISTRATIVO)
    object Withholdings : Screen("withholdings", "Retenciones", Icons.Filled.Policy, MainModuleGroup.ADMINISTRATIVO)
    object Suppliers : Screen("suppliers", "Proveedores", Icons.Filled.LocalShipping, MainModuleGroup.ADMINISTRATIVO)
    object Customers : Screen("customers", "Clientes", Icons.Filled.People, MainModuleGroup.ADMINISTRATIVO)
    object PosTerminals : Screen("pos_terminals", "Puntos de Venta", Icons.Filled.CreditScore, MainModuleGroup.ADMINISTRATIVO)
    object Expenses : Screen("expenses", "Gastos", Icons.Filled.ReceiptLong, MainModuleGroup.ADMINISTRATIVO)

    // INFO GENERAL
    object Cashea : Screen("cashea", "Cashea", Icons.Filled.QrCode2, MainModuleGroup.INFO_GENERAL)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.SpaceDashboard, MainModuleGroup.INFO_GENERAL)
    object Summary : Screen("summary", "Resumen", Icons.Filled.Analytics, MainModuleGroup.INFO_GENERAL)

    // CONFIG
    object Users : Screen("users", "Usuarios", Icons.Filled.Person, MainModuleGroup.CONFIG)
    object Settings : Screen("settings", "Ajustes", Icons.Filled.Settings, MainModuleGroup.CONFIG)

    companion object {
        val allSubModules: List<Screen> = listOf(
            // VENTAS
            Pos,
            Quotes,
            DeliveryNotes,
            Invoices,
            // ADMINISTRATIVO
            Inventory,
            Withholdings,
            Suppliers,
            Customers,
            PosTerminals,
            Expenses,
            // INFO GENERAL
            Cashea,
            Dashboard,
            Summary,
            // CONFIG
            Users,
            Settings
        )

        val allSubModuleRoutes: Set<String> = allSubModules.map { it.route }.toSet()

        fun subModulesForGroup(group: MainModuleGroup): List<Screen> =
            allSubModules.filter { it.group == group }
    }
}

object ModulePermissions {

    fun defaultRoutesForRole(role: String): Set<String> {
        val upper = role.uppercase(Locale.ROOT)
        return when {
            upper == "ADMIN" || upper == "ADMINISTRADOR" -> Screen.allSubModuleRoutes
            upper == "SUPERVISOR" -> Screen.allSubModuleRoutes - setOf(
                Screen.Dashboard.route,
                Screen.Users.route,
                Screen.Settings.route,
                Screen.Pos.route
            )
            upper == "CASHIER" || upper == "CAJERO" -> Screen.allSubModuleRoutes - setOf(
                Screen.Dashboard.route,
                Screen.Users.route,
                Screen.Settings.route,
                Screen.Suppliers.route
            )
            else -> emptySet()
        }
    }

    fun resolveAllowedRoutes(user: User): Set<String> {
        val raw = user.allowedModules.trim()
        if (raw.isEmpty()) {
            return defaultRoutesForRole(user.role)
        }
        if (raw.equals("NONE", ignoreCase = true)) {
            return emptySet()
        }
        if (raw == "*") {
            return Screen.allSubModuleRoutes
        }
        return raw.split(",")
            .map { it.trim() }
            .filter { it in Screen.allSubModuleRoutes }
            .toSet()
    }

    fun encodeAllowedRoutes(routes: Set<String>): String {
        val valid = Screen.allSubModules
            .map { it.route }
            .filter { it in routes }
        return if (valid.isEmpty()) "NONE" else valid.joinToString(",")
    }

    fun summarizeAccess(user: User): String {
        val allowed = resolveAllowedRoutes(user)
        val total = Screen.allSubModules.size
        return when {
            allowed.size == total -> "Acceso completo ($total/$total)"
            allowed.isEmpty() -> "Sin submódulos asignados (0/$total)"
            else -> "${allowed.size}/$total submódulos activos"
        }
    }
}
