package com.example.ui.summary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.PreferencesManager
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailyClosurePdfGenerator {

    fun generateDailyClosurePdf(
        context: Context,
        prefs: PreferencesManager,
        cashierName: String,
        sales: List<Sale>,
        items: List<SaleItem>,
        closureDate: Date = Date()
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points
        val pageHeight = 842 // Standard A4 height in points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()

        val leftMargin = 36f
        val rightMargin = pageWidth - 36f
        val contentWidth = rightMargin - leftMargin

        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFmt.format(closureDate)
        val timeStr = timeFmt.format(Date())

        // Calculations
        val totalSalesCount = sales.size
        val totalUsd = sales.sumOf { it.totalUsd }
        val totalBs = sales.sumOf { it.totalBs }

        val totalCashUsd = sales.sumOf { it.paymentCashUsd }
        val countCashUsd = sales.count { it.paymentCashUsd > 0.001 }

        val totalMobileBs = sales.sumOf { it.paymentMobileBs }
        val countMobileBs = sales.count { it.paymentMobileBs > 0.001 }

        val totalCardBs = sales.sumOf { it.paymentCardBs }
        val countCardBs = sales.count { it.paymentCardBs > 0.001 }

        val totalCasheaUsd = sales.sumOf { it.paymentCasheaUsd }
        val countCasheaUsd = sales.count { it.paymentCasheaUsd > 0.001 }

        val totalCreditUsd = sales.sumOf { it.paymentCreditUsd }
        val countCreditUsd = sales.count { it.paymentCreditUsd > 0.001 }

        val rate = if (sales.isNotEmpty() && sales[0].rateBcv > 0.001) sales[0].rateBcv
                   else if (prefs.lastBcvRate > 0.001f) prefs.lastBcvRate.toDouble() else 1.0

        val totalBaseTaxableUsd = sales.sumOf { it.baseTaxableUsd }
        val totalBaseExemptUsd = sales.sumOf { it.baseExemptUsd }
        val totalDiscountUsd = sales.sumOf { it.discountUsd }
        val totalIvaUsd = sales.sumOf { it.ivaUsd }
        val totalIgtfUsd = sales.sumOf { it.igtfUsd }
        val totalIgtfBs = sales.sumOf { it.igtfBs }

        val totalBaseTaxableBs = totalBaseTaxableUsd * rate
        val totalBaseExemptBs = totalBaseExemptUsd * rate
        val totalDiscountBs = totalDiscountUsd * rate
        val totalIvaBs = totalIvaUsd * rate

        // Group products
        data class ProductSummary(
            val name: String,
            val isTaxExempt: Boolean,
            val quantity: Double,
            val totalUsd: Double,
            val totalBs: Double
        )

        val groupedProducts = items.groupBy { it.productName }.map { (name, pItems) ->
            val isExempt = pItems.any { it.isTaxExempt }
            val qty = pItems.sumOf { it.quantity }
            val pUsd = pItems.sumOf { it.priceUsd * it.quantity }
            ProductSummary(
                name = name,
                isTaxExempt = isExempt,
                quantity = qty,
                totalUsd = pUsd,
                totalBs = pUsd * rate
            )
        }.sortedByDescending { it.quantity }

        var pageNumber = 1
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun drawFooter(c: Canvas, pNum: Int) {
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            c.drawText("Sistema POS • Reporte Oficial de Cierre Diario", leftMargin, pageHeight - 20f, paint)

            paint.textAlign = Paint.Align.RIGHT
            c.drawText("Página $pNum", rightMargin, pageHeight - 20f, paint)
        }

        var currentY = 32f

        fun ensureSpace(neededHeight: Float) {
            if (currentY + neededHeight > pageHeight - 40f) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas

                // Running header for next pages
                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("CIERRE DIARIO DE VENTAS • ${prefs.storeName} • Fecha: $dateStr", leftMargin, 26f, paint)
                paint.color = Color.parseColor("#CBD5E1")
                paint.strokeWidth = 0.5f
                canvas.drawLine(leftMargin, 32f, rightMargin, 32f, paint)

                currentY = 46f
            }
        }

        // ==================== PAGE 1 HEADER ====================
        // Brand banner
        paint.color = Color.parseColor("#1E1B4B")
        val headerRect = RectF(leftMargin, currentY, rightMargin, currentY + 70f)
        canvas.drawRoundRect(headerRect, 8f, 8f, paint)

        // Store info inside banner
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(prefs.storeName.uppercase(), leftMargin + 16f, currentY + 24f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        paint.color = Color.parseColor("#E0E7FF")
        val storeSub = "RIF: ${prefs.storeRif}  •  Telf: ${prefs.storePhone}  •  ${prefs.storeAddress}"
        canvas.drawText(storeSub, leftMargin + 16f, currentY + 40f, paint)

        paint.textSize = 8f
        paint.color = Color.parseColor("#C7D2FE")
        canvas.drawText("REPORTE OFICIAL DE CIERRE DE CAJA Y VENTAS DIARIAS (CORTE Z)", leftMargin + 16f, currentY + 56f, paint)

        currentY += 80f

        // Metadata grid (Date, Time, Cashier, Rate, Invoices count)
        paint.color = Color.parseColor("#F8FAFC")
        val metaRect = RectF(leftMargin, currentY, rightMargin, currentY + 36f)
        canvas.drawRoundRect(metaRect, 6f, 6f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(metaRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.LEFT
        val colW = contentWidth / 4f

        // Col 1: Fecha y Hora
        paint.color = Color.parseColor("#64748B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("FECHA / HORA:", leftMargin + 10f, currentY + 14f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("$dateStr $timeStr", leftMargin + 10f, currentY + 27f, paint)

        // Col 2: Cajero / Operador
        paint.color = Color.parseColor("#64748B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("CAJERO(A) / CAJA:", leftMargin + colW + 10f, currentY + 14f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regNum = prefs.cashRegisterNumber.ifBlank { "01" }
        val cashierDisplay = if (cashierName.isNotBlank()) "$cashierName (Caja $regNum)" else "Caja $regNum"
        canvas.drawText(cashierDisplay, leftMargin + colW + 10f, currentY + 27f, paint)

        // Col 3: Tasa BCV
        paint.color = Color.parseColor("#64748B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TASA BCV:", leftMargin + (colW * 2) + 10f, currentY + 14f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Bs. ${String.format(Locale.US, "%.2f", rate)}", leftMargin + (colW * 2) + 10f, currentY + 27f, paint)

        // Col 4: Facturas
        paint.color = Color.parseColor("#64748B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL FACTURAS:", leftMargin + (colW * 3) + 10f, currentY + 14f, paint)
        paint.color = Color.parseColor("#2563EB")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("$totalSalesCount operaciones", leftMargin + (colW * 3) + 10f, currentY + 27f, paint)

        currentY += 46f

        // ==================== SECTION 1: RESUMEN DE INGRESOS POR MEDIO DE PAGO ====================
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("1. RESUMEN DE INGRESOS POR MÉTODO DE PAGO", leftMargin, currentY, paint)
        currentY += 8f

        // Header Table Payments
        paint.color = Color.parseColor("#EDE9FE")
        val payHeadRect = RectF(leftMargin, currentY, rightMargin, currentY + 18f)
        canvas.drawRect(payHeadRect, paint)

        paint.color = Color.parseColor("#3730A3")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Método de Pago", leftMargin + 8f, currentY + 12f, paint)
        canvas.drawText("Transacciones", leftMargin + 170f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Divisas (USD)", rightMargin - 130f, currentY + 12f, paint)
        canvas.drawText("Total Bolívares (Bs.)", rightMargin - 8f, currentY + 12f, paint)
        currentY += 18f

        // Rows for payment methods
        val payRows = listOf(
            Triple("Efectivo Divisas ($ USD)", countCashUsd, Pair(totalCashUsd, totalCashUsd * rate)),
            Triple("Pago Móvil (Bs.)", countMobileBs, Pair(totalMobileBs / rate, totalMobileBs)),
            Triple("Punto de Venta / Débito (Bs.)", countCardBs, Pair(totalCardBs / rate, totalCardBs)),
            Triple("Cashea ($ USD)", countCasheaUsd, Pair(totalCasheaUsd, totalCasheaUsd * rate)),
            Triple("Ventas a Crédito ($ USD)", countCreditUsd, Pair(totalCreditUsd, totalCreditUsd * rate))
        )

        payRows.forEachIndexed { idx, row ->
            paint.color = if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 16f), paint)

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(row.first, leftMargin + 8f, currentY + 11f, paint)
            canvas.drawText("${row.second} facturas", leftMargin + 170f, currentY + 11f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.US, "$%.2f", row.third.first), rightMargin - 130f, currentY + 11f, paint)
            canvas.drawText(String.format(Locale.US, "Bs. %.2f", row.third.second), rightMargin - 8f, currentY + 11f, paint)
            currentY += 16f
        }

        // Total Row
        paint.color = Color.parseColor("#4F46E5")
        canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
        paint.color = Color.WHITE
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL FACTURADO EN JORNADA", leftMargin + 8f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.US, "$%.2f USD", totalUsd), rightMargin - 130f, currentY + 12f, paint)
        canvas.drawText(String.format(Locale.US, "Bs. %.2f", totalBs), rightMargin - 8f, currentY + 12f, paint)
        currentY += 26f

        // ==================== SECTION 2: DESGLOSE FISCAL / IMPUESTOS ====================
        ensureSpace(120f)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("2. DESGLOSE FISCAL Y TRIBUTARIO (SENIAT / IMPUESTOS)", leftMargin, currentY, paint)
        currentY += 8f

        // Table Tax Header
        paint.color = Color.parseColor("#FEF3C7")
        canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
        paint.color = Color.parseColor("#92400E")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Concepto Tributario", leftMargin + 8f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Base Imponible (USD)", rightMargin - 130f, currentY + 12f, paint)
        canvas.drawText("Monto en Bolívares (Bs.)", rightMargin - 8f, currentY + 12f, paint)
        currentY += 18f

        val taxRows = mutableListOf(
            Triple("Base Imponible Gravada (IVA 16%)", totalBaseTaxableUsd, totalBaseTaxableBs),
            Triple("Ventas Exentas de Impuesto (IVA 0%)", totalBaseExemptUsd, totalBaseExemptBs)
        )
        if (totalDiscountUsd > 0.001) {
            taxRows.add(Triple("Descuentos Otorgados (Deducidos del subtotal pre-IVA)", -totalDiscountUsd, -totalDiscountBs))
        }
        taxRows.add(Triple("Total Impuesto IVA Recaudado (16%)", totalIvaUsd, totalIvaBs))
        taxRows.add(Triple("Total Recargo IGTF Divisas Efectivo (3%)", totalIgtfUsd, totalIgtfBs))

        taxRows.forEachIndexed { idx, row ->
            paint.color = if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 16f), paint)

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(row.first, leftMargin + 8f, currentY + 11f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.US, "$%.2f", row.second), rightMargin - 130f, currentY + 11f, paint)
            canvas.drawText(String.format(Locale.US, "Bs. %.2f", row.third), rightMargin - 8f, currentY + 11f, paint)
            currentY += 16f
        }

        // Subtotal Fiscal Box
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL VENTAS + IMPUESTOS Y RECARGOS", leftMargin + 8f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.US, "$%.2f USD", totalUsd), rightMargin - 130f, currentY + 12f, paint)
        canvas.drawText(String.format(Locale.US, "Bs. %.2f", totalBs), rightMargin - 8f, currentY + 12f, paint)
        currentY += 26f

        // ==================== SECTION 3: CONSOLIDADO DE PRODUCTOS VENDIDOS ====================
        ensureSpace(80f)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("3. CONSOLIDADO DE PRODUCTOS VENDIDOS EN EL DÍA", leftMargin, currentY, paint)
        currentY += 8f

        // Table Products Header
        paint.color = Color.parseColor("#E0F2FE")
        canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
        paint.color = Color.parseColor("#0369A1")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Descripción del Producto", leftMargin + 8f, currentY + 12f, paint)
        canvas.drawText("Condición", leftMargin + 240f, currentY + 12f, paint)
        canvas.drawText("Cant.", leftMargin + 310f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total USD", rightMargin - 100f, currentY + 12f, paint)
        canvas.drawText("Total Bolívares (Bs.)", rightMargin - 8f, currentY + 12f, paint)
        currentY += 18f

        if (groupedProducts.isEmpty()) {
            paint.color = Color.WHITE
            canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("No se registraron productos en las ventas de hoy.", leftMargin + 8f, currentY + 12f, paint)
            currentY += 22f
        } else {
            groupedProducts.forEachIndexed { index, p ->
                ensureSpace(18f)
                paint.color = if (index % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
                canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 16f), paint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                // Truncate name if too long
                val maxChars = 40
                val displayName = if (p.name.length > maxChars) p.name.take(maxChars - 3) + "..." else p.name
                canvas.drawText(displayName, leftMargin + 8f, currentY + 11f, paint)

                // Condition
                if (p.isTaxExempt) {
                    paint.color = Color.parseColor("#059669")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Exento (0%)", leftMargin + 240f, currentY + 11f, paint)
                } else {
                    paint.color = Color.parseColor("#64748B")
                    canvas.drawText("IVA 16%", leftMargin + 240f, currentY + 11f, paint)
                }

                paint.color = Color.parseColor("#0F172A")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("${p.quantity}", leftMargin + 310f, currentY + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(String.format(Locale.US, "$%.2f", p.totalUsd), rightMargin - 100f, currentY + 11f, paint)
                canvas.drawText(String.format(Locale.US, "Bs. %.2f", p.totalBs), rightMargin - 8f, currentY + 11f, paint)
                currentY += 16f
            }
            currentY += 10f
        }

        // ==================== SECTION 4: RELACIÓN DETALLADA DE FACTURAS ====================
        ensureSpace(90f)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("4. RELACIÓN DE FACTURAS EMITIDAS", leftMargin, currentY, paint)
        currentY += 8f

        // Table Invoices Header
        paint.color = Color.parseColor("#DCFCE7")
        canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
        paint.color = Color.parseColor("#15803D")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Factura", leftMargin + 8f, currentY + 12f, paint)
        canvas.drawText("Hora", leftMargin + 60f, currentY + 12f, paint)
        canvas.drawText("Cliente (CI/RIF)", leftMargin + 105f, currentY + 12f, paint)
        canvas.drawText("Forma de Pago", leftMargin + 280f, currentY + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total USD", rightMargin - 90f, currentY + 12f, paint)
        canvas.drawText("Total Bs.", rightMargin - 8f, currentY + 12f, paint)
        currentY += 18f

        if (sales.isEmpty()) {
            paint.color = Color.WHITE
            canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 18f), paint)
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("No se emitieron facturas en esta jornada.", leftMargin + 8f, currentY + 12f, paint)
            currentY += 22f
        } else {
            val hourFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            sales.forEachIndexed { index, sale ->
                ensureSpace(18f)
                paint.color = if (index % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
                canvas.drawRect(RectF(leftMargin, currentY, rightMargin, currentY + 16f), paint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("#${sale.id}", leftMargin + 8f, currentY + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(hourFmt.format(Date(sale.timestamp)), leftMargin + 60f, currentY + 11f, paint)

                val clientStr = "${sale.customerName} (${sale.customerDocument})"
                val maxCChars = 30
                val displayClient = if (clientStr.length > maxCChars) clientStr.take(maxCChars - 3) + "..." else clientStr
                canvas.drawText(displayClient, leftMargin + 105f, currentY + 11f, paint)

                val methodsUsed = listOfNotNull(
                    if (sale.paymentCashUsd > 0.001) "Efectivo USD" else null,
                    if (sale.paymentMobileBs > 0.001) "Pago Móvil" else null,
                    if (sale.paymentCardBs > 0.001) "Punto Tarjeta" else null,
                    if (sale.paymentCasheaUsd > 0.001) "Cashea" else null,
                    if (sale.paymentCreditUsd > 0.001) "Crédito" else null
                )
                val payMethod = when {
                    methodsUsed.size > 1 -> "Mixto"
                    methodsUsed.size == 1 -> methodsUsed.first()
                    else -> "Efectivo"
                }
                canvas.drawText(payMethod, leftMargin + 280f, currentY + 11f, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(String.format(Locale.US, "$%.2f", sale.totalUsd), rightMargin - 90f, currentY + 11f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(String.format(Locale.US, "Bs. %.2f", sale.totalBs), rightMargin - 8f, currentY + 11f, paint)
                currentY += 16f
            }
            currentY += 14f
        }

        // ==================== SECTION 5: CUADRE FÍSICO Y FIRMAS ====================
        ensureSpace(95f)
        paint.color = Color.parseColor("#F1F5F9")
        val signBox = RectF(leftMargin, currentY, rightMargin, currentY + 75f)
        canvas.drawRoundRect(signBox, 6f, 6f, paint)
        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(signBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#475569")
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "Certifico la exactitud del presente arqueo y cierre de operaciones de la jornada comercial.",
            pageWidth / 2f,
            currentY + 16f,
            paint
        )

        // Signature lines
        val sig1X = leftMargin + 100f
        val sig2X = rightMargin - 100f
        val sigY = currentY + 52f

        paint.color = Color.parseColor("#94A3B8")
        paint.strokeWidth = 1f
        canvas.drawLine(sig1X - 70f, sigY, sig1X + 70f, sigY, paint)
        canvas.drawLine(sig2X - 70f, sigY, sig2X + 70f, sigY, paint)

        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Firma Cajero(a) Responsable", sig1X, sigY + 12f, paint)
        canvas.drawText("Firma Supervisor / Gerencia", sig2X, sigY + 12f, paint)

        // Finish last page
        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(currentPage)

        return try {
            val fileName = "cierre_diario_${dateStr.replace("/", "_")}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
