package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.ui.components.TicketUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuoteModuleTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesManager(context).apply {
            storeName = "Comercial Prueba C.A."
            storeRif = "J-12345678-9"
            storeAddress = "Av. Principal #10"
            storePhone = "04141234567"
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun quoteDao_insertUpdateAndCascadeDelete_worksCorrectly() = runBlocking {
        val dao = database.quoteDao()
        val now = System.currentTimeMillis()
        val quote = Quote(
            quoteNumber = "PRE-00001",
            customerName = "María Pérez",
            customerDocument = "V-12345678",
            customerPhone = "04145556677",
            timestamp = now,
            validityDays = 7,
            validUntil = now + 7L * 24L * 60L * 60L * 1000L,
            rateBcv = 50.0,
            baseTaxableUsd = 10.0,
            baseExemptUsd = 5.0,
            ivaUsd = 1.60,
            discountUsd = 0.0,
            totalUsd = 16.60,
            totalBs = 830.0,
            notesOrConditions = "Válido por 7 días",
            status = "VIGENTE",
            cashierName = "Admin",
            cashRegisterNumber = "01"
        )

        val quoteId = dao.insertQuote(quote).toInt()
        assertTrue(quoteId > 0)

        val items = listOf(
            QuoteItem(quoteId = quoteId, productId = 1, productName = "Café 500g", quantity = 2.0, priceUsd = 5.80, isTaxExempt = false),
            QuoteItem(quoteId = quoteId, productId = 2, productName = "Harina PAN", quantity = 5.0, priceUsd = 1.00, isTaxExempt = true)
        )
        dao.insertQuoteItems(items)

        val savedQuotes = dao.getAllQuotes().first()
        assertEquals(1, savedQuotes.size)
        assertEquals("PRE-00001", savedQuotes[0].quoteNumber)
        assertEquals("04145556677", savedQuotes[0].customerPhone)

        val savedItems = dao.getItemsForQuoteDirect(quoteId)
        assertEquals(2, savedItems.size)

        dao.updateStatus(quoteId, "FACTURADO")
        val updated = dao.getAllQuotes().first().first()
        assertEquals("FACTURADO", updated.status)

        dao.deleteQuote(quoteId)
        assertTrue(dao.getAllQuotes().first().isEmpty())
        assertTrue(dao.getItemsForQuoteDirect(quoteId).isEmpty())
    }

    @Test
    fun quoteTicketGenerators_produceValidBitmapsAndFiles() {
        val now = System.currentTimeMillis()
        val quote = Quote(
            id = 42,
            quoteNumber = "PRE-00042",
            customerName = "Carlos Gómez",
            customerDocument = "V-98765432",
            customerPhone = "04129998877",
            timestamp = now,
            validityDays = 15,
            validUntil = now + 15L * 24L * 60L * 60L * 1000L,
            rateBcv = 55.0,
            baseTaxableUsd = 20.0,
            baseExemptUsd = 0.0,
            ivaUsd = 3.20,
            discountUsd = 1.00,
            totalUsd = 23.20,
            totalBs = 1276.0,
            notesOrConditions = "Precios sujetos a cambio.",
            status = "VIGENTE",
            cashierName = "Caja 1",
            cashRegisterNumber = "01"
        )
        val items = listOf(
            QuoteItem(id = 1, quoteId = 42, productId = 10, productName = "Teclado USB", quantity = 2.0, priceUsd = 11.60, isTaxExempt = false)
        )

        val thermalBitmap = TicketUtils.generateQuoteTicketBitmap(context, prefs, quote, items)
        assertTrue(thermalBitmap.width > 0)
        assertTrue(thermalBitmap.height > 0)

        val squareBitmap = TicketUtils.generateWhatsAppSquareQuoteBitmap(context, prefs, quote, items)
        assertEquals(800, squareBitmap.width)
        assertTrue(squareBitmap.height >= 800)

        val imageFile = TicketUtils.saveQuoteBitmapToFile(context, squareBitmap, quote.id)
        assertNotNull(imageFile)
        assertTrue(imageFile!!.exists())
        assertTrue(imageFile.length() > 0)
    }

    @Test
    fun repositoryCreateQuote_generatesSequentialNumber_andDoesNotDeductStock() = runBlocking {
        val repository = com.example.data.repository.AppRepository(database, prefs)
        val product = com.example.data.local.Product(
            code = "P-01",
            name = "Monitor 24\"",
            priceUsd = 150.0,
            costPriceUsd = 100.0,
            stock = 12.0,
            category = "Tecnología"
        )
        repository.insertProduct(product)
        val savedProduct = repository.allProducts.first().first()

        val num1 = repository.getNextQuoteNumber()
        assertEquals("PRE-00001", num1)

        val quoteId = repository.createQuote(
            Quote(
                quoteNumber = num1,
                customerName = "Empresa ABC",
                customerDocument = "J-30111222-0",
                totalUsd = 300.0,
                totalBs = 15000.0
            ),
            listOf(
                QuoteItem(
                    productId = savedProduct.id,
                    productName = savedProduct.name,
                    quantity = 2.0,
                    priceUsd = 150.0
                )
            )
        )
        assertTrue(quoteId > 0)

        val num2 = repository.getNextQuoteNumber()
        assertEquals("PRE-00002", num2)

        val stockAfterQuote = repository.allProducts.first().first().stock
        assertEquals(12.0, stockAfterQuote, 0.001)
    }
}
