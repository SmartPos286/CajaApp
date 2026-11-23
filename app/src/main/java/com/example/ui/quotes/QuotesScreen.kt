package com.example.ui.quotes

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.local.Customer
import com.example.data.local.Product
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.ui.CartItem
import com.example.ui.PosViewModel
import com.example.ui.components.KeyboardAwareAlertDialog
import com.example.ui.components.KeyboardAwareDialog
import com.example.ui.components.QuoteTicketDialog
import com.example.ui.components.ResponsiveActionRow
import com.example.ui.components.ResponsivePair
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.pos.CustomerSelectorCard
import com.example.ui.pos.EditCartItemDialog
import com.example.ui.pos.EditRateDialog
import com.example.ui.pos.ProductQuickGridItem
import com.example.ui.pos.QuickAddCustomerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuotesScreen(
    viewModel: PosViewModel,
    onNavigateToPos: () -> Unit = {}
) {
    val context = LocalContext.current
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val quoteCart by viewModel.quoteCart.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val quotes by viewModel.allQuotes.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Crear Presupuesto, 1: Historial
    var customerName by remember { mutableStateOf("") }
    var customerDoc by remember { mutableStateOf("") }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showEditRateDialog by remember { mutableStateOf(false) }
    var showCompleteQuoteDialog by remember { mutableStateOf(false) }
    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var completedQuotePair by remember { mutableStateOf<Pair<Quote, List<QuoteItem>>?>(null) }
    var selectedHistoryQuote by remember { mutableStateOf<Quote?>(null) }

    val selectedCustomerPhone = remember(customerName, customerDoc, customers) {
        val matched = customers.find {
            (customerDoc.isNotBlank() && it.documentId.equals(customerDoc, ignoreCase = true)) ||
                (customerName.isNotBlank() && it.name.equals(customerName, ignoreCase = true))
        }
        matched?.phone.orEmpty()
    }

    val filteredProducts = remember(products, searchQuery) {
        products.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredCustomers = remember(customers, customerDoc) {
        customers.filter {
            it.documentId.contains(customerDoc, ignoreCase = true) ||
                it.name.contains(customerDoc, ignoreCase = true)
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Top Mode Selector Bar
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
                        leadingIcon = { Icon(Icons.Filled.RequestQuote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(
                                "Crear Presupuesto",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quotes_tab_create")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(
                                "Guardados (${quotes.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quotes_tab_history")
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
                        // Left Column: Catalog, Customer, Rate
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
                                QuoteRateAndModeBar(
                                    bcvRate = bcvRate,
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

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Buscar producto por nombre o código...") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("quote_search_input")
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
                                        "Toca para cotizar",
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
                                                onAddToCart = { viewModel.addToQuoteCart(product) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right Column: Quote Cart & Complete
                        Card(
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            border = BorderStroke(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.25f))
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
                                        Icon(Icons.Filled.RequestQuote, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                                        Text(
                                            "Presupuesto (${quoteCart.sumOf { it.quantity }}):",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    if (quoteCart.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearQuoteCart() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (quoteCart.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Filled.RequestQuote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.size(38.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Presupuesto vacío", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Toca un producto para agregarlo a la cotización", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(quoteCart) { item ->
                                            QuoteCartItemRow(
                                                item = item,
                                                bcvRate = bcvRate,
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

                                QuoteTotalsSection(
                                    cart = quoteCart,
                                    bcvRate = bcvRate,
                                    onCompleteClick = { showCompleteQuoteDialog = true }
                                )
                            }
                        }
                    }
                } else {
                    // Portrait layout mirroring PosScreen
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            QuoteRateAndModeBar(
                                bcvRate = bcvRate,
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
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    isDropdownExpanded = true
                                },
                                label = { Text("Buscar producto por nombre o código...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth()
                                    .testTag("quote_search_input")
                            )
                            if (searchQuery.isNotEmpty() && filteredProducts.isNotEmpty()) {
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
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(product.name, fontWeight = FontWeight.Bold)
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text(
                                                                if (product.isRecipe) "Código: ${product.code} • Receta" else "Código: ${product.code} • Stock: ${product.stock}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            if (product.wholesalePriceUsd > 0.0) {
                                                                Text(
                                                                    "Mayor: \$${String.format("%.2f", product.wholesalePriceUsd)}",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = Color(0xFF2563EB)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        "\$${String.format("%.2f", product.priceUsd)}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.addToQuoteCart(product)
                                                searchQuery = ""
                                                isDropdownExpanded = false
                                            }
                                        )
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
                                "Productos en Presupuesto (${quoteCart.sumOf { it.quantity }}):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (quoteCart.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearQuoteCart() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }

                        if (quoteCart.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.RequestQuote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "El presupuesto está vacío",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Busca productos arriba para armar la cotización del cliente",
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
                                items(quoteCart) { item ->
                                    QuoteCartItemRow(
                                        item = item,
                                        bcvRate = bcvRate,
                                        viewModel = viewModel,
                                        onEditClick = { editingCartItem = item }
                                    )
                                }
                            }
                        }

                        if (quoteCart.isNotEmpty()) {
                            val taxableItems = quoteCart.filter { !it.product.isTaxExempt }
                            val exemptItems = quoteCart.filter { it.product.isTaxExempt }
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
                                border = BorderStroke(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                            "IVA (16%): \$${String.format("%.2f", ivaUsd)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF1D4ED8),
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

                                    ResponsiveActionRow(
                                        breakpoint = 320.dp,
                                        verticalSpacing = 4.dp,
                                        stackedActionAlignment = Alignment.Start,
                                        content = {
                                            Text(
                                                "Total Presupuesto:",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        },
                                        action = {
                                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    "\$${String.format("%.2f", totalUsd)} USD",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    color = Color(0xFF1D4ED8)
                                                )
                                                Text(
                                                    "• Bs. ${String.format("%.2f", totalBs)}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )

                                    Button(
                                        onClick = { showCompleteQuoteDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                            .height(46.dp)
                                            .testTag("complete_quote_button")
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Completar Presupuesto", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: SAVED QUOTES HISTORY
                SavedQuotesHistorySection(
                    quotes = quotes,
                    onViewQuote = { selectedHistoryQuote = it },
                    onConvertToSale = { quote ->
                        viewModel.loadQuoteToBillingCart(quote) {
                            Toast.makeText(context, "Presupuesto ${quote.quoteNumber} cargado en Facturación", Toast.LENGTH_SHORT).show()
                            onNavigateToPos()
                        }
                    },
                    onUpdateStatus = { quote, newStatus ->
                        viewModel.updateQuoteStatus(quote.id, newStatus)
                    },
                    onDeleteQuote = { quote ->
                        viewModel.deleteQuote(quote.id)
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
        EditCartItemDialog(
            item = editingCartItem!!,
            onDismiss = { editingCartItem = null },
            onConfirm = { quantity, price ->
                viewModel.updateQuoteCartItem(editingCartItem!!, quantity, price)
                editingCartItem = null
            },
            onDelete = {
                viewModel.removeFromQuoteCart(editingCartItem!!)
                editingCartItem = null
            }
        )
    }

    if (showCompleteQuoteDialog) {
        CompleteQuoteDialog(
            cart = quoteCart,
            bcvRate = bcvRate,
            customerName = customerName,
            customerDocument = customerDoc,
            initialPhone = selectedCustomerPhone,
            onDismiss = { showCompleteQuoteDialog = false },
            onConfirm = { finalName, finalDoc, finalPhone, validityDays, discountUsd, notes ->
                viewModel.createQuote(
                    customerName = finalName,
                    customerDocument = finalDoc,
                    customerPhone = finalPhone,
                    validityDays = validityDays,
                    discountUsd = discountUsd,
                    notesOrConditions = notes,
                    onSuccess = { savedQuote, savedItems ->
                        showCompleteQuoteDialog = false
                        customerName = ""
                        customerDoc = ""
                        completedQuotePair = Pair(savedQuote, savedItems)
                        Toast.makeText(context, "Presupuesto ${savedQuote.quoteNumber} generado", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    completedQuotePair?.let { (quote, items) ->
        QuoteTicketDialog(
            quote = quote,
            items = items,
            viewModel = viewModel,
            onConvertToSale = {
                viewModel.loadQuoteToBillingCart(quote) {
                    completedQuotePair = null
                    Toast.makeText(context, "Presupuesto ${quote.quoteNumber} cargado en Facturación", Toast.LENGTH_SHORT).show()
                    onNavigateToPos()
                }
            },
            onDismiss = { completedQuotePair = null }
        )
    }

    selectedHistoryQuote?.let { quote ->
        val items by viewModel.getItemsForQuote(quote.id).collectAsStateWithLifecycle(initialValue = emptyList())
        QuoteTicketDialog(
            quote = quote,
            items = items,
            viewModel = viewModel,
            onConvertToSale = {
                viewModel.loadQuoteToBillingCart(quote) {
                    selectedHistoryQuote = null
                    Toast.makeText(context, "Presupuesto ${quote.quoteNumber} cargado en Facturación", Toast.LENGTH_SHORT).show()
                    onNavigateToPos()
                }
            },
            onDismiss = { selectedHistoryQuote = null }
        )
    }
}

@Composable
private fun QuoteRateAndModeBar(
    bcvRate: Double,
    onEditRateClick: () -> Unit,
    onRefreshRateClick: () -> Unit
) {
    ResponsiveActionRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        breakpoint = 330.dp,
        verticalSpacing = 4.dp,
        stackedActionAlignment = Alignment.Start,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onEditRateClick() }
            ) {
                Icon(
                    Icons.Filled.CurrencyExchange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
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
                    modifier = Modifier.size(14.dp)
                )
            }
        },
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1D4ED8).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF1D4ED8)
                        )
                        Text(
                            text = "Sin descontar stock",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshRateClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar Tasa", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    )
}

@Composable
private fun QuoteCartItemRow(
    item: CartItem,
    bcvRate: Double,
    viewModel: PosViewModel,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
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
                Surface(
                    onClick = {
                        if (item.quantity > 1.0) {
                            viewModel.updateQuoteCartItem(item, item.quantity - 1.0, item.customPrice)
                        } else {
                            viewModel.removeFromQuoteCart(item)
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Remove, contentDescription = "Restar", modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format("%.2f", item.quantity),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                Surface(
                    onClick = {
                        viewModel.updateQuoteCartItem(item, item.quantity + 1.0, item.customPrice)
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1D4ED8).copy(alpha = 0.12f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Sumar", tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                    }
                }

                IconButton(
                    onClick = { viewModel.removeFromQuoteCart(item) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun QuoteTotalsSection(
    cart: List<CartItem>,
    bcvRate: Double,
    onCompleteClick: () -> Unit
) {
    val totalUsd = cart.sumOf { it.effectivePrice * it.quantity }
    val totalBs = totalUsd * bcvRate

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Total Estimado:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "\$${String.format("%.2f", totalUsd)} USD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1D4ED8)
                )
                Text(
                    "Bs. ${String.format("%.2f", totalBs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onCompleteClick,
            enabled = cart.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("complete_quote_button")
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Completar Presupuesto", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompleteQuoteDialog(
    cart: List<CartItem>,
    bcvRate: Double,
    customerName: String,
    customerDocument: String,
    initialPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (
        customerName: String,
        customerDocument: String,
        customerPhone: String,
        validityDays: Int,
        discountUsd: Double,
        notes: String
    ) -> Unit
) {
    var nameInput by remember { mutableStateOf(customerName.ifBlank { "Consumidor Final" }) }
    var docInput by remember { mutableStateOf(customerDocument.ifBlank { "V-00000000" }) }
    var phoneInput by remember { mutableStateOf(initialPhone) }
    var validityDaysStr by remember { mutableStateOf("7") }
    var discountUsdStr by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("Precios sujetos a disponibilidad y tasa BCV vigente.") }

    val validityDays = validityDaysStr.toIntOrNull()?.coerceIn(1, 365) ?: 7
    val rawDiscount = discountUsdStr.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    val taxableGrossUsd = cart.filter { !it.product.isTaxExempt }.sumOf { it.effectivePrice * it.quantity }
    val exemptGrossUsd = cart.filter { it.product.isTaxExempt }.sumOf { it.effectivePrice * it.quantity }
    val subtotalTaxableBase = taxableGrossUsd / 1.16
    val subtotalBeforeTax = subtotalTaxableBase + exemptGrossUsd
    val effectiveDiscount = minOf(rawDiscount, subtotalBeforeTax)
    val ratioTaxable = if (subtotalBeforeTax > 0.0001) subtotalTaxableBase / subtotalBeforeTax else 1.0
    val ratioExempt = if (subtotalBeforeTax > 0.0001) exemptGrossUsd / subtotalBeforeTax else 0.0
    val baseTaxableUsd = maxOf(0.0, subtotalTaxableBase - (effectiveDiscount * ratioTaxable))
    val baseExemptUsd = maxOf(0.0, exemptGrossUsd - (effectiveDiscount * ratioExempt))
    val ivaUsd = baseTaxableUsd * 0.16
    val totalUsd = baseTaxableUsd + baseExemptUsd + ivaUsd
    val totalBs = totalUsd * bcvRate

    KeyboardAwareDialog(
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
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
                        Icon(Icons.Filled.RequestQuote, contentDescription = null, tint = Color(0xFF1D4ED8))
                        Text("Completar Presupuesto", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Cliente") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                ResponsivePair(
                    breakpoint = 340.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    firstWeight = 1f,
                    secondWeight = 1.2f,
                    first = { mod ->
                        OutlinedTextField(
                            value = docInput,
                            onValueChange = { docInput = it },
                            label = { Text("CI / RIF") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus()
                        )
                    },
                    second = { mod ->
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("WhatsApp / Telf.") },
                            placeholder = { Text("04141234567") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod
                                .bringIntoViewOnFocus()
                                .testTag("quote_customer_phone_input")
                        )
                    }
                )

                ResponsivePair(
                    breakpoint = 340.dp,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    first = { mod ->
                        OutlinedTextField(
                            value = validityDaysStr,
                            onValueChange = { validityDaysStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Validez (días)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus()
                        )
                    },
                    second = { mod ->
                        OutlinedTextField(
                            value = discountUsdStr,
                            onValueChange = { discountUsdStr = it },
                            label = { Text("Descuento ($)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = mod.bringIntoViewOnFocus()
                        )
                    }
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(3, 7, 15, 30).forEach { days ->
                        SuggestionChip(
                            onClick = { validityDaysStr = days.toString() },
                            label = { Text("$days días", fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Observaciones / Condiciones") },
                    maxLines = 2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1D4ED8).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Base Imponible + Exento:", style = MaterialTheme.typography.bodySmall)
                            Text("\$${String.format("%.2f", baseTaxableUsd + baseExemptUsd)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("IVA (16%):", style = MaterialTheme.typography.bodySmall)
                            Text("\$${String.format("%.2f", ivaUsd)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL PRESUPUESTO:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format("%.2f", totalUsd)} USD", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                Text("Bs. ${String.format("%.2f", totalBs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

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
                            onConfirm(
                                nameInput.ifBlank { "Consumidor Final" },
                                docInput.ifBlank { "V-00000000" },
                                phoneInput.trim(),
                                validityDays,
                                effectiveDiscount,
                                notesInput.trim()
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("confirm_create_quote_button")
                    ) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generar y Enviar Ticket", fontWeight = FontWeight.Bold)
                    }
                }
            }
    }
}

@Composable
private fun SavedQuotesHistorySection(
    quotes: List<Quote>,
    onViewQuote: (Quote) -> Unit,
    onConvertToSale: (Quote) -> Unit,
    onUpdateStatus: (Quote, String) -> Unit,
    onDeleteQuote: (Quote) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("TODOS") }
    var quoteToDelete by remember { mutableStateOf<Quote?>(null) }

    val filteredQuotes = remember(quotes, searchQuery, statusFilter) {
        quotes.filter { q ->
            val matchesQuery = searchQuery.isBlank() ||
                q.quoteNumber.contains(searchQuery, ignoreCase = true) ||
                q.customerName.contains(searchQuery, ignoreCase = true) ||
                q.customerDocument.contains(searchQuery, ignoreCase = true)
            val matchesStatus = statusFilter == "TODOS" || q.status == statusFilter
            matchesQuery && matchesStatus
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por N° presupuesto, cliente o CI/RIF...") },
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("TODOS", "VIGENTE", "ACEPTADO", "FACTURADO", "ANULADO")) { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = status },
                    label = { Text(status, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        if (filteredQuotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.RequestQuote,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No hay presupuestos guardados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Crea un presupuesto para enviarlo por WhatsApp o convertirlo en factura.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredQuotes, key = { it.id }) { quote ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewQuote(quote) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        quote.quoteNumber.ifBlank { "PRE-${quote.id}" },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF1D4ED8)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (quote.status) {
                                            "ACEPTADO" -> Color(0xFF059669).copy(alpha = 0.15f)
                                            "FACTURADO" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                            "ANULADO" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            else -> Color(0xFF2563EB).copy(alpha = 0.14f)
                                        }
                                    ) {
                                        Text(
                                            text = quote.status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (quote.status) {
                                                "ACEPTADO" -> Color(0xFF059669)
                                                "FACTURADO" -> MaterialTheme.colorScheme.primary
                                                "ANULADO" -> MaterialTheme.colorScheme.error
                                                else -> Color(0xFF1D4ED8)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "\$${String.format("%.2f", quote.totalUsd)} USD",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF1D4ED8)
                                    )
                                    Text(
                                        "Bs. ${String.format("%.2f", quote.totalBs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text("${quote.customerName} (${quote.customerDocument})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (quote.customerPhone.isNotBlank()) {
                                Text("WhatsApp: ${quote.customerPhone}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF059669))
                            }
                            Text(
                                "Emisión: ${sdf.format(Date(quote.timestamp))} • Válido hasta: ${if (quote.validUntil > 0L) sdfDate.format(Date(quote.validUntil)) else "${quote.validityDays} días"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                            if (quote.notesOrConditions.isNotBlank()) {
                                Text(
                                    quote.notesOrConditions,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                            ResponsiveActionRow(
                                modifier = Modifier.fillMaxWidth(),
                                breakpoint = 320.dp,
                                verticalSpacing = 6.dp,
                                stackedActionAlignment = Alignment.End,
                                content = {
                                    TextButton(
                                        onClick = { onViewQuote(quote) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ticket / WhatsApp", fontSize = 12.sp)
                                    }
                                },
                                action = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (quote.status != "ANULADO" && quote.status != "FACTURADO") {
                                            TextButton(
                                                onClick = { onConvertToSale(quote) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Facturar", fontSize = 12.sp)
                                            }
                                        }
                                        if (quote.status == "VIGENTE") {
                                            IconButton(
                                                onClick = { onUpdateStatus(quote, "ACEPTADO") },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = "Marcar Aceptado", tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(
                                            onClick = { quoteToDelete = quote },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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

    quoteToDelete?.let { quote ->
        KeyboardAwareAlertDialog(
            onDismissRequest = { quoteToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar Presupuesto?", fontWeight = FontWeight.Bold) },
            text = { Text("Se eliminará el presupuesto ${quote.quoteNumber} de ${quote.customerName}. Esta acción no afecta el inventario.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteQuote(quote)
                        quoteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { quoteToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
