package com.example.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.Customer
import com.example.data.local.PreferencesManager
import com.example.data.local.Product
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardProductRank(
    val productName: String,
    val quantitySold: Double,
    val totalRevenueUsd: Double
)

data class DashboardCustomerRank(
    val customerName: String,
    val document: String,
    val transactionCount: Int,
    val totalSpentUsd: Double
)

object DashboardPdfExporter {

    fun generateMonthlyDashboardPdf(
        context: Context,
        prefs: PreferencesManager,
        monthYearTitle: String,
        totalSalesUsd: Double,
        totalSalesBs: Double,
        totalCogsUsd: Double,
        grossProfitUsd: Double,
        grossProfitMarginPct: Double,
        purchaseInvoicesCount: Int,
        merchandiseInflowUnits: Double,
        availableStockUnits: Double,
        unitProductsCount: Int,
        topProducts: List<DashboardProductRank>,
        topCustomers: List<DashboardCustomerRank>,
        paymentBreakdownUsd: Map<String, Double>,
        casheaCommissionUsd: Double = 0.0,
        grossSalesUsd: Double = totalSalesUsd
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width
        val pageHeight = 842 // A4 height
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas

        val leftMargin = 36f
        val rightMargin = pageWidth - 36f
        val contentWidth = rightMargin - leftMargin

        val paint = Paint().apply { isAntiAlias = true }

        var y = 40f

        // --- HEADER ---
        paint.color = Color.parseColor("#1E1B4B") // Deep Navy
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)

