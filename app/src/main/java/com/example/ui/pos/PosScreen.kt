package com.example.ui.pos

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.data.local.Customer
import com.example.data.local.Product
import com.example.ui.CartItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.ui.PosViewModel
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.HeldSale
import com.example.ui.components.DeliveryNoteDialog
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.LicenseDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.TicketDialog
import com.example.ui.components.bringIntoViewOnFocus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel,
    isSidebarVisible: Boolean = false,
    onToggleMenu: (() -> Unit)? = null,
    onNavigateHome: (() -> Unit)? = null
) {
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val heldSales by viewModel.allHeldSales.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val posPrefillCustomer by viewModel.posPrefillCustomer.collectAsStateWithLifecycle()

    val isLicensed by viewModel.isLicensed.collectAsStateWithLifecycle()
    val isUnlimitedInvoices by viewModel.isUnlimitedDailyInvoices.collectAsStateWithLifecycle()
    val customDailyInvoiceLimit by viewModel.customDailyInvoiceLimit.collectAsStateWithLifecycle()
    val todaySalesCount = remember(sales) { viewModel.getTodaySalesCount() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showCheckout by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showLimitInvoiceDialog by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showEditRateDialog by remember { mutableStateOf(false) }
    var showHoldCartDialog by remember { mutableStateOf(false) }
    var showHeldSalesListDialog by remember { mutableStateOf(false) }
    var showCreateDeliveryNoteDialog by remember { mutableStateOf(false) }
    var processedSale by remember { mutableStateOf<Pair<Sale, List<SaleItem>>?>(null) }
    var completedDeliveryNote by remember { mutableStateOf<Pair<DeliveryNote, List<DeliveryNoteItem>>?>(null) }
    var customerName by remember { mutableStateOf("") }
    var customerDoc by remember { mutableStateOf("") }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var selectedCategory by remember { mutableStateOf("Todas") }

    val availableCategories = remember(products) {
        val cats = products
            .map { it.category.trim().ifBlank { "General" } }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        listOf("Todas") + cats
    }

    LaunchedEffect(availableCategories) {
        if (selectedCategory !in availableCategories) {
            selectedCategory = "Todas"
        }
    }

    LaunchedEffect(posPrefillCustomer) {
        posPrefillCustomer?.let { (name, doc) ->
            customerName = name
            customerDoc = doc
            viewModel.consumePosPrefillCustomer()
        }
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { product ->
            val productCategory = product.category.trim().ifBlank { "General" }
            val matchesCategory = selectedCategory == "Todas" || productCategory.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.code.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val filteredCustomers = customers.filter {
        it.documentId.contains(customerDoc, ignoreCase = true) ||
        it.name.contains(customerDoc, ignoreCase = true)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Column: Catalog, Customer, Search, Rate
                Card(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        BcvRateAndLicenseBar(
                            bcvRate = bcvRate,
                            heldSalesCount = heldSales.size,
                            isSidebarVisible = isSidebarVisible,
                            onToggleMenu = onToggleMenu,
                            onNavigateHome = onNavigateHome,
                            onHeldSalesClick = { showHeldSalesListDialog = true },
                            onEditRateClick = { showEditRateDialog = true },
                            onRefreshRateClick = { viewModel.refreshRate() }
                        )

                        if (voiceStatus.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    voiceStatus,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        CustomerSelectorCard(
                            customerDoc = customerDoc,
                            customerName = customerName,
                            onCustomerSelected = { name, doc ->
                                customerName = name
                                customerDoc = doc
                            },
                            filteredCustomers = filteredCustomers,
                            isCustomerDropdownExpanded = isCustomerDropdownExpanded,
                            onExpandedChange = { isCustomerDropdownExpanded = it },
                            onAddNewCustomer = { showAddCustomerDialog = true }
                        )

                        CompactPosSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .testTag("pos_search_input")
                        )

                        CategoryFilterChipsRow(
                            categories = availableCategories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Catálogo (${filteredProducts.size}):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Toca para añadir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (filteredProducts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay productos coincidentes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 130.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(filteredProducts) { product ->
                                    ProductQuickGridItem(
                                        product = product,
                                        bcvRate = bcvRate,
                                        onAddToCart = { viewModel.addToCart(product) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Column: Cart items & Checkout
                Card(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    "Ticket (${cart.sumOf { it.quantity }}):",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (cart.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearCart() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }

                        if (cart.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.ShoppingCart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Ticket vacío", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Toca un producto para facturar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(cart) { item ->
                                    CartItemRow(item = item, bcvRate = bcvRate, viewModel = viewModel, onEditClick = { editingCartItem = item })
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        TotalsAndCheckoutSection(
                            cart = cart,
                            bcvRate = bcvRate,
                            canProcessInvoices = viewModel.canProcessMoreInvoicesToday(),
                            onLimitReached = { showLimitInvoiceDialog = true },
                            onCheckoutClick = { showCheckout = true },
                            onHoldCartClick = { showHoldCartDialog = true },
                            onDeliveryNoteClick = { showCreateDeliveryNoteDialog = true }
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Compact BCV Exchange Rate Header with Navigation Menu Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onToggleMenu != null) {
                        IconButton(
                            onClick = onToggleMenu,
                            modifier = Modifier
                                .size(40.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("pos_menu_button")
                        ) {
                            Icon(
                                if (isSidebarVisible) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                contentDescription = "Alternar menú principal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (onNavigateHome != null) {
                        IconButton(
                            onClick = onNavigateHome,
                            modifier = Modifier
                                .size(40.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("pos_home_button")
                        ) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = "Ir a módulos principales",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable { showEditRateDialog = true }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.CurrencyExchange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Tasa BCV: Bs. ${String.format("%.2f", bcvRate)}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar tasa",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (heldSales.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { showHeldSalesListDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.PauseCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "En Espera (${heldSales.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.refreshRate() },
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Actualizar Tasa",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (voiceStatus.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Text(
                        voiceStatus,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Compact Customer Selector & Icon-only Add Customer Button (collapses when selected)
            CustomerSelectorCard(
                customerDoc = customerDoc,
                customerName = customerName,
                onCustomerSelected = { name, doc ->
                    customerName = name
                    customerDoc = doc
                },
                filteredCustomers = filteredCustomers,
                isCustomerDropdownExpanded = isCustomerDropdownExpanded,
                onExpandedChange = { isCustomerDropdownExpanded = it },
                onAddNewCustomer = { showAddCustomerDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Slim Product Search / Scanner Bar
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
            ) {
                CompactPosSearchField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        isDropdownExpanded = true
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth()
                        .testTag("pos_portrait_search_input")
                )
                if ((searchQuery.isNotEmpty() || selectedCategory != "Todas") && filteredProducts.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        filteredProducts.forEach { product ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(product.name, fontWeight = FontWeight.Bold)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    if (product.isRecipe) "Código: ${product.code} • Receta (Ilimitado)" else "Código: ${product.code} • Stock: ${product.stock}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (product.isTaxExempt) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = if (product.isTaxExempt) "Exento" else "IVA 16%",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (product.isTaxExempt) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text("\$${String.format("%.2f", product.priceUsd)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    viewModel.addToCart(product)
                                    searchQuery = ""
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Category Filter Chips (Portrait)
            CategoryFilterChipsRow(
                categories = availableCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Quick filtered product strip when a category is selected in Portrait
            if (selectedCategory != "Todas") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Categoría: $selectedCategory (${filteredProducts.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { selectedCategory = "Todas" },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Text("Ver todas", fontSize = 12.sp)
                        }
                    }
                    if (filteredProducts.isEmpty()) {
                        Text(
                            "No hay productos en esta categoría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(filteredProducts) { product ->
                                Box(modifier = Modifier.width(156.dp)) {
                                    ProductQuickGridItem(
                                        product = product,
                                        bcvRate = bcvRate,
                                        onAddToCart = { viewModel.addToCart(product) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cart Items Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Productos en Carrito (${cart.sumOf { it.quantity }}):",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (cart.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearCart() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            // Cart Items List
            if (cart.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "El carrito está vacío",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Selecciona o busca productos arriba para facturar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cart) { item ->
                        CartItemRow(
                            item = item,
                            bcvRate = bcvRate,
                            viewModel = viewModel,
                            onEditClick = { editingCartItem = item }
                        )
                    }
                }
            }

            // Balanced, sleek bottom billing bar (No oversized dollar amounts)
            if (cart.isNotEmpty()) {
                val taxableItems = cart.filter { !it.product.isTaxExempt }
                val exemptItems = cart.filter { it.product.isTaxExempt }

                val taxableTotalUsd = taxableItems.sumOf { it.effectivePrice * it.quantity }
                val baseTaxableUsd = taxableTotalUsd / 1.16
                val ivaUsd = taxableTotalUsd - baseTaxableUsd
                val baseExemptUsd = exemptItems.sumOf { it.effectivePrice * it.quantity }

                val totalUsd = taxableTotalUsd + baseExemptUsd
                val totalBs = totalUsd * bcvRate

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Breakdown row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Base Gravable: \$${String.format("%.2f", baseTaxableUsd)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (baseExemptUsd > 0.001) {
                                Text(
                                    "Exento: \$${String.format("%.2f", baseExemptUsd)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                "IVA (16%): \$${String.format("%.2f", ivaUsd)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Tasa: Bs. ${String.format("%.2f", bcvRate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        // Clean, balanced Total (Comfortable 16.sp font, elegant layout)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Total a Facturar:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "\$${String.format("%.2f", totalUsd)} USD",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "• Bs. ${String.format("%.2f", totalBs)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (!viewModel.canProcessMoreInvoicesToday()) {
                                    showLimitInvoiceDialog = true
                                } else {
                                    showCheckout = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cobrar / Facturar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showHoldCartDialog = true },
                                enabled = cart.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("En Espera", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { showCreateDeliveryNoteDialog = true },
                                enabled = cart.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = if (cart.isNotEmpty()) 0.6f else 0.2f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nota Entrega", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

    if (showHoldCartDialog) {
        HoldCartDialog(
            cart = cart,
            customerName = customerName,
            customerDocument = customerDoc,
            onDismiss = { showHoldCartDialog = false },
            onConfirm = { note ->
                viewModel.holdCurrentCart(
                    note = note,
                    customerName = customerName,
                    customerDocument = customerDoc,
                    onSuccess = {
                        showHoldCartDialog = false
                        Toast.makeText(context, "Factura guardada en espera", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showHeldSalesListDialog) {
        HeldSalesListDialog(
            viewModel = viewModel,
            onDismiss = { showHeldSalesListDialog = false },
            onRestore = { heldSale ->
                viewModel.restoreHeldCart(heldSale.id) {
                    customerName = heldSale.customerName
                    customerDoc = heldSale.customerDocument
                    Toast.makeText(context, "Factura cargada al carrito", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showCreateDeliveryNoteDialog) {
        CreateDeliveryNoteDialog(
            cart = cart,
            bcvRate = bcvRate,
            customerName = customerName,
            customerDocument = customerDoc,
            onDismiss = { showCreateDeliveryNoteDialog = false },
            onConfirm = { cName, cDoc, recip, reason, displayCurrency ->
                viewModel.processDeliveryNote(
                    customerName = cName,
                    customerDocument = cDoc,
                    recipientName = recip,
                    reasonOrObservation = reason,
                    displayCurrency = displayCurrency,
                    onSuccess = { createdNote ->
                        showCreateDeliveryNoteDialog = false
                        coroutineScope.launch {
                            val noteItems = viewModel.getItemsForDeliveryNote(createdNote.id).firstOrNull() ?: emptyList()
                            completedDeliveryNote = Pair(createdNote, noteItems)
                        }
                        Toast.makeText(context, "Nota de entrega ${createdNote.noteNumber} emitida con éxito", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showLimitInvoiceDialog) {
        KeyboardAwareAlertDialog(
            onDismissRequest = { showLimitInvoiceDialog = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Límite Diario de Facturación", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showLimitInvoiceDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Text(
                    if (!isLicensed) {
                        "La versión sin licencia permite un máximo de 5 facturas al día (Has emitido $todaySalesCount facturas hoy).\n\nPara facturación ilimitada o límites ampliados, el administrador debe registrar una clave de licencia."
                    } else {
                        "Has alcanzado el límite diario de $customDailyInvoiceLimit facturas configurado para esta licencia. Puedes ampliar el límite en la gestión de licencia."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLimitInvoiceDialog = false
                        showLicenseDialog = true
                    }
                ) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (!isLicensed) "Registrar Clave" else "Gestionar Licencia")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitInvoiceDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showLicenseDialog) {
        LicenseDialog(
            viewModel = viewModel,
            onDismiss = { showLicenseDialog = false }
        )
    }

    // Cashier Dialog: Add New Customer On-the-Fly
    if (showAddCustomerDialog) {
        QuickAddCustomerDialog(
            initialDoc = if (customerDoc != "V-00000000") customerDoc else "",
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, doc, address, phone ->
                viewModel.addCustomer(name, doc, address, phone)
                customerName = name
                customerDoc = doc
                showAddCustomerDialog = false
                Toast.makeText(context, "Cliente $name registrado y asignado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Manual BCV Rate Dialog
    if (showEditRateDialog) {
        EditRateDialog(
            currentRate = bcvRate,
            onDismiss = { showEditRateDialog = false },
            onConfirm = { newRate ->
                viewModel.setBcvRate(newRate)
                showEditRateDialog = false
                Toast.makeText(context, "Tasa BCV actualizada a Bs. $newRate", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingCartItem != null) {
        EditCartItemDialog(
            item = editingCartItem!!,
            onDismiss = { editingCartItem = null },
            onConfirm = { quantity, price ->
                viewModel.updateCartItem(editingCartItem!!, quantity, price)
                editingCartItem = null
            },
            onDelete = {
                viewModel.removeCartItemCompletely(editingCartItem!!)
                editingCartItem = null
            }
        )
    }

    if (showCheckout) {
        CheckoutDialog(
            viewModel = viewModel,
            customerName = customerName,
            customerDoc = customerDoc,
            onDismiss = { showCheckout = false },
            onSaleComplete = { sale, items ->
                processedSale = Pair(sale, items)
                showCheckout = false
                customerName = ""
                customerDoc = ""
            }
        )
    }

    processedSale?.let { (sale, items) ->
        TicketDialog(
            sale = sale,
            items = items,
            viewModel = viewModel,
            onDismiss = { processedSale = null }
        )
    }

    completedDeliveryNote?.let { (note, items) ->
        DeliveryNoteDialog(
            note = note,
            items = items,
            viewModel = viewModel,
            onDismiss = { completedDeliveryNote = null }
        )
    }
}

@Composable
fun EditRateDialog(
    currentRate: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var rateStr by remember { mutableStateOf(String.format("%.2f", currentRate).replace(",", ".")) }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ajustar Tasa Oficial BCV", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Introduce la tasa de cambio en Bolívares por Dólar:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = rateStr,
                    onValueChange = { rateStr = it },
                    label = { Text("Tasa (Bs / USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = rateStr.trim().replace(",", ".").toDoubleOrNull()
                if (clean != null && clean > 0) {
                    onConfirm(clean)
                }
            }) {
                Text("Guardar Tasa")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddCustomerDialog(
    initialDoc: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, documentId: String, address: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var docPrefix by remember { mutableStateOf("V-") }
    var docNumber by remember {
        val clean = initialDoc.removePrefix("V-").removePrefix("J-").removePrefix("E-").removePrefix("G-")
        mutableStateOf(clean)
    }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 520.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp
    ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Registrar Nuevo Cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                Text(
                    "Agrega el cliente para emitir su factura:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Cédula / RIF with Prefix
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var expandedPrefix by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.width(90.dp)) {
                        OutlinedButton(
                            onClick = { expandedPrefix = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(docPrefix, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = expandedPrefix, onDismissRequest = { expandedPrefix = false }) {
                            listOf("V-", "J-", "E-", "G-").forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        docPrefix = p
                                        expandedPrefix = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = docNumber,
                        onValueChange = { docNumber = it },
                        label = { Text("Cédula / RIF *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus()
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre o Razón Social *") },
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono de Contacto") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección (Opcional)") },
                    leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val fullDoc = "$docPrefix${docNumber.trim()}"
                            if (name.isNotBlank() && docNumber.isNotBlank()) {
                                onConfirm(name.trim(), fullDoc, address.trim(), phone.trim())
                            }
                        },
                        enabled = name.isNotBlank() && docNumber.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Guardar y Asignar")
                    }
                }
            }
    }
}

// Utility to cleanly parse user currency inputs (supporting commas, dots, and empty strings)
private fun parseUserAmount(input: String): Double {
    val sanitized = input.trim().replace(" ", "").replace(",", ".")
    return sanitized.toDoubleOrNull() ?: 0.0
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutDialog(
    viewModel: PosViewModel,
    customerName: String,
    customerDoc: String,
    onDismiss: () -> Unit,
    onSaleComplete: (Sale, List<SaleItem>) -> Unit
) {
    var cashUsdInput by remember { mutableStateOf("") }
    var mobileBsInput by remember { mutableStateOf("") }
    var cardBsInput by remember { mutableStateOf("") }
    var casheaUsdInput by remember { mutableStateOf("") }
    var creditUsdInput by remember { mutableStateOf("") }
    var creditDueDateDays by remember { mutableStateOf(15) }
    var creditNotesInput by remember { mutableStateOf("") }
    var discountUsdInput by remember { mutableStateOf("") }

    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val isLicensed by viewModel.isLicensed.collectAsStateWithLifecycle()
    val isUnlimitedInvoices by viewModel.isUnlimitedDailyInvoices.collectAsStateWithLifecycle()
    val customDailyInvoiceLimit by viewModel.customDailyInvoiceLimit.collectAsStateWithLifecycle()
    val canProcessSale = viewModel.canProcessMoreInvoicesToday()
    val maxAllowedInvoices = viewModel.getMaxDailyInvoicesAllowed()
    val todaySalesCount = viewModel.getTodaySalesCount()
    var showLicenseDialogInCheckout by remember { mutableStateOf(false) }

    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    val taxableItems = cart.filter { !it.product.isTaxExempt }
    val exemptItems = cart.filter { it.product.isTaxExempt }

    val taxableGrossUsd = taxableItems.sumOf { it.effectivePrice * it.quantity }
    val exemptGrossUsd = exemptItems.sumOf { it.effectivePrice * it.quantity }

    val subtotalTaxableBase = taxableGrossUsd / 1.16
    val subtotalExemptBase = exemptGrossUsd
    val subtotalBeforeTax = subtotalTaxableBase + subtotalExemptBase

    val enteredDiscountUsd = parseUserAmount(discountUsdInput)
    val effectiveDiscountUsd = minOf(maxOf(0.0, enteredDiscountUsd), subtotalBeforeTax)

    val ratioTaxable = if (subtotalBeforeTax > 0.0001) subtotalTaxableBase / subtotalBeforeTax else 1.0
    val ratioExempt = if (subtotalBeforeTax > 0.0001) subtotalExemptBase / subtotalBeforeTax else 0.0

    val discountTaxable = effectiveDiscountUsd * ratioTaxable
    val discountExempt = effectiveDiscountUsd * ratioExempt

    val baseTaxableUsd = maxOf(0.0, subtotalTaxableBase - discountTaxable)
    val baseExemptUsd = maxOf(0.0, subtotalExemptBase - discountExempt)

    val ivaUsd = baseTaxableUsd * 0.16
    val productsTotalUsd = baseTaxableUsd + baseExemptUsd + ivaUsd
    val productsTotalBs = productsTotalUsd * effectiveRate

    // Reactive payments
    val paidCashUsd = parseUserAmount(cashUsdInput)
    val paidMobileBs = parseUserAmount(mobileBsInput)
    val paidCardBs = parseUserAmount(cardBsInput)
    val paidCasheaUsd = parseUserAmount(casheaUsdInput)
    val paidCreditUsd = parseUserAmount(creditUsdInput)

    // Converted to USD
    val paidMobileUsd = paidMobileBs / effectiveRate
    val paidCardUsd = paidCardBs / effectiveRate

    // 3% IGTF applies exclusively on payments made in foreign currency (divisas en efectivo)
    val igtfUsd = if (paidCashUsd > 0.001) paidCashUsd * 0.03 else 0.0
    val igtfBs = igtfUsd * effectiveRate

    val totalDueUsd = productsTotalUsd + igtfUsd
    val totalDueBs = (productsTotalUsd * effectiveRate) + igtfBs

    val totalPaidUsd = paidCashUsd + paidMobileUsd + paidCardUsd + paidCasheaUsd + paidCreditUsd

    // Difference
    val remainingUsd = totalDueUsd - totalPaidUsd
    val remainingBs = remainingUsd * effectiveRate

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp
    ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cobro de Factura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(18.dp))
                    }
                }

                // Balanced, compact invoice summary card (Clean typography, no oversized USD text)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ResponsiveActionRow(
                            breakpoint = 310.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = { Text("Total a Pagar:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) },
                            action = {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "\$${String.format("%.2f", totalDueUsd)} USD",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "(Bs. ${String.format("%.2f", totalDueBs)})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )

                        ResponsiveActionRow(
                            breakpoint = 310.dp,
                            verticalSpacing = 2.dp,
                            stackedActionAlignment = Alignment.Start,
                            content = {
                                Text(
                                    "Base: \$${String.format("%.2f", baseTaxableUsd)}" +
                                        (if (baseExemptUsd > 0.001) " • Exento: \$${String.format("%.2f", baseExemptUsd)}" else "") +
                                        " • IVA (16%): \$${String.format("%.2f", ivaUsd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            action = {
                                Text(
                                    "Tasa: Bs. ${String.format("%.2f", effectiveRate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )

                        if (effectiveDiscountUsd > 0.001) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE11D48).copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Descuento Aplicado (pre-IVA):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFBE123C)
                                    )
                                    Text(
                                        "-\$${String.format("%.2f", effectiveDiscountUsd)} USD (-Bs. ${String.format("%.2f", effectiveDiscountUsd * effectiveRate)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFBE123C)
                                    )
                                }
                            }
                        }

                        if (igtfUsd > 0.001) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "IGTF (3% Divisas):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309)
                                    )
                                    Text(
                                        "+\$${String.format("%.2f", igtfUsd)} USD (+Bs. ${String.format("%.2f", igtfBs)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Text(
                            "Cliente: $customerName ($customerDoc)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Descuento en Factura (se descuenta del subtotal antes de sumarle el IVA)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, if (effectiveDiscountUsd > 0.0) Color(0xFFE11D48).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Discount, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(16.dp))
                                Text("Descuento en Factura (Pre-IVA)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (effectiveDiscountUsd > 0.0) {
                                Text(
                                    "-\$${String.format("%.2f", effectiveDiscountUsd)} USD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE11D48),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = discountUsdInput,
                            onValueChange = { discountUsdInput = it },
                            placeholder = { Text("0.00 (Monto en USD)") },
                            prefix = { Text("\$ ", fontWeight = FontWeight.Bold, color = Color(0xFFE11D48)) },
                            trailingIcon = {
                                if (discountUsdInput.isNotBlank()) {
                                    IconButton(onClick = { discountUsdInput = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Quitar descuento", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                        )
                        Text(
                            "Se descuenta del subtotal antes de aplicar el 16% de IVA.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Text("Métodos de Pago:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                // 1. Pago Móvil (Bs) with live subtraction and USD equivalent
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pago Móvil (Bs)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        if (remainingUsd > 0.009) {
                            TextButton(
                                onClick = {
                                    val neededBs = (remainingUsd * effectiveRate).coerceAtLeast(0.0)
                                    mobileBsInput = String.format("%.2f", neededBs).replace(",", ".")
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Pagar Restante en Bs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = mobileBsInput,
                        onValueChange = { mobileBsInput = it },
                        placeholder = { Text("0.00") },
                        prefix = { Text("Bs. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (paidMobileBs > 0) {
                                Text(
                                    "≈ \$${String.format("%.2f", paidMobileUsd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )
                }

                // 2. Efectivo USD ($) with live subtraction and 3% IGTF explanation
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Efectivo Divisas ($ USD)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    "+3% IGTF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (remainingUsd > 0.009) {
                            TextButton(
                                onClick = {
                                    val unpaidProductUsd = (productsTotalUsd - (paidMobileUsd + paidCardUsd + paidCasheaUsd + paidCreditUsd)).coerceAtLeast(0.0)
                                    val neededCashUsd = unpaidProductUsd * 1.03
                                    cashUsdInput = String.format("%.2f", neededCashUsd).replace(",", ".")
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Pagar Restante en \$", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = cashUsdInput,
                        onValueChange = { cashUsdInput = it },
                        placeholder = { Text("0.00") },
                        prefix = { Text("\$ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )
                }

                // 3. Punto de Venta / Tarjeta (Bs) with live subtraction
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Punto de Venta / Tarjeta (Bs)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        if (remainingUsd > 0.009) {
                            TextButton(
                                onClick = {
                                    val neededBs = (remainingUsd * effectiveRate).coerceAtLeast(0.0)
                                    cardBsInput = String.format("%.2f", neededBs).replace(",", ".")
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Pagar Restante en Bs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = cardBsInput,
                        onValueChange = { cardBsInput = it },
                        placeholder = { Text("0.00") },
                        prefix = { Text("Bs. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (paidCardBs > 0) {
                                Text(
                                    "≈ \$${String.format("%.2f", paidCardUsd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )
                }

                // 3.5. Cashea ($ USD) with live subtraction and Bs equivalent
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFACC15)
                            ) {
                                Text(
                                    "Cashea",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF111827),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text("Cashea (\$ USD)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (remainingUsd > 0.009) {
                            TextButton(
                                onClick = {
                                    val neededCashea = remainingUsd.coerceAtLeast(0.0)
                                    casheaUsdInput = String.format(java.util.Locale.US, "%.2f", neededCashea)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("checkout_cashea_fill_button")
                            ) {
                                Text("Pagar Restante con Cashea", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = casheaUsdInput,
                        onValueChange = { casheaUsdInput = it },
                        placeholder = { Text("0.00") },
                        prefix = { Text("\$ ", fontWeight = FontWeight.Bold, color = Color(0xFFCA8A04)) },
                        trailingIcon = {
                            if (paidCasheaUsd > 0) {
                                Text(
                                    "≈ Bs. ${String.format("%.2f", paidCasheaUsd * effectiveRate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCA8A04),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("checkout_cashea_input")
                    )
                }

                // 4. Crédito Otorgado ($ USD - Cuentas por Cobrar)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Text("Crédito / Cuenta por Cobrar (\$ USD)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        if (remainingUsd > 0.009) {
                            TextButton(
                                onClick = {
                                    creditUsdInput = String.format(java.util.Locale.US, "%.2f", remainingUsd)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Asignar Restante a Crédito", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = creditUsdInput,
                        onValueChange = { creditUsdInput = it },
                        placeholder = { Text("0.00") },
                        prefix = { Text("$ ", fontWeight = FontWeight.Bold, color = Color(0xFFD97706)) },
                        trailingIcon = {
                            if (paidCreditUsd > 0) {
                                Text(
                                    "≈ Bs. ${String.format("%.2f", paidCreditUsd * effectiveRate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD97706),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )

                    // Credit due date and details if credit is granted
                    if (paidCreditUsd > 0.001) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7).copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Plazo para pago del crédito:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(7, 15, 30, 45).forEach { days ->
                                        FilterChip(
                                            selected = creditDueDateDays == days,
                                            onClick = { creditDueDateDays = days },
                                            label = { Text("$days Días", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = creditNotesInput,
                                    onValueChange = { creditNotesInput = it },
                                    label = { Text("Notas o condiciones del crédito", fontSize = 11.sp) },
                                    placeholder = { Text("Ej: Pago acordado quincena...", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                                )
                                Text(
                                    "ℹ️ La deuda de $${String.format(java.util.Locale.US, "%.2f", paidCreditUsd)} USD quedará registrada a nombre de '${customerName.ifBlank { "Consumidor Final" }}' para su cobranza posterior.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF78350F)
                                )
                            }
                        }
                    }
                }

                // Live Dynamic Remaining / Change Indicator
                val isPaidComplete = remainingUsd <= 0.009
                val isOverpaid = remainingUsd < -0.009

                Surface(
                    color = when {
                        isOverpaid -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                        isPaidComplete -> Color(0xFF10B981).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isOverpaid -> Color(0xFF3B82F6).copy(alpha = 0.4f)
                            isPaidComplete -> Color(0xFF10B981).copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when {
                                    isOverpaid -> "Cambio / Vuelto a entregar:"
                                    isPaidComplete -> "Pago completado con éxito"
                                    else -> "Restante por cobrar:"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isOverpaid -> Color(0xFF2563EB)
                                    isPaidComplete -> Color(0xFF059669)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                            if (isPaidComplete && !isOverpaid) {
                                Text("Total cubierto correctamente", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
                            }
                        }

                        val balanceText = when {
                            isOverpaid -> "\$" + String.format("%.2f", -remainingUsd) + " USD (Bs. " + String.format("%.2f", -remainingBs) + ")"
                            isPaidComplete -> "✓ Listo"
                            else -> "\$" + String.format("%.2f", remainingUsd) + " USD (Bs. " + String.format("%.2f", remainingBs) + ")"
                        }
                        Text(
                            text = balanceText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = when {
                                isOverpaid -> Color(0xFF2563EB)
                                isPaidComplete -> Color(0xFF059669)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }

                if (!canProcessSale) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text(
                                    "Límite Diario Alcanzado ($todaySalesCount / $maxAllowedInvoices)",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                if (!isLicensed) {
                                    "La versión sin licencia permite un máximo de 5 facturas al día. El administrador debe registrar una clave de licencia para continuar facturando."
                                } else {
                                    "Has alcanzado el límite de $maxAllowedInvoices facturas al día configurado para esta licencia."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = { showLicenseDialogInCheckout = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (!isLicensed) "🔑 Registrar Clave de Licencia" else "⚙️ Configurar Licencia",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Actions
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val creditDueTimestamp = if (paidCreditUsd > 0.001) {
                                System.currentTimeMillis() + (creditDueDateDays.toLong() * 24L * 60L * 60L * 1000L)
                            } else {
                                0L
                            }
                            viewModel.processSale(
                                customerName = customerName,
                                customerDocument = customerDoc,
                                paymentCashUsd = paidCashUsd,
                                paymentMobileBs = paidMobileBs,
                                paymentCardBs = paidCardBs,
                                paymentCreditUsd = paidCreditUsd,
                                paymentCasheaUsd = paidCasheaUsd,
                                creditDueDate = creditDueTimestamp,
                                creditNotes = creditNotesInput.trim(),
                                discountUsd = effectiveDiscountUsd,
                                onSuccess = { sale ->
                                    val items = cart.map {
                                        SaleItem(0, sale.id, it.product.id, it.product.name, it.quantity, it.effectivePrice, it.product.isTaxExempt)
                                    }
                                    onSaleComplete(sale, items)
                                }
                            )
                        },
                        enabled = canProcessSale && (isPaidComplete || totalPaidUsd > 0.0),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Confirmar Venta")
                    }
                }
            }
    }

    if (showLicenseDialogInCheckout) {
        LicenseDialog(
            viewModel = viewModel,
            onDismiss = { showLicenseDialogInCheckout = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCartItemDialog(
    item: CartItem,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, price: Double?) -> Unit,
    onDelete: () -> Unit = {}
) {
    var quantityStr by remember { mutableStateOf(if (item.product.allowDecimalQuantity) item.quantity.toString() else item.quantity.toInt().toString()) }
    var priceStr by remember { mutableStateOf(item.effectivePrice.toString()) }
    var selectedWholesale by remember {
        mutableStateOf(
            item.product.wholesalePriceUsd > 0.0 &&
                abs(item.effectivePrice - item.product.wholesalePriceUsd) < 0.001
        )
    }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    supportingText = {
                        if (!item.product.allowDecimalQuantity) {
                            Text("Solo cantidades enteras permitidas")
                        }
                    }
                )

                if (item.product.wholesalePriceUsd > 0.0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Tarifa Aplicada",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = !selectedWholesale,
                                onClick = {
                                    selectedWholesale = false
                                    priceStr = String.format("%.2f", item.product.priceUsd).replace(",", ".")
                                },
                                label = {
                                    Text(
                                        "Normal (\$${String.format("%.2f", item.product.priceUsd)})",
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier.testTag("pos_normal_price_chip")
                            )
                            FilterChip(
                                selected = selectedWholesale,
                                onClick = {
                                    selectedWholesale = true
                                    priceStr = String.format("%.2f", item.product.wholesalePriceUsd).replace(",", ".")
                                },
                                label = {
                                    Text(
                                        "Mayor (\$${String.format("%.2f", item.product.wholesalePriceUsd)})",
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier.testTag("pos_wholesale_price_chip")
                            )
                        }
                    }
                }
                
                if (item.product.isPriceModifiable) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = {
                            priceStr = it
                            val parsed = it.replace(",", ".").toDoubleOrNull()
                            selectedWholesale = parsed != null &&
                                item.product.wholesalePriceUsd > 0.0 &&
                                abs(parsed - item.product.wholesalePriceUsd) < 0.001
                        },
                        label = { Text("Precio Unitario ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        supportingText = { Text("Precio original: \$${String.format("%.2f", item.product.priceUsd)}") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantityStr.replace(",", ".").toDoubleOrNull() ?: item.quantity
                val p = when {
                    item.product.isPriceModifiable -> priceStr.replace(",", ".").toDoubleOrNull()
                    item.product.wholesalePriceUsd > 0.0 -> if (selectedWholesale) item.product.wholesalePriceUsd else null
                    else -> null
                }
                
                val finalQ = if (item.product.allowDecimalQuantity) q else q.toInt().toDouble()
                if (finalQ <= 0.0) {
                    onDelete()
                } else {
                    onConfirm(finalQ, p)
                }
            }) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@Composable
fun CategoryFilterChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.testTag("pos_category_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            val tagSlug = category.lowercase(java.util.Locale.ROOT).replace(" ", "_")
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("pos_category_filter_chip_$tagSlug")
            )
        }
    }
}

@Composable
fun CompactPosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Buscar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Limpiar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BcvRateAndLicenseBar(
    bcvRate: Double,
    heldSalesCount: Int = 0,
    isSidebarVisible: Boolean = false,
    onToggleMenu: (() -> Unit)? = null,
    onNavigateHome: (() -> Unit)? = null,
    onHeldSalesClick: () -> Unit = {},
    onEditRateClick: () -> Unit,
    onRefreshRateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (onToggleMenu != null) {
                IconButton(
                    onClick = onToggleMenu,
                    modifier = Modifier
                        .size(36.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("pos_landscape_menu_button")
                ) {
                    Icon(
                        if (isSidebarVisible) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                        contentDescription = "Alternar menú principal",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (onNavigateHome != null) {
                IconButton(
                    onClick = onNavigateHome,
                    modifier = Modifier
                        .size(36.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("pos_landscape_home_button")
                ) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Ir a módulos principales",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clickable { onEditRateClick() }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.CurrencyExchange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Tasa BCV: Bs. ${String.format("%.2f", bcvRate)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Editar tasa",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (heldSalesCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { onHeldSalesClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.PauseCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "En Espera ($heldSalesCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            IconButton(
                onClick = onRefreshRateClick,
                modifier = Modifier
                    .size(36.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Actualizar Tasa",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSelectorCard(
    customerDoc: String,
    customerName: String,
    onCustomerSelected: (String, String) -> Unit,
    filteredCustomers: List<Customer>,
    isCustomerDropdownExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddNewCustomer: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
) {
    val hasSelectedCustomer = customerName.isNotBlank() || customerDoc.isNotBlank()

    if (hasSelectedCustomer) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (customerDoc == "V-00000000") {
                            "Cliente: Consumidor Final"
                        } else if (customerDoc.isNotBlank() && customerName.isNotBlank()) {
                            "Cliente: $customerName ($customerDoc)"
                        } else {
                            "Cliente: ${customerName.ifBlank { customerDoc }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        onExpandedChange(false)
                        onCustomerSelected("", "")
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("pos_clear_customer_button")
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Quitar cliente",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = isCustomerDropdownExpanded,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("pos_customer_selector_field")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Seleccionar cliente...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCustomerDropdownExpanded)
                    }
                }
                ExposedDropdownMenu(
                    expanded = isCustomerDropdownExpanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Consumidor Final (V-00000000)", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            onCustomerSelected("Consumidor Final", "V-00000000")
                            onExpandedChange(false)
                        }
                    )
                    HorizontalDivider()
                    filteredCustomers.forEach { customer ->
                        DropdownMenuItem(
                            text = { Text("${customer.documentId} - ${customer.name}") },
                            onClick = {
                                onCustomerSelected(customer.name, customer.documentId)
                                onExpandedChange(false)
                            }
                        )
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onAddNewCustomer,
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("pos_add_customer_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "Añadir cliente",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ProductQuickGridItem(
    product: Product,
    bcvRate: Double,
    onAddToCart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddToCart() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (product.isTaxExempt) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (product.isTaxExempt) "Exento" else "IVA 16%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (product.isTaxExempt) Color(0xFF047857) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (product.isRecipe) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Receta",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Stock: ${product.stock}",
                        fontSize = 10.sp,
                        color = if (product.stock <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "\$${String.format("%.2f", product.priceUsd)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Bs. ${String.format("%.2f", product.priceUsd * bcvRate)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (product.wholesalePriceUsd > 0.0) {
                        Text(
                            text = "Mayor: \$${String.format("%.2f", product.wholesalePriceUsd)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    bcvRate: Double,
    viewModel: PosViewModel,
    onEditClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEditClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.product.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.product.isTaxExempt) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Exento",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF047857),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.product.wholesalePriceUsd > 0.0 && abs(item.effectivePrice - item.product.wholesalePriceUsd) < 0.001) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2563EB).copy(alpha = 0.14f)
                        ) {
                            Text(
                                "Al Mayor",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1D4ED8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.product.isRecipe) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Receta",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    "${item.quantity} x \$${String.format("%.2f", item.effectivePrice)} (Bs. ${String.format("%.2f", item.effectivePrice * bcvRate)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Botón Menos
                Surface(
                    onClick = {
                        if (item.quantity > 1.0) {
                            viewModel.updateCartItem(item, item.quantity - 1.0, item.customPrice)
                        } else {
                            viewModel.removeFromCart(item.product)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Remove, 
                            contentDescription = "Disminuir", 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Cantidad
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .padding(horizontal = 2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onEditClick() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.product.allowDecimalQuantity) String.format("%.3f", item.quantity) else "${item.quantity.toInt()}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Botón Más
                Surface(
                    onClick = { viewModel.updateCartItem(item, item.quantity + 1.0, item.customPrice) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add, 
                            contentDescription = "Aumentar", 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Botón Eliminar
                IconButton(
                    onClick = { viewModel.removeCartItemCompletely(item) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete, 
                        contentDescription = "Eliminar", 
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), 
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    "$${String.format("%.2f", item.quantity * item.effectivePrice)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TotalsAndCheckoutSection(
    cart: List<CartItem>,
    bcvRate: Double,
    canProcessInvoices: Boolean,
    onLimitReached: () -> Unit,
    onCheckoutClick: () -> Unit,
    onHoldCartClick: () -> Unit = {},
    onDeliveryNoteClick: () -> Unit = {}
) {
    val taxableItems = cart.filter { !it.product.isTaxExempt }
    val exemptItems = cart.filter { it.product.isTaxExempt }

    val taxableTotalUsd = taxableItems.sumOf { it.effectivePrice * it.quantity }
    val baseTaxableUsd = taxableTotalUsd / 1.16
    val ivaUsd = taxableTotalUsd - baseTaxableUsd
    val baseExemptUsd = exemptItems.sumOf { it.effectivePrice * it.quantity }

    val totalUsd = taxableTotalUsd + baseExemptUsd
    val totalBs = totalUsd * bcvRate

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Base: \$${String.format("%.2f", baseTaxableUsd)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (baseExemptUsd > 0.001) {
                Text(
                    "Exento: \$${String.format("%.2f", baseExemptUsd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF047857),
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                "IVA: \$${String.format("%.2f", ivaUsd)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Tasa: Bs. ${String.format("%.2f", bcvRate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Total a Facturar:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "\$${String.format("%.2f", totalUsd)} USD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "• Bs. ${String.format("%.2f", totalBs)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = {
                if (!canProcessInvoices) {
                    onLimitReached()
                } else {
                    onCheckoutClick()
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(46.dp)
        ) {
            Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cobrar / Facturar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onHoldCartClick,
                enabled = cart.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("En Espera", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onDeliveryNoteClick,
                enabled = cart.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = if (cart.isNotEmpty()) 0.6f else 0.2f)),
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nota Entrega", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
