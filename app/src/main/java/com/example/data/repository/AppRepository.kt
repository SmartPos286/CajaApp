package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.CasheaSettlement
import com.example.data.local.Customer
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.Expense
import com.example.data.local.HeldSale
import com.example.data.local.HeldSaleItem
import com.example.data.local.PosTerminalRecord
import com.example.data.local.PreferencesManager
import com.example.data.local.Product
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.data.local.Supplier
import com.example.data.local.PurchaseInvoice
import com.example.data.local.User
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val database: AppDatabase,
    val prefs: PreferencesManager
) {
    // Users
    val allUsers: Flow<List<User>> = database.userDao().getAllUsers()
    suspend fun insertUser(user: User) = database.userDao().insertUser(user)
    suspend fun updateUser(user: User) = database.userDao().updateUser(user)
    suspend fun deleteUser(user: User) = database.userDao().deleteUser(user)
    suspend fun getUserById(id: Int) = database.userDao().getUserById(id)
    suspend fun getUserByUsername(username: String) = database.userDao().getUserByUsername(username)
    suspend fun getUsersCount() = database.userDao().getUsersCount()

    // Suppliers
    val allSuppliers: Flow<List<Supplier>> = database.supplierDao().getAllSuppliers()
    suspend fun insertSupplier(supplier: Supplier) = database.supplierDao().insertSupplier(supplier)
    suspend fun insertSupplierAndGetId(supplier: Supplier): Long = database.supplierDao().insertSupplierAndGetId(supplier)

    // Purchase Invoices
    val allPurchaseInvoices: Flow<List<PurchaseInvoice>> = database.purchaseInvoiceDao().getAllInvoices()
    suspend fun insertPurchaseInvoice(invoice: PurchaseInvoice) = database.purchaseInvoiceDao().insertInvoice(invoice)
    suspend fun updatePurchaseInvoice(invoice: PurchaseInvoice) = database.purchaseInvoiceDao().updateInvoice(invoice)
    suspend fun updatePurchaseInvoicePayment(id: Int, isPaid: Boolean, paidTimestamp: Long, notes: String = "") =
        database.purchaseInvoiceDao().updatePaymentStatus(id, isPaid, paidTimestamp, notes)
    suspend fun updatePurchaseInvoiceWithholding(id: Int, voucherNumber: String, status: String) =
        database.purchaseInvoiceDao().updateWithholding(id, voucherNumber, status)
    suspend fun deletePurchaseInvoice(id: Int) = database.purchaseInvoiceDao().deleteInvoiceById(id)

    // Products
    val allProducts: Flow<List<Product>> = database.productDao().getAllProducts()
    fun searchProducts(query: String) = database.productDao().searchProducts(query)
    suspend fun insertProduct(product: Product) = database.productDao().insertProduct(product)
    suspend fun insertProducts(products: List<Product>) = database.productDao().insertProducts(products)
    suspend fun updateProduct(product: Product) = database.productDao().updateProduct(product)
    suspend fun deleteProduct(id: Int) = database.productDao().deleteProductById(id)
    suspend fun deleteAllProducts() = database.productDao().deleteAllProducts()
    suspend fun getProductByCode(code: String) = database.productDao().getProductByCode(code)
    suspend fun getProductByName(name: String) = database.productDao().getProductByName(name)

    // Customers
    val allCustomers: Flow<List<Customer>> = database.customerDao().getAllCustomers()
    suspend fun getCustomerByDocument(doc: String) = database.customerDao().getCustomerByDocument(doc)
    suspend fun insertCustomer(customer: Customer) = database.customerDao().insertCustomer(customer)

    // Sales
    val allSales: Flow<List<Sale>> = database.saleDao().getAllSales()
    val allSaleItems: Flow<List<SaleItem>> = database.saleDao().getAllSaleItems()
    val allCreditSales: Flow<List<Sale>> = database.saleDao().getCreditSales()
    fun getItemsForSale(saleId: Int) = database.saleDao().getItemsForSale(saleId)
    fun getItemsForSales(saleIds: List<Int>) = database.saleDao().getItemsForSales(saleIds)
    suspend fun getSaleById(saleId: Int): Sale? = database.saleDao().getSaleById(saleId)
    suspend fun updateSale(sale: Sale) = database.saleDao().updateSale(sale)
    suspend fun insertSale(sale: Sale): Long = database.saleDao().insertSale(sale)
    suspend fun insertSaleItems(items: List<SaleItem>) = database.saleDao().insertSaleItems(items)
    suspend fun updateCreditPayment(saleId: Int, isPaid: Boolean, paidAmount: Double, notes: String = "") =
        database.saleDao().updateCreditPayment(saleId, isPaid, paidAmount, notes)
    suspend fun updateSaleWithholding(saleId: Int, voucherNumber: String, status: String) =
        database.saleDao().updateWithholding(saleId, voucherNumber, status)

    suspend fun processSale(sale: Sale, items: List<SaleItem>) {
        val saleId = database.saleDao().insertSale(sale)
        val itemsWithId = items.map { it.copy(saleId = saleId.toInt()) }
        database.saleDao().insertSaleItems(itemsWithId)

        // Deduct inventory
        for (item in items) {
            val product = database.productDao().getProductById(item.productId)
            if (product != null && !product.isRecipe) {
                if (product.isCombo) {
                    val components = product.getComboComponents()
                    for (comp in components) {
                        val subProduct = database.productDao().getProductById(comp.productId)
                        if (subProduct != null && !subProduct.isRecipe) {
                            val qtyToDeduct = comp.quantity * item.quantity
                            val newStock = maxOf(0.0, subProduct.stock - qtyToDeduct)
                            database.productDao().updateProduct(subProduct.copy(stock = newStock))
                        }
                    }
                } else {
                    database.productDao().updateProduct(product.copy(stock = maxOf(0.0, product.stock - item.quantity)))
                }
            }
        }
    }

    // Held Sales (Facturas en Espera / Guardadas)
    val allHeldSales: Flow<List<HeldSale>> = database.heldSaleDao().getAllHeldSales()
    fun getItemsForHeldSale(heldSaleId: Int) = database.heldSaleDao().getItemsForHeldSale(heldSaleId)
    suspend fun getItemsForHeldSaleDirect(heldSaleId: Int) = database.heldSaleDao().getItemsForHeldSaleDirect(heldSaleId)

    suspend fun saveHeldSale(heldSale: HeldSale, items: List<HeldSaleItem>): Long {
        val heldSaleId = database.heldSaleDao().insertHeldSale(heldSale)
        val itemsWithId = items.map { it.copy(heldSaleId = heldSaleId.toInt()) }
        database.heldSaleDao().insertHeldSaleItems(itemsWithId)
        return heldSaleId
    }

    suspend fun deleteHeldSale(heldSaleId: Int) {
        database.heldSaleDao().deleteHeldSaleWithItems(heldSaleId)
    }

    // Delivery Notes (Notas de Entrega sin Facturar)
    val allDeliveryNotes: Flow<List<DeliveryNote>> = database.deliveryNoteDao().getAllDeliveryNotes()
    fun getItemsForDeliveryNote(noteId: Int) = database.deliveryNoteDao().getItemsForDeliveryNote(noteId)
    suspend fun getItemsForDeliveryNoteDirect(noteId: Int) = database.deliveryNoteDao().getItemsForDeliveryNoteDirect(noteId)

    suspend fun getNextDeliveryNoteNumber(): String {
        val count = database.deliveryNoteDao().getDeliveryNotesCount()
        return "NE-${String.format("%05d", count + 1)}"
    }

    suspend fun processDeliveryNote(note: DeliveryNote, items: List<DeliveryNoteItem>): Long {
        val noteId = database.deliveryNoteDao().insertDeliveryNote(note)
        val itemsWithId = items.map { it.copy(deliveryNoteId = noteId.toInt()) }
        database.deliveryNoteDao().insertDeliveryNoteItems(itemsWithId)

        // Deduct inventory for delivered goods
        for (item in items) {
            val product = database.productDao().getProductById(item.productId)
            if (product != null && !product.isRecipe) {
                if (product.isCombo) {
                    val components = product.getComboComponents()
                    for (comp in components) {
                        val subProduct = database.productDao().getProductById(comp.productId)
                        if (subProduct != null && !subProduct.isRecipe) {
                            val qtyToDeduct = comp.quantity * item.quantity
                            val newStock = maxOf(0.0, subProduct.stock - qtyToDeduct)
                            database.productDao().updateProduct(subProduct.copy(stock = newStock))
                        }
                    }
                } else {
                    database.productDao().updateProduct(product.copy(stock = maxOf(0.0, product.stock - item.quantity)))
                }
            }
        }
        return noteId
    }

    suspend fun cancelDeliveryNote(noteId: Int) {
        val items = database.deliveryNoteDao().getItemsForDeliveryNoteDirect(noteId)
        // Return inventory
        for (item in items) {
            val product = database.productDao().getProductById(item.productId)
            if (product != null && !product.isRecipe) {
                if (product.isCombo) {
                    val components = product.getComboComponents()
                    for (comp in components) {
                        val subProduct = database.productDao().getProductById(comp.productId)
                        if (subProduct != null && !subProduct.isRecipe) {
                            val qtyToReturn = comp.quantity * item.quantity
                            database.productDao().updateProduct(subProduct.copy(stock = subProduct.stock + qtyToReturn))
                        }
                    }
                } else {
                    database.productDao().updateProduct(product.copy(stock = product.stock + item.quantity))
                }
            }
        }
        database.deliveryNoteDao().updateStatus(noteId, "ANULADO")
    }

    suspend fun markDeliveryNoteAsInvoiced(noteId: Int) {
        database.deliveryNoteDao().updateStatus(noteId, "FACTURADO")
    }

    // Quotes (Presupuestos / Cotizaciones)
    val allQuotes: Flow<List<Quote>> = database.quoteDao().getAllQuotes()
    fun getItemsForQuote(quoteId: Int) = database.quoteDao().getItemsForQuote(quoteId)
    suspend fun getItemsForQuoteDirect(quoteId: Int) = database.quoteDao().getItemsForQuoteDirect(quoteId)

    suspend fun getNextQuoteNumber(): String {
        val count = database.quoteDao().getQuotesCount()
        return "PRE-${String.format("%05d", count + 1)}"
    }

    suspend fun createQuote(quote: Quote, items: List<QuoteItem>): Long {
        val quoteId = database.quoteDao().insertQuote(quote)
        val itemsWithId = items.map { it.copy(quoteId = quoteId.toInt()) }
        database.quoteDao().insertQuoteItems(itemsWithId)
        return quoteId
    }

    suspend fun updateQuoteStatus(quoteId: Int, status: String) {
        database.quoteDao().updateStatus(quoteId, status)
    }

    suspend fun deleteQuote(quoteId: Int) {
        database.quoteDao().deleteQuote(quoteId)
    }

    // Expenses (Gastos Operativos del Negocio)
    val allExpenses: Flow<List<Expense>> = database.expenseDao().getAllExpenses()
    suspend fun insertExpense(expense: Expense) = database.expenseDao().insertExpense(expense)
    suspend fun deleteExpense(expense: Expense) = database.expenseDao().deleteExpense(expense)
    suspend fun deleteExpenseById(id: Int) = database.expenseDao().deleteExpenseById(id)

    // Cashea Settlements (Liquidaciones y Comisiones de Cashea)
    val allCasheaSettlements: Flow<List<CasheaSettlement>> = database.casheaSettlementDao().getAllSettlements()
    suspend fun insertCasheaSettlement(settlement: CasheaSettlement) = database.casheaSettlementDao().insertSettlement(settlement)
    suspend fun deleteCasheaSettlement(settlement: CasheaSettlement) = database.casheaSettlementDao().deleteSettlement(settlement)
    suspend fun deleteCasheaSettlementById(id: Int) = database.casheaSettlementDao().deleteSettlementById(id)

    // POS Terminal Voucher Records (Registros de Puntos de Venta)
    val allPosTerminalRecords: Flow<List<PosTerminalRecord>> = database.posTerminalRecordDao().getAllRecords()
    suspend fun insertPosTerminalRecord(record: PosTerminalRecord): Long = database.posTerminalRecordDao().insertRecord(record)
    suspend fun updatePosTerminalRecord(record: PosTerminalRecord) = database.posTerminalRecordDao().updateRecord(record)
    suspend fun deletePosTerminalRecord(record: PosTerminalRecord) = database.posTerminalRecordDao().deleteRecord(record)
    suspend fun deletePosTerminalRecordById(id: Int) = database.posTerminalRecordDao().deleteRecordById(id)

    // Rate
    fun updateRate(newRate: Double) {
        prefs.lastBcvRate = newRate.toFloat()
    }

    suspend fun getBcvRate(): Double = fetchCurrentBcvRate()

    suspend fun fetchCurrentBcvRate(): Double {
        return try {
            val response = RetrofitClient.dolarVzlaService.getCurrentRate()
            val rate = response.current?.usd ?: 1.0
            prefs.lastBcvRate = rate.toFloat()
            rate
        } catch (e: Exception) {
            prefs.lastBcvRate.toDouble()
        }
    }
}
