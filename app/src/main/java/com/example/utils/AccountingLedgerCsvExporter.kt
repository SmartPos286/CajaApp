package com.example.utils

import android.content.Context
import com.example.data.local.DeliveryNote
import com.example.data.local.Expense
import com.example.data.local.PreferencesManager
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.ui.components.TicketUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccountingLedgerCsvExporter {

    fun exportLedgerToCsv(
        context: Context,
        prefs: PreferencesManager,
        sales: List<Sale>,
        deliveryNotes: List<DeliveryNote>,
        expenses: List<Expense>,
        purchaseInvoices: List<PurchaseInvoice>,
        periodName: String = "Resumen de Operaciones"
    ): File? {
        return try {
            val sdfFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val sdfDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val timestamp = sdfFile.format(Date())
            val fileName = "libro_contable_$timestamp.csv"
            val file = File(context.cacheDir, fileName)

            val outputStream = FileOutputStream(file)
            // Add UTF-8 BOM byte order mark (\uFEFF) so Excel opens UTF-8 text with proper accents
            outputStream.write(0xEF)
            outputStream.write(0xBB)
            outputStream.write(0xBF)

            val writer = OutputStreamWriter(outputStream, "UTF-8")

            fun esc(text: Any?): String {
                val str = text?.toString() ?: ""
                val clean = str.replace("\"", "\"\"")
                return "\"$clean\""
            }

            fun fmtBs(amountUsd: Double, rate: Double): String {
                val r = if (rate > 0.001) rate else 1.0
                return String.format(Locale.US, "%.2f", amountUsd * r)
            }

            fun fmtBsDirect(amountBs: Double): String {
                return String.format(Locale.US, "%.2f", amountBs)
            }

            fun fmtNum(num: Double): String {
                return String.format(Locale.US, "%.2f", num)
            }

            // Header Section
            writer.append("${esc("LIBRO CONTABLE DE TRANSACCIONES - CONTROL DE VENTAS")}\n")
            writer.append("${esc("Comercio")},${esc(prefs.storeName.ifBlank { "MI COMERCIO POS" })}\n")
            writer.append("${esc("RIF")},${esc(prefs.storeRif.ifBlank { "J-00000000-0" })}\n")
            writer.append("${esc("Período")},${esc(periodName)}\n")
            writer.append("${esc("Fecha de Emisión")},${esc(sdfDate.format(Date()))}\n")
            writer.append("${esc("Moneda de Registro")},${esc("Bolívares (Bs.) - Convertido según tipo de cambio del día")}\n")
            writer.append("${esc("Aviso Legal")},${esc("SOLO PARA USO INTERNO - CONTROL DE VENTAS. NO VÁLIDO COMO FACTURA FISCAL")}\n\n")

            // --- 1. VENTAS Y FACTURACIÓN ---
            writer.append("${esc("--- 1. REGISTRO DE VENTAS Y FACTURACIÓN (EN BS.) ---")}\n")
            writer.append(
                "${esc("N° Factura")}," +
                "${esc("Fecha/Hora")}," +
                "${esc("Cliente")}," +
                "${esc("RIF/CI")}," +
                "${esc("Cajero")}," +
                "${esc("Tasa BCV (Bs.)")}," +
                "${esc("Base Imponible (Bs.)")}," +
                "${esc("Monto Exento (Bs.)")}," +
                "${esc("Descuento (Bs.)")}," +
                "${esc("IVA 16% (Bs.)")}," +
                "${esc("IGTF 3% (Bs.)")}," +
                "${esc("Retención IVA Clientes (Bs.)")}," +
                "${esc("Retención ISLR Clientes (Bs.)")}," +
                "${esc("TOTAL FACTURA (BS.)")}," +
                "${esc("Efectivo Recibido (Bs.)")}," +
                "${esc("Pago Móvil (Bs.)")}," +
                "${esc("Punto/Tarjeta (Bs.)")}," +
                "${esc("Cashea (Bs.)")}," +
                "${esc("Crédito Concedido (Bs.)")}\n"
            )

            var sumSalesBaseTaxableBs = 0.0
            var sumSalesBaseExemptBs = 0.0
            var sumSalesDiscountBs = 0.0
            var sumSalesIvaBs = 0.0
            var sumSalesIgtfBs = 0.0
            var sumSalesClientRetIvaBs = 0.0
            var sumSalesClientRetIslrBs = 0.0
            var sumSalesTotalBs = 0.0
            var sumSalesCashBs = 0.0
            var sumSalesMobileBs = 0.0
            var sumSalesCardBs = 0.0
            var sumSalesCasheaBs = 0.0
            var sumSalesCreditBs = 0.0

            for (sale in sales) {
                val r = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0
                val dateStr = sdfDate.format(Date(sale.timestamp))
                val baseTaxableBs = CurrencyMath.convertUsdToBs(sale.baseTaxableUsd, r)
                val baseExemptBs = CurrencyMath.convertUsdToBs(sale.baseExemptUsd, r)
                val discountBs = CurrencyMath.convertUsdToBs(sale.discountUsd, r)
                val ivaBs = CurrencyMath.convertUsdToBs(sale.ivaUsd, r)
                val igtfBs = if (sale.igtfBs > 0.001) CurrencyMath.round2(sale.igtfBs) else CurrencyMath.convertUsdToBs(sale.igtfUsd, r)
                val retIvaBs = CurrencyMath.convertUsdToBs(sale.retencionIvaUsd, r)
                val retIslrBs = CurrencyMath.convertUsdToBs(sale.retencionIslrUsd, r)
                val totalBs = if (sale.totalBs > 0.001) CurrencyMath.round2(sale.totalBs) else CurrencyMath.convertUsdToBs(sale.totalUsd, r)
                val cashBs = CurrencyMath.convertUsdToBs(sale.paymentCashUsd, r)
                val mobileBs = CurrencyMath.round2(sale.paymentMobileBs)
                val cardBs = CurrencyMath.round2(sale.paymentCardBs)
                val casheaBs = CurrencyMath.convertUsdToBs(sale.paymentCasheaUsd, r)
                val creditBs = CurrencyMath.convertUsdToBs(sale.paymentCreditUsd, r)

                sumSalesBaseTaxableBs += baseTaxableBs
                sumSalesBaseExemptBs += baseExemptBs
                sumSalesDiscountBs += discountBs
                sumSalesIvaBs += ivaBs
                sumSalesIgtfBs += igtfBs
                sumSalesClientRetIvaBs += retIvaBs
                sumSalesClientRetIslrBs += retIslrBs
                sumSalesTotalBs += totalBs
                sumSalesCashBs += cashBs
                sumSalesMobileBs += mobileBs
                sumSalesCardBs += cardBs
                sumSalesCasheaBs += casheaBs
                sumSalesCreditBs += creditBs

                writer.append(
                    "${esc(sale.id)}," +
                    "${esc(dateStr)}," +
                    "${esc(sale.customerName.ifBlank { "Consumidor Final" })}," +
                    "${esc(sale.customerDocument.ifBlank { "V-00000000" })}," +
                    "${esc(sale.cashierName.ifBlank { "Cajero" })}," +
                    "${esc(fmtNum(r))}," +
                    "${esc(fmtBsDirect(baseTaxableBs))}," +
                    "${esc(fmtBsDirect(baseExemptBs))}," +
                    "${esc(fmtBsDirect(discountBs))}," +
                    "${esc(fmtBsDirect(ivaBs))}," +
                    "${esc(fmtBsDirect(igtfBs))}," +
                    "${esc(fmtBsDirect(retIvaBs))}," +
                    "${esc(fmtBsDirect(retIslrBs))}," +
                    "${esc(fmtBsDirect(totalBs))}," +
                    "${esc(fmtBsDirect(cashBs))}," +
                    "${esc(fmtBsDirect(mobileBs))}," +
                    "${esc(fmtBsDirect(cardBs))}," +
                    "${esc(fmtBsDirect(casheaBs))}," +
                    "${esc(fmtBsDirect(creditBs))}\n"
                )
            }

            // Subtotal row for Sales
            writer.append(
                "${esc("TOTALES VENTAS")},,,,," +
                "${esc("---")}," +
                "${esc(fmtBsDirect(sumSalesBaseTaxableBs))}," +
                "${esc(fmtBsDirect(sumSalesBaseExemptBs))}," +
                "${esc(fmtBsDirect(sumSalesDiscountBs))}," +
                "${esc(fmtBsDirect(sumSalesIvaBs))}," +
                "${esc(fmtBsDirect(sumSalesIgtfBs))}," +
                "${esc(fmtBsDirect(sumSalesClientRetIvaBs))}," +
                "${esc(fmtBsDirect(sumSalesClientRetIslrBs))}," +
                "${esc(fmtBsDirect(sumSalesTotalBs))}," +
                "${esc(fmtBsDirect(sumSalesCashBs))}," +
                "${esc(fmtBsDirect(sumSalesMobileBs))}," +
                "${esc(fmtBsDirect(sumSalesCardBs))}," +
                "${esc(fmtBsDirect(sumSalesCasheaBs))}," +
                "${esc(fmtBsDirect(sumSalesCreditBs))}\n\n"
            )

            // --- 2. NOTAS DE ENTREGA ---
            writer.append("${esc("--- 2. REGISTRO DE NOTAS DE ENTREGA (EN BS.) ---")}\n")
            writer.append(
                "${esc("N° Nota")}," +
                "${esc("Fecha/Hora")}," +
                "${esc("Cliente")}," +
                "${esc("RIF/CI")}," +
                "${esc("Tasa BCV (Bs.)")}," +
                "${esc("Monto Total (Bs.)")}," +
                "${esc("Estado")}\n"
            )

            var sumDeliveryNotesBs = 0.0
            for (note in deliveryNotes) {
                val r = if (note.rateBcv > 0.001) note.rateBcv else 1.0
                val totalBs = if (note.totalEstimatedBs > 0.001) note.totalEstimatedBs else note.totalEstimatedUsd * r
                if (note.status != "ANULADO") sumDeliveryNotesBs += totalBs

                val noteNum = note.noteNumber.ifBlank { "NE-${note.id}" }
                writer.append(
                    "${esc(noteNum)}," +
                    "${esc(sdfDate.format(Date(note.timestamp)))}," +
                    "${esc(note.customerName)}," +
                    "${esc(note.customerDocument)}," +
                    "${esc(fmtNum(r))}," +
                    "${esc(fmtBsDirect(totalBs))}," +
                    "${esc(note.status)}\n"
                )
            }
            writer.append("${esc("TOTAL NOTAS DE ENTREGA ACTIVAS")},,,,,${esc(fmtBsDirect(sumDeliveryNotesBs))},\n\n")

            // --- 3. GASTOS OPERATIVOS ---
            writer.append("${esc("--- 3. GASTOS OPERATIVOS Y EGRESOS (EN BS.) ---")}\n")
            writer.append(
                "${esc("ID Gasto")}," +
                "${esc("Fecha/Hora")}," +
                "${esc("Categoría")}," +
                "${esc("Descripción")}," +
                "${esc("Registrado Por")}," +
                "${esc("Tasa BCV (Bs.)")}," +
                "${esc("Monto Gasto (Bs.)")}\n"
            )

            var sumExpensesBs = 0.0
            for (expense in expenses) {
                val r = if (expense.bcvRateUsed > 0.001) expense.bcvRateUsed else 1.0
                val expenseBs = if (expense.amountBs > 0.001) expense.amountBs else expense.amountUsd * r
                sumExpensesBs += expenseBs

                writer.append(
                    "${esc(expense.id)}," +
                    "${esc(sdfDate.format(Date(expense.timestamp)))}," +
                    "${esc(expense.category)}," +
                    "${esc(expense.description)}," +
                    "${esc(expense.registeredBy)}," +
                    "${esc(fmtNum(r))}," +
                    "${esc(fmtBsDirect(expenseBs))}\n"
                )
            }
            writer.append("${esc("TOTAL GASTOS OPERATIVOS")},,,,,${esc("---")},${esc(fmtBsDirect(sumExpensesBs))}\n\n")

            // --- 4. FACTURAS DE COMPRAS Y PROVEEDORES ---
            writer.append("${esc("--- 4. COMPRAS A PROVEEDORES Y RETENCIONES PRACTICADAS (EN BS.) ---")}\n")
            writer.append(
                "${esc("N° Factura Proveedor")}," +
                "${esc("Fecha/Hora")}," +
                "${esc("Proveedor ID")}," +
                "${esc("RIF/Doc")}," +
                "${esc("Tasa BCV (Bs.)")}," +
                "${esc("Base Imponible (Bs.)")}," +
                "${esc("IVA Pagado (Bs.)")}," +
                "${esc("Retención IVA Practicada (Bs.)")}," +
                "${esc("Retención ISLR Practicada (Bs.)")}," +
                "${esc("Total Factura Compra (Bs.)")}," +
                "${esc("Estado Pago")}\n"
            )

            var sumPurchasesBaseTaxableBs = 0.0
            var sumPurchasesIvaBs = 0.0
            var sumPurchasesRetIvaBs = 0.0
            var sumPurchasesRetIslrBs = 0.0
            var sumPurchasesTotalBs = 0.0

            val defaultBcvRate = sales.firstOrNull { it.rateBcv > 0.001 }?.rateBcv ?: 1.0

            for (inv in purchaseInvoices) {
                val r = defaultBcvRate
                val baseTaxableBs = inv.subtotalUsd * r
                val ivaBs = inv.taxAmountUsd * r
                val retIvaBs = inv.retencionIvaUsd * r
                val retIslrBs = inv.retencionIslrUsd * r
                val totalBs = inv.totalAmount * r

                sumPurchasesBaseTaxableBs += baseTaxableBs
                sumPurchasesIvaBs += ivaBs
                sumPurchasesRetIvaBs += retIvaBs
                sumPurchasesRetIslrBs += retIslrBs
                sumPurchasesTotalBs += totalBs

                val paymentStatus = if (inv.isPaid) "PAGADO" else "PENDIENTE"

                writer.append(
                    "${esc(inv.invoiceNumber)}," +
                    "${esc(sdfDate.format(Date(inv.timestamp)))}," +
                    "${esc("Proveedor #${inv.supplierId}")}," +
                    "${esc("---")}," +
                    "${esc(fmtNum(r))}," +
                    "${esc(fmtBsDirect(baseTaxableBs))}," +
                    "${esc(fmtBsDirect(ivaBs))}," +
                    "${esc(fmtBsDirect(retIvaBs))}," +
                    "${esc(fmtBsDirect(retIslrBs))}," +
                    "${esc(fmtBsDirect(totalBs))}," +
                    "${esc(paymentStatus)}\n"
                )
            }
            writer.append(
                "${esc("TOTAL COMPRAS PROVEEDORES")},,,," +
                "${esc("---")}," +
                "${esc(fmtBsDirect(sumPurchasesBaseTaxableBs))}," +
                "${esc(fmtBsDirect(sumPurchasesIvaBs))}," +
                "${esc(fmtBsDirect(sumPurchasesRetIvaBs))}," +
                "${esc(fmtBsDirect(sumPurchasesRetIslrBs))}," +
                "${esc(fmtBsDirect(sumPurchasesTotalBs))},\n\n"
            )

            // --- 5. RESUMEN CONTABLE CONSOLIDADO ---
            val netCashflowBs = sumSalesTotalBs - sumExpensesBs
            val netOperatingBalanceBs = sumSalesTotalBs - sumExpensesBs - sumPurchasesTotalBs

            writer.append("${esc("--- 5. CONSOLIDADO RESUMEN CONTABLE (EN BS.) ---")}\n")
            writer.append("${esc("Concepto Contable")},${esc("Monto en Bolívares (Bs.)")}\n")
            writer.append("${esc("Total Ventas Brutas Facturadas (Bs.)")},${esc(fmtBsDirect(sumSalesTotalBs))}\n")
            writer.append("${esc("Total Base Imponible Gravable (Bs.)")},${esc(fmtBsDirect(sumSalesBaseTaxableBs))}\n")
            writer.append("${esc("Total Monto Exento (Bs.)")},${esc(fmtBsDirect(sumSalesBaseExemptBs))}\n")
            writer.append("${esc("Total IVA Débito Fiscal Cobrado (Bs.)")},${esc(fmtBsDirect(sumSalesIvaBs))}\n")
            writer.append("${esc("Total IGTF 3% Percibido (Bs.)")},${esc(fmtBsDirect(sumSalesIgtfBs))}\n")
            writer.append("${esc("Total Retenciones IVA Dejadas por Clientes (Bs.)")},${esc(fmtBsDirect(sumSalesClientRetIvaBs))}\n")
            writer.append("${esc("Total Retenciones ISLR Dejadas por Clientes (Bs.)")},${esc(fmtBsDirect(sumSalesClientRetIslrBs))}\n")
            writer.append("${esc("Total Recaudado en Efectivo Bs.")},${esc(fmtBsDirect(sumSalesCashBs))}\n")
            writer.append("${esc("Total Recaudado en Pago Móvil Bs.")},${esc(fmtBsDirect(sumSalesMobileBs))}\n")
            writer.append("${esc("Total Recaudado en Punto / Tarjeta Bs.")},${esc(fmtBsDirect(sumSalesCardBs))}\n")
            writer.append("${esc("Total Recaudado en Cashea Bs.")},${esc(fmtBsDirect(sumSalesCasheaBs))}\n")
            writer.append("${esc("Total Ventas a Crédito Concedidas (Bs.)")},${esc(fmtBsDirect(sumSalesCreditBs))}\n")
            writer.append("${esc("Total Gastos Operativos del Negocio (Bs.)")},${esc(fmtBsDirect(sumExpensesBs))}\n")
            writer.append("${esc("Total Compras Facturadas de Proveedores (Bs.)")},${esc(fmtBsDirect(sumPurchasesTotalBs))}\n")
            writer.append("${esc("Total IVA Crédito Fiscal Pagado a Proveedores (Bs.)")},${esc(fmtBsDirect(sumPurchasesIvaBs))}\n")
            writer.append("${esc("Total Retenciones IVA Aplicadas a Proveedores (Bs.)")},${esc(fmtBsDirect(sumPurchasesRetIvaBs))}\n")
            writer.append("${esc("Total Retenciones ISLR Aplicadas a Proveedores (Bs.)")},${esc(fmtBsDirect(sumPurchasesRetIslrBs))}\n")
            writer.append("${esc("FLUJO NETO DE CAJA DEL PERÍODO (VENTAS - GASTOS) (BS.)")},${esc(fmtBsDirect(netCashflowBs))}\n")
            writer.append("${esc("BALANCE NETO CONTABLE OPERATIVO (BS.)")},${esc(fmtBsDirect(netOperatingBalanceBs))}\n")

            writer.flush()
            writer.close()
            outputStream.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