        // Store Name Header
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val storeTitle = prefs.storeName.ifBlank { "MI NEGOCIO" }.uppercase(Locale.getDefault())
        canvas.drawText("RESUMEN GERENCIAL - $storeTitle", leftMargin, 42f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val rifStr = if (prefs.storeRif.isNotBlank()) "RIF: ${prefs.storeRif}" else "CONTROL DE VENTAS"
        canvas.drawText(rifStr, leftMargin, 60f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PERÍODO: $monthYearTitle", leftMargin, 82f, paint)

        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val generatedStr = "Fecha Emisión: ${dateFmt.format(Date())}"
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(generatedStr, rightMargin, 82f, paint)
        paint.textAlign = Paint.Align.LEFT

        y = 120f

        // --- SECTION 1: FINANCIAL HIGHLIGHTS ---
        paint.color = Color.parseColor("#312E81")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. RESUMEN FINANCIERO Y MARGEN DE GANANCIA", leftMargin, y, paint)
        y += 10f

        paint.color = Color.parseColor("#E2E8F0")
        paint.strokeWidth = 1.5f
        canvas.drawLine(leftMargin, y, rightMargin, y, paint)
        y += 18f

        // Financial Cards (2x2 Grid)
        val cardW = (contentWidth - 16f) / 2f
        val cardH = 50f

        // Card 1: Ingresos Totales
        val incomeSub = if (casheaCommissionUsd > 0.001) {
            String.format(Locale.US, "Bs. %.2f • Bruto $%.2f (-$%.2f Cashea)", totalSalesBs, grossSalesUsd, casheaCommissionUsd)
        } else {
            String.format(Locale.US, "%.2f Bs.", totalSalesBs)
        }
        drawKpiCard(
            canvas, leftMargin, y, cardW, cardH,
            "INGRESOS NETOS (VENTAS)",
            String.format(Locale.US, "$%.2f USD", totalSalesUsd),
            incomeSub,
            "#065F46", "#D1FAE5"
        )

        // Card 2: Margen de Ganancia
        val marginStr = String.format(Locale.US, "$%.2f USD (%.1f%%)", grossProfitUsd, grossProfitMarginPct)
        val cogsStr = String.format(Locale.US, "Costo Ventas: $%.2f USD", totalCogsUsd)
        drawKpiCard(
            canvas, leftMargin + cardW + 16f, y, cardW, cardH,
            "MARGEN DE GANANCIA NETO",
            marginStr,
            cogsStr,
            "#1E40AF", "#DBEAFE"
        )

        y += cardH + 16f

        // --- SECTION 2: OPERACIONAL Y CONTROL DE INVENTARIO ---
        paint.color = Color.parseColor("#312E81")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("2. ACTIVIDAD DE INVENTARIO Y COMPRAS", leftMargin, y, paint)
        y += 10f

        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawLine(leftMargin, y, rightMargin, y, paint)
        y += 18f

        // 4 Columns Operational Metrics
        val opW = (contentWidth - 24f) / 4f
        drawMiniCard(canvas, leftMargin, y, opW, cardH, "FACTURAS COMPRA", "$purchaseInvoicesCount fac.")
        drawMiniCard(canvas, leftMargin + opW + 8f, y, opW, cardH, "MERCANCÍA QUE ENTRÓ", "$merchandiseInflowUnits unid.")
        drawMiniCard(canvas, leftMargin + (opW + 8f) * 2, y, opW, cardH, "STOCK DISPONIBLE", "$availableStockUnits unid.")
        drawMiniCard(canvas, leftMargin + (opW + 8f) * 3, y, opW, cardH, "PROD. UNITARIOS", "$unitProductsCount ítems")

        y += cardH + 24f

        // --- SECTION 3: PRODUCTOS MÁS VENDIDOS ---
        paint.color = Color.parseColor("#312E81")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("3. PRODUCTOS MÁS VENDIDOS DEL MES", leftMargin, y, paint)
        y += 10f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawLine(leftMargin, y, rightMargin, y, paint)
        y += 14f

        // Table Header
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(leftMargin, y, rightMargin, y + 20f, paint)

        paint.color = Color.parseColor("#334155")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("#", leftMargin + 8f, y + 14f, paint)
        canvas.drawText("PRODUCTO", leftMargin + 30f, y + 14f, paint)
        canvas.drawText("CANT. VENDIDA", leftMargin + 320f, y + 14f, paint)
        canvas.drawText("TOTAL ($ USD)", rightMargin - 80f, y + 14f, paint)

        y += 24f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        topProducts.take(8).forEachIndexed { idx, item ->
            paint.color = if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            canvas.drawRect(leftMargin, y - 10f, rightMargin, y + 10f, paint)

            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("${idx + 1}", leftMargin + 8f, y, paint)

            val pName = if (item.productName.length > 40) item.productName.take(38) + ".." else item.productName
            canvas.drawText(pName, leftMargin + 30f, y, paint)
            canvas.drawText("${item.quantitySold} unid.", leftMargin + 320f, y, paint)
            canvas.drawText(String.format(Locale.US, "$%.2f", item.totalRevenueUsd), rightMargin - 80f, y, paint)
            y += 20f
        }

        y += 16f

        // --- SECTION 4: CLIENTES FRECUENTES ---
        paint.color = Color.parseColor("#312E81")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("4. CLIENTES FRECUENTES DEL MES", leftMargin, y, paint)
        y += 10f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawLine(leftMargin, y, rightMargin, y, paint)
        y += 14f

        // Table Header
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(leftMargin, y, rightMargin, y + 20f, paint)

        paint.color = Color.parseColor("#334155")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("#", leftMargin + 8f, y + 14f, paint)
        canvas.drawText("CLIENTE", leftMargin + 30f, y + 14f, paint)
        canvas.drawText("CÉDULA / RIF", leftMargin + 250f, y + 14f, paint)
        canvas.drawText("TRANSACCIONES", leftMargin + 370f, y + 14f, paint)
        canvas.drawText("TOTAL COMPRADO", rightMargin - 85f, y + 14f, paint)

        y += 24f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        topCustomers.take(5).forEachIndexed { idx, cust ->
            paint.color = if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            canvas.drawRect(leftMargin, y - 10f, rightMargin, y + 10f, paint)

            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("${idx + 1}", leftMargin + 8f, y, paint)
            val cName = if (cust.customerName.length > 30) cust.customerName.take(28) + ".." else cust.customerName
            canvas.drawText(cName, leftMargin + 30f, y, paint)
            canvas.drawText(cust.document.ifBlank { "V-00000000" }, leftMargin + 250f, y, paint)
            canvas.drawText("${cust.transactionCount} compras", leftMargin + 370f, y, paint)
            canvas.drawText(String.format(Locale.US, "$%.2f USD", cust.totalSpentUsd), rightMargin - 85f, y, paint)
            y += 20f
        }

        // --- SECTION 5: DESGLOSE POR MÉTODO DE PAGO ---
        if (paymentBreakdownUsd.isNotEmpty()) {
            y += 14f
            val neededForPayments = 50f + (paymentBreakdownUsd.size * 18f)
            if (y + neededForPayments > pageHeight - 50f) {
                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("DOCUMENTO INTERNO DE CONTROL GERENCIAL - Página $pageNumber", pageWidth / 2f, pageHeight - 25f, paint)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 40f
            }

            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#312E81")
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("5. DESGLOSE DE INGRESOS POR MÉTODO DE PAGO", leftMargin, y, paint)
            y += 10f
            paint.color = Color.parseColor("#E2E8F0")
            canvas.drawLine(leftMargin, y, rightMargin, y, paint)
            y += 14f

            val totalPay = paymentBreakdownUsd.values.sum().coerceAtLeast(0.001)
            paymentBreakdownUsd.entries.forEachIndexed { idx, (method, amount) ->
                paint.color = if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
                canvas.drawRect(leftMargin, y - 10f, rightMargin, y + 8f, paint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(method, leftMargin + 8f, y + 2f, paint)

                val pct = (amount / totalPay) * 100.0
                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(String.format(Locale.US, "$%.2f USD (%.1f%%)", amount, pct), rightMargin - 8f, y + 2f, paint)
                y += 18f
            }
        }

        // Footer Note
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DOCUMENTO INTERNO DE CONTROL GERENCIAL • Página $pageNumber", pageWidth / 2f, pageHeight - 25f, paint)

        pdfDocument.finishPage(page)

        val safeTitle = monthYearTitle.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val outputFile = File(context.cacheDir, "Dashboard_$safeTitle.pdf")
        return try {
            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun drawKpiCard(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        title: String, mainVal: String, subVal: String,
        textColorHex: String, bgHex: String
    ) {
        val paint = Paint().apply { isAntiAlias = true }

        // Background Box
        paint.color = Color.parseColor(bgHex)
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        // Border
        paint.color = Color.parseColor(textColorHex)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(title, x + 10f, y + 16f, paint)

        paint.textSize = 13f
        canvas.drawText(mainVal, x + 10f, y + 33f, paint)

        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(subVal, x + 10f, y + 45f, paint)
    }

    private fun drawMiniCard(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        title: String, value: String
    ) {
        val paint = Paint().apply { isAntiAlias = true }

        // Background Box
        paint.color = Color.parseColor("#F8FAFC")
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#475569")
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(title, x + 6f, y + 16f, paint)

        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 12f
        canvas.drawText(value, x + 6f, y + 36f, paint)
    }
}
