package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CasheaSettlement
import com.example.data.local.PreferencesManager
import com.example.data.local.Product
import com.example.data.local.Sale
import com.example.ui.cashea.calculateCasheaMetrics
import com.example.ui.components.DateFilterUtils
import com.example.ui.dashboard.calculateInventoryValuation
import com.example.utils.AccountingLedgerCsvExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardAndCasheaTest {

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
            storeName = "Abastos Central"
            storeRif = "J-98765432-1"
            lastBcvRate = 50.0f
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun calculateInventoryValuation_computesCapitalAndRetailIgnoringCombosAndZeroStock() {
        val products = listOf(
            Product(id = 1, code = "001", name = "Arroz", category = "Víveres", priceUsd = 2.0, costPriceUsd = 1.25, stock = 10.0),
            Product(id = 2, code = "002", name = "Refresco", category = "Bebidas", priceUsd = 1.5, costPriceUsd = 0.90, stock = 20.0),
            Product(id = 3, code = "003", name = "Agotado", category = "Víveres", priceUsd = 5.0, costPriceUsd = 3.0, stock = 0.0),
            Product(id = 4, code = "004", name = "Combo Desayuno", category = "Combos", priceUsd = 10.0, costPriceUsd = 6.0, stock = 5.0, isCombo = true),
            Product(id = 5, code = "005", name = "Café Preparado", category = "Cafetería", priceUsd = 2.5, costPriceUsd = 0.8, stock = 100.0, isRecipe = true)
        )

        val valuation = calculateInventoryValuation(products)

        // Capital neto: (10 * 1.25) + (20 * 0.90) = 12.5 + 18.0 = 30.50
        assertEquals(30.50, valuation.netInvestedCapitalUsd, 0.001)
        // Valor venta total: (10 * 2.0) + (20 * 1.5) = 20.0 + 30.0 = 50.00
        assertEquals(50.00, valuation.totalRetailValueUsd, 0.001)
        assertEquals(30.0, valuation.totalStockUnits, 0.001)
        assertEquals(3, valuation.unitProductsCount)
        assertEquals(2, valuation.valuedProductsCount)
    }

    @Test
    fun saleDao_persistsPaymentCasheaUsdAndExportsIncludeCashea() = runBlocking {
        val sale = Sale(
            customerName = "Carlos Gómez",
            customerDocument = "V-20111222",
            timestamp = System.currentTimeMillis(),
            rateBcv = 50.0,
            totalUsd = 25.0,
            totalBs = 1250.0,
            paymentCashUsd = 0.0,
            paymentMobileBs = 0.0,
            paymentCardBs = 0.0,
            paymentCreditUsd = 0.0,
            paymentCasheaUsd = 25.0,
            baseTaxableUsd = 21.55,
            ivaUsd = 3.45
        )
        database.saleDao().insertSale(sale)

        val storedSales = database.saleDao().getAllSales().first()
        assertEquals(1, storedSales.size)
        assertEquals(25.0, storedSales.first().paymentCasheaUsd, 0.001)

        val csvFile = AccountingLedgerCsvExporter.exportLedgerToCsv(
            context = context,
            prefs = prefs,
            sales = storedSales,
            deliveryNotes = emptyList(),
            expenses = emptyList(),
            purchaseInvoices = emptyList(),
            periodName = "Prueba Cashea"
        )
        assertNotNull(csvFile)
        val csvContent = csvFile!!.readText()
        assertTrue(csvContent.contains("Cashea (Bs.)"))
        assertTrue(csvContent.contains("Total Recaudado en Cashea Bs."))
    }

    @Test
    fun casheaSettlementDao_andCalculateCasheaMetrics_subtractsDebtAndTracksCommissions() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MARCH, 10, 12, 0, 0)
        val day1 = cal.timeInMillis
        cal.set(2026, Calendar.MARCH, 15, 14, 0, 0)
        val day2 = cal.timeInMillis

        val sale1 = Sale(
            customerName = "Ana",
            customerDocument = "V-11111111",
            timestamp = day1,
            rateBcv = 50.0,
            totalUsd = 100.0,
            totalBs = 5000.0,
            paymentCashUsd = 0.0,
            paymentMobileBs = 0.0,
            paymentCardBs = 0.0,
            paymentCasheaUsd = 100.0
        )
        val sale2 = Sale(
            customerName = "Luis",
            customerDocument = "V-22222222",
            timestamp = day2,
            rateBcv = 50.0,
            totalUsd = 60.0,
            totalBs = 3000.0,
            paymentCashUsd = 0.0,
            paymentMobileBs = 0.0,
            paymentCardBs = 0.0,
            paymentCasheaUsd = 60.0
        )
        database.saleDao().insertSale(sale1)
        database.saleDao().insertSale(sale2)

        // Cashea pays $100 gross with 5% commission -> $5 unrealized, $95 net received
        val settlement = CasheaSettlement(
            settlementDate = day2,
            grossAmountUsd = 100.0,
            commissionPercentage = 5.0,
            commissionAmountUsd = 5.0,
            netReceivedUsd = 95.0,
            rateBcv = 50.0,
            notes = "Liquidación lote 1"
        )
        database.casheaSettlementDao().insertSettlement(settlement)

        val storedSales = database.saleDao().getAllSales().first()
        val storedSettlements = database.casheaSettlementDao().getAllSettlements().first()
        assertEquals(1, storedSettlements.size)

        val allMetrics = calculateCasheaMetrics(storedSales, storedSettlements, DateFilterUtils.allTime())
        assertEquals(160.0, allMetrics.globalCasheaBilledUsd, 0.001)
        assertEquals(100.0, allMetrics.globalSettledGrossUsd, 0.001)
        assertEquals(60.0, allMetrics.globalPendingDebtUsd, 0.001)
        assertEquals(5.0, allMetrics.globalCommissionUnrealizedUsd, 0.001)
        assertEquals(95.0, allMetrics.globalNetReceivedUsd, 0.001)

        // Range filter only matching day2
        val day2Filter = DateFilterUtils.singleDate(day2)
        val day2Metrics = calculateCasheaMetrics(storedSales, storedSettlements, day2Filter)
        assertEquals(60.0, day2Metrics.periodCasheaBilledUsd, 0.001)
        assertEquals(1, day2Metrics.periodCasheaSalesCount)
        assertEquals(100.0, day2Metrics.periodSettledGrossUsd, 0.001)
        assertEquals(5.0, day2Metrics.periodCommissionUnrealizedUsd, 0.001)
        assertEquals(95.0, day2Metrics.periodNetReceivedUsd, 0.001)
        assertEquals(5.0, day2Metrics.periodAvgCommissionPct, 0.001)
        // Global pending debt remains 60.0 regardless of period filter
        assertEquals(60.0, day2Metrics.globalPendingDebtUsd, 0.001)
    }

    @Test
    fun dateFilterUtils_singleDateAndRangeFiltersIncludeFullDayBounds() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.APRIL, 1, 0, 5, 0)
        val april1Early = cal.timeInMillis
        cal.set(2026, Calendar.APRIL, 5, 23, 55, 0)
        val april5Late = cal.timeInMillis
        cal.set(2026, Calendar.APRIL, 6, 0, 1, 0)
        val april6Early = cal.timeInMillis

        val rangeFilter = DateFilterUtils.dateRange(april5Late, april1Early) // reversed order handled
        assertTrue(rangeFilter.matches(april1Early))
        assertTrue(rangeFilter.matches(april5Late))
        assertFalse(rangeFilter.matches(april6Early))

        val singleFilter = DateFilterUtils.singleDate(april1Early)
        assertTrue(singleFilter.matches(april1Early))
        assertFalse(singleFilter.matches(april5Late))
    }
}
