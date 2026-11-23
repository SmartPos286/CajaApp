package com.example.utils

import com.example.data.local.Sale
import com.example.data.local.SaleItem
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Generador de comandos e instrucciones para Impresoras Fiscales de Venezuela:
 * - The Factory HKA (DT-230, PNP-280, HKA112)
 * - Bixolon Fiscal (SRP-350, SRP-270)
 * - Dascom Fiscal (Tally Dascom)
 */
object FiscalPrinterHelper {

    /**
     * Genera la trama de comandos en formato ASCII para la emisión de Factura Fiscal oficial.
     * Nota SENIAT: La impresora fiscal imprime el encabezado de la empresa desde su Memoria Fiscal interna.
     * El software únicamente transmite: Datos del cliente, ítems de productos, descuentos y comando de pago.
     */
    fun buildFiscalInvoiceCommands(
        sale: Sale,
        items: List<SaleItem>,
        brand: String
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        fun writeCmd(cmd: String) {
            baos.write(cmd.toByteArray(Charsets.ISO_8859_1))
            baos.write(0x0D) // CR
            baos.write(0x0A) // LF
        }

        // 1. Datos del Cliente (No incluye encabezado de tienda; la impresora lo toma de su Memoria Fiscal)
        val customerName = sale.customerName.ifBlank { "CONSUMIDOR FINAL" }.take(38)
        val customerRif = sale.customerDocument.ifBlank { "V-00000000" }.take(18)

        when {
            brand.contains("Factory", ignoreCase = true) || brand.contains("HKA", ignoreCase = true) -> {
                writeCmd("i01CLIENTE: $customerName")
                writeCmd("i02RIF/CI: $customerRif")
                writeCmd("i03FACTURA POS N: ${sale.id}")
            }
            brand.contains("Bixolon", ignoreCase = true) -> {
                writeCmd("iS*$customerName")
                writeCmd("iR*$customerRif")
            }
            brand.contains("Dascom", ignoreCase = true) -> {
                writeCmd("@CLIENTE:$customerName")
                writeCmd("@RIF:$customerRif")
            }
            else -> {
                writeCmd("i01CLIENTE: $customerName")
                writeCmd("i02RIF/CI: $customerRif")
            }
        }

        // 2. Registro de Ítems de Venta (La impresora fiscal calcula el IVA internamente)
        for (item in items) {
            val name = item.productName.replace("\n", " ").take(30)
            val qty = (item.quantity * 1000).toLong() // Cantidad x 1000
            val price = (item.priceUsd * sale.rateBcv * 100).toLong() // Precio Bs. x 100

            val taxCode = if (!item.isTaxExempt) "!" else "d" // ! = 16% IVA Fiscal, d = Exento

            val qtyFormatted = String.format(Locale.US, "%05d", qty.coerceAtMost(99999))
            val priceFormatted = String.format(Locale.US, "%08d", price.coerceAtMost(99999999))

            // Trama estándar HKA / Bixolon: Code + Price (8 d) + Qty (5 d) + Name
            val itemLine = "$taxCode$priceFormatted$qtyFormatted$name"
            writeCmd(itemLine)
        }

        // 3. Descuento si aplica
        if (sale.discountUsd > 0.001) {
            val discBs = (sale.discountUsd * sale.rateBcv * 100).toLong()
            val discFormatted = String.format(Locale.US, "%08d", discBs)
            writeCmd("p-$discFormatted")
        }

        // 4. Cierre y Formas de Pago
        // 101 = Efectivo, 102 = Tarjeta/Punto, 105 = Transferencia/Pago Móvil
        writeCmd("101") // Cierra Factura Fiscal

        return baos.toByteArray()
    }

    /**
     * Genera un comando de Diagnóstico / Impresión de Prueba Fiscal
     */
    fun buildFiscalTestCommands(
        brand: String
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        fun writeCmd(cmd: String) {
            baos.write(cmd.toByteArray(Charsets.ISO_8859_1))
            baos.write(0x0D)
            baos.write(0x0A)
        }

        when {
            brand.contains("Factory", ignoreCase = true) || brand.contains("HKA", ignoreCase = true) -> {
                writeCmd("i01PRUEBA FISCAL THE FACTORY HKA")
                writeCmd("i02RIF/CI: V-00000000")
                writeCmd("!00000100000100ITEM PRUEBA FISCAL")
                writeCmd("101")
            }
            brand.contains("Bixolon", ignoreCase = true) -> {
                writeCmd("iS*PRUEBA FISCAL BIXOLON")
                writeCmd("iR*V-00000000")
                writeCmd("!00000100000100ITEM PRUEBA BIXOLON")
                writeCmd("101")
            }
            brand.contains("Dascom", ignoreCase = true) -> {
                writeCmd("@CLIENTE:PRUEBA FISCAL DASCOM")
                writeCmd("@RIF:V-00000000")
                writeCmd("!00000100000100ITEM PRUEBA DASCOM")
                writeCmd("101")
            }
            else -> {
                writeCmd("i01PRUEBA FISCAL POS")
                writeCmd("i02RIF/CI: V-00000000")
                writeCmd("!00000100000100PRUEBA CONEXION FISCAL")
                writeCmd("101")
            }
        }

        return baos.toByteArray()
    }
}
