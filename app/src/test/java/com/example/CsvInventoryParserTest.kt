package com.example

import com.example.utils.CsvInventoryParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvInventoryParserTest {

    @Test
    fun parseCsv_standardFormat_calculatesCorrectValues() {
        val csvData = """
            producto,cantidad,precio_costo,precio_venta
            Arroz Mary,10,$1.20,$1.80
            Harina PAN,20,0.90,1.40
        """.trimIndent()

        val result = CsvInventoryParser.parse(
            ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8)),
            "inventario.csv"
        )

        assertEquals(2, result.validRows.size)
        assertEquals(0, result.warnings.size)
        assertEquals(30, result.totalQuantity)

        // Arroz Mary: margen = 1.80 - 1.20 = 0.60, costo = 12.00, venta = 18.00
        val row1 = result.validRows[0]
        assertEquals("Arroz Mary", row1.productName)
        assertEquals(10, row1.quantity)
        assertEquals(1.20, row1.costPrice, 0.001)
        assertEquals(1.80, row1.salePrice, 0.001)
        assertEquals(0.60, row1.unitMargin, 0.001)
        assertEquals(12.00, row1.totalCostValue, 0.001)
        assertEquals(18.00, row1.totalSaleValue, 0.001)

        // Totals
        assertEquals(30.00, result.totalCostValue, 0.001) // 12 + 18
        assertEquals(46.00, result.totalSaleValue, 0.001) // 18 + 28
        assertEquals(16.00, result.totalMargin, 0.001)
    }

    @Test
    fun parseCsv_headerVariationsAndDelimiters_worksCorrectly() {
        val csvData = """
            PRODUCTO;EXISTENCIAS;COSTO UNITARIO;PRECIO DE VENTA;MAYOR
            Aceite Diana;15;Bs. 2,50;$ 3,50;3,10
            Café Fama;5; 4.00 ; 6.00 ;5.50
        """.trimIndent()

        val result = CsvInventoryParser.parse(
            ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8)),
            "prueba.csv"
        )

        assertEquals(2, result.validRows.size)
        assertEquals(0, result.warnings.size)
        assertEquals(20, result.totalQuantity)
        assertEquals(3.10, result.validRows[0].wholesalePrice, 0.001)
    }

    @Test
    fun parseCsv_invalidRows_skipAndGenerateWarningsWithoutStopping() {
        val csvData = """
            item,qty,cost_price,sale_price
            Pasta Capri,12,1.10,1.60
            ,5,1.00,1.50
            Leche en Polvo,-2,4.00,5.50
            Azúcar Montalbán,10,invalido,2.00
            Galletas María,8,0.50,-1.00
            Atún Margarita,15,1.80,2.50
        """.trimIndent()

        val result = CsvInventoryParser.parse(
            ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8)),
            "datos_mixtos.csv"
        )

        // Valid: Pasta Capri, Atún Margarita
        assertEquals(2, result.validRows.size)
        // Warnings: 4 invalid rows (missing name, negative qty, invalid cost, negative price)
        assertEquals(4, result.warnings.size)
        assertEquals(27, result.totalQuantity)
    }

    @Test
    fun parseCleanDecimal_cleansCurrencyAndThousandSeparators() {
        assertEquals(1250.50, CsvInventoryParser.parseCleanDecimal("$ 1,250.50") ?: 0.0, 0.001)
        assertEquals(1250.50, CsvInventoryParser.parseCleanDecimal("1.250,50 €") ?: 0.0, 0.001)
        assertEquals(15.75, CsvInventoryParser.parseCleanDecimal("Bs. 15,75") ?: 0.0, 0.001)
        assertEquals(25.00, CsvInventoryParser.parseCleanDecimal("25 USD") ?: 0.0, 0.001)
    }
}
