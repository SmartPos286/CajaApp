package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import com.example.data.local.CasheaSettlement
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.Expense
import com.example.data.local.PosTerminalRecord
import com.example.data.local.PreferencesManager
import com.example.data.local.Product
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.data.local.Supplier
import com.example.ui.components.TicketUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportManager {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT_MARGIN = 34f
    private const val RIGHT_MARGIN = PAGE_WIDTH - 34f
    private const val CONTENT_WIDTH = RIGHT_MARGIN - LEFT_MARGIN
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - 38f

    private val COLOR_NAVY = Color.parseColor("#0F172A")
    private val COLOR_INDIGO = Color.parseColor("#1E3A8A")
    private val COLOR_TEXT_DARK = Color.parseColor("#0F172A")
    private val COLOR_TEXT_MUTED = Color.parseColor("#64748B")
    private val COLOR_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_ROW_ALT = Color.parseColor("#F8FAFC")
    private val COLOR_HEADER_BG = Color.parseColor("#E2E8F0")
    private val COLOR_EMERALD = Color.parseColor("#059669")
    private val COLOR_EMERALD_BG = Color.parseColor("#ECFDF5")
    private val COLOR_RED = Color.parseColor("#DC2626")
    private val COLOR_RED_BG = Color.parseColor("#FEF2F2")
    private val COLOR_AMBER = Color.parseColor("#B45309")
    private val COLOR_AMBER_BG = Color.parseColor("#FFFBEB")

    fun copyFileToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: return false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun sharePdfFile(
        context: Context,
        file: File,
        subject: String = file.nameWithoutExtension
    ) {
        try {
            val uri = TicketUtils.getUriForFile(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, subject)
                clipData = android.content.ClipData.newUri(context.contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartir PDF").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No se pudo compartir el archivo PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPdfFile(context: Context, file: File) {
        try {
            val uri = TicketUtils.getUriForFile(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Abrir PDF").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            sharePdfFile(context, file)
        }
    }

    private class PagedPdfCanvas(
        private val storeName: String,
        private val runningHeaderTitle: String,
        private val footerLeftLabel: String
    ) {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
            private set
        var currentPage: PdfDocument.Page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
            private set
        var canvas: Canvas = currentPage.canvas
            private set
        var y: Float = 26f

        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 8f
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            strokeWidth = 0.6f
        }

        fun ensureSpace(neededHeight: Float, onNewPage: ((Canvas) -> Unit)? = null): Boolean {
            if (y + neededHeight <= BOTTOM_LIMIT) return false
            drawFooter()
            pdfDocument.finishPage(currentPage)
            pageNumber++
            currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = currentPage.canvas

            // Running header
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_DARK
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(
                "${storeName.ifBlank { "MI COMERCIO" }} • $runningHeaderTitle",
                LEFT_MARGIN,
                24f,
                headerPaint
            )
            canvas.drawLine(LEFT_MARGIN, 30f, RIGHT_MARGIN, 30f, linePaint)
            y = 42f
            onNewPage?.invoke(canvas)
            return true
        }

        fun drawFooter() {
            footerPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(footerLeftLabel, LEFT_MARGIN, PAGE_HEIGHT - 18f, footerPaint)
            footerPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Página $pageNumber", RIGHT_MARGIN, PAGE_HEIGHT - 18f, footerPaint)
        }

        fun finishAndSave(outputFile: File): File? {
            return try {
                drawFooter()
                pdfDocument.finishPage(currentPage)
                FileOutputStream(outputFile).use { fos ->
                    pdfDocument.writeTo(fos)
                    fos.flush()
                }
                pdfDocument.close()
                outputFile
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    pdfDocument.close()
                } catch (_: Exception) {
                }
                null
            }
        }
    }

    private fun loadStoreLogo(prefs: PreferencesManager): Bitmap? {
        val path = prefs.storeLogoPath
        if (path.isBlank()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun drawStoreHeaderBanner(
        canvas: Canvas,
        prefs: PreferencesManager,
        documentTitle: String,
        documentRightPrimary: String,
        documentRightSecondary: String
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bannerTop = 24f
        val bannerHeight = 76f
        paint.color = COLOR_NAVY
        canvas.drawRoundRect(
            RectF(LEFT_MARGIN, bannerTop, RIGHT_MARGIN, bannerTop + bannerHeight),
            8f,
            8f,
            paint
        )

        val logoBitmap = loadStoreLogo(prefs)
        var textStartX = LEFT_MARGIN + 14f
        if (logoBitmap != null) {
            val logoSize = 54f
            val logoRect = RectF(
                LEFT_MARGIN + 12f,
                bannerTop + 11f,
                LEFT_MARGIN + 12f + logoSize,
                bannerTop + 11f + logoSize
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(logoRect, 6f, 6f, bgPaint)
            canvas.drawBitmap(logoBitmap, null, logoRect, null)
            logoBitmap.recycle()
            textStartX = LEFT_MARGIN + 12f + logoSize + 12f
        }

        val storeName = prefs.storeName.ifBlank { "MI COMERCIO POS" }.uppercase(Locale.getDefault())
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 14.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(ellipsizeText(storeName, paint, 285f), textStartX, bannerTop + 23f, paint)

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#CBD5E1")
        val rifStr = "RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}"
        val phoneStr = if (prefs.storePhone.isNotBlank()) " • Telf: ${prefs.storePhone}" else ""
        canvas.drawText(ellipsizeText("$rifStr$phoneStr", paint, 285f), textStartX, bannerTop + 39f, paint)

        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(ellipsizeText(prefs.storeAddress, paint, 285f), textStartX, bannerTop + 52f, paint)
        }

        paint.textSize = 8f
        paint.color = Color.parseColor("#93C5FD")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(documentTitle, textStartX, bannerTop + 66f, paint)

        // Right-aligned doc identifier
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(documentRightPrimary, RIGHT_MARGIN - 14f, bannerTop + 32f, paint)

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawText(documentRightSecondary, RIGHT_MARGIN - 14f, bannerTop + 48f, paint)

        return bannerTop + bannerHeight + 10f
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return if (end <= 1) text.take(1) else text.substring(0, end) + "..."
    }

    private fun createStaticLayout(
        text: String,
        textPaint: TextPaint,
        width: Int
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1.0f)
            .setIncludePad(false)
            .build()
    }

    private fun fmtUsd(amount: Double): String = String.format(Locale.US, "$%.2f", amount)
    private fun fmtBs(amount: Double): String = String.format(Locale.US, "Bs. %.2f", amount)
    private fun fmtQty(qty: Double): String {
        return if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.US, "%.2f", qty)
    }

    // ==================== 1. COMPROBANTE / FACTURA INDIVIDUAL ====================

    fun generateSaleInvoicePdf(
        context: Context,
        prefs: PreferencesManager,
        sale: Sale,
        items: List<SaleItem>,
        customerPhone: String = "",
        customerAddress: String = ""
    ): File? {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0
        val storeLabel = prefs.storeName.ifBlank { "Mi Tienda" }

        val paged = PagedPdfCanvas(
            storeName = storeLabel,
            runningHeaderTitle = "Factura #${sale.id}",
            footerLeftLabel = if (prefs.isFiscalPrinter) {
                "Comprobante Fiscal N° ${String.format(Locale.getDefault(), "%08d", sale.id)} • $storeLabel"
            } else {
                "Documento de Control Interno • No válido como factura fiscal"
            }
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val docBadge = if (prefs.isFiscalPrinter) {
            "SENIAT • FACTURA FISCAL (${prefs.fiscalPrinterBrand.ifBlank { "FISCAL" }})"
        } else {
            "COMPROBANTE DE VENTA • CONTROL INTERNO"
        }

        paged.y = drawStoreHeaderBanner(
            canvas = paged.canvas,
            prefs = prefs,
            documentTitle = docBadge,
            documentRightPrimary = "FACTURA N° #${sale.id}",
            documentRightSecondary = "Fecha: ${sdfDateTime.format(Date(sale.timestamp))}"
        )

        // Legal Notice strip
        val noticeBg = if (prefs.isFiscalPrinter) COLOR_EMERALD_BG else COLOR_AMBER_BG
        val noticeTextCol = if (prefs.isFiscalPrinter) COLOR_EMERALD else COLOR_AMBER
        paint.color = noticeBg
        paged.canvas.drawRoundRect(RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f), 4f, 4f, paint)
        paint.color = noticeTextCol
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val noticeMsg = if (prefs.isFiscalPrinter) {
            String.format(Locale.getDefault(), "COMPROBANTE FISCAL AUTORIZADO N° %08d • TASA BCV: Bs. %.2f", sale.id, rate)
        } else {
            "SOLO PARA USO INTERNO / CONTROL DE VENTAS • NO VÁLIDO COMO FACTURA FISCAL • TASA BCV: Bs. ${String.format(Locale.US, "%.2f", rate)}"
        }
        paged.canvas.drawText(noticeMsg, PAGE_WIDTH / 2f, paged.y + 13f, paint)
        paged.y += 28f

        // Two Metadata Boxes: Document vs Customer
        val boxGap = 10f
        val boxW = (CONTENT_WIDTH - boxGap) / 2f
        val metaH = 68f
        val leftBox = RectF(LEFT_MARGIN, paged.y, LEFT_MARGIN + boxW, paged.y + metaH)
        val rightBox = RectF(LEFT_MARGIN + boxW + boxGap, paged.y, RIGHT_MARGIN, paged.y + metaH)

        for (rect in listOf(leftBox, rightBox)) {
            paint.style = Paint.Style.FILL
            paint.color = COLOR_ROW_ALT
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = COLOR_BORDER
            paint.strokeWidth = 0.8f
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT

        // Left Box: Document info
        val cashierDisplay = sale.cashierName.ifBlank { "Cajero" }
        val regDisplay = sale.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        paint.color = COLOR_INDIGO
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("DATOS DE OPERACIÓN", leftBox.left + 10f, leftBox.top + 14f, paint)

        paint.color = COLOR_TEXT_DARK
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paged.canvas.drawText("Comprobante: #${sale.id}", leftBox.left + 10f, leftBox.top + 28f, paint)
        paged.canvas.drawText("Emisión: ${sdfDateTime.format(Date(sale.timestamp))}", leftBox.left + 10f, leftBox.top + 41f, paint)
        paged.canvas.drawText("Caja: $regDisplay  •  Cajero(a): $cashierDisplay", leftBox.left + 10f, leftBox.top + 54f, paint)

        // Right Box: Customer info
        paint.color = COLOR_INDIGO
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("DATOS DEL CLIENTE", rightBox.left + 10f, rightBox.top + 14f, paint)

        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val custName = sale.customerName.ifBlank { "Consumidor Final" }
        paged.canvas.drawText(ellipsizeText(custName, paint, boxW - 20f), rightBox.left + 10f, rightBox.top + 28f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val custDoc = sale.customerDocument.ifBlank { "V-00000000" }
        val phonePart = if (customerPhone.isNotBlank()) " • Telf: $customerPhone" else ""
        paged.canvas.drawText(ellipsizeText("CI/RIF: $custDoc$phonePart", paint, boxW - 20f), rightBox.left + 10f, rightBox.top + 41f, paint)
        val extraInfo = if (customerAddress.isNotBlank()) customerAddress else "Moneda ref.: Dólar USD / Bolívares Bs."
        paged.canvas.drawText(ellipsizeText(extraInfo, paint, boxW - 20f), rightBox.left + 10f, rightBox.top + 54f, paint)

        paged.y += metaH + 14f

        // Section: Items Table
        fun drawItemsHeader(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = COLOR_NAVY
            c.drawRoundRect(RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f), 4f, 4f, p)
            p.color = Color.WHITE
            p.textSize = 8.5f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            p.textAlign = Paint.Align.CENTER
            c.drawText("Cant.", LEFT_MARGIN + 22f, paged.y + 13f, p)
            p.textAlign = Paint.Align.LEFT
            c.drawText("Descripción del Producto", LEFT_MARGIN + 48f, paged.y + 13f, p)
            p.textAlign = Paint.Align.CENTER
            c.drawText("Imp.", LEFT_MARGIN + 318f, paged.y + 13f, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText("P. Unit ($)", LEFT_MARGIN + 392f, paged.y + 13f, p)
            c.drawText("Total ($)", LEFT_MARGIN + 455f, paged.y + 13f, p)
            c.drawText("Total (Bs.)", RIGHT_MARGIN - 8f, paged.y + 13f, p)
            paged.y += 22f
        }

        drawItemsHeader(paged.canvas)

        val itemTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DARK
            textSize = 8.5f
        }

        items.forEachIndexed { index, item ->
            val descText = item.productName.ifBlank { "Producto" }
            val descLayout = createStaticLayout(descText, itemTextPaint, 240)
            val rowH = maxOf(18f, descLayout.height + 8f)

            paged.ensureSpace(rowH + 6f) { newCanvas ->
                drawItemsHeader(newCanvas)
            }

            if (index % 2 == 1) {
                paint.color = COLOR_ROW_ALT
                paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + rowH, paint)
            }

            val textBaseline = paged.y + 12f
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paged.canvas.drawText(fmtQty(item.quantity), LEFT_MARGIN + 22f, textBaseline, paint)

            // Wrapped description
            paged.canvas.save()
            paged.canvas.translate(LEFT_MARGIN + 48f, paged.y + 4f)
            descLayout.draw(paged.canvas)
            paged.canvas.restore()

            // Tax flag
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 7.5f
            if (item.isTaxExempt) {
                paint.color = COLOR_EMERALD
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText("Exento", LEFT_MARGIN + 318f, textBaseline, paint)
            } else {
                paint.color = COLOR_TEXT_MUTED
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paged.canvas.drawText("IVA 16%", LEFT_MARGIN + 318f, textBaseline, paint)
            }

            val lineTotalUsd = item.priceUsd * item.quantity
            val lineTotalBs = lineTotalUsd * rate

            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8.5f
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(fmtUsd(item.priceUsd), LEFT_MARGIN + 392f, textBaseline, paint)
            paged.canvas.drawText(fmtUsd(lineTotalUsd), LEFT_MARGIN + 455f, textBaseline, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText(fmtBs(lineTotalBs), RIGHT_MARGIN - 8f, textBaseline, paint)

            paged.y += rowH
            paint.color = COLOR_HEADER_BG
            paint.strokeWidth = 0.5f
            paged.canvas.drawLine(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y, paint)
        }

        paged.y += 14f
        paged.ensureSpace(175f)

        // Bottom Split: Payments (Left) & Fiscal Totals (Right)
        val splitTop = paged.y
        val payBoxW = 245f
        val totBoxX = LEFT_MARGIN + payBoxW + 14f
        val totBoxW = RIGHT_MARGIN - totBoxX

        // --- Left: Payment Methods Breakdown ---
        var payY = splitTop
        paint.color = COLOR_ROW_ALT
        val payEntries = mutableListOf<Pair<String, String>>()
        if (sale.paymentCashUsd > 0.001) {
            payEntries.add("Efectivo USD" to "${fmtUsd(sale.paymentCashUsd)} (${fmtBs(sale.paymentCashUsd * rate)})")
        }
        if (sale.paymentMobileBs > 0.001) {
            payEntries.add("Pago Móvil" to fmtBs(sale.paymentMobileBs))
        }
        if (sale.paymentCardBs > 0.001) {
            payEntries.add("Punto / Tarjeta" to fmtBs(sale.paymentCardBs))
        }
        if (sale.paymentCasheaUsd > 0.001) {
            payEntries.add("Cashea" to "${fmtUsd(sale.paymentCasheaUsd)} (${fmtBs(sale.paymentCasheaUsd * rate)})")
        }
        if (sale.paymentCreditUsd > 0.001) {
            val statusStr = if (sale.isCreditPaid) "PAGADO" else "PENDIENTE"
            payEntries.add("Crédito ($statusStr)" to fmtUsd(sale.paymentCreditUsd))
            if (!sale.isCreditPaid) {
                val pendingUsd = (sale.paymentCreditUsd - sale.creditPaidAmountUsd).coerceAtLeast(0.0)
                payEntries.add("Saldo Pendiente" to "${fmtUsd(pendingUsd)} (${fmtBs(pendingUsd * rate)})")
            }
            if (sale.creditDueDate > 0L) {
                payEntries.add("Vencimiento Crédito" to sdfDateOnly.format(Date(sale.creditDueDate)))
            }
        }
        if (payEntries.isEmpty()) {
            payEntries.add("Contado" to fmtBs(if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate))
        }

        val payBoxH = 28f + (payEntries.size * 15f)
        val payRect = RectF(LEFT_MARGIN, payY, LEFT_MARGIN + payBoxW, payY + payBoxH)
        paint.style = Paint.Style.FILL
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(payRect, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        paged.canvas.drawRoundRect(payRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_INDIGO
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paged.canvas.drawText("DETALLE DE PAGOS RECIBIDOS", LEFT_MARGIN + 10f, payY + 15f, paint)
        payY += 26f

        payEntries.forEach { (label, valStr) ->
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(label, LEFT_MARGIN + 10f, payY, paint)

            paint.color = COLOR_TEXT_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.RIGHT
            paged.canvas.drawText(valStr, LEFT_MARGIN + payBoxW - 10f, payY, paint)
            payY += 15f
        }

        // --- Right: Fiscal Totals ---
        var totY = splitTop
        val baseTaxableBs = sale.baseTaxableUsd * rate
        val baseExemptBs = sale.baseExemptUsd * rate
        val discountBs = sale.discountUsd * rate
        val ivaBs = sale.ivaUsd * rate
        val igtfBs = if (sale.igtfBs > 0.001) sale.igtfBs else sale.igtfUsd * rate
        val totalBs = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate

        fun drawTotalLine(label: String, usdVal: String, bsVal: String, color: Int = COLOR_TEXT_DARK) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 8.5f
            paint.color = COLOR_TEXT_MUTED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(label, totBoxX + 4f, totY + 10f, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = color
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText("$usdVal  |  $bsVal", RIGHT_MARGIN - 4f, totY + 10f, paint)
            totY += 16f
        }

        drawTotalLine("Base Gravable (16%):", fmtUsd(sale.baseTaxableUsd), fmtBs(baseTaxableBs))
        if (sale.baseExemptUsd > 0.001) {
            drawTotalLine("Monto Exento (0%):", fmtUsd(sale.baseExemptUsd), fmtBs(baseExemptBs), COLOR_EMERALD)
        }
        if (sale.discountUsd > 0.001) {
            drawTotalLine("Descuento (pre-IVA):", "-${fmtUsd(sale.discountUsd)}", "-${fmtBs(discountBs)}", COLOR_RED)
        }
        drawTotalLine("IVA (16%):", fmtUsd(sale.ivaUsd), fmtBs(ivaBs), COLOR_INDIGO)
        if (sale.igtfUsd > 0.001 || igtfBs > 0.001) {
            drawTotalLine("IGTF (3% Divisas):", fmtUsd(sale.igtfUsd), fmtBs(igtfBs), COLOR_AMBER)
        }
        if (sale.retencionIvaUsd > 0.001) {
            drawTotalLine("Retención IVA:", "-${fmtUsd(sale.retencionIvaUsd)}", "-${fmtBs(sale.retencionIvaUsd * rate)}", COLOR_RED)
        }
        if (sale.retencionIslrUsd > 0.001) {
            drawTotalLine("Retención ISLR:", "-${fmtUsd(sale.retencionIslrUsd)}", "-${fmtBs(sale.retencionIslrUsd * rate)}", COLOR_RED)
        }

        totY += 4f
        val grandRect = RectF(totBoxX, totY, RIGHT_MARGIN, totY + 42f)
        paint.color = COLOR_NAVY
        paged.canvas.drawRoundRect(grandRect, 6f, 6f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("TOTAL FACTURA", totBoxX + 10f, totY + 18f, paint)

        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#CBD5E1")
        paged.canvas.drawText("Tasa BCV: Bs. ${String.format(Locale.US, "%.2f", rate)}", totBoxX + 10f, totY + 32f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText(fmtBs(totalBs), RIGHT_MARGIN - 10f, totY + 19f, paint)

        paint.textSize = 9f
        paint.color = Color.parseColor("#6EE7B7")
        paged.canvas.drawText("${fmtUsd(sale.totalUsd)} USD", RIGHT_MARGIN - 10f, totY + 33f, paint)

        paged.y = maxOf(payRect.bottom, grandRect.bottom) + 18f

        if (sale.creditNotes.isNotBlank()) {
            paged.ensureSpace(24f)
            paint.textAlign = Paint.Align.LEFT
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paged.canvas.drawText("Notas: ${sale.creditNotes.take(90)}", LEFT_MARGIN, paged.y, paint)
            paged.y += 14f
        }

        paint.textAlign = Paint.Align.CENTER
        paint.color = COLOR_TEXT_MUTED
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paged.canvas.drawText("¡Gracias por su preferencia!", PAGE_WIDTH / 2f, paged.y + 4f, paint)

        val outFile = File(context.cacheDir, "Factura_${sale.id}.pdf")
        return paged.finishAndSave(outFile)
    }

    // ==================== 2. PRESUPUESTO / COTIZACIÓN PDF ====================

    fun generateQuotePdf(
        context: Context,
        prefs: PreferencesManager,
        quote: Quote,
        items: List<QuoteItem>
    ): File? {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val rate = if (quote.rateBcv > 0.001) quote.rateBcv else 1.0
        val quoteCode = quote.quoteNumber.ifBlank { "PRE-${quote.id}" }
        val storeLabel = prefs.storeName.ifBlank { "Mi Tienda" }

        val paged = PagedPdfCanvas(
            storeName = storeLabel,
            runningHeaderTitle = "Presupuesto $quoteCode",
            footerLeftLabel = "Presupuesto / Cotización $quoteCode • No válido como factura fiscal"
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paged.y = drawStoreHeaderBanner(
            canvas = paged.canvas,
            prefs = prefs,
            documentTitle = "PRESUPUESTO / COTIZACIÓN (NO FISCAL)",
            documentRightPrimary = quoteCode,
            documentRightSecondary = "Emisión: ${sdfDateTime.format(Date(quote.timestamp))}"
        )

        // Two Meta Boxes
        val boxGap = 10f
        val boxW = (CONTENT_WIDTH - boxGap) / 2f
        val metaH = 68f
        val leftBox = RectF(LEFT_MARGIN, paged.y, LEFT_MARGIN + boxW, paged.y + metaH)
        val rightBox = RectF(LEFT_MARGIN + boxW + boxGap, paged.y, RIGHT_MARGIN, paged.y + metaH)

        for (rect in listOf(leftBox, rightBox)) {
            paint.style = Paint.Style.FILL
            paint.color = COLOR_ROW_ALT
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = COLOR_BORDER
            paint.strokeWidth = 0.8f
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT

        paint.color = COLOR_INDIGO
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("DETALLES DEL PRESUPUESTO", leftBox.left + 10f, leftBox.top + 14f, paint)

        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val validStr = if (quote.validUntil > 0L) {
            "${sdfDateOnly.format(Date(quote.validUntil))} (${quote.validityDays} días)"
        } else {
            "${quote.validityDays} días"
        }
        paged.canvas.drawText("Válido hasta: $validStr", leftBox.left + 10f, leftBox.top + 28f, paint)
        paged.canvas.drawText("Estado: ${quote.status}  •  Tasa BCV: Bs. ${String.format(Locale.US, "%.2f", rate)}", leftBox.left + 10f, leftBox.top + 41f, paint)
        paged.canvas.drawText("Atendido por: ${quote.cashierName.ifBlank { "Cajero" }}", leftBox.left + 10f, leftBox.top + 54f, paint)

        paint.color = COLOR_INDIGO
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("CLIENTE DESTINATARIO", rightBox.left + 10f, rightBox.top + 14f, paint)

        paint.color = COLOR_TEXT_DARK
        paged.canvas.drawText(ellipsizeText(quote.customerName.ifBlank { "Consumidor Final" }, paint, boxW - 20f), rightBox.left + 10f, rightBox.top + 28f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paged.canvas.drawText("CI/RIF: ${quote.customerDocument.ifBlank { "V-00000000" }}", rightBox.left + 10f, rightBox.top + 41f, paint)
        if (quote.customerPhone.isNotBlank()) {
            paged.canvas.drawText("Teléfono: ${quote.customerPhone}", rightBox.left + 10f, rightBox.top + 54f, paint)
        }

        paged.y += metaH + 14f

        fun drawItemsHeader(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = COLOR_INDIGO
            c.drawRoundRect(RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f), 4f, 4f, p)
            p.color = Color.WHITE
            p.textSize = 8.5f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            c.drawText("Cant.", LEFT_MARGIN + 22f, paged.y + 13f, p)
            p.textAlign = Paint.Align.LEFT
            c.drawText("Descripción", LEFT_MARGIN + 48f, paged.y + 13f, p)
            p.textAlign = Paint.Align.CENTER
            c.drawText("Imp.", LEFT_MARGIN + 318f, paged.y + 13f, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText("P. Unit ($)", LEFT_MARGIN + 392f, paged.y + 13f, p)
            c.drawText("Total ($)", LEFT_MARGIN + 455f, paged.y + 13f, p)
            c.drawText("Total (Bs.)", RIGHT_MARGIN - 8f, paged.y + 13f, p)
            paged.y += 22f
        }

        drawItemsHeader(paged.canvas)

        val itemTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DARK
            textSize = 8.5f
        }

        items.forEachIndexed { index, item ->
            val descLayout = createStaticLayout(item.productName.ifBlank { "Producto" }, itemTextPaint, 240)
            val rowH = maxOf(18f, descLayout.height + 8f)
            paged.ensureSpace(rowH + 6f) { drawItemsHeader(it) }

            if (index % 2 == 1) {
                paint.color = COLOR_ROW_ALT
                paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + rowH, paint)
            }

            val baseLine = paged.y + 12f
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paged.canvas.drawText(fmtQty(item.quantity), LEFT_MARGIN + 22f, baseLine, paint)

            paged.canvas.save()
            paged.canvas.translate(LEFT_MARGIN + 48f, paged.y + 4f)
            descLayout.draw(paged.canvas)
            paged.canvas.restore()

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 7.5f
            paint.color = if (item.isTaxExempt) COLOR_EMERALD else COLOR_TEXT_MUTED
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (item.isTaxExempt) Typeface.BOLD else Typeface.NORMAL)
            paged.canvas.drawText(if (item.isTaxExempt) "Exento" else "IVA 16%", LEFT_MARGIN + 318f, baseLine, paint)

            val lineUsd = item.priceUsd * item.quantity
            val lineBs = lineUsd * rate
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8.5f
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(fmtUsd(item.priceUsd), LEFT_MARGIN + 392f, baseLine, paint)
            paged.canvas.drawText(fmtUsd(lineUsd), LEFT_MARGIN + 455f, baseLine, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText(fmtBs(lineBs), RIGHT_MARGIN - 8f, baseLine, paint)

            paged.y += rowH
            paint.color = COLOR_HEADER_BG
            paint.strokeWidth = 0.5f
            paged.canvas.drawLine(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y, paint)
        }

        paged.y += 14f
        paged.ensureSpace(145f)

        // Totals right-aligned
        val totX = RIGHT_MARGIN - 265f
        val baseTaxBs = quote.baseTaxableUsd * rate
        val baseExBs = quote.baseExemptUsd * rate
        val discBs = quote.discountUsd * rate
        val ivaBs = quote.ivaUsd * rate
        val totalBs = if (quote.totalBs > 0.001) quote.totalBs else quote.totalUsd * rate

        fun drawQuoteLine(label: String, usdStr: String, bsStr: String, color: Int = COLOR_TEXT_DARK) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 8.5f
            paint.color = COLOR_TEXT_MUTED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(label, totX, paged.y + 10f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = color
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText("$usdStr  |  $bsStr", RIGHT_MARGIN - 4f, paged.y + 10f, paint)
            paged.y += 16f
        }

        drawQuoteLine("Base Imponible (16%):", fmtUsd(quote.baseTaxableUsd), fmtBs(baseTaxBs))
        if (quote.baseExemptUsd > 0.001) {
            drawQuoteLine("Monto Exento (0%):", fmtUsd(quote.baseExemptUsd), fmtBs(baseExBs), COLOR_EMERALD)
        }
        if (quote.discountUsd > 0.001) {
            drawQuoteLine("Descuento (pre-IVA):", "-${fmtUsd(quote.discountUsd)}", "-${fmtBs(discBs)}", COLOR_RED)
        }
        drawQuoteLine("IVA (16%):", fmtUsd(quote.ivaUsd), fmtBs(ivaBs), COLOR_INDIGO)

        paged.y += 4f
        val totalRect = RectF(totX - 6f, paged.y, RIGHT_MARGIN, paged.y + 42f)
        paint.color = COLOR_INDIGO
        paged.canvas.drawRoundRect(totalRect, 6f, 6f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("TOTAL PRESUPUESTO", totX + 6f, paged.y + 18f, paint)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#DBEAFE")
        paged.canvas.drawText("Tasa BCV: Bs. ${String.format(Locale.US, "%.2f", rate)}", totX + 6f, paged.y + 32f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText(fmtBs(totalBs), RIGHT_MARGIN - 10f, paged.y + 19f, paint)
        paint.textSize = 9f
        paint.color = Color.parseColor("#93C5FD")
        paged.canvas.drawText("${fmtUsd(quote.totalUsd)} USD", RIGHT_MARGIN - 10f, paged.y + 33f, paint)

        paged.y = totalRect.bottom + 14f

        if (quote.notesOrConditions.isNotBlank()) {
            val notesLayout = createStaticLayout("Condiciones / Observaciones: ${quote.notesOrConditions}", itemTextPaint, CONTENT_WIDTH.toInt())
            paged.ensureSpace(notesLayout.height + 16f)
            paged.canvas.save()
            paged.canvas.translate(LEFT_MARGIN, paged.y)
            notesLayout.draw(paged.canvas)
            paged.canvas.restore()
            paged.y += notesLayout.height + 12f
        }

        paint.textAlign = Paint.Align.CENTER
        paint.color = COLOR_TEXT_MUTED
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paged.canvas.drawText("Precios referenciales sujetos a disponibilidad y variación de tasa BCV al momento de compra.", PAGE_WIDTH / 2f, paged.y + 4f, paint)

        val safeCode = quoteCode.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val outFile = File(context.cacheDir, "Presupuesto_$safeCode.pdf")
        return paged.finishAndSave(outFile)
    }

    // ==================== 3. NOTA DE ENTREGA PDF ====================

    fun generateDeliveryNotePdf(
        context: Context,
        prefs: PreferencesManager,
        note: DeliveryNote,
        items: List<DeliveryNoteItem>
    ): File? {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val rate = if (note.rateBcv > 0.001) note.rateBcv else 1.0
        val useBs = note.displayCurrency.equals("BS", ignoreCase = true)
        val noteCode = note.noteNumber.ifBlank { "NE-${note.id}" }
        val storeLabel = prefs.storeName.ifBlank { "Mi Tienda" }

        val paged = PagedPdfCanvas(
            storeName = storeLabel,
            runningHeaderTitle = "Nota de Entrega $noteCode",
            footerLeftLabel = "Nota de Entrega $noteCode • No válido para crédito fiscal"
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paged.y = drawStoreHeaderBanner(
            canvas = paged.canvas,
            prefs = prefs,
            documentTitle = "NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL",
            documentRightPrimary = noteCode,
            documentRightSecondary = "Fecha: ${sdfDateTime.format(Date(note.timestamp))}"
        )

        // Legal Notice strip (SENIAT)
        paint.color = COLOR_AMBER_BG
        paged.canvas.drawRoundRect(RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f), 4f, 4f, paint)
        paint.color = COLOR_AMBER
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val legalStrip = if (useBs) {
            "NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL • MONEDA: BOLÍVARES (Bs.) • TASA BCV: Bs. ${String.format(Locale.US, "%.2f", rate)}"
        } else {
            "NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL • MONEDA: DÓLARES ($ USD)"
        }
        paged.canvas.drawText(legalStrip, PAGE_WIDTH / 2f, paged.y + 13f, paint)
        paged.y += 28f

        val boxGap = 10f
        val boxW = (CONTENT_WIDTH - boxGap) / 2f
        val metaH = 68f
        val leftBox = RectF(LEFT_MARGIN, paged.y, LEFT_MARGIN + boxW, paged.y + metaH)
        val rightBox = RectF(LEFT_MARGIN + boxW + boxGap, paged.y, RIGHT_MARGIN, paged.y + metaH)

        for (rect in listOf(leftBox, rightBox)) {
            paint.style = Paint.Style.FILL
            paint.color = COLOR_ROW_ALT
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = COLOR_BORDER
            paint.strokeWidth = 0.8f
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT

        paint.color = COLOR_INDIGO
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("DATOS DE DESPACHO", leftBox.left + 10f, leftBox.top + 14f, paint)
        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paged.canvas.drawText("Estado: ${note.status} • Caja: ${note.cashRegisterNumber.ifBlank { "01" }}", leftBox.left + 10f, leftBox.top + 28f, paint)
        paged.canvas.drawText("Despachado por: ${note.cashierName.ifBlank { "Cajero" }}", leftBox.left + 10f, leftBox.top + 41f, paint)
        paged.canvas.drawText(ellipsizeText("Motivo: ${note.reasonOrObservation}", paint, boxW - 20f), leftBox.left + 10f, leftBox.top + 54f, paint)

        paint.color = COLOR_INDIGO
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("CLIENTE / RECEPTOR", rightBox.left + 10f, rightBox.top + 14f, paint)
        paint.color = COLOR_TEXT_DARK
        paged.canvas.drawText(ellipsizeText(note.customerName.ifBlank { "Cliente General" }, paint, boxW - 20f), rightBox.left + 10f, rightBox.top + 28f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paged.canvas.drawText("CI/RIF: ${note.customerDocument.ifBlank { "V-00000000" }}", rightBox.left + 10f, rightBox.top + 41f, paint)
        if (note.recipientName.isNotBlank()) {
            paged.canvas.drawText(ellipsizeText("Recibe: ${note.recipientName}", paint, boxW - 20f), rightBox.left + 10f, rightBox.top + 54f, paint)
        }

        paged.y += metaH + 14f

        fun drawItemsHeader(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = COLOR_NAVY
            c.drawRoundRect(RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f), 4f, 4f, p)
            p.color = Color.WHITE
            p.textSize = 8.5f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            c.drawText("Cant.", LEFT_MARGIN + 22f, paged.y + 13f, p)
            p.textAlign = Paint.Align.LEFT
            c.drawText("Descripción de Mercancía", LEFT_MARGIN + 48f, paged.y + 13f, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText(if (useBs) "P. Venta (Bs.)" else "P. Venta ($)", LEFT_MARGIN + 425f, paged.y + 13f, p)
            c.drawText(if (useBs) "Total (Bs.)" else "Total ($)", RIGHT_MARGIN - 8f, paged.y + 13f, p)
            paged.y += 22f
        }

        drawItemsHeader(paged.canvas)

        val itemTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_DARK
            textSize = 8.5f
        }

        items.forEachIndexed { index, item ->
            val descLayout = createStaticLayout(item.productName.ifBlank { "Producto" }, itemTextPaint, 290)
            val rowH = maxOf(18f, descLayout.height + 8f)
            paged.ensureSpace(rowH + 6f) { drawItemsHeader(it) }

            if (index % 2 == 1) {
                paint.color = COLOR_ROW_ALT
                paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + rowH, paint)
            }

            val baseLine = paged.y + 12f
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paged.canvas.drawText(fmtQty(item.quantity), LEFT_MARGIN + 22f, baseLine, paint)

            paged.canvas.save()
            paged.canvas.translate(LEFT_MARGIN + 48f, paged.y + 4f)
            descLayout.draw(paged.canvas)
            paged.canvas.restore()

            val lineUsd = item.priceUsd * item.quantity
            val lineBs = lineUsd * rate
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (useBs) {
                paged.canvas.drawText(fmtBs(item.priceUsd * rate), LEFT_MARGIN + 425f, baseLine, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText(fmtBs(lineBs), RIGHT_MARGIN - 8f, baseLine, paint)
            } else {
                paged.canvas.drawText(fmtUsd(item.priceUsd), LEFT_MARGIN + 425f, baseLine, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText(fmtUsd(lineUsd), RIGHT_MARGIN - 8f, baseLine, paint)
            }

            paged.y += rowH
            paint.color = COLOR_HEADER_BG
            paint.strokeWidth = 0.5f
            paged.canvas.drawLine(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y, paint)
        }

        paged.y += 14f
        paged.ensureSpace(125f)

        val totalRect = RectF(RIGHT_MARGIN - 260f, paged.y, RIGHT_MARGIN, paged.y + 42f)
        paint.color = COLOR_NAVY
        paged.canvas.drawRoundRect(totalRect, 6f, 6f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("TOTAL NOTA DE ENTREGA", totalRect.left + 10f, totalRect.top + 17f, paint)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#CBD5E1")
        paged.canvas.drawText(
            if (useBs) "Tasa BCV: Bs. ${String.format(Locale.US, "%.2f", rate)}" else "No válido para crédito fiscal",
            totalRect.left + 10f,
            totalRect.top + 32f,
            paint
        )

        val estBs = if (note.totalEstimatedBs > 0.001) note.totalEstimatedBs else note.totalEstimatedUsd * rate
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (useBs) {
            paged.canvas.drawText(fmtBs(estBs), RIGHT_MARGIN - 10f, totalRect.top + 25f, paint)
        } else {
            paged.canvas.drawText("${fmtUsd(note.totalEstimatedUsd)} USD", RIGHT_MARGIN - 10f, totalRect.top + 25f, paint)
        }

        paged.y = totalRect.bottom + 24f

        // Signature Box
        val sigBox = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 62f)
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        val sig1X = LEFT_MARGIN + 115f
        val sig2X = RIGHT_MARGIN - 115f
        val sigLineY = sigBox.top + 38f
        paint.color = COLOR_TEXT_MUTED
        paint.strokeWidth = 0.9f
        paged.canvas.drawLine(sig1X - 75f, sigLineY, sig1X + 75f, sigLineY, paint)
        paged.canvas.drawLine(sig2X - 75f, sigLineY, sig2X + 75f, sigLineY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8.5f
        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("Entregado Por", sig1X, sigLineY + 12f, paint)
        paged.canvas.drawText("Recibido Conforme (Firma / C.I.)", sig2X, sigLineY + 12f, paint)

        val safeCode = noteCode.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val outFile = File(context.cacheDir, "Nota_Entrega_$safeCode.pdf")
        return paged.finishAndSave(outFile)
    }

    // ==================== 4. REPORTE DE VENTAS Y CONTABILIDAD PDF ====================

    fun generateSalesReportPdf(
        context: Context,
        prefs: PreferencesManager,
        periodLabel: String,
        generatedBy: String,
        effectiveRate: Double,
        sales: List<Sale>,
        saleItems: List<SaleItem>,
        expenses: List<Expense> = emptyList(),
        casheaSettlements: List<CasheaSettlement> = emptyList(),
        purchaseInvoices: List<PurchaseInvoice> = emptyList(),
        suppliers: List<Supplier> = emptyList(),
        products: List<Product> = emptyList(),
        includeAccountingSections: Boolean = true
    ): File? {
        val rate = if (effectiveRate > 0.001) effectiveRate else 1.0
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfFile = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val storeLabel = prefs.storeName.ifBlank { "Mi Tienda" }

        val paged = PagedPdfCanvas(
            storeName = storeLabel,
            runningHeaderTitle = "Reporte de Ventas ($periodLabel)",
            footerLeftLabel = "Control Contable y de Ventas • $storeLabel • No válido como factura fiscal"
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paged.y = drawStoreHeaderBanner(
            canvas = paged.canvas,
            prefs = prefs,
            documentTitle = "REPORTE DE VENTAS Y REGISTRO CONTABLE",
            documentRightPrimary = "${sales.size} FACTURA(S)",
            documentRightSecondary = "Emitido: ${sdfDateTime.format(Date())}"
        )

        // Period + Rate + Operator strip
        paint.color = COLOR_ROW_ALT
        val infoRect = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 32f)
        paged.canvas.drawRoundRect(infoRect, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        paged.canvas.drawRoundRect(infoRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        val colW3 = CONTENT_WIDTH / 3f
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 7.5f
        paint.color = COLOR_TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paged.canvas.drawText("PERÍODO CONSULTADO:", LEFT_MARGIN + 10f, paged.y + 12f, paint)
        paged.canvas.drawText("GENERADO POR:", LEFT_MARGIN + colW3 + 10f, paged.y + 12f, paint)
        paged.canvas.drawText("TASA BCV REFERENCIAL:", LEFT_MARGIN + (colW3 * 2) + 10f, paged.y + 12f, paint)

        paint.textSize = 8.5f
        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText(ellipsizeText(periodLabel, paint, colW3 - 18f), LEFT_MARGIN + 10f, paged.y + 24f, paint)
        paged.canvas.drawText(ellipsizeText(generatedBy.ifBlank { "Operador" }, paint, colW3 - 18f), LEFT_MARGIN + colW3 + 10f, paged.y + 24f, paint)
        paged.canvas.drawText("Bs. ${String.format(Locale.US, "%.2f", rate)}", LEFT_MARGIN + (colW3 * 2) + 10f, paged.y + 24f, paint)
        paged.y += 42f

        // Financial Calculations
        val grossSalesUsd = sales.sumOf { it.totalUsd }
        val grossSalesBs = sales.sumOf { if (it.totalBs > 0.001) it.totalBs else it.totalUsd * rate }
        val casheaCommissionUsd = casheaSettlements.sumOf { it.commissionAmountUsd }
        val realIncomeUsd = grossSalesUsd - casheaCommissionUsd
        val realIncomeBs = realIncomeUsd * rate
        val expensesUsd = expenses.sumOf { it.amountUsd }
        val expensesBs = expenses.sumOf { if (it.amountBs > 0.001) it.amountBs else it.amountUsd * rate }
        val netBalanceUsd = realIncomeUsd - expensesUsd
        val netBalanceBs = netBalanceUsd * rate

        val productMap = products.associateBy { it.id }
        val estimatedCogsUsd = if (products.isNotEmpty()) {
            saleItems.sumOf { item -> (productMap[item.productId]?.costPriceUsd ?: 0.0) * item.quantity }
        } else 0.0
        val estimatedGrossProfitUsd = realIncomeUsd - estimatedCogsUsd

        // --- SECTION 1: RESUMEN EJECUTIVO ---
        fun drawSectionTitle(title: String) {
            paint.color = COLOR_NAVY
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(title, LEFT_MARGIN, paged.y, paint)
            paged.y += 6f
            paint.color = COLOR_BORDER
            paint.strokeWidth = 0.8f
            paged.canvas.drawLine(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y, paint)
            paged.y += 8f
        }

        drawSectionTitle("1. RESUMEN EJECUTIVO FINANCIERO")

        val kpiGap = 8f
        val kpiCount = if (includeAccountingSections) 4 else 3
        val kpiW = (CONTENT_WIDTH - kpiGap * (kpiCount - 1)) / kpiCount
        val kpiH = 48f

        fun drawKpiBox(x: Float, title: String, primary: String, secondary: String, fgColor: Int, bgColor: Int) {
            val rect = RectF(x, paged.y, x + kpiW, paged.y + kpiH)
            paint.style = Paint.Style.FILL
            paint.color = bgColor
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = fgColor
            paint.strokeWidth = 0.8f
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.textAlign = Paint.Align.LEFT
            paint.color = fgColor
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText(ellipsizeText(title, paint, kpiW - 12f), x + 7f, paged.y + 13f, paint)

            paint.textSize = 11f
            paged.canvas.drawText(ellipsizeText(primary, paint, kpiW - 12f), x + 7f, paged.y + 28f, paint)

            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(ellipsizeText(secondary, paint, kpiW - 12f), x + 7f, paged.y + 40f, paint)
        }

        drawKpiBox(
            LEFT_MARGIN,
            "VENTAS BRUTAS",
            "${fmtUsd(grossSalesUsd)} USD",
            "${fmtBs(grossSalesBs)} (${sales.size} fac.)",
            COLOR_EMERALD,
            COLOR_EMERALD_BG
        )
        drawKpiBox(
            LEFT_MARGIN + (kpiW + kpiGap),
            "INGRESOS REALES",
            "${fmtUsd(realIncomeUsd)} USD",
            fmtBs(realIncomeBs),
            COLOR_INDIGO,
            Color.parseColor("#EFF6FF")
        )
        if (includeAccountingSections) {
            drawKpiBox(
                LEFT_MARGIN + (kpiW + kpiGap) * 2,
                "GASTOS OPERATIVOS",
                "-${fmtUsd(expensesUsd)} USD",
                "-${fmtBs(expensesBs)} (${expenses.size} reg.)",
                COLOR_RED,
                COLOR_RED_BG
            )
            drawKpiBox(
                LEFT_MARGIN + (kpiW + kpiGap) * 3,
                "BALANCE NETO",
                "${fmtUsd(netBalanceUsd)} USD",
                fmtBs(netBalanceBs),
                if (netBalanceUsd >= 0) COLOR_EMERALD else COLOR_RED,
                if (netBalanceUsd >= 0) COLOR_EMERALD_BG else COLOR_RED_BG
            )
        } else {
            val marginPct = if (realIncomeUsd > 0.001) (estimatedGrossProfitUsd / realIncomeUsd) * 100.0 else 0.0
            drawKpiBox(
                LEFT_MARGIN + (kpiW + kpiGap) * 2,
                "UTILIDAD BRUTA EST.",
                "${fmtUsd(estimatedGrossProfitUsd)} USD",
                "Margen: ${String.format(Locale.US, "%.1f%%", marginPct)}",
                COLOR_AMBER,
                COLOR_AMBER_BG
            )
        }

        paged.y += kpiH + 10f

        if (casheaCommissionUsd > 0.001) {
            paint.color = COLOR_AMBER_BG
            paged.canvas.drawRoundRect(RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f), 5f, 5f, paint)
            paint.color = COLOR_AMBER
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(
                "Ingresos no recibidos (Comisiones Cashea en ${casheaSettlements.size} liquidación(es)): -${fmtUsd(casheaCommissionUsd)} USD (-${fmtBs(casheaCommissionUsd * rate)})",
                LEFT_MARGIN + 10f,
                paged.y + 13f,
                paint
            )
            paged.y += 26f
        }

        // --- SECTION 2: MÉTODOS DE PAGO ---
        paged.ensureSpace(135f)
        drawSectionTitle("2. DESGLOSE DE INGRESOS POR MÉTODO DE PAGO")

        paint.color = Color.parseColor("#EDE9FE")
        paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 18f, paint)
        paint.color = Color.parseColor("#3730A3")
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paged.canvas.drawText("Método de Pago", LEFT_MARGIN + 8f, paged.y + 12f, paint)
        paged.canvas.drawText("Operaciones", LEFT_MARGIN + 205f, paged.y + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paged.canvas.drawText("Total ($ USD)", RIGHT_MARGIN - 125f, paged.y + 12f, paint)
        paged.canvas.drawText("Total (Bs.)", RIGHT_MARGIN - 8f, paged.y + 12f, paint)
        paged.y += 18f

        val totalCashUsd = sales.sumOf { it.paymentCashUsd }
        val totalMobileBs = sales.sumOf { it.paymentMobileBs }
        val totalCardBs = sales.sumOf { it.paymentCardBs }
        val totalCasheaUsd = sales.sumOf { it.paymentCasheaUsd }
        val totalCreditUsd = sales.sumOf { it.paymentCreditUsd }

        data class PaymentSummaryRow(val name: String, val count: Int, val usd: Double, val bs: Double)
        val paymentRows = listOf(
            PaymentSummaryRow("Efectivo Divisas ($ USD)", sales.count { it.paymentCashUsd > 0.001 }, totalCashUsd, totalCashUsd * rate),
            PaymentSummaryRow("Pago Móvil (Bs.)", sales.count { it.paymentMobileBs > 0.001 }, totalMobileBs / rate, totalMobileBs),
            PaymentSummaryRow("Punto de Venta / Débito (Bs.)", sales.count { it.paymentCardBs > 0.001 }, totalCardBs / rate, totalCardBs),
            PaymentSummaryRow("Cashea ($ USD)", sales.count { it.paymentCasheaUsd > 0.001 }, totalCasheaUsd, totalCasheaUsd * rate),
            PaymentSummaryRow("Ventas a Crédito ($ USD)", sales.count { it.paymentCreditUsd > 0.001 }, totalCreditUsd, totalCreditUsd * rate)
        )

        paymentRows.forEachIndexed { idx, row ->
            if (idx % 2 == 1) {
                paint.color = COLOR_ROW_ALT
                paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 16f, paint)
            }
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(row.name, LEFT_MARGIN + 8f, paged.y + 11f, paint)
            paged.canvas.drawText("${row.count} fac.", LEFT_MARGIN + 205f, paged.y + 11f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paged.canvas.drawText(fmtUsd(row.usd), RIGHT_MARGIN - 125f, paged.y + 11f, paint)
            paged.canvas.drawText(fmtBs(row.bs), RIGHT_MARGIN - 8f, paged.y + 11f, paint)
            paged.y += 16f
        }

        paint.color = COLOR_NAVY
        paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paged.canvas.drawText("TOTAL FACTURADO EN EL PERÍODO", LEFT_MARGIN + 8f, paged.y + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paged.canvas.drawText("${fmtUsd(grossSalesUsd)} USD", RIGHT_MARGIN - 125f, paged.y + 12f, paint)
        paged.canvas.drawText(fmtBs(grossSalesBs), RIGHT_MARGIN - 8f, paged.y + 12f, paint)
        paged.y += 26f

        // --- SECTION 3: DESGLOSE FISCAL (SENIAT) ---
        paged.ensureSpace(140f)
        drawSectionTitle("3. RESUMEN FISCAL Y TRIBUTARIO (SENIAT)")

        val totalBaseTaxUsd = sales.sumOf { it.baseTaxableUsd }
        val totalBaseExemptUsd = sales.sumOf { it.baseExemptUsd }
        val totalDiscountUsd = sales.sumOf { it.discountUsd }
        val totalIvaUsd = sales.sumOf { it.ivaUsd }
        val totalIgtfUsd = sales.sumOf { it.igtfUsd }
        val totalIgtfBs = sales.sumOf { if (it.igtfBs > 0.001) it.igtfBs else it.igtfUsd * rate }
        val totalRetIvaClientUsd = sales.sumOf { it.retencionIvaUsd }
        val totalRetIslrClientUsd = sales.sumOf { it.retencionIslrUsd }

        paint.color = Color.parseColor("#FEF3C7")
        paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 18f, paint)
        paint.color = Color.parseColor("#92400E")
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paged.canvas.drawText("Concepto Tributario", LEFT_MARGIN + 8f, paged.y + 12f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paged.canvas.drawText("Monto ($ USD)", RIGHT_MARGIN - 125f, paged.y + 12f, paint)
        paged.canvas.drawText("Monto (Bs.)", RIGHT_MARGIN - 8f, paged.y + 12f, paint)
        paged.y += 18f

        val taxLines = mutableListOf(
            Triple("Base Imponible Gravable (IVA 16%)", totalBaseTaxUsd, totalBaseTaxUsd * rate),
            Triple("Ventas Exentas de Impuesto (IVA 0%)", totalBaseExemptUsd, totalBaseExemptUsd * rate)
        )
        if (totalDiscountUsd > 0.001) {
            taxLines.add(Triple("Descuentos Otorgados (pre-IVA)", -totalDiscountUsd, -totalDiscountUsd * rate))
        }
        taxLines.add(Triple("IVA Débito Fiscal Recaudado (16%)", totalIvaUsd, totalIvaUsd * rate))
        if (totalIgtfUsd > 0.001 || totalIgtfBs > 0.001) {
            taxLines.add(Triple("IGTF Percibido Divisas (3%)", totalIgtfUsd, totalIgtfBs))
        }
        if (totalRetIvaClientUsd > 0.001) {
            taxLines.add(Triple("Retenciones IVA de Clientes", totalRetIvaClientUsd, totalRetIvaClientUsd * rate))
        }
        if (totalRetIslrClientUsd > 0.001) {
            taxLines.add(Triple("Retenciones ISLR de Clientes", totalRetIslrClientUsd, totalRetIslrClientUsd * rate))
        }
        if (includeAccountingSections && purchaseInvoices.isNotEmpty()) {
            val purchasesTotalUsd = purchaseInvoices.sumOf { it.totalAmount }
            val purchasesIvaUsd = purchaseInvoices.sumOf { it.taxAmountUsd }
            taxLines.add(Triple("Compras a Proveedores (${purchaseInvoices.size} fac.)", purchasesTotalUsd, purchasesTotalUsd * rate))
            taxLines.add(Triple("IVA Crédito Fiscal Pagado a Proveedores", purchasesIvaUsd, purchasesIvaUsd * rate))
        }

        taxLines.forEachIndexed { idx, row ->
            paged.ensureSpace(18f)
            if (idx % 2 == 1) {
                paint.color = COLOR_ROW_ALT
                paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 16f, paint)
            }
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(row.first, LEFT_MARGIN + 8f, paged.y + 11f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paged.canvas.drawText(fmtUsd(row.second), RIGHT_MARGIN - 125f, paged.y + 11f, paint)
            paged.canvas.drawText(fmtBs(row.third), RIGHT_MARGIN - 8f, paged.y + 11f, paint)
            paged.y += 16f
        }
        paged.y += 12f

        // --- SECTION 4: PRODUCTOS VENDIDOS ---
        data class GroupedProd(val name: String, val exempt: Boolean, val qty: Double, val totalUsd: Double)
        val groupedProducts = saleItems.groupBy { it.productName }.map { (name, pList) ->
            GroupedProd(
                name = name,
                exempt = pList.any { it.isTaxExempt },
                qty = pList.sumOf { it.quantity },
                totalUsd = pList.sumOf { it.priceUsd * it.quantity }
            )
        }.sortedByDescending { it.qty }

        if (groupedProducts.isNotEmpty()) {
            paged.ensureSpace(75f)
            drawSectionTitle("4. CONSOLIDADO DE PRODUCTOS VENDIDOS (${groupedProducts.size} ítems)")

            fun drawProdHeader(c: Canvas) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = Color.parseColor("#E0F2FE")
                c.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 18f, p)
                p.color = Color.parseColor("#0369A1")
                p.textSize = 8f
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textAlign = Paint.Align.LEFT
                c.drawText("#  Producto", LEFT_MARGIN + 8f, paged.y + 12f, p)
                c.drawText("Condición", LEFT_MARGIN + 260f, paged.y + 12f, p)
                p.textAlign = Paint.Align.CENTER
                c.drawText("Cant.", LEFT_MARGIN + 340f, paged.y + 12f, p)
                p.textAlign = Paint.Align.RIGHT
                c.drawText("Total ($ USD)", RIGHT_MARGIN - 98f, paged.y + 12f, p)
                c.drawText("Total (Bs.)", RIGHT_MARGIN - 8f, paged.y + 12f, p)
                paged.y += 18f
            }

            drawProdHeader(paged.canvas)

            groupedProducts.take(40).forEachIndexed { idx, prod ->
                paged.ensureSpace(18f) { drawProdHeader(it) }
                if (idx % 2 == 1) {
                    paint.color = COLOR_ROW_ALT
                    paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 16f, paint)
                }
                paint.color = COLOR_TEXT_DARK
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                val label = "${idx + 1}. ${prod.name}"
                paged.canvas.drawText(ellipsizeText(label, paint, 240f), LEFT_MARGIN + 8f, paged.y + 11f, paint)

                paint.color = if (prod.exempt) COLOR_EMERALD else COLOR_TEXT_MUTED
                paint.typeface = Typeface.create(Typeface.DEFAULT, if (prod.exempt) Typeface.BOLD else Typeface.NORMAL)
                paged.canvas.drawText(if (prod.exempt) "Exento" else "IVA 16%", LEFT_MARGIN + 260f, paged.y + 11f, paint)

                paint.color = COLOR_TEXT_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                paged.canvas.drawText(fmtQty(prod.qty), LEFT_MARGIN + 340f, paged.y + 11f, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paged.canvas.drawText(fmtUsd(prod.totalUsd), RIGHT_MARGIN - 98f, paged.y + 11f, paint)
                paged.canvas.drawText(fmtBs(prod.totalUsd * rate), RIGHT_MARGIN - 8f, paged.y + 11f, paint)
                paged.y += 16f
            }
            paged.y += 12f
        }

        // --- SECTION 5: RELACIÓN DETALLADA DE FACTURAS ---
        paged.ensureSpace(75f)
        drawSectionTitle("5. RELACIÓN DETALLADA DE FACTURAS EMITIDAS (${sales.size})")

        fun drawSalesTableHeader(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.parseColor("#DCFCE7")
            c.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 18f, p)
            p.color = Color.parseColor("#166534")
            p.textSize = 8f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.LEFT
            c.drawText("Fac.", LEFT_MARGIN + 6f, paged.y + 12f, p)
            c.drawText("Fecha/Hora", LEFT_MARGIN + 42f, paged.y + 12f, p)
            c.drawText("Cliente (CI/RIF)", LEFT_MARGIN + 118f, paged.y + 12f, p)
            c.drawText("Pago", LEFT_MARGIN + 286f, paged.y + 12f, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText("IVA ($)", RIGHT_MARGIN - 148f, paged.y + 12f, p)
            c.drawText("Total ($)", RIGHT_MARGIN - 84f, paged.y + 12f, p)
            c.drawText("Total (Bs.)", RIGHT_MARGIN - 6f, paged.y + 12f, p)
            paged.y += 18f
        }

        drawSalesTableHeader(paged.canvas)

        if (sales.isEmpty()) {
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText("No se registraron facturas en este período.", LEFT_MARGIN + 8f, paged.y + 13f, paint)
            paged.y += 22f
        } else {
            val saleDateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            sales.forEachIndexed { idx, sale ->
                paged.ensureSpace(18f) { drawSalesTableHeader(it) }
                if (idx % 2 == 1) {
                    paint.color = COLOR_ROW_ALT
                    paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 16f, paint)
                }

                paint.color = COLOR_TEXT_DARK
                paint.textSize = 7.8f
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText("#${sale.id}", LEFT_MARGIN + 6f, paged.y + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paged.canvas.drawText(saleDateFmt.format(Date(sale.timestamp)), LEFT_MARGIN + 42f, paged.y + 11f, paint)

                val clientLabel = "${sale.customerName.ifBlank { "Consumidor Final" }} (${sale.customerDocument.ifBlank { "S/D" }})"
                paged.canvas.drawText(ellipsizeText(clientLabel, paint, 162f), LEFT_MARGIN + 118f, paged.y + 11f, paint)

                val methods = listOfNotNull(
                    if (sale.paymentCashUsd > 0.001) "Efec.$" else null,
                    if (sale.paymentMobileBs > 0.001) "P.Móvil" else null,
                    if (sale.paymentCardBs > 0.001) "Punto" else null,
                    if (sale.paymentCasheaUsd > 0.001) "Cashea" else null,
                    if (sale.paymentCreditUsd > 0.001) "Crédito" else null
                )
                val payStr = when {
                    methods.size > 1 -> "Mixto"
                    methods.size == 1 -> methods.first()
                    else -> "Contado"
                }
                paged.canvas.drawText(payStr, LEFT_MARGIN + 286f, paged.y + 11f, paint)

                val saleBs = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * (if (sale.rateBcv > 0.001) sale.rateBcv else rate)
                paint.textAlign = Paint.Align.RIGHT
                paged.canvas.drawText(fmtUsd(sale.ivaUsd), RIGHT_MARGIN - 148f, paged.y + 11f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText(fmtUsd(sale.totalUsd), RIGHT_MARGIN - 84f, paged.y + 11f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paged.canvas.drawText(fmtBs(saleBs), RIGHT_MARGIN - 6f, paged.y + 11f, paint)

                paged.y += 16f
            }

            // Summary Row
            paged.ensureSpace(22f)
            paint.color = COLOR_NAVY
            paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText("TOTALES (${sales.size} FACTURAS)", LEFT_MARGIN + 6f, paged.y + 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paged.canvas.drawText(fmtUsd(totalIvaUsd), RIGHT_MARGIN - 148f, paged.y + 12f, paint)
            paged.canvas.drawText(fmtUsd(grossSalesUsd), RIGHT_MARGIN - 84f, paged.y + 12f, paint)
            paged.canvas.drawText(fmtBs(grossSalesBs), RIGHT_MARGIN - 6f, paged.y + 12f, paint)
            paged.y += 26f
        }

        // --- SECTION 6: GASTOS Y LIQUIDACIONES CASHEA (if accounting sections enabled) ---
        if (includeAccountingSections && (expenses.isNotEmpty() || casheaSettlements.isNotEmpty())) {
            paged.ensureSpace(70f)
            drawSectionTitle("6. EGRESOS OPERATIVOS Y COMISIONES CASHEA (${expenses.size + casheaSettlements.size})")

            val expDateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            casheaSettlements.forEach { cs ->
                paged.ensureSpace(18f)
                paint.color = COLOR_AMBER
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                paged.canvas.drawText(
                    "[Cashea] ${expDateFmt.format(Date(cs.settlementDate))} • Liquidado: ${fmtUsd(cs.grossAmountUsd)} (${String.format(Locale.US, "%.1f%%", cs.commissionPercentage)} com.)",
                    LEFT_MARGIN + 6f,
                    paged.y + 11f,
                    paint
                )
                paint.textAlign = Paint.Align.RIGHT
                paged.canvas.drawText("-${fmtUsd(cs.commissionAmountUsd)}", RIGHT_MARGIN - 6f, paged.y + 11f, paint)
                paged.y += 16f
            }

            expenses.forEach { exp ->
                paged.ensureSpace(18f)
                paint.color = COLOR_RED
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                val desc = "[${exp.category}] ${expDateFmt.format(Date(exp.timestamp))} • ${exp.description.ifBlank { exp.paymentMethod }}"
                paged.canvas.drawText(ellipsizeText(desc, paint, 415f), LEFT_MARGIN + 6f, paged.y + 11f, paint)
                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText("-${fmtUsd(exp.amountUsd)}", RIGHT_MARGIN - 6f, paged.y + 11f, paint)
                paged.y += 16f
            }
            paged.y += 12f
        }

        // --- SECTION 7: FIRMAS CONTABLES ---
        paged.ensureSpace(75f)
        val sigBox = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 62f)
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        val sig1X = LEFT_MARGIN + 115f
        val sig2X = RIGHT_MARGIN - 115f
        val sigLineY = sigBox.top + 38f
        paint.color = COLOR_TEXT_MUTED
        paint.strokeWidth = 0.9f
        paged.canvas.drawLine(sig1X - 75f, sigLineY, sig1X + 75f, sigLineY, paint)
        paged.canvas.drawLine(sig2X - 75f, sigLineY, sig2X + 75f, sigLineY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8.5f
        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paged.canvas.drawText("Elaborado Por ($generatedBy)", sig1X, sigLineY + 12f, paint)
        paged.canvas.drawText("Revisado / Contabilidad", sig2X, sigLineY + 12f, paint)

        val safePeriod = periodLabel.replace(Regex("[^A-Za-z0-9_-]"), "_").take(28)
        val outFile = File(context.cacheDir, "Reporte_Ventas_${safePeriod}_${sdfFile.format(Date())}.pdf")
        return paged.finishAndSave(outFile)
    }

    // ==================== 5. REPORTE DE INVENTARIO (PDF) ====================

    fun generateInventoryPdf(
        context: Context,
        prefs: PreferencesManager,
        products: List<Product>,
        allProducts: List<Product> = products,
        effectiveRate: Double,
        filterLabel: String = "Todo el inventario",
        generatedBy: String = ""
    ): File? {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val rate = if (effectiveRate > 0.001) effectiveRate else 1.0
        val storeLabel = prefs.storeName.ifBlank { "Mi Tienda" }
        val nowStr = sdfDateTime.format(Date())

        val paged = PagedPdfCanvas(
            storeName = storeLabel,
            runningHeaderTitle = "Inventario ($nowStr)",
            footerLeftLabel = "$storeLabel • Reporte de Inventario • Tasa BCV: ${String.format(Locale.US, "%.2f", rate)} Bs/$"
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paged.y = drawStoreHeaderBanner(
            canvas = paged.canvas,
            prefs = prefs,
            documentTitle = "REPORTE GENERAL DE INVENTARIO Y VALORACIÓN",
            documentRightPrimary = "${products.size} ÍTEMS",
            documentRightSecondary = nowStr
        )

        // Info strip
        val infoBox = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 24f)
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(infoBox, 5f, 5f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.7f
        paged.canvas.drawRoundRect(infoBox, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.LEFT
        paint.color = COLOR_TEXT_DARK
        paint.textSize = 8.2f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val userPart = if (generatedBy.isNotBlank()) " • Usuario: $generatedBy" else ""
        paged.canvas.drawText(
            ellipsizeText("Filtro: $filterLabel$userPart", paint, 355f),
            LEFT_MARGIN + 8f,
            paged.y + 15f,
            paint
        )
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = COLOR_INDIGO
        paged.canvas.drawText(
            "Tasa BCV: ${String.format(Locale.US, "%.2f", rate)} Bs./$",
            RIGHT_MARGIN - 8f,
            paged.y + 15f,
            paint
        )
        paged.y += 34f

        // Inventory valuation metrics (physical items hold actual stock to avoid double-counting combos)
        val physicalProducts = products.filter { !it.isRecipe && !it.isCombo }
        val totalUnits = physicalProducts.sumOf { it.stock.coerceAtLeast(0.0) }
        val totalCostUsd = physicalProducts.sumOf { it.costPriceUsd.coerceAtLeast(0.0) * it.stock.coerceAtLeast(0.0) }
        val totalRetailUsd = physicalProducts.sumOf { it.priceUsd.coerceAtLeast(0.0) * it.stock.coerceAtLeast(0.0) }
        val outOfStockCount = physicalProducts.count { it.stock <= 0.001 }
        val lowStockCount = physicalProducts.count {
            it.isLowStockAlertEnabled && it.stock > 0.001 && it.stock <= (it.minStockAlertQuantity + 1e-6)
        }

        // Section 1: Summary KPIs
        fun drawInvSectionTitle(title: String) {
            paint.color = COLOR_NAVY
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(title, LEFT_MARGIN, paged.y, paint)
            paged.y += 6f
            paint.color = COLOR_BORDER
            paint.strokeWidth = 0.8f
            paged.canvas.drawLine(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y, paint)
            paged.y += 8f
        }

        drawInvSectionTitle("1. RESUMEN DE EXISTENCIAS Y VALORACIÓN")

        val kpiGap = 8f
        val kpiW = (CONTENT_WIDTH - kpiGap * 3) / 4f
        val kpiH = 48f

        fun drawInvKpiBox(x: Float, title: String, primary: String, secondary: String, fgColor: Int, bgColor: Int) {
            val rect = RectF(x, paged.y, x + kpiW, paged.y + kpiH)
            paint.style = Paint.Style.FILL
            paint.color = bgColor
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = fgColor
            paint.strokeWidth = 0.8f
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.textAlign = Paint.Align.LEFT
            paint.color = fgColor
            paint.textSize = 7.3f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText(ellipsizeText(title, paint, kpiW - 12f), x + 7f, paged.y + 13f, paint)

            paint.textSize = 10.5f
            paged.canvas.drawText(ellipsizeText(primary, paint, kpiW - 12f), x + 7f, paged.y + 28f, paint)

            paint.textSize = 7.3f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(ellipsizeText(secondary, paint, kpiW - 12f), x + 7f, paged.y + 40f, paint)
        }

        drawInvKpiBox(
            x = LEFT_MARGIN,
            title = "CATÁLOGO TOTAL",
            primary = "${products.size} ítems",
            secondary = "$outOfStockCount agot. • $lowStockCount bajo",
            fgColor = COLOR_INDIGO,
            bgColor = Color.parseColor("#EFF6FF")
        )
        drawInvKpiBox(
            x = LEFT_MARGIN + (kpiW + kpiGap),
            title = "UNIDADES EN STOCK",
            primary = "${fmtQty(totalUnits)} u.",
            secondary = "${physicalProducts.size} prod. físicos",
            fgColor = if (outOfStockCount > 0) COLOR_AMBER else COLOR_EMERALD,
            bgColor = if (outOfStockCount > 0) COLOR_AMBER_BG else COLOR_EMERALD_BG
        )
        drawInvKpiBox(
            x = LEFT_MARGIN + (kpiW + kpiGap) * 2,
            title = "COSTO INVENTARIO",
            primary = "${fmtUsd(totalCostUsd)} USD",
            secondary = fmtBs(totalCostUsd * rate),
            fgColor = Color.parseColor("#475569"),
            bgColor = Color.parseColor("#F1F5F9")
        )
        drawInvKpiBox(
            x = LEFT_MARGIN + (kpiW + kpiGap) * 3,
            title = "VALOR DE VENTA",
            primary = "${fmtUsd(totalRetailUsd)} USD",
            secondary = fmtBs(totalRetailUsd * rate),
            fgColor = COLOR_EMERALD,
            bgColor = COLOR_EMERALD_BG
        )

        paged.y += kpiH + 14f

        // Section 2: Detailed Inventory Table
        drawInvSectionTitle("2. DETALLE DE PRODUCTOS EN INVENTARIO (${products.size})")

        fun drawInventoryTableHeader(c: Canvas) {
            val hPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            hPaint.color = COLOR_HEADER_BG
            c.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 19f, hPaint)
            hPaint.color = COLOR_TEXT_DARK
            hPaint.textSize = 8f
            hPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            hPaint.textAlign = Paint.Align.LEFT
            c.drawText("Código", LEFT_MARGIN + 6f, paged.y + 13f, hPaint)
            c.drawText("Producto / Categoría", LEFT_MARGIN + 76f, paged.y + 13f, hPaint)
            hPaint.textAlign = Paint.Align.RIGHT
            c.drawText("Stock", RIGHT_MARGIN - 196f, paged.y + 13f, hPaint)
            c.drawText("Costo ($)", RIGHT_MARGIN - 134f, paged.y + 13f, hPaint)
            c.drawText("Venta ($)", RIGHT_MARGIN - 72f, paged.y + 13f, hPaint)
            c.drawText("Venta (Bs.)", RIGHT_MARGIN - 6f, paged.y + 13f, hPaint)
            paged.y += 19f
        }

        drawInventoryTableHeader(paged.canvas)

        if (products.isEmpty()) {
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText("No hay productos registrados en el inventario.", LEFT_MARGIN + 8f, paged.y + 14f, paint)
            paged.y += 24f
        } else {
            val nameTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_DARK
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = 7.1f
            }

            products.forEachIndexed { idx, product ->
                val catLabel = product.category.ifBlank { "General" }
                val ivaTag = if (product.isTaxExempt) "Exento" else "IVA 16%"
                val typeTag = when {
                    product.isRecipe -> " • Receta"
                    product.isCombo -> " • Combo"
                    else -> ""
                }
                val aisleTag = if (product.aisleNumber.isNotBlank()) " • Pasillo ${product.aisleNumber}" else ""
                val subtitleStr = "$catLabel • $ivaTag$typeTag$aisleTag"

                val nameLayout = createStaticLayout(product.name.ifBlank { "Producto sin nombre" }, nameTextPaint, 200)
                val subLayout = createStaticLayout(subtitleStr, subTextPaint, 200)
                val rowHeight = maxOf(22f, nameLayout.height + subLayout.height + 8f)

                paged.ensureSpace(rowHeight + 4f) { drawInventoryTableHeader(it) }

                if (idx % 2 == 1) {
                    paint.color = COLOR_ROW_ALT
                    paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + rowHeight, paint)
                }

                // Code
                paint.color = COLOR_TEXT_MUTED
                paint.textSize = 7.6f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                val codeText = product.code.ifBlank { "S/C" }
                paged.canvas.drawText(ellipsizeText(codeText, paint, 64f), LEFT_MARGIN + 6f, paged.y + 12f, paint)

                // Product name + subtitle
                paged.canvas.save()
                paged.canvas.translate(LEFT_MARGIN + 76f, paged.y + 4f)
                nameLayout.draw(paged.canvas)
                paged.canvas.translate(0f, nameLayout.height.toFloat() + 1f)
                subLayout.draw(paged.canvas)
                paged.canvas.restore()

                // Stock (color-coded)
                val effectiveStock = if (product.isCombo) {
                    product.calculateAvailableComboStock(allProducts)
                } else {
                    product.stock
                }
                val stockText = when {
                    product.isRecipe -> "Receta"
                    product.isCombo -> "${fmtQty(effectiveStock)} (C)"
                    else -> fmtQty(effectiveStock)
                }
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = when {
                    product.isRecipe -> COLOR_AMBER
                    effectiveStock <= 0.001 -> COLOR_RED
                    effectiveStock <= 5.0 -> COLOR_AMBER
                    else -> COLOR_EMERALD
                }
                paged.canvas.drawText(stockText, RIGHT_MARGIN - 196f, paged.y + 13f, paint)

                // Cost USD
                val effectiveCost = if (product.isCombo) {
                    product.calculateCalculatedCostFromComponents(allProducts)
                } else {
                    product.costPriceUsd
                }
                paint.color = COLOR_TEXT_MUTED
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paged.canvas.drawText(
                    if (product.isRecipe) "—" else fmtUsd(effectiveCost),
                    RIGHT_MARGIN - 134f,
                    paged.y + 13f,
                    paint
                )

                // Price USD & Bs
                paint.color = COLOR_TEXT_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText(fmtUsd(product.priceUsd), RIGHT_MARGIN - 72f, paged.y + 13f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paged.canvas.drawText(fmtBs(product.priceUsd * rate), RIGHT_MARGIN - 6f, paged.y + 13f, paint)

                // Row separator
                paint.color = COLOR_BORDER
                paint.strokeWidth = 0.4f
                paged.canvas.drawLine(LEFT_MARGIN, paged.y + rowHeight, RIGHT_MARGIN, paged.y + rowHeight, paint)

                paged.y += rowHeight
            }

            // Summary totals bar
            paged.ensureSpace(26f)
            paint.color = COLOR_NAVY
            paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText("TOTALES (${products.size} ÍTEMS)", LEFT_MARGIN + 6f, paged.y + 13f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paged.canvas.drawText("${fmtQty(totalUnits)} u.", RIGHT_MARGIN - 196f, paged.y + 13f, paint)
            paged.canvas.drawText(fmtUsd(totalCostUsd), RIGHT_MARGIN - 134f, paged.y + 13f, paint)
            paged.canvas.drawText(fmtUsd(totalRetailUsd), RIGHT_MARGIN - 72f, paged.y + 13f, paint)
            paged.canvas.drawText(fmtBs(totalRetailUsd * rate), RIGHT_MARGIN - 6f, paged.y + 13f, paint)
            paged.y += 28f
        }

        // Signature / audit footer box
        paged.ensureSpace(68f)
        val sigBox = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 56f)
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        val sig1X = LEFT_MARGIN + 120f
        val sig2X = RIGHT_MARGIN - 120f
        val sigLineY = sigBox.top + 34f
        paint.color = COLOR_TEXT_MUTED
        paint.strokeWidth = 0.9f
        paged.canvas.drawLine(sig1X - 75f, sigLineY, sig1X + 75f, sigLineY, paint)
        paged.canvas.drawLine(sig2X - 75f, sigLineY, sig2X + 75f, sigLineY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8.2f
        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val authorLabel = if (generatedBy.isNotBlank()) "Generado por ($generatedBy)" else "Responsable de Inventario"
        paged.canvas.drawText(authorLabel, sig1X, sigLineY + 12f, paint)
        paged.canvas.drawText("Verificado / Administración", sig2X, sigLineY + 12f, paint)

        val outFile = File(context.cacheDir, "Inventario_${sdfFile.format(Date())}.pdf")
        return paged.finishAndSave(outFile)
    }

    // ==================== 6. REPORTE DE REGISTROS DE PUNTOS DE VENTA (PDF) ====================

    fun generatePosTerminalRecordsPdf(
        context: Context,
        prefs: PreferencesManager,
        records: List<PosTerminalRecord>,
        filterLabel: String = "Todos los registros",
        generatedBy: String = ""
    ): File? {
        val sdfDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val storeLabel = prefs.storeName.ifBlank { "Mi Tienda" }
        val nowStr = sdfDateTime.format(Date())

        val paged = PagedPdfCanvas(
            storeName = storeLabel,
            runningHeaderTitle = "Puntos de Venta ($nowStr)",
            footerLeftLabel = "$storeLabel • Registro de Puntos de Venta (${records.size} tickets)"
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paged.y = drawStoreHeaderBanner(
            canvas = paged.canvas,
            prefs = prefs,
            documentTitle = "REGISTRO DE TICKETS Y TRANSACCIONES DE PUNTOS DE VENTA",
            documentRightPrimary = "${records.size} TICKETS",
            documentRightSecondary = nowStr
        )

        // Info strip
        val infoBox = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 24f)
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(infoBox, 5f, 5f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.7f
        paged.canvas.drawRoundRect(infoBox, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.LEFT
        paint.color = COLOR_TEXT_DARK
        paint.textSize = 8.2f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val userPart = if (generatedBy.isNotBlank()) " • Usuario: $generatedBy" else ""
        paged.canvas.drawText(
            ellipsizeText("Filtro: $filterLabel$userPart", paint, 380f),
            LEFT_MARGIN + 8f,
            paged.y + 15f,
            paint
        )
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = COLOR_INDIGO
        paged.canvas.drawText(
            "Emitido: $nowStr",
            RIGHT_MARGIN - 8f,
            paged.y + 15f,
            paint
        )
        paged.y += 34f

        val approved = records.filter { it.transactionStatus.equals("APROBADA", ignoreCase = true) }
        val rejected = records.filter { it.transactionStatus.equals("RECHAZADA", ignoreCase = true) }
        val voided = records.filter { it.transactionStatus.equals("ANULADA", ignoreCase = true) }

        val approvedBs = approved.filter { !it.currency.equals("USD", ignoreCase = true) }.sumOf { it.totalAmount }
        val approvedUsd = approved.filter { it.currency.equals("USD", ignoreCase = true) }.sumOf { it.totalAmount }

        fun drawPosSecTitle(title: String) {
            paint.color = COLOR_NAVY
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText(title, LEFT_MARGIN, paged.y, paint)
            paged.y += 6f
            paint.color = COLOR_BORDER
            paint.strokeWidth = 0.8f
            paged.canvas.drawLine(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y, paint)
            paged.y += 8f
        }

        drawPosSecTitle("1. RESUMEN DE OPERACIONES DE PUNTO DE VENTA")

        val kpiGap = 8f
        val kpiW = (CONTENT_WIDTH - kpiGap * 3) / 4f
        val kpiH = 48f

        fun drawPosKpi(x: Float, title: String, primary: String, secondary: String, fgColor: Int, bgColor: Int) {
            val rect = RectF(x, paged.y, x + kpiW, paged.y + kpiH)
            paint.style = Paint.Style.FILL
            paint.color = bgColor
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = fgColor
            paint.strokeWidth = 0.8f
            paged.canvas.drawRoundRect(rect, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.textAlign = Paint.Align.LEFT
            paint.color = fgColor
            paint.textSize = 7.3f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paged.canvas.drawText(ellipsizeText(title, paint, kpiW - 12f), x + 7f, paged.y + 13f, paint)

            paint.textSize = 10.5f
            paged.canvas.drawText(ellipsizeText(primary, paint, kpiW - 12f), x + 7f, paged.y + 28f, paint)

            paint.textSize = 7.3f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paged.canvas.drawText(ellipsizeText(secondary, paint, kpiW - 12f), x + 7f, paged.y + 40f, paint)
        }

        drawPosKpi(
            x = LEFT_MARGIN,
            title = "TOTAL TICKETS",
            primary = "${records.size} reg.",
            secondary = "${approved.size} aprobadas",
            fgColor = COLOR_INDIGO,
            bgColor = Color.parseColor("#EFF6FF")
        )
        drawPosKpi(
            x = LEFT_MARGIN + (kpiW + kpiGap),
            title = "APROBADO EN BS.",
            primary = fmtBs(approvedBs),
            secondary = "${approved.count { !it.currency.equals("USD", true) }} vouchers Bs.",
            fgColor = COLOR_EMERALD,
            bgColor = COLOR_EMERALD_BG
        )
        drawPosKpi(
            x = LEFT_MARGIN + (kpiW + kpiGap) * 2,
            title = "APROBADO EN USD",
            primary = fmtUsd(approvedUsd),
            secondary = "${approved.count { it.currency.equals("USD", true) }} vouchers $",
            fgColor = Color.parseColor("#0F766E"),
            bgColor = Color.parseColor("#F0FDFA")
        )
        drawPosKpi(
            x = LEFT_MARGIN + (kpiW + kpiGap) * 3,
            title = "RECHAZ. / ANUL.",
            primary = "${rejected.size + voided.size}",
            secondary = "${rejected.size} rech. • ${voided.size} anul.",
            fgColor = if (rejected.isNotEmpty() || voided.isNotEmpty()) COLOR_RED else Color.parseColor("#475569"),
            bgColor = if (rejected.isNotEmpty() || voided.isNotEmpty()) COLOR_RED_BG else Color.parseColor("#F1F5F9")
        )

        paged.y += kpiH + 14f

        drawPosSecTitle("2. DETALLE DE TRANSACCIONES (${records.size})")

        fun drawPosTableHeader(c: Canvas) {
            val hPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            hPaint.color = COLOR_HEADER_BG
            c.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 19f, hPaint)
            hPaint.color = COLOR_TEXT_DARK
            hPaint.textSize = 7.8f
            hPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            hPaint.textAlign = Paint.Align.LEFT
            c.drawText("Fecha / Hora", LEFT_MARGIN + 6f, paged.y + 13f, hPaint)
            c.drawText("Recibo / Ref. / Aprob.", LEFT_MARGIN + 90f, paged.y + 13f, hPaint)
            c.drawText("Tarjeta / Terminal", LEFT_MARGIN + 206f, paged.y + 13f, hPaint)
            c.drawText("Operación", LEFT_MARGIN + 322f, paged.y + 13f, hPaint)
            c.drawText("Estado", LEFT_MARGIN + 398f, paged.y + 13f, hPaint)
            hPaint.textAlign = Paint.Align.RIGHT
            c.drawText("Monto Total", RIGHT_MARGIN - 6f, paged.y + 13f, hPaint)
            paged.y += 19f
        }

        drawPosTableHeader(paged.canvas)

        if (records.isEmpty()) {
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText("No hay registros de puntos de venta en este filtro.", LEFT_MARGIN + 8f, paged.y + 14f, paint)
            paged.y += 24f
        } else {
            val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_DARK
                textSize = 7.6f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = 7.0f
            }

            records.forEachIndexed { idx, rec ->
                val dateLabel = rec.transactionDateTime.ifBlank { sdfDateTime.format(Date(rec.transactionTimestamp)) }
                val codesStr = buildList {
                    if (rec.receiptNumber.isNotBlank()) add("Rec: ${rec.receiptNumber}")
                    if (rec.referenceNumber.isNotBlank()) add("Ref: ${rec.referenceNumber}")
                    if (rec.approvalNumber.isNotBlank()) add("Aprob: ${rec.approvalNumber}")
                }.joinToString("\n").ifBlank { "Sin códigos" }

                val cardLine = rec.maskedCardNumber.ifBlank { "Tarjeta S/N" }
                val bankLine = rec.bankOrTerminal.ifBlank { "POS Bancario" }

                val codesLayout = createStaticLayout(codesStr, boldBodyPaint, 110)
                val cardLayout = createStaticLayout(cardLine, boldBodyPaint, 110)
                val bankLayout = createStaticLayout(bankLine, mutedBodyPaint, 110)

                val rowHeight = maxOf(
                    24f,
                    codesLayout.height + 8f,
                    cardLayout.height + bankLayout.height + 9f
                )

                paged.ensureSpace(rowHeight + 4f) { drawPosTableHeader(it) }

                if (idx % 2 == 1) {
                    paint.color = COLOR_ROW_ALT
                    paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + rowHeight, paint)
                }

                // Fecha / Hora
                paint.color = COLOR_TEXT_DARK
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                paged.canvas.drawText(ellipsizeText(dateLabel, paint, 80f), LEFT_MARGIN + 6f, paged.y + 12f, paint)

                // Recibo / Ref / Aprob
                paged.canvas.save()
                paged.canvas.translate(LEFT_MARGIN + 90f, paged.y + 4f)
                codesLayout.draw(paged.canvas)
                paged.canvas.restore()

                // Tarjeta + Banco
                paged.canvas.save()
                paged.canvas.translate(LEFT_MARGIN + 206f, paged.y + 4f)
                cardLayout.draw(paged.canvas)
                paged.canvas.translate(0f, cardLayout.height.toFloat() + 1f)
                bankLayout.draw(paged.canvas)
                paged.canvas.restore()

                // Tipo de Operación
                paint.color = COLOR_TEXT_DARK
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.LEFT
                paged.canvas.drawText(ellipsizeText(rec.operationType.ifBlank { "COMPRA" }, paint, 70f), LEFT_MARGIN + 322f, paged.y + 12f, paint)

                // Estado
                val upperStatus = rec.transactionStatus.uppercase(Locale.ROOT)
                paint.color = when (upperStatus) {
                    "APROBADA" -> COLOR_EMERALD
                    "RECHAZADA" -> COLOR_RED
                    "ANULADA" -> COLOR_AMBER
                    else -> COLOR_TEXT_MUTED
                }
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paged.canvas.drawText(ellipsizeText(upperStatus, paint, 58f), LEFT_MARGIN + 398f, paged.y + 12f, paint)

                // Monto
                val amountStr = if (rec.currency.equals("USD", ignoreCase = true)) {
                    "${fmtUsd(rec.totalAmount)} USD"
                } else {
                    fmtBs(rec.totalAmount)
                }
                paint.color = COLOR_TEXT_DARK
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 8f
                paged.canvas.drawText(amountStr, RIGHT_MARGIN - 6f, paged.y + 12f, paint)

                paint.color = COLOR_BORDER
                paint.strokeWidth = 0.4f
                paged.canvas.drawLine(LEFT_MARGIN, paged.y + rowHeight, RIGHT_MARGIN, paged.y + rowHeight, paint)
                paged.y += rowHeight
            }

            // Totals Row
            paged.ensureSpace(24f)
            paint.color = COLOR_NAVY
            paged.canvas.drawRect(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 20f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paged.canvas.drawText("TOTAL APROBADO (${approved.size} / ${records.size} TICKETS)", LEFT_MARGIN + 6f, paged.y + 13f, paint)
            paint.textAlign = Paint.Align.RIGHT
            val totalsSummary = buildList {
                if (approvedBs > 0.0 || approvedUsd <= 0.0) add(fmtBs(approvedBs))
                if (approvedUsd > 0.0) add("${fmtUsd(approvedUsd)} USD")
            }.joinToString("  |  ")
            paged.canvas.drawText(totalsSummary, RIGHT_MARGIN - 6f, paged.y + 13f, paint)
            paged.y += 28f
        }

        // Signature / audit box
        paged.ensureSpace(68f)
        val sigBox = RectF(LEFT_MARGIN, paged.y, RIGHT_MARGIN, paged.y + 56f)
        paint.color = COLOR_ROW_ALT
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        paged.canvas.drawRoundRect(sigBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        val sig1X = LEFT_MARGIN + 120f
        val sig2X = RIGHT_MARGIN - 120f
        val sigLineY = sigBox.top + 34f
        paint.color = COLOR_TEXT_MUTED
        paint.strokeWidth = 0.9f
        paged.canvas.drawLine(sig1X - 75f, sigLineY, sig1X + 75f, sigLineY, paint)
        paged.canvas.drawLine(sig2X - 75f, sigLineY, sig2X + 75f, sigLineY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8.2f
        paint.color = COLOR_TEXT_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val authorLabel = if (generatedBy.isNotBlank()) "Elaborado por ($generatedBy)" else "Responsable de Caja"
        paged.canvas.drawText(authorLabel, sig1X, sigLineY + 12f, paint)
        paged.canvas.drawText("Conciliación / Administración", sig2X, sigLineY + 12f, paint)

        val outFile = File(context.cacheDir, "PuntosDeVenta_${sdfFile.format(Date())}.pdf")
        return paged.finishAndSave(outFile)
    }
}
