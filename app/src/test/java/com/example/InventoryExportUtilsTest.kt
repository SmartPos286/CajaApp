package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.utils.CsvInventoryParser
import com.example.utils.InventoryExportUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryExportUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun createExportFile_csv_createsRealCsvFileWithUtf8Bom_andParsesBack() {
        val csvContent = buildString {
            append("Codigo,Nombre,Categoria,PrecioUSD,PrecioMayorUSD,Stock,CostoUSD,FacturaCompra,ExentoIVA,TieneComision,PorcentajeComision,MontoComisionUSD,EsReceta\n")
            append("P-001,Café Molido 500g,Víveres,4.50,3.90,25,2.80,FAC-100,0,0,0.00,0.00,0\n")
        }

        val file = InventoryExportUtils.createExportFile(context, csvContent, "CSV")
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue(file.name.endsWith(".csv"))
        assertEquals("text/csv", InventoryExportUtils.getMimeTypeForFormat("CSV"))

        val rawBytes = file.readBytes()
        assertTrue(rawBytes.size > 3)
        // Verify UTF-8 BOM for Excel compatibility
        assertEquals(0xEF.toByte(), rawBytes[0])
        assertEquals(0xBB.toByte(), rawBytes[1])
        assertEquals(0xBF.toByte(), rawBytes[2])

        // Verify round-trip parsing with CsvInventoryParser
        val parsed = FileInputStream(file).use { stream ->
            CsvInventoryParser.parse(stream, file.name)
        }
        assertEquals(1, parsed.validRows.size)
        assertEquals(0, parsed.warnings.size)
        assertEquals("Café Molido 500g", parsed.validRows[0].productName)
        assertEquals(25, parsed.validRows[0].quantity)
        assertEquals(2.80, parsed.validRows[0].costPrice, 0.001)
        assertEquals(4.50, parsed.validRows[0].salePrice, 0.001)
        assertEquals(3.90, parsed.validRows[0].wholesalePrice, 0.001)
    }

    @Test
    fun writeExportToUri_json_writesRealFileWithoutBom() {
        val targetFile = File(context.cacheDir, "saf_test_output.json")
        val uri = Uri.fromFile(targetFile)
        val jsonContent = "[{\"code\":\"P-1\",\"name\":\"Producto\"}]"

        val ok = InventoryExportUtils.writeExportToUri(context, uri, jsonContent, "JSON")
        assertTrue(ok)
        assertTrue(targetFile.exists())
        assertEquals(jsonContent, targetFile.readText(Charsets.UTF_8))
    }
}
