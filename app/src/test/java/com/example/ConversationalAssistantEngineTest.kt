package com.example

import com.example.data.local.CasheaSettlement
import com.example.data.local.Customer
import com.example.data.local.Expense
import com.example.data.local.Product
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.data.repository.GeminiRepository
import com.example.ui.assistant.ConversationalAssistantEngine
import com.example.ui.assistant.PendingAssistantAction
import com.example.ui.assistant.ProcessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ConversationalAssistantEngineTest {

    private fun createEngine(): ConversationalAssistantEngine =
        ConversationalAssistantEngine(GeminiRepository())

    @Test
    fun processMessage_registerProductWithCodeProfitAndWholesale_parsesAllFields() = runBlocking {
        val engine = createEngine()
        val prompt = "Nuevo producto Café Amanecer código CAF-01 cantidad 12 costo 4.00 ganancia 50% al mayor 5.20"

        val result = engine.processMessage(
            rawText = prompt,
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0,
            minProfitMarginPercent = null
        )

        assertTrue(result is ProcessResult.ProposeAction)
        val confirmation = result as ProcessResult.ProposeAction
        val action = confirmation.action as PendingAssistantAction.CreateProductAction

        assertEquals("Café Amanecer", action.name)
        assertEquals("CAF-01", action.code)
        assertEquals(12.0, action.stock, 0.001)
        assertEquals(4.0, action.costPriceUsd, 0.001)
        assertEquals(50.0, action.profitPercentage, 0.001)
        assertEquals(6.0, action.priceUsd, 0.001) // 4.00 * (1 + 50%)
        assertEquals(5.2, action.wholesalePriceUsd, 0.001)
        assertFalse(action.autoCalculatedWithMinMargin)
    }

    @Test
    fun processMessage_registerProductWithCostAndMinMarginEnabled_autoCalculatesSalePrice() = runBlocking {
        val engine = createEngine()
        val prompt = "Agregar producto Harina Integral cantidad 20 precio costo 2.00"

        val result = engine.processMessage(
            rawText = prompt,
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0,
            minProfitMarginPercent = 30.0
        )

        assertTrue(result is ProcessResult.ProposeAction)
        val confirmation = result as ProcessResult.ProposeAction
        val action = confirmation.action as PendingAssistantAction.CreateProductAction

        assertEquals("Harina Integral", action.name)
        assertEquals(20.0, action.stock, 0.001)
        assertEquals(2.0, action.costPriceUsd, 0.001)
        assertEquals(2.6, action.priceUsd, 0.001) // 2.00 * 1.30
        assertEquals(30.0, action.profitPercentage, 0.001)
        assertTrue(action.autoCalculatedWithMinMargin)
        assertTrue(confirmation.proposalText.contains("Margen mínimo configurado"))
    }

    @Test
    fun processMessage_registerProductWithCostOnlyAndMinMarginDisabled_leavesSalePriceZeroForEditing() = runBlocking {
        val engine = createEngine()
        val prompt = "Crear producto Azúcar Morena stock 10 costo 1.50"

        val result = engine.processMessage(
            rawText = prompt,
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0,
            minProfitMarginPercent = null
        )

        assertTrue(result is ProcessResult.ProposeAction)
        val confirmation = result as ProcessResult.ProposeAction
        val action = confirmation.action as PendingAssistantAction.CreateProductAction

        assertEquals("Azúcar Morena", action.name)
        assertEquals(1.5, action.costPriceUsd, 0.001)
        assertEquals(0.0, action.priceUsd, 0.001)
        assertFalse(action.autoCalculatedWithMinMargin)
        assertTrue(confirmation.proposalText.contains("Pendiente"))
    }

    @Test
    fun processMessage_realtimeCorrectionOnPendingProduct_updatesFieldsInPlace() = runBlocking {
        val engine = createEngine()
        val pending = PendingAssistantAction.CreateProductAction(
            name = "Azúcar Morena",
            category = "General",
            stock = 10.0,
            costPriceUsd = 2.0,
            priceUsd = 2.5,
            isRecipe = false
        )

        val result = engine.processMessage(
            rawText = "Cambia el código a AZU-01, ponle ganancia 40% y precio al mayor 2.60",
            currentPendingAction = pending,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0
        )

        assertTrue(result is ProcessResult.ProposeAction)
        val updated = (result as ProcessResult.ProposeAction).action as PendingAssistantAction.CreateProductAction
        assertEquals("AZU-01", updated.code)
        assertEquals(40.0, updated.profitPercentage, 0.001)
        assertEquals(2.8, updated.priceUsd, 0.001)
        assertEquals(2.6, updated.wholesalePriceUsd, 0.001)
    }

    @Test
    fun processMessage_createQuoteAndRealtimeEdit_buildsAndModifiesQuote() = runBlocking {
        val engine = createEngine()
        val products = listOf(
            Product(id = 1, code = "HAR-1", name = "Harina Pan", priceUsd = 1.50, costPriceUsd = 1.00, stock = 50.0, category = "Víveres", isTaxExempt = true),
            Product(id = 2, code = "ACE-1", name = "Aceite Mazeite", priceUsd = 4.00, costPriceUsd = 2.50, stock = 20.0, category = "Víveres", isTaxExempt = false)
        )
        val customers = listOf(
            Customer(id = 1, name = "Carlos Pérez", documentId = "V-12345678", phone = "04141234567", address = "Centro")
        )

        val createResult = engine.processMessage(
            rawText = "Crear presupuesto para Carlos Pérez con 2 Harina Pan y 1 Aceite Mazeite validez 10 días",
            currentPendingAction = null,
            products = products,
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0,
            customers = customers
        )

        assertTrue(createResult is ProcessResult.ProposeAction)
        val quoteAction = (createResult as ProcessResult.ProposeAction).action as PendingAssistantAction.CreateQuoteAction
        assertEquals("Carlos Pérez", quoteAction.customerName)
        assertEquals("V-12345678", quoteAction.customerDocument)
        assertEquals("04141234567", quoteAction.customerPhone)
        assertEquals(10, quoteAction.validityDays)
        assertEquals(2, quoteAction.items.size)
        assertEquals(7.00, quoteAction.totalUsd, 0.01) // 2*1.50 (exempt) + 4.00 (taxable, IVA included)

        // Now correct the pending quote in real time!
        val editResult = engine.processMessage(
            rawText = "Cambia la validez a 15 días y descuento de 1 dolar",
            currentPendingAction = quoteAction,
            products = products,
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0,
            customers = customers
        )

        assertTrue(editResult is ProcessResult.ProposeAction)
        val editedQuote = (editResult as ProcessResult.ProposeAction).action as PendingAssistantAction.CreateQuoteAction
        assertEquals(15, editedQuote.validityDays)
        assertEquals(1.0, editedQuote.discountUsd, 0.001)
        assertTrue(editedQuote.totalUsd < quoteAction.totalUsd)
    }

    @Test
    fun processMessage_registerExpenseAndBcvUpdateAndNavigation_resolveCorrectly() = runBlocking {
        val engine = createEngine()

        val expenseRes = engine.processMessage(
            rawText = "Registrar gasto de 30 dolares por internet",
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0
        )
        assertTrue(expenseRes is ProcessResult.ProposeAction)
        val expenseAction = (expenseRes as ProcessResult.ProposeAction).action as PendingAssistantAction.RegisterExpenseAction
        assertEquals(30.0, expenseAction.amountUsd, 0.001)
        assertEquals("Servicios", expenseAction.category)

        val bcvRes = engine.processMessage(
            rawText = "Actualizar tasa BCV a 58.40",
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0
        )
        assertTrue(bcvRes is ProcessResult.ProposeAction)
        val bcvAction = (bcvRes as ProcessResult.ProposeAction).action as PendingAssistantAction.UpdateBcvRateAction
        assertEquals(58.4, bcvAction.newRateBs, 0.001)

        val navRes = engine.processMessage(
            rawText = "Ir a presupuestos",
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = emptyList(),
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0
        )
        assertTrue(navRes is ProcessResult.Navigate)
        assertEquals("quotes", (navRes as ProcessResult.Navigate).route)
    }

    @Test
    fun formatCreateProductProposal_includesCodeProfitAndWholesale() {
        val action = PendingAssistantAction.CreateProductAction(
            name = "Aceite Vegetal",
            category = "Víveres",
            stock = 15.0,
            costPriceUsd = 2.0,
            priceUsd = 3.0,
            isRecipe = false,
            code = "ACE-100",
            profitPercentage = 50.0,
            wholesalePriceUsd = 2.6,
            autoCalculatedWithMinMargin = true
        )

        val formatted = ConversationalAssistantEngine.formatCreateProductProposal(action, bcvRate = 40.0)

        assertTrue(formatted.contains("ACE-100"))
        assertTrue(formatted.contains("50.0%"))
        assertTrue(formatted.contains("3.00"))
        assertTrue(formatted.contains("2.60"))
        assertTrue(formatted.contains("Margen mínimo configurado"))
    }

    @Test
    fun processMessage_lastInvoiceAndDateRangeWithFollowUp_returnsItemsAndSpecificInvoice() = runBlocking {
        val engine = createEngine()
        val now = System.currentTimeMillis()
        val sales = listOf(
            Sale(
                id = 101,
                customerName = "María López",
                customerDocument = "V-11111111",
                timestamp = now - 3600_000L,
                rateBcv = 50.0,
                totalUsd = 10.0,
                totalBs = 500.0,
                paymentCashUsd = 10.0,
                paymentMobileBs = 0.0,
                paymentCardBs = 0.0
            ),
            Sale(
                id = 102,
                customerName = "Carlos Ruiz",
                customerDocument = "V-22222222",
                timestamp = now - 60_000L,
                rateBcv = 50.0,
                totalUsd = 25.0,
                totalBs = 1250.0,
                paymentCashUsd = 0.0,
                paymentMobileBs = 0.0,
                paymentCardBs = 0.0,
                paymentCasheaUsd = 25.0
            )
        )
        val saleItems = listOf(
            SaleItem(id = 1, saleId = 101, productId = 1, productName = "Harina Pan", quantity = 5.0, priceUsd = 2.0),
            SaleItem(id = 2, saleId = 102, productId = 2, productName = "Aceite Mazeite", quantity = 5.0, priceUsd = 5.0)
        )

        // 1. Last invoice detail
        val lastResult = engine.processMessage(
            rawText = "Detalle de la última factura",
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = sales,
            bcvRate = 50.0,
            todaySalesTotalUsd = 35.0,
            todaySalesTotalBs = 1750.0,
            todaySalesCount = 2,
            saleItems = saleItems
        )
        assertTrue(lastResult is ProcessResult.DirectReply)
        val lastText = (lastResult as ProcessResult.DirectReply).text
        assertTrue(lastText.contains("#102"))
        assertTrue(lastText.contains("Carlos Ruiz"))
        assertTrue(lastText.contains("Aceite Mazeite"))
        assertTrue(lastText.contains("Cashea"))

        // 2. Date summary (hoy) -> numbered list (#102 first, #101 second)
        val listResult = engine.processMessage(
            rawText = "Resumen de facturas de hoy",
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = sales,
            bcvRate = 50.0,
            todaySalesTotalUsd = 35.0,
            todaySalesTotalBs = 1750.0,
            todaySalesCount = 2,
            saleItems = saleItems
        )
        assertTrue(listResult is ProcessResult.DirectReply)
        val listText = (listResult as ProcessResult.DirectReply).text
        assertTrue(listText.contains("2 factura(s)"))
        assertTrue(listText.contains("#102"))
        assertTrue(listText.contains("#101"))

        // 3. Follow-up inspection ("muéstrame la 2" -> invoice #101 María López with Harina Pan)
        val followUpResult = engine.processMessage(
            rawText = "muéstrame la 2",
            currentPendingAction = null,
            products = emptyList(),
            suppliers = emptyList(),
            sales = sales,
            bcvRate = 50.0,
            todaySalesTotalUsd = 35.0,
            todaySalesTotalBs = 1750.0,
            todaySalesCount = 2,
            saleItems = saleItems
        )
        assertTrue(followUpResult is ProcessResult.DirectReply)
        val followUpText = (followUpResult as ProcessResult.DirectReply).text
        assertTrue(followUpText.contains("#101"))
        assertTrue(followUpText.contains("María López"))
        assertTrue(followUpText.contains("Harina Pan"))
    }

    @Test
    fun processMessage_casheaInventoryValuationAndPriceUpdate_workLocally() = runBlocking {
        val engine = createEngine()
        val products = listOf(
            Product(id = 1, code = "HAR-1", name = "Harina Pan", priceUsd = 1.50, costPriceUsd = 1.00, stock = 10.0, category = "Víveres")
        )
        val sales = listOf(
            Sale(
                id = 1,
                customerName = "Ana",
                customerDocument = "V-1",
                timestamp = System.currentTimeMillis(),
                rateBcv = 50.0,
                totalUsd = 100.0,
                totalBs = 5000.0,
                paymentCashUsd = 0.0,
                paymentMobileBs = 0.0,
                paymentCardBs = 0.0,
                paymentCasheaUsd = 100.0,
                paymentCreditUsd = 40.0,
                isCreditPaid = false
            )
        )
        val settlements = listOf(
            CasheaSettlement(
                id = 1,
                settlementDate = System.currentTimeMillis(),
                grossAmountUsd = 60.0,
                commissionPercentage = 10.0,
                commissionAmountUsd = 6.0,
                netReceivedUsd = 54.0
            )
        )

        val casheaRes = engine.processMessage(
            rawText = "Resumen de Cashea",
            currentPendingAction = null,
            products = products,
            suppliers = emptyList(),
            sales = sales,
            bcvRate = 50.0,
            todaySalesTotalUsd = 100.0,
            todaySalesTotalBs = 5000.0,
            todaySalesCount = 1,
            casheaSettlements = settlements
        )
        assertTrue(casheaRes is ProcessResult.DirectReply)
        val casheaText = (casheaRes as ProcessResult.DirectReply).text
        assertTrue(casheaText.contains("100.00"))
        assertTrue(casheaText.contains("40.00")) // pending debt
        assertTrue(casheaText.contains("6.00"))  // commissions

        val valRes = engine.processMessage(
            rawText = "Valor del inventario",
            currentPendingAction = null,
            products = products,
            suppliers = emptyList(),
            sales = sales,
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0
        )
        assertTrue(valRes is ProcessResult.DirectReply)
        val valText = (valRes as ProcessResult.DirectReply).text
        assertTrue(valText.contains("10.00")) // cost valuation
        assertTrue(valText.contains("15.00")) // retail valuation

        val priceRes = engine.processMessage(
            rawText = "Cambia el precio de Harina Pan a $2.25",
            currentPendingAction = null,
            products = products,
            suppliers = emptyList(),
            sales = sales,
            bcvRate = 50.0,
            todaySalesTotalUsd = 0.0,
            todaySalesTotalBs = 0.0,
            todaySalesCount = 0
        )
        assertTrue(priceRes is ProcessResult.ProposeAction)
        val priceAction = (priceRes as ProcessResult.ProposeAction).action as PendingAssistantAction.UpdateProductPriceAction
        assertEquals(1, priceAction.productId)
        assertEquals(2.25, priceAction.newPriceUsd, 0.001)
    }
}
