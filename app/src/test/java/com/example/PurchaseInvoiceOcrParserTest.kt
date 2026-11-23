package com.example

import com.example.data.local.Product
import com.example.data.local.Supplier
import com.example.utils.PurchaseInvoiceOcrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseInvoiceOcrParserTest {

    private val sampleProducts = listOf(
        Product(
            id = 10,
            code = "H001",
            name = "Harina PAN 1Kg",
            category = "Víveres",
            priceUsd = 1.60,
            stock = 20.0,
            costPriceUsd = 1.15
        ),
        Product(
            id = 11,
            code = "A002",
            name = "Aceite Mazeite 1L",
            category = "Víveres",
            priceUsd = 4.50,
            stock = 8.0,
            costPriceUsd = 3.20
        )
    )

    private val sampleSuppliers = listOf(
        Supplier(
            id = 5,
            name = "Distribuidora Alimentos Centro C.A.",
            documentId = "J-30123456-7",
            phone = "0414-0001122",
            address = "Caracas"
        )
    )

    @Test
    fun computeUpdatedCost_updatesOnlyWhenIncomingCostIsStrictlyHigher() {
        // Nuevo costo MAYOR al existente -> se actualiza
        assertEquals(
            1.40,
            PurchaseInvoiceOcrParser.computeUpdatedCost(
                existingCostUsd = 1.15,
                incomingUnitCostUsd = 1.40,
                isExisting = true
            ),
            0.001
        )

        // Nuevo costo MENOR al existente -> NO baja, mantiene el existente
        assertEquals(
            3.20,
            PurchaseInvoiceOcrParser.computeUpdatedCost(
                existingCostUsd = 3.20,
                incomingUnitCostUsd = 2.85,
                isExisting = true
            ),
            0.001
        )

        // Nuevo costo IGUAL al existente -> mantiene el existente
        assertEquals(
            3.20,
            PurchaseInvoiceOcrParser.computeUpdatedCost(
                existingCostUsd = 3.20,
                incomingUnitCostUsd = 3.20,
                isExisting = true
            ),
            0.001
        )

        // Producto nuevo -> toma el costo entrante directamente
        assertEquals(
            2.10,
            PurchaseInvoiceOcrParser.computeUpdatedCost(
                existingCostUsd = 0.0,
                incomingUnitCostUsd = 2.10,
                isExisting = false
            ),
            0.001
        )
    }

    @Test
    fun parseFromRawText_extractsSupplierInvoiceNumberItemsAndLinksExistingInventory() {
        val rawOcr = """
            DISTRIBUIDORA ALIMENTOS CENTRO C.A.
            RIF: J-30123456-7
            FACTURA N° FAC-004821
            FECHA: 20/11/2026
            CANT  DESCRIPCION              UNIT    TOTAL
            10 Harina PAN 1Kg              1.35    13.50
            6 Aceite Mazeite 1L            2.90    17.40
            Arroz Mary 1Kg
            12 x $0.95 = $11.40
            SUBTOTAL                       42.30
            TOTAL A PAGAR                  42.30
        """.trimIndent()

        val result = PurchaseInvoiceOcrParser.parseFromRawText(
            rawText = rawOcr,
            existingProducts = sampleProducts,
            existingSuppliers = sampleSuppliers
        )

        assertEquals("Distribuidora Alimentos Centro C.A.", result.supplierName)
        assertEquals("FAC-004821", result.invoiceNumber)
        assertEquals(42.30, result.totalAmountUsd, 0.01)
        assertEquals(3, result.items.size)

        // Item 1: existente con costo MAYOR (1.35 > 1.15) -> debe subir costo y sumar stock
        val harina = result.items[0]
        assertEquals(10, harina.matchedProductId)
        assertEquals(10.0, harina.quantity, 0.01)
        assertEquals(1.35, harina.unitCostUsd, 0.01)
        assertEquals(13.50, harina.lineTotalUsd, 0.01)
        assertTrue(harina.willRaiseCost)
        assertEquals(1.35, harina.resolvedCostUsd, 0.01)

        // Item 2: existente con costo MENOR (2.90 < 3.20) -> debe mantener costo en 3.20
        val aceite = result.items[1]
        assertEquals(11, aceite.matchedProductId)
        assertEquals(6.0, aceite.quantity, 0.01)
        assertEquals(2.90, aceite.unitCostUsd, 0.01)
        assertFalse(aceite.willRaiseCost)
        assertTrue(aceite.costKeptBecauseLowerOrEqual)
        assertEquals(3.20, aceite.resolvedCostUsd, 0.01)

        // Item 3: nuevo producto en 2 líneas
        val arroz = result.items[2]
        assertNull(arroz.matchedProductId)
        assertEquals("Arroz Mary 1Kg", arroz.name)
        assertEquals(12.0, arroz.quantity, 0.01)
        assertEquals(0.95, arroz.unitCostUsd, 0.01)
        assertEquals(11.40, arroz.lineTotalUsd, 0.01)
    }

    @Test
    fun parseFromGeminiJson_parsesAndLinksInventoryCleanly() {
        val json = """
            ```json
            {
              "supplierName": "Comercializadora Andina",
              "invoiceNumber": "INV-9981",
              "totalAmountUsd": 25.00,
              "items": [
                {
                  "name": "harina pan 1kg",
                  "quantity": 10,
                  "unitCostUsd": 1.05,
                  "lineTotalUsd": 10.50
                },
                {
                  "name": "Azúcar Montalbán 1Kg",
                  "quantity": 10,
                  "unitCostUsd": 1.45,
                  "lineTotalUsd": 14.50
                }
              ]
            }
            ```
        """.trimIndent()

        val parsed = PurchaseInvoiceOcrParser.parseFromGeminiJson(
            jsonText = json,
            rawOcrText = "",
            existingProducts = sampleProducts,
            existingSuppliers = sampleSuppliers
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals("Comercializadora Andina", parsed.supplierName)
        assertEquals("INV-9981", parsed.invoiceNumber)
        assertEquals(25.00, parsed.totalAmountUsd, 0.01)
        assertEquals(2, parsed.items.size)

        // Costo entrante 1.05 <= actual 1.15 -> mantiene 1.15
        assertEquals(10, parsed.items[0].matchedProductId)
        assertEquals(1.15, parsed.items[0].resolvedCostUsd, 0.01)
        // Producto nuevo
        assertNull(parsed.items[1].matchedProductId)
        assertEquals(1.45, parsed.items[1].resolvedCostUsd, 0.01)
    }

    @Test
    fun parseFromRawText_detectsBolivaresIvaAndTaxExemptAndConvertsToUsd() {
        val rawVes = """
            COMERCIALIZADORA VENEZUELA C.A.
            FACTURA N° FAC-77881
            10 Harina PAN 1Kg (E)      Bs. 45,00    Bs. 450,00
            5 Detergente Limpio 1Kg    Bs. 180,00   Bs. 900,00
            SUBTOTAL                   Bs. 1.350,00
            IVA (16%)                  Bs. 144,00
            TOTAL A PAGAR              Bs. 1.494,00
        """.trimIndent()

        val result = PurchaseInvoiceOcrParser.parseFromRawText(
            rawText = rawVes,
            existingProducts = sampleProducts,
            existingSuppliers = sampleSuppliers
        )

        assertEquals(com.example.utils.InvoiceCurrency.VES, result.detectedCurrency)
        assertTrue(result.hasDetectedIva)
        assertEquals(16.0, result.detectedIvaPercent, 0.01)
        assertEquals(144.0, result.detectedIvaAmount, 0.01)
        assertEquals(1494.0, result.totalAmountUsd, 0.01)
        assertEquals(2, result.items.size)

        // Harina PAN marcada con (E) es exenta
        val harina = result.items[0]
        assertEquals(10, harina.matchedProductId)
        assertTrue(harina.isTaxExempt)
        assertEquals(45.0, harina.invoiceUnitCost, 0.01)

        // Detergente es gravado
        val detergente = result.items[1]
        assertFalse(detergente.isTaxExempt)
        assertEquals(180.0, detergente.invoiceUnitCost, 0.01)

        // Convertir a USD a tasa 45.00 Bs/USD
        val convertedUsd = PurchaseInvoiceOcrParser.convertInvoiceItemsToUsd(
            items = result.items,
            currency = result.detectedCurrency,
            exchangeRateBs = 45.0
        )

        // Harina: 45 / 45 = $1.00 USD, exenta -> sigue siendo $1.00 aunque se sume IVA al costo
        assertEquals(1.00, convertedUsd[0].unitCostUsd, 0.01)
        assertEquals(1.00, convertedUsd[0].effectiveUnitCostUsd(applyIva = true, ivaPercent = 16.0, includeIvaInCost = true), 0.01)

        // Detergente: 180 / 45 = $4.00 USD base -> con 16% IVA incluido en costo = $4.64 USD
        assertEquals(4.00, convertedUsd[1].unitCostUsd, 0.01)
        assertEquals(4.64, convertedUsd[1].effectiveUnitCostUsd(applyIva = true, ivaPercent = 16.0, includeIvaInCost = true), 0.01)
        assertEquals(4.00, convertedUsd[1].effectiveUnitCostUsd(applyIva = true, ivaPercent = 16.0, includeIvaInCost = false), 0.01)
    }

    @Test
    fun parseFromGeminiJson_parsesVesCurrencyAndIvaFields() {
        val json = """
            {
              "supplierName": "Importadora Caracas",
              "invoiceNumber": "00291",
              "currency": "VES",
              "subtotalAmount": 900.0,
              "hasIva": true,
              "ivaPercentage": 16.0,
              "ivaAmount": 144.0,
              "totalAmountUsd": 1044.0,
              "items": [
                {
                  "name": "Jabón Azul",
                  "quantity": 10,
                  "unitCostUsd": 90.0,
                  "lineTotalUsd": 900.0,
                  "isTaxExempt": false
                }
              ]
            }
        """.trimIndent()

        val parsed = PurchaseInvoiceOcrParser.parseFromGeminiJson(
            jsonText = json,
            rawOcrText = "",
            existingProducts = sampleProducts,
            existingSuppliers = sampleSuppliers
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(com.example.utils.InvoiceCurrency.VES, parsed.detectedCurrency)
        assertTrue(parsed.hasDetectedIva)
        assertEquals(16.0, parsed.detectedIvaPercent, 0.01)
        assertEquals(144.0, parsed.detectedIvaAmount, 0.01)
        assertEquals(90.0, parsed.items[0].invoiceUnitCost, 0.01)
        assertFalse(parsed.items[0].isTaxExempt)
    }
}
