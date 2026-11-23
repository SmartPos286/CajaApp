package com.example.ui.delivery

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.Product
import com.example.ui.CartItem
import com.example.ui.PosViewModel
import com.example.ui.components.DateFilterUtils
import com.example.ui.components.DateRangeFilterBar
import com.example.ui.components.DeliveryNoteDialog
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.pos.CategoryFilterChipsRow
import com.example.ui.pos.CompactPosSearchField
import com.example.ui.pos.CustomerSelectorCard
import com.example.ui.pos.EditRateDialog
import com.example.ui.pos.QuickAddCustomerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeliveryNotesScreen(
    viewModel: PosViewModel,
    onNavigateToPos: () -> Unit = {}
) {
    val context = LocalContext.current
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val noteCart by viewModel.deliveryNoteCart.collectAsStateWithLifecycle()
    val displayCurrency by viewModel.deliveryNoteCurrency.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val deliveryNotes by viewModel.allDeliveryNotes.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Crear Nota, 1: Historial
    var customerName by remember { mutableStateOf("") }
    var customerDoc by remember { mutableStateOf("") }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Todas") }

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showEditRateDialog by remember { mutableStateOf(false) }
    var showEmitNoteDialog by remember { mutableStateOf(false) }
    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var completedNotePair by remember { mutableStateOf<Pair<DeliveryNote, List<DeliveryNoteItem>>?>(null) }
    var selectedHistoryNote by remember { mutableStateOf<DeliveryNote?>(null) }

    val useBs = displayCurrency.equals("BS", ignoreCase = true)

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

    val filteredCustomers = remember(customers, customerDoc) {
        customers.filter {
            it.documentId.contains(customerDoc, ignoreCase = true) ||
                it.name.contains(customerDoc, ignoreCase = true)
        }
    }

    fun tryAddProduct(product: Product) {
        val added = viewModel.addToDeliveryNoteCart(product)
        if (!added) {
            Toast.makeText(
                context,
                "Stock insuficiente para ${product.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Top Mode Tabs
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        leadingIcon = { Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(
                                "Crear Nota",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("delivery_notes_tab_create")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(
                                "Historial (${deliveryNotes.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("delivery_notes_tab_history")
                    )
                }
            }

            if (selectedTab == 0) {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Column: Rate, Currency Switch, Customer, Search, Categories & Catalog
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
                                DeliveryRateAndCurrencyBar(
                                    bcvRate = bcvRate,
                                    displayCurrency = displayCurrency,
                                    onCurrencyChange = { viewModel.setDeliveryNoteCurrency(it) },
                                    onEditRateClick = { showEditRateDialog = true },
                                    onRefreshRateClick = { viewModel.refreshRate() }
                                )

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
                                        .testTag("delivery_note_search_input")
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
                                        "Toca para despachar (Sin IVA)",
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
                                        items(filteredProducts, key = { it.id }) { product ->
                                            DeliveryProductQuickGridItem(
                                                product = product,
                                                bcvRate = bcvRate,
                                                useBs = useBs,
                                                onAddToCart = { tryAddProduct(product) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right Column: Delivery Note Cart & Emitir
                        Card(
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f))
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
                                        Icon(Icons.Filled.Assignment, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                        Text(
                                            "Nota Entrega (${noteCart.sumOf { it.quantity }.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.2f", it) }}):",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    if (noteCart.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearDeliveryNoteCart() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (noteCart.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Filled.Assignment,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.size(38.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Nota de entrega vacía", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "Descuenta inventario sin facturar ni cobrar IVA",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(noteCart) { item ->
                                            DeliveryNoteCartItemRow(
                                                item = item,
                                                bcvRate = bcvRate,
                                                useBs = useBs,
                                                viewModel = viewModel,
                                                onEditClick = { editingCartItem = item }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )

                                DeliveryNoteTotalsSection(
                                    cart = noteCart,
                                    bcvRate = bcvRate,
                                    useBs = useBs,
                                    onCurrencyToggle = { nextUseBs ->
                                        viewModel.setDeliveryNoteCurrency(if (nextUseBs) "BS" else "USD")
                                    },
                                    onEmitClick = { showEmitNoteDialog = true }
                                )
                            }
                        }
                    }
                } else {
                    // Portrait layout mirroring PosScreen without IVA
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                            DeliveryRateAndCurrencyBar(
                                bcvRate = bcvRate,
                                displayCurrency = displayCurrency,
                                onCurrencyChange = { viewModel.setDeliveryNoteCurrency(it) },
                                onEditRateClick = { showEditRateDialog = true },
                                onRefreshRateClick = { viewModel.refreshRate() }
                            )
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
                            onAddNewCustomer = { showAddCustomerDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        )

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
                                    .testTag("delivery_note_portrait_search_input")
                            )
                            if ((searchQuery.isNotEmpty() || selectedCategory != "Todas") && filteredProducts.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false }
                                ) {
                                    filteredProducts.forEach { product ->
                                        val priceDisplay = if (useBs) {
                                            "Bs. ${String.format(Locale.US, "%.2f", product.priceUsd * bcvRate)}"
                                        } else {
                                            "\$${String.format(Locale.US, "%.2f", product.priceUsd)}"
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                        Text(product.name, fontWeight = FontWeight.Bold)
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                if (product.isRecipe) "Código: ${product.code} • Receta" else "Código: ${product.code} • Stock: ${product.stock}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            if (product.wholesalePriceUsd > 0.0) {
                                                                val wholesaleText = if (useBs) {
                                                                    "Mayor: Bs.${String.format(Locale.US, "%.2f", product.wholesalePriceUsd * bcvRate)}"
                                                                } else {
                                                                    "Mayor: \$${String.format(Locale.US, "%.2f", product.wholesalePriceUsd)}"
                                                                }
                                                                Text(
                                                                    wholesaleText,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = Color(0xFF0284C7)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        priceDisplay,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0284C7)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                tryAddProduct(product)
                                                searchQuery = ""
                                                isDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        CategoryFilterChipsRow(
                            categories = availableCategories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        )

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
                                        color = Color(0xFF0284C7)
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
                                        items(filteredProducts, key = { it.id }) { product ->
                                            Box(modifier = Modifier.width(156.dp)) {
                                                DeliveryProductQuickGridItem(
                                                    product = product,
                                                    bcvRate = bcvRate,
                                                    useBs = useBs,
                                                    onAddToCart = { tryAddProduct(product) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Productos en Nota (${noteCart.sumOf { it.quantity }.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.2f", it) }}):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (noteCart.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearDeliveryNoteCart() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }

                        if (noteCart.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Assignment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Nota de entrega vacía",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Busca productos arriba para despachar sin factura ni IVA (descuenta stock). Toca un ítem para editar su precio de venta.",
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
                                items(noteCart) { item ->
                                    DeliveryNoteCartItemRow(
                                        item = item,
                                        bcvRate = bcvRate,
                                        useBs = useBs,
                                        viewModel = viewModel,
                                        onEditClick = { editingCartItem = item }
                                    )
                                }
                            }
                        }

                        if (noteCart.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f))
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    DeliveryNoteTotalsSection(
                                        cart = noteCart,
                                        bcvRate = bcvRate,
                                        useBs = useBs,
                                        onCurrencyToggle = { nextUseBs ->
                                            viewModel.setDeliveryNoteCurrency(if (nextUseBs) "BS" else "USD")
                                        },
                                        onEmitClick = { showEmitNoteDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: HISTORIAL DE NOTAS DE ENTREGA
                DeliveryNotesHistorySection(
                    notes = deliveryNotes,
                    onViewNote = { selectedHistoryNote = it },
                    onConvertToInvoice = { note ->
                        viewModel.loadDeliveryNoteToCart(note.id) {
                            Toast.makeText(
                                context,
                                "Nota ${note.noteNumber} cargada en Facturación",
                                Toast.LENGTH_SHORT
                            ).show()
                            onNavigateToPos()
                        }
                    },
                    onCancelNote = { note ->
                        viewModel.cancelDeliveryNote(note.id)
                        Toast.makeText(
                            context,
                            "Nota ${note.noteNumber} anulada y stock devuelto al inventario",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

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
        EditDeliveryNoteItemDialog(
            item = editingCartItem!!,
            bcvRate = bcvRate,
            useBs = useBs,
            onDismiss = { editingCartItem = null },
            onConfirm = { quantity, priceUsd ->
                viewModel.updateDeliveryNoteCartItem(editingCartItem!!, quantity, priceUsd)
                editingCartItem = null
            },
            onDelete = {
                viewModel.removeFromDeliveryNoteCart(editingCartItem!!)
                editingCartItem = null
            }
        )
    }

    if (showEmitNoteDialog) {
        ConfirmEmitDeliveryNoteDialog(
            cart = noteCart,
            bcvRate = bcvRate,
            customerName = customerName,
            customerDocument = customerDoc,
            initialCurrency = displayCurrency,
            onDismiss = { showEmitNoteDialog = false },
            onConfirm = { cName, cDoc, recip, reason, finalCurrency ->
                viewModel.setDeliveryNoteCurrency(finalCurrency)
                viewModel.processDedicatedDeliveryNote(
                    customerName = cName,
                    customerDocument = cDoc,
                    recipientName = recip,
                    reasonOrObservation = reason,
                    displayCurrency = finalCurrency,
                    onSuccess = { savedNote, savedItems ->
                        showEmitNoteDialog = false
                        customerName = ""
                        customerDoc = ""
                        completedNotePair = Pair(savedNote, savedItems)
                        Toast.makeText(
                            context,
                            "Nota de entrega ${savedNote.noteNumber} emitida (inventario descontado)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )
    }

    completedNotePair?.let { (note, items) ->
        DeliveryNoteDialog(
            note = note,
            items = items,
            viewModel = viewModel,
            onDismiss = { completedNotePair = null }
        )
    }

    selectedHistoryNote?.let { note ->
        val items by viewModel.getItemsForDeliveryNote(note.id).collectAsStateWithLifecycle(initialValue = emptyList())
        DeliveryNoteDialog(
            note = note,
            items = items,
            viewModel = viewModel,
            onDismiss = { selectedHistoryNote = null }
        )
    }
}

@Composable
private fun DeliveryRateAndCurrencyBar(
    bcvRate: Double,
    displayCurrency: String,
    onCurrencyChange: (String) -> Unit,
    onEditRateClick: () -> Unit,
    onRefreshRateClick: () -> Unit
) {
    val useBs = displayCurrency.equals("BS", ignoreCase = true)
    ResponsiveActionRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        breakpoint = 340.dp,
        verticalSpacing = 4.dp,
        stackedActionAlignment = Alignment.Start,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clickable { onEditRateClick() }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.CurrencyExchange,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "BCV: Bs. ${String.format(Locale.US, "%.2f", bcvRate)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF0284C7)
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Editar tasa",
                    tint = Color(0xFF0284C7).copy(alpha = 0.65f),
                    modifier = Modifier.size(13.dp)
                )
                IconButton(
                    onClick = onRefreshRateClick,
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Actualizar Tasa",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        },
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "Sin IVA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }

                FilterChip(
                    selected = !useBs,
                    onClick = { onCurrencyChange("USD") },
                    label = { Text("$ USD", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .testTag("delivery_currency_usd_chip")
                )
                FilterChip(
                    selected = useBs,
                    onClick = { onCurrencyChange("BS") },
                    label = { Text("Bs.", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .testTag("delivery_currency_bs_chip")
                )
            }
        }
    )
}

@Composable
private fun DeliveryProductQuickGridItem(
    product: Product,
    bcvRate: Double,
    useBs: Boolean,
    onAddToCart: () -> Unit
) {
    val hasStock = product.isRecipe || product.stock > 0
    val displayPrice = if (useBs) {
        "Bs. ${String.format(Locale.US, "%.2f", product.priceUsd * bcvRate)}"
    } else {
        "\$${String.format(Locale.US, "%.2f", product.priceUsd)}"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasStock) { onAddToCart() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasStock) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (hasStock) Color(0xFF0284C7).copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0284C7),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (product.isRecipe) "Receta" else "Stock: ${if (product.stock % 1.0 == 0.0) product.stock.toInt() else product.stock}",
                    fontSize = 10.sp,
                    fontWeight = if (!hasStock) FontWeight.Bold else FontWeight.Normal,
                    color = if (product.stock <= 3 && !product.isRecipe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = displayPrice,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (product.wholesalePriceUsd > 0.0) {
                        val wholesaleTxt = if (useBs) {
                            "Mayor: Bs.${String.format(Locale.US, "%.2f", product.wholesalePriceUsd * bcvRate)}"
                        } else {
                            "Mayor: \$${String.format(Locale.US, "%.2f", product.wholesalePriceUsd)}"
                        }
                        Text(
                            text = wholesaleTxt,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (hasStock) Color(0xFF0284C7) else MaterialTheme.colorScheme.outlineVariant,
                    contentColor = Color.White,
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
private fun DeliveryNoteCartItemRow(
    item: CartItem,
    bcvRate: Double,
    useBs: Boolean,
    viewModel: PosViewModel,
    onEditClick: () -> Unit
) {
    val isCustomPrice = item.customPrice != null && abs(item.customPrice - item.product.priceUsd) > 0.001
    val isWholesale = item.product.wholesalePriceUsd > 0.0 && abs(item.effectivePrice - item.product.wholesalePriceUsd) < 0.001
    val unitFormatted = if (useBs) {
        "Bs. ${String.format(Locale.US, "%.2f", item.effectivePrice * bcvRate)}"
    } else {
        "\$${String.format(Locale.US, "%.2f", item.effectivePrice)}"
    }
    val lineTotalFormatted = if (useBs) {
        "Bs.${String.format(Locale.US, "%.2f", item.quantity * item.effectivePrice * bcvRate)}"
    } else {
        "\$${String.format(Locale.US, "%.2f", item.quantity * item.effectivePrice)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(
            1.dp,
            if (isCustomPrice) Color(0xFF0284C7).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        item.product.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isWholesale) {
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
                    } else if (isCustomPrice) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.14f)
                        ) {
                            Text(
                                "Precio Editado",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0284C7),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)
                Text(
                    "$qtyStr x $unitFormatted • Toca para editar precio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Surface(
                    onClick = {
                        if (item.quantity > 1.0) {
                            viewModel.updateDeliveryNoteCartItem(item, item.quantity - 1.0, item.effectivePrice)
                        } else {
                            viewModel.removeFromDeliveryNoteCart(item)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Remove, contentDescription = "Disminuir", modifier = Modifier.size(16.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onEditClick() }
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.product.allowDecimalQuantity) String.format(Locale.US, "%.2f", item.quantity) else "${item.quantity.toInt()}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    onClick = {
                        viewModel.updateDeliveryNoteCartItem(item, item.quantity + 1.0, item.effectivePrice)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.14f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Aumentar", tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                    }
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Modificar precio de venta",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.removeFromDeliveryNoteCart(item) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Text(
                    lineTotalFormatted,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0284C7),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DeliveryNoteTotalsSection(
    cart: List<CartItem>,
    bcvRate: Double,
    useBs: Boolean,
    onCurrencyToggle: (Boolean) -> Unit,
    onEmitClick: () -> Unit
) {
    val totalUsd = cart.sumOf { it.effectivePrice * it.quantity }
    val totalBs = totalUsd * bcvRate

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResponsiveActionRow(
            breakpoint = 310.dp,
            verticalSpacing = 4.dp,
            content = {
                Column {
                    Text(
                        "Total Nota de Entrega:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (useBs) "Sin IVA • Expresado en Bolívares (Tasa: ${String.format(Locale.US, "%.2f", bcvRate)})" else "Sin IVA • Expresado en Dólares ($ USD)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            action = {
                Text(
                    text = if (useBs) {
                        "Bs. ${String.format(Locale.US, "%.2f", totalBs)}"
                    } else {
                        "\$${String.format(Locale.US, "%.2f", totalUsd)} USD"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0284C7)
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onCurrencyToggle(!useBs) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("delivery_note_toggle_currency_button"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (useBs) "Ver en $" else "Ver en Bs.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onEmitClick,
                enabled = cart.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("emit_delivery_note_button")
            ) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Emitir Nota de Entrega", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditDeliveryNoteItemDialog(
    item: CartItem,
    bcvRate: Double,
    useBs: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, priceUsd: Double?) -> Unit,
    onDelete: () -> Unit
) {
    val safeRate = if (bcvRate > 0.001) bcvRate else 1.0
    var quantityStr by remember {
        mutableStateOf(if (item.product.allowDecimalQuantity) item.quantity.toString() else item.quantity.toInt().toString())
    }
    val initialPriceInCurrency = if (useBs) item.effectivePrice * safeRate else item.effectivePrice
    var priceStr by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", initialPriceInCurrency))
    }

    val parsedCurrencyPrice = priceStr.replace(",", ".").toDoubleOrNull()
    val convertedUsd = if (parsedCurrencyPrice != null) {
        if (useBs) parsedCurrencyPrice / safeRate else parsedCurrencyPrice
    } else {
        item.effectivePrice
    }

    KeyboardAwareAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Producto y Precio de Venta", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    supportingText = {
                        val stockLabel = if (item.product.isRecipe) "Receta" else "Disponible: ${item.product.stock}"
                        Text(if (item.product.allowDecimalQuantity) stockLabel else "$stockLabel • Solo enteros")
                    }
                )

                if (item.product.wholesalePriceUsd > 0.0) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val normalVal = if (useBs) item.product.priceUsd * safeRate else item.product.priceUsd
                        val wholesaleVal = if (useBs) item.product.wholesalePriceUsd * safeRate else item.product.wholesalePriceUsd
                        SuggestionChip(
                            onClick = { priceStr = String.format(Locale.US, "%.2f", normalVal) },
                            label = {
                                Text(
                                    if (useBs) "Normal (Bs.${String.format(Locale.US, "%.2f", normalVal)})"
                                    else "Normal (\$${String.format(Locale.US, "%.2f", normalVal)})",
                                    fontSize = 12.sp
                                )
                            }
                        )
                        SuggestionChip(
                            onClick = { priceStr = String.format(Locale.US, "%.2f", wholesaleVal) },
                            label = {
                                Text(
                                    if (useBs) "Mayor (Bs.${String.format(Locale.US, "%.2f", wholesaleVal)})"
                                    else "Mayor (\$${String.format(Locale.US, "%.2f", wholesaleVal)})",
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = {
                        Text(if (useBs) "Precio de Venta Unitario (Bs.)" else "Precio de Venta Unitario ($ USD)")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .testTag("delivery_note_edit_price_input"),
                    supportingText = {
                        val refOriginal = if (useBs) {
                            "Catálogo: Bs. ${String.format(Locale.US, "%.2f", item.product.priceUsd * safeRate)} (\$${String.format(Locale.US, "%.2f", item.product.priceUsd)})"
                        } else {
                            "Catálogo: \$${String.format(Locale.US, "%.2f", item.product.priceUsd)} (Equiv.: Bs. ${String.format(Locale.US, "%.2f", convertedUsd * safeRate)})"
                        }
                        Text(refOriginal)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantityStr.replace(",", ".").toDoubleOrNull() ?: item.quantity
                    val finalQ = if (item.product.allowDecimalQuantity) q else q.toInt().toDouble()
                    val finalPriceUsd = parsedCurrencyPrice?.let { entered ->
                        val usd = if (useBs) entered / safeRate else entered
                        maxOf(0.0, usd)
                    }
                    if (finalQ <= 0.0) {
                        onDelete()
                    } else {
                        onConfirm(finalQ, finalPriceUsd)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmEmitDeliveryNoteDialog(
    cart: List<CartItem>,
    bcvRate: Double,
    customerName: String,
    customerDocument: String,
    initialCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (customerName: String, customerDoc: String, recipientName: String, reason: String, displayCurrency: String) -> Unit
) {
    var custName by remember { mutableStateOf(if (customerName.isNotBlank() && customerName != "Cliente General") customerName else "") }
    var custDoc by remember { mutableStateOf(if (customerDocument.isNotBlank() && customerDocument != "V-00000000") customerDocument else "") }
    var recipientName by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Despacho de mercancía - No válido para crédito fiscal") }
    var selectedCurrency by remember { mutableStateOf(if (initialCurrency.equals("BS", ignoreCase = true)) "BS" else "USD") }

    val totalItems = cart.sumOf { it.quantity }
    val totalUsd = cart.sumOf { it.effectivePrice * it.quantity }
    val totalBs = totalUsd * bcvRate
    val useBs = selectedCurrency == "BS"

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 540.dp,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF0284C7))
                    Text("Emitir Nota de Entrega", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0284C7).copy(alpha = 0.09f),
                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1)
                    )
                    Text(
                        "Descuenta el inventario sin registrar facturación ni IVA. El recibo se emitirá en la moneda elegida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResponsiveActionRow(
                        breakpoint = 310.dp,
                        content = {
                            Text(
                                "${if (totalItems % 1.0 == 0.0) totalItems.toInt() else String.format(Locale.US, "%.2f", totalItems)} unidad(es)",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        action = {
                            Text(
                                text = if (useBs) {
                                    "Total: Bs. ${String.format(Locale.US, "%.2f", totalBs)}"
                                } else {
                                    "Total: \$${String.format(Locale.US, "%.2f", totalUsd)} USD"
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }

            Text(
                "Moneda del Comprobante:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !useBs,
                    onClick = { selectedCurrency = "USD" },
                    leadingIcon = if (!useBs) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Dólares ($ USD)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                )
                FilterChip(
                    selected = useBs,
                    onClick = { selectedCurrency = "BS" },
                    leadingIcon = if (useBs) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Bolívares (Bs.)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                )
            }

            OutlinedTextField(
                value = custName,
                onValueChange = { custName = it },
                label = { Text("Nombre o Razón Social Cliente") },
                placeholder = { Text("Cliente General") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
            )

            ResponsivePair(
                breakpoint = 340.dp,
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp,
                first = { mod ->
                    OutlinedTextField(
                        value = custDoc,
                        onValueChange = { custDoc = it },
                        label = { Text("Cédula / RIF") },
                        placeholder = { Text("V-00000000") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = mod.bringIntoViewOnFocus()
                    )
                },
                second = { mod ->
                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it },
                        label = { Text("Persona que recibe") },
                        placeholder = { Text("Opcional") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = mod.bringIntoViewOnFocus()
                    )
                }
            )

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motivo u Observación") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
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
                        val finalName = if (custName.isBlank()) "Cliente General" else custName.trim()
                        val finalDoc = if (custDoc.isBlank()) "V-00000000" else custDoc.trim()
                        val finalRecip = if (recipientName.isBlank()) finalName else recipientName.trim()
                        onConfirm(finalName, finalDoc, finalRecip, reason.trim(), selectedCurrency)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_emit_delivery_note_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generar y Despachar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DeliveryNotesHistorySection(
    notes: List<DeliveryNote>,
    onViewNote: (DeliveryNote) -> Unit,
    onConvertToInvoice: (DeliveryNote) -> Unit,
    onCancelNote: (DeliveryNote) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("TODOS") }
    var dateFilter by remember { mutableStateOf(DateFilterUtils.allTime()) }
    var noteToCancel by remember { mutableStateOf<DeliveryNote?>(null) }

    val filteredNotes = remember(notes, searchQuery, statusFilter, dateFilter) {
        notes.filter { note ->
            val matchesDate = dateFilter.matches(note.timestamp)
            val matchesQuery = searchQuery.isBlank() ||
                note.noteNumber.contains(searchQuery, ignoreCase = true) ||
                note.customerName.contains(searchQuery, ignoreCase = true) ||
                note.customerDocument.contains(searchQuery, ignoreCase = true) ||
                note.recipientName.contains(searchQuery, ignoreCase = true)
            val matchesStatus = statusFilter == "TODOS" || note.status == statusFilter
            matchesDate && matchesQuery && matchesStatus
        }
    }

    val activeUsd = remember(filteredNotes) {
        filteredNotes.filter { it.status != "ANULADO" }.sumOf { it.totalEstimatedUsd }
    }
    val activeBs = remember(filteredNotes) {
        filteredNotes.filter { it.status != "ANULADO" }.sumOf {
            if (it.totalEstimatedBs > 0.001) it.totalEstimatedBs else it.totalEstimatedUsd * it.rateBcv
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DateRangeFilterBar(
            state = dateFilter,
            onStateChange = { dateFilter = it }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por N° nota, cliente, CI/RIF o receptor...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(listOf("TODOS", "ENTREGADO", "FACTURADO", "ANULADO")) { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = { statusFilter = status },
                        label = { Text(status, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0284C7).copy(alpha = 0.1f),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "${filteredNotes.size} nota(s)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                    Text(
                        "\$${String.format(Locale.US, "%.2f", activeUsd)} • Bs.${String.format(Locale.US, "%.2f", activeBs)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (filteredNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No hay notas de entrega registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 330.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    val useBsNote = note.displayCurrency.equals("BS", ignoreCase = true)
                    val estBs = if (note.totalEstimatedBs > 0.001) note.totalEstimatedBs else note.totalEstimatedUsd * note.rateBcv
                    val isCancelled = note.status == "ANULADO"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewNote(note) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ResponsiveActionRow(
                                breakpoint = 290.dp,
                                content = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            note.noteNumber.ifBlank { "NE-${note.id}" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF0284C7)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when (note.status) {
                                                "ENTREGADO" -> Color(0xFF059669).copy(alpha = 0.15f)
                                                "FACTURADO" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            }
                                        ) {
                                            Text(
                                                text = note.status,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (note.status) {
                                                    "ENTREGADO" -> Color(0xFF059669)
                                                    "FACTURADO" -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.error
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF0284C7).copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = if (useBsNote) "Bs." else "USD",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0284C7),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                action = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (useBsNote) {
                                                "Bs. ${String.format(Locale.US, "%.2f", estBs)}"
                                            } else {
                                                "\$${String.format(Locale.US, "%.2f", note.totalEstimatedUsd)} USD"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF0284C7)
                                        )
                                        Text(
                                            text = if (useBsNote) {
                                                "Ref.: \$${String.format(Locale.US, "%.2f", note.totalEstimatedUsd)} USD"
                                            } else {
                                                "Tasa: Bs. ${String.format(Locale.US, "%.2f", note.rateBcv)}"
                                            },
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )

                            Text(
                                "${note.customerName} (${note.customerDocument})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (note.recipientName.isNotBlank() && note.recipientName != note.customerName) {
                                Text(
                                    "Recibe: ${note.recipientName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "Emisión: ${sdf.format(Date(note.timestamp))} • Cajero: ${note.cashierName.ifBlank { "Cajero" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                            ResponsiveActionRow(
                                modifier = Modifier.fillMaxWidth(),
                                breakpoint = 310.dp,
                                content = {
                                    TextButton(
                                        onClick = { onViewNote(note) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Recibo / WhatsApp / PDF", fontSize = 12.sp)
                                    }
                                },
                                action = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!isCancelled && note.status != "FACTURADO") {
                                            TextButton(
                                                onClick = { onConvertToInvoice(note) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Facturar", fontSize = 12.sp)
                                            }
                                        }
                                        if (!isCancelled) {
                                            TextButton(
                                                onClick = { noteToCancel = note },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Anular", fontSize = 12.sp)
                                            }
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

    noteToCancel?.let { note ->
        KeyboardAwareAlertDialog(
            onDismissRequest = { noteToCancel = null },
            icon = { Icon(Icons.Filled.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Anular Nota de Entrega?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se anulará la nota ${note.noteNumber} de ${note.customerName} y se devolverán los productos al inventario físico.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelNote(note)
                        noteToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Anular y Devolver Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToCancel = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
