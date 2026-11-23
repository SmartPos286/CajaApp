package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.PreferencesManager
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TicketUtils {

    /**
     * Genera el bitmap del ticket de venta adaptado dinámicamente al ancho del papel térmico:
     * - 58 mm: 384 px de ancho (portátil / BLE Fun Print / mini)
     * - 80 mm: 576 px de ancho (mostrador / USB / POS estándar)
     */
    fun generateTicketBitmap(
        context: Context,
        prefs: PreferencesManager,
        sale: Sale,
        items: List<SaleItem>,
        customWidthPx: Int? = null
    ): Bitmap {
        val is80mm = customWidthPx == 576 || (customWidthPx == null && prefs.printerPaperWidth.contains("80"))
        val width = if (is80mm) 576 else 384
        val baseLineHeight = if (is80mm) 30 else 24
        val height = (if (is80mm) 950 else 800) + (items.size * baseLineHeight)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 15f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = if (is80mm) 48f else 38f
        val xCenter = width / 2f
        val xLeft = if (is80mm) 28f else 18f
        val xRight = width - (if (is80mm) 28f else 18f)

        // Header - Fiscal vs. Internal Control Notice
        paint.isFakeBoldText = true
        if (prefs.isFiscalPrinter) {
            paint.textSize = if (is80mm) 16f else 13f
            val brand = prefs.fiscalPrinterBrand.ifBlank { "FISCAL" }
            canvas.drawText("SENIAT - FACTURA FISCAL ($brand)", xCenter, y, paint)
            y += if (is80mm) 20f else 16f
            val fiscalInvoiceStr = String.format(Locale.getDefault(), "COMPROBANTE FISCAL N°: %08d", sale.id)
            canvas.drawText(fiscalInvoiceStr, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        } else {
            paint.textSize = if (is80mm) 14f else 11f
            canvas.drawText("SOLO PARA USO INTERNO - CONTROL DE VENTAS.", xCenter, y, paint)
            y += if (is80mm) 18f else 15f
            canvas.drawText("NO VÁLIDO COMO FACTURA FISCAL", xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        paint.isFakeBoldText = false

        // Header - Store Info
        paint.textSize = if (is80mm) 25f else 19f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += if (is80mm) 30f else 24f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
            y += if (is80mm) 24f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Document Info
        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date(sale.timestamp))}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        paint.isFakeBoldText = true
        canvas.drawText("Factura N°: ${sale.id}", xLeft, y, paint)
        paint.isFakeBoldText = false
        y += if (is80mm) 22f else 18f

        val cashierDisplay = sale.cashierName.ifBlank { "Cajero" }
        val registerDisplay = sale.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        canvas.drawText("Caja: $registerDisplay  •  Cajero(a): $cashierDisplay", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("Cliente: ${sale.customerName.ifBlank { "Consumidor Final" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("CI/RIF: ${sale.customerDocument.ifBlank { "V-00000000" }}", xLeft, y, paint)
        y += if (is80mm) 26f else 22f

        val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

        // Column Titles
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("Cant / Descripción", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Bs.", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += if (is80mm) 10f else 8f
        paint.isFakeBoldText = false
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        // Product Items Detail (Strictly in Bolívares)
        for (item in items) {
            val exemptTag = if (item.isTaxExempt) " (E)" else ""
            val line = "${item.quantity}x ${item.productName}$exemptTag"
            canvas.drawText(line, xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val itemTotalBs = item.priceUsd * rate * item.quantity
            canvas.drawText(String.format("Bs. %.2f", itemTotalBs), xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += if (is80mm) 24f else 19f
        }

        y += if (is80mm) 8f else 6f
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = if (is80mm) 16f else 13f

        val baseTaxableBs = sale.baseTaxableUsd * rate
        val baseExemptBs = sale.baseExemptUsd * rate
        val ivaBs = sale.ivaUsd * rate
        val igtfBs = if (sale.igtfBs > 0.001) sale.igtfBs else sale.igtfUsd * rate
        val totalBsCalculated = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate

        canvas.drawText("Tasa BCV del Día: Bs. ${String.format("%.2f", rate)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        canvas.drawText("Base Imponible (16%): Bs. ${String.format("%.2f", baseTaxableBs)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        if (sale.discountUsd > 0.001) {
            val discountBs = sale.discountUsd * rate
            canvas.drawText("Descuento (pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        canvas.drawText("IVA (16%): Bs. ${String.format("%.2f", ivaBs)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        if (igtfBs > 0.001) {
            canvas.drawText("IGTF (3% Divisas): Bs. ${String.format("%.2f", igtfBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.retencionIvaUsd > 0.001) {
            val retIvaBs = sale.retencionIvaUsd * rate
            canvas.drawText("Retención IVA: -Bs. ${String.format("%.2f", retIvaBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.retencionIslrUsd > 0.001) {
            val retIslrBs = sale.retencionIslrUsd * rate
            canvas.drawText("Retención ISLR: -Bs. ${String.format("%.2f", retIslrBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        y += if (is80mm) 6f else 4f

        paint.textSize = if (is80mm) 20f else 16f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL FACTURA: Bs. ${String.format("%.2f", totalBsCalculated)}", xRight, y, paint)
        y += if (is80mm) 28f else 24f

        paint.isFakeBoldText = false
        paint.textSize = if (is80mm) 16f else 13f
        if (sale.paymentCashUsd > 0.001) {
            val cashBs = sale.paymentCashUsd * rate
            canvas.drawText("Efectivo Recibido: Bs. ${String.format("%.2f", cashBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentMobileBs > 0) {
            canvas.drawText("Pago Móvil: Bs. ${String.format("%.2f", sale.paymentMobileBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentCardBs > 0) {
            canvas.drawText("Punto / Tarjeta: Bs. ${String.format("%.2f", sale.paymentCardBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentCasheaUsd > 0.001) {
            val casheaBs = sale.paymentCasheaUsd * rate
            canvas.drawText("Cashea: Bs. ${String.format("%.2f", casheaBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentCreditUsd > 0.001) {
            val creditBs = sale.paymentCreditUsd * rate
            val creditStatus = if (sale.isCreditPaid) "PAGADO" else "PENDIENTE POR COBRAR"
            val pendingCreditBs = (sale.paymentCreditUsd - sale.creditPaidAmountUsd).coerceAtLeast(0.0) * rate
            canvas.drawText("Crédito Concedido: Bs. ${String.format("%.2f", creditBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
            paint.isFakeBoldText = true
            canvas.drawText("Estado Crédito: $creditStatus", xRight, y, paint)
            paint.isFakeBoldText = false
            y += if (is80mm) 22f else 18f
            if (!sale.isCreditPaid) {
                canvas.drawText("Saldo Pendiente: Bs. ${String.format("%.2f", pendingCreditBs)}", xRight, y, paint)
                y += if (is80mm) 22f else 18f
            }
        }

        y += if (is80mm) 26f else 20f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("¡Gracias por su compra!", xCenter, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 35).toInt())
    }

    /**
     * Genera el bitmap de la Nota de Entrega adaptado dinámicamente al ancho térmico (58mm o 80mm).
     * Cumple con SENIAT: "NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL", sin desglose de IVA,
     * reflejando los montos en USD o Bolívares según note.displayCurrency.
     */
    fun generateDeliveryNoteBitmap(
        context: Context,
        prefs: PreferencesManager,
        note: DeliveryNote,
        items: List<DeliveryNoteItem>,
        customWidthPx: Int? = null
    ): Bitmap {
        val is80mm = customWidthPx == 576 || (customWidthPx == null && prefs.printerPaperWidth.contains("80"))
        val width = if (is80mm) 576 else 384
        val baseLineHeight = if (is80mm) 28 else 22
        val height = (if (is80mm) 960 else 820) + (items.size * baseLineHeight)
        val useBs = note.displayCurrency.equals("BS", ignoreCase = true)
        val rate = if (note.rateBcv > 0.001) note.rateBcv else 1.0

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 15f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = if (is80mm) 44f else 36f
        val xCenter = width / 2f
        val xLeft = if (is80mm) 28f else 18f
        val xRight = width - (if (is80mm) 28f else 18f)

        // Header
        paint.textSize = if (is80mm) 24f else 19f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += if (is80mm) 28f else 22f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
            y += if (is80mm) 24f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Document Banner (SENIAT compliant)
        paint.color = Color.BLACK
        paint.textSize = if (is80mm) 18f else 14f
        paint.isFakeBoldText = true
        canvas.drawText("NOTA DE ENTREGA", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        paint.color = Color.DKGRAY
        paint.textSize = if (is80mm) 14f else 11f
        canvas.drawText("NO VÁLIDO PARA CRÉDITO FISCAL", xCenter, y, paint)
        paint.color = Color.BLACK
        paint.isFakeBoldText = false
        y += if (is80mm) 28f else 22f

        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date(note.timestamp))}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        paint.isFakeBoldText = true
        canvas.drawText("N° Nota: ${note.noteNumber.ifBlank { "NE-${note.id}" }}", xLeft, y, paint)
        paint.isFakeBoldText = false
        y += if (is80mm) 22f else 18f

        val noteCashier = note.cashierName.ifBlank { "Cajero" }
        val noteRegister = note.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        canvas.drawText("Caja: $noteRegister  •  Emite: $noteCashier", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("Cliente: ${note.customerName.ifBlank { "Cliente General" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("CI/RIF: ${note.customerDocument.ifBlank { "V-00000000" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        if (note.recipientName.isNotBlank()) {
            canvas.drawText("Receptor: ${note.recipientName}", xLeft, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (note.reasonOrObservation.isNotBlank()) {
            canvas.drawText("Obs.: ${note.reasonOrObservation}", xLeft, y, paint)
            y += if (is80mm) 22f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Column Titles
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("Cant. / Descripción", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (useBs) "Total Bs." else "Total USD", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += if (is80mm) 10f else 8f
        paint.isFakeBoldText = false
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        // Items (no IVA breakdown)
        for (item in items) {
            val qtyLabel = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)
            val lineTotalUsd = item.priceUsd * item.quantity
            val unitDisplay = if (useBs) "Bs. ${String.format(Locale.US, "%.2f", item.priceUsd * rate)}" else "\$${String.format(Locale.US, "%.2f", item.priceUsd)}"
            val line = "${qtyLabel}x ${item.productName} ($unitDisplay)"
            canvas.drawText(line.take(if (is80mm) 36 else 26), xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val lineTotalStr = if (useBs) {
                "Bs.${String.format(Locale.US, "%.2f", lineTotalUsd * rate)}"
            } else {
                "\$${String.format(Locale.US, "%.2f", lineTotalUsd)}"
            }
            canvas.drawText(lineTotalStr, xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += if (is80mm) 24f else 19f
        }

        y += if (is80mm) 8f else 6f
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 26f else 20f

        // Totals in selected currency ONLY (no IVA)
        val totalBs = if (note.totalEstimatedBs > 0.001) note.totalEstimatedBs else note.totalEstimatedUsd * rate
        paint.textAlign = Paint.Align.RIGHT
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 20f else 15f
        if (useBs) {
            canvas.drawText("TOTAL NOTA: Bs. ${String.format(Locale.US, "%.2f", totalBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
            paint.textSize = if (is80mm) 14f else 11f
            paint.isFakeBoldText = false
            canvas.drawText("Tasa BCV ref.: Bs. ${String.format(Locale.US, "%.2f", rate)}", xRight, y, paint)
        } else {
            canvas.drawText("TOTAL NOTA: \$${String.format(Locale.US, "%.2f", note.totalEstimatedUsd)} USD", xRight, y, paint)
            y += if (is80mm) 22f else 18f
            paint.textSize = if (is80mm) 14f else 11f
            paint.isFakeBoldText = false
            canvas.drawText("Moneda: Dólares (USD)", xRight, y, paint)
        }
        y += if (is80mm) 38f else 30f

        // Signatures area
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (is80mm) 14f else 11f
        val sigLineLeft = xLeft + 15f
        val sigLineRight = xCenter - 10f
        val sig2LineLeft = xCenter + 10f
        val sig2LineRight = xRight - 15f

        canvas.drawLine(sigLineLeft, y, sigLineRight, y, paint)
        canvas.drawLine(sig2LineLeft, y, sig2LineRight, y, paint)
        y += if (is80mm) 20f else 16f
        canvas.drawText("Entregado Por", (sigLineLeft + sigLineRight) / 2f, y, paint)
        canvas.drawText("Recibido Conforme", (sig2LineLeft + sig2LineRight) / 2f, y, paint)
        y += if (is80mm) 28f else 22f

        paint.textSize = if (is80mm) 13f else 10f
        canvas.drawText("Nota de entrega • No válido para crédito fiscal.", xCenter, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 35).toInt())
    }

    /**
     * Genera un comprobante estructurado de Nota de Entrega para enviar por WhatsApp:
     * - Encabezado destacado SENIAT: "NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL"
     * - Sin desglose de IVA, únicamente precio de venta unitario y total
     * - Refleja los montos en Dólares ($ USD) o Bolívares (Bs.) según note.displayCurrency
     */
    fun generateWhatsAppDeliveryNoteBitmap(
        context: Context,
        prefs: PreferencesManager,
        note: DeliveryNote,
        items: List<DeliveryNoteItem>
    ): Bitmap {
        val useBs = note.displayCurrency.equals("BS", ignoreCase = true)
        val rate = if (note.rateBcv > 0.001) note.rateBcv else 1.0
        val width = 800
        val headerHeight = 310
        val itemRowHeight = 44
        val tableHeaderHeight = 44
        val totalsHeight = 250
        val contentHeight = headerHeight + tableHeaderHeight + (items.size * itemRowHeight) + totalsHeight + 60
        val height = contentHeight.coerceAtLeast(820)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val cardMargin = 20f
        val cardPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, cardPaint)
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, borderPaint)

        var y = 46f

        // 1. SENIAT Delivery Note Banner (explicit wording requested by user)
        val bannerPaint = Paint().apply {
            color = Color.parseColor("#EFF6FF")
            style = Paint.Style.FILL
        }
        val bannerBorderPaint = Paint().apply {
            color = Color.parseColor("#93C5FD")
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 46f, 10f, 10f, bannerPaint)
        canvas.drawRoundRect(40f, y, width - 40f, y + 46f, 10f, 10f, bannerBorderPaint)

        paint.color = Color.parseColor("#1E3A8A")
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("NOTA DE ENTREGA • NO VÁLIDO PARA CRÉDITO FISCAL", width / 2f, y + 29f, paint)

        y += 66f

        // 2. Store Details & Logo
        val leftX = 45f
        val logoWidth = 124f
        val logoHeight = 124f
        val logoX = width - 45f - logoWidth
        val logoY = y

        var hasLogo = false
        if (prefs.storeLogoPath.isNotBlank()) {
            try {
                val logoFile = File(prefs.storeLogoPath)
                if (logoFile.exists()) {
                    val originalBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (originalBitmap != null) {
                        val scaledLogo = Bitmap.createScaledBitmap(originalBitmap, logoWidth.toInt(), logoHeight.toInt(), true)
                        canvas.drawBitmap(scaledLogo, logoX, logoY, null)
                        hasLogo = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!hasLogo) {
            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val placeholderBorder = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderPaint)
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderBorder)

            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("LOGO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) - 5f, paint)
            canvas.drawText("COMERCIO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) + 12f, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }.take(24), leftX, y + 25f, paint)

        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#334155")
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", leftX, y + 52f, paint)
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress.take(32), leftX, y + 74f, paint)
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", leftX, y + 96f, paint)
        }

        y += 136f

        // 3. Meta Box
        val metaPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 76f, 10f, 10f, metaPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(note.timestamp))
        val noteCode = note.noteNumber.ifBlank { "NE-${note.id}" }

        paint.textSize = 14f
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Nota de Entrega N°: $noteCode", leftX + 10f, y + 22f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("Fecha: $dateStr", leftX + 10f, y + 43f, paint)
        val currencyLabel = if (useBs) "Moneda: Bolívares (Bs.) • Tasa BCV: ${String.format(Locale.US, "%.2f", rate)}" else "Moneda: Dólares ($ USD)"
        canvas.drawText(currencyLabel, leftX + 10f, y + 63f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Cliente: ${note.customerName.ifBlank { "Cliente General" }.take(24)}", width - 50f, y + 22f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("CI/RIF: ${note.customerDocument.ifBlank { "V-00000000" }}", width - 50f, y + 43f, paint)
        if (note.recipientName.isNotBlank()) {
            canvas.drawText("Recibe: ${note.recipientName.take(22)}", width - 50f, y + 63f, paint)
        }

        y += 92f

        // 4. Table Header (No IVA column)
        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#0369A1")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + tableHeaderHeight, 8f, 8f, tableHeaderPaint)

        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Producto / Mercancía", leftX + 10f, y + 28f, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Cant.", 395f, y + 28f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (useBs) "P. Venta (Bs.)" else "P. Venta ($)", 575f, y + 28f, paint)
        canvas.drawText(if (useBs) "Total (Bs.)" else "Total ($)", width - 50f, y + 28f, paint)

        y += tableHeaderHeight + 8f

        // 5. Rows
        paint.color = Color.parseColor("#1E293B")
        paint.typeface = Typeface.DEFAULT
        val rowLinePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        for (item in items) {
            val unitVal = if (useBs) item.priceUsd * rate else item.priceUsd
            val lineVal = unitVal * item.quantity
            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)

            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14f
            canvas.drawText(item.productName.take(24), leftX + 10f, y + 27f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(qtyStr, 395f, y + 27f, paint)

            paint.textAlign = Paint.Align.RIGHT
            val unitStr = if (useBs) String.format(Locale.US, "Bs. %.2f", unitVal) else String.format(Locale.US, "$ %.2f", unitVal)
            val totalStr = if (useBs) String.format(Locale.US, "Bs. %.2f", lineVal) else String.format(Locale.US, "$ %.2f", lineVal)
            canvas.drawText(unitStr, 575f, y + 27f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(totalStr, width - 50f, y + 27f, paint)

            y += itemRowHeight
            canvas.drawLine(40f, y, width - 40f, y, rowLinePaint)
            y += 4f
        }

        y += 18f

        // 6. Grand Total Box (No IVA)
        val totalBs = if (note.totalEstimatedBs > 0.001) note.totalEstimatedBs else note.totalEstimatedUsd * rate
        val totalBoxPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(width - 430f, y, width - 40f, y + 56f, 12f, 12f, totalBoxPaint)

        paint.color = Color.WHITE
        paint.textSize = 19f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        val grandTotalText = if (useBs) {
            "TOTAL: Bs. ${String.format(Locale.US, "%.2f", totalBs)}"
        } else {
            "TOTAL: \$${String.format(Locale.US, "%.2f", note.totalEstimatedUsd)} USD"
        }
        canvas.drawText(grandTotalText, width - 60f, y + 35f, paint)

        y += 74f

        if (note.reasonOrObservation.isNotBlank()) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#475569")
            paint.textSize = 13f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("Observación: ${note.reasonOrObservation.take(72)}", leftX, y, paint)
            y += 26f
        }

        // Signature / Reception footer
        val linePaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = 1.5f
        }
        y += 28f
        canvas.drawLine(leftX + 20f, y, 330f, y, linePaint)
        canvas.drawLine(width - 330f, y, width - 65f, y, linePaint)
        y += 18f

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.parseColor("#334155")
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Entregado Por", (leftX + 20f + 330f) / 2f, y, paint)
        canvas.drawText("Recibido Conforme", (width - 330f + width - 65f) / 2f, y, paint)

        y += 26f
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Nota de Entrega • No válido para crédito fiscal • Control de inventario y despacho", width / 2f, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 40).toInt().coerceAtMost(height))
    }

    fun saveDeliveryNoteBitmapToFile(context: Context, bitmap: Bitmap, noteId: Int): File? {
        return try {
            val file = File(context.cacheDir, "nota_entrega_$noteId.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera un ticket en formato de imagen cuadrada perfectamente estructurada para enviar por WhatsApp:
     * - Encabezado destacado: "DOCUMENTO NO VÁLIDO COMO FACTURA FISCAL"
     * - Información de la empresa (Nombre, RIF, Teléfono, Dirección)
     * - Logo de la empresa en la esquina paralela si fue cargado por el usuario
     * - Tabla organizada: Producto | Cant. | P. Unit (Sin IVA) | Total Bs.
     * - Resumen de Totales: Subtotal sin IVA, Descuento, Base Imponible, IVA (16%), Total a pagar con IVA.
     */
    fun generateWhatsAppSquareTicketBitmap(
        context: Context,
        prefs: PreferencesManager,
        sale: Sale,
        items: List<SaleItem>
    ): Bitmap {
        val width = 800
        val headerHeight = 280
        val itemRowHeight = 42
        val tableHeaderHeight = 44
        val totalsHeight = 240
        val contentHeight = headerHeight + tableHeaderHeight + (items.size * itemRowHeight) + totalsHeight + 60
        val height = contentHeight.coerceAtLeast(800)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean off-white background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        // Draw Outer Container Card
        val cardMargin = 20f
        val cardPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, cardPaint)
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, borderPaint)

        var y = 50f

        // 1. Non-Fiscal Banner Header
        val bannerPaint = Paint().apply {
            color = Color.parseColor("#FEF2F2")
            style = Paint.Style.FILL
        }
        val bannerBorderPaint = Paint().apply {
            color = Color.parseColor("#FCA5A5")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 42f, 10f, 10f, bannerPaint)
        canvas.drawRoundRect(40f, y, width - 40f, y + 42f, 10f, 10f, bannerBorderPaint)

        paint.color = Color.parseColor("#991B1B")
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DOCUMENTO NO VÁLIDO COMO FACTURA FISCAL", width / 2f, y + 27f, paint)

        y += 65f

        // 2. Company Info (Left Column) vs. Logo (Right Column / Parallel Corner)
        val leftX = 45f
        val logoWidth = 130f
        val logoHeight = 130f
        val logoX = width - 45f - logoWidth
        val logoY = y

        // Try to load store logo if user uploaded one
        var hasLogo = false
        if (prefs.storeLogoPath.isNotBlank()) {
            try {
                val logoFile = File(prefs.storeLogoPath)
                if (logoFile.exists()) {
                    val originalBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (originalBitmap != null) {
                        val scaledLogo = Bitmap.createScaledBitmap(originalBitmap, logoWidth.toInt(), logoHeight.toInt(), true)
                        canvas.drawBitmap(scaledLogo, logoX, logoY, null)
                        hasLogo = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!hasLogo) {
            // Draw logo placeholder box
            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val placeholderBorder = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderPaint)
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderBorder)

            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("LOGO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) - 5f, paint)
            canvas.drawText("COMERCIO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) + 12f, paint)
        }

        // Store Details Text
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val storeNameText = prefs.storeName.ifBlank { "MI COMERCIO POS" }.take(22)
        canvas.drawText(storeNameText, leftX, y + 25f, paint)

        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#334155")
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", leftX, y + 52f, paint)

        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress.take(30), leftX, y + 74f, paint)
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", leftX, y + 96f, paint)
        }

        y += 140f

        // 3. Document Meta Info Box
        val metaPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 55f, 10f, 10f, metaPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.timestamp))

        paint.textSize = 14f
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Ticket N°: #${sale.id}", leftX + 10f, y + 24f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("Fecha: $dateStr", leftX + 10f, y + 44f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val customerDisplay = sale.customerName.ifBlank { "Consumidor Final" }.take(22)
        canvas.drawText("Cliente: $customerDisplay", width - 50f, y + 24f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        val docDisplay = sale.customerDocument.ifBlank { "V-00000000" }
        canvas.drawText("CI/RIF: $docDisplay", width - 50f, y + 44f, paint)

        y += 70f

        // 4. Products Table Header
        val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + tableHeaderHeight, 8f, 8f, tableHeaderPaint)

        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Producto", leftX + 10f, y + 28f, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Cant.", 380f, y + 28f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("P. Unit (Sin IVA)", 580f, y + 28f, paint)
        canvas.drawText("Total Bs.", width - 50f, y + 28f, paint)

        y += tableHeaderHeight + 8f

        // Products Rows
        paint.color = Color.parseColor("#1E293B")
        paint.typeface = Typeface.DEFAULT
        val rowLinePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        for (item in items) {
            val itemPriceBsTotal = item.priceUsd * rate
            val unitPriceNoTaxBs = if (!item.isTaxExempt) itemPriceBsTotal / 1.16 else itemPriceBsTotal
            val lineTotalBs = itemPriceBsTotal * item.quantity

            val exemptMark = if (item.isTaxExempt) " (E)" else ""
            val productNameDisplay = "${item.productName.take(20)}$exemptMark"

            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14f
            canvas.drawText(productNameDisplay, leftX + 10f, y + 26f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", 380f, y + 26f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format("Bs. %.2f", unitPriceNoTaxBs), 580f, y + 26f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(String.format("Bs. %.2f", lineTotalBs), width - 50f, y + 26f, paint)

            y += itemRowHeight
            canvas.drawLine(40f, y, width - 40f, y, rowLinePaint)
            y += 4f
        }

        y += 15f

        // 5. Totals Breakdown Box
        val subtotalBs = sale.baseTaxableUsd * rate
        val baseExemptBs = sale.baseExemptUsd * rate
        val discountBs = sale.discountUsd * rate
        val ivaBs = sale.ivaUsd * rate
        val totalBs = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 14f
        paint.color = Color.parseColor("#475569")

        canvas.drawText("Base Imponible (Sin IVA): Bs. ${String.format("%.2f", subtotalBs)}", width - 50f, y, paint)
        y += 22f

        if (discountBs > 0.001) {
            paint.color = Color.parseColor("#059669")
            canvas.drawText("Descuento (Pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", width - 50f, y, paint)
            y += 22f
            paint.color = Color.parseColor("#475569")
        }

        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", width - 50f, y, paint)
            y += 22f
        }

        canvas.drawText("IVA Fiscal (16%): Bs. ${String.format("%.2f", ivaBs)}", width - 50f, y, paint)
        y += 28f

        // Grand Total Box
        val totalBoxPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(width - 420f, y, width - 40f, y + 54f, 12f, 12f, totalBoxPaint)

        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL: Bs. ${String.format("%.2f", totalBs)}", width - 60f, y + 34f, paint)

        y += 68f

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Equivalente USD (Tasa BCV Bs. ${String.format("%.2f", rate)}): $ ${String.format("%.2f", sale.totalUsd)}", width - 50f, y, paint)

        return bitmap
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, saleId: Int): File? {
        return try {
            val file = File(context.cacheDir, "ticket_$saleId.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera el bitmap térmico de Presupuesto / Cotización (58mm o 80mm).
     */
    fun generateQuoteTicketBitmap(
        context: Context,
        prefs: PreferencesManager,
        quote: Quote,
        items: List<QuoteItem>,
        customWidthPx: Int? = null
    ): Bitmap {
        val is80mm = customWidthPx == 576 || (customWidthPx == null && prefs.printerPaperWidth.contains("80"))
        val width = if (is80mm) 576 else 384
        val baseLineHeight = if (is80mm) 30 else 24
        val notesLines = if (quote.notesOrConditions.isNotBlank()) 3 else 0
        val height = (if (is80mm) 980 else 820) + ((items.size + notesLines) * baseLineHeight)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 15f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = if (is80mm) 44f else 36f
        val xCenter = width / 2f
        val xLeft = if (is80mm) 28f else 18f
        val xRight = width - (if (is80mm) 28f else 18f)

        // Banner - Quote Non-Fiscal Header
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 18f else 14f
        canvas.drawText("--- PRESUPUESTO / COTIZACIÓN ---", xCenter, y, paint)
        y += if (is80mm) 20f else 16f
        paint.textSize = if (is80mm) 13f else 10.5f
        paint.isFakeBoldText = false
        canvas.drawText("DOCUMENTO NO VÁLIDO COMO FACTURA FISCAL", xCenter, y, paint)
        y += if (is80mm) 24f else 18f

        // Store Info
        paint.textSize = if (is80mm) 24f else 19f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += if (is80mm) 28f else 22f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
            y += if (is80mm) 24f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Document Info
        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        canvas.drawText("Emisión: ${sdf.format(Date(quote.timestamp))}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        if (quote.validUntil > 0L) {
            canvas.drawText("Válido hasta: ${sdfDate.format(Date(quote.validUntil))} (${quote.validityDays} días)", xLeft, y, paint)
            y += if (is80mm) 22f else 18f
        }

        paint.isFakeBoldText = true
        canvas.drawText("Presupuesto N°: ${quote.quoteNumber.ifBlank { "PRE-${quote.id}" }}", xLeft, y, paint)
        paint.isFakeBoldText = false
        y += if (is80mm) 22f else 18f

        val cashierDisplay = quote.cashierName.ifBlank { "Cajero" }
        val registerDisplay = quote.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        canvas.drawText("Caja: $registerDisplay  •  Atendido por: $cashierDisplay", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("Cliente: ${quote.customerName.ifBlank { "Consumidor Final" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f
        canvas.drawText("CI/RIF: ${quote.customerDocument.ifBlank { "V-00000000" }}", xLeft, y, paint)
        y += if (is80mm) 24f else 20f

        val rate = if (quote.rateBcv > 0.001) quote.rateBcv else 1.0

        // Column Titles
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("Cant / Descripción", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Bs.", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += if (is80mm) 10f else 8f
        paint.isFakeBoldText = false
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        for (item in items) {
            val exemptTag = if (item.isTaxExempt) " (E)" else ""
            val line = "${item.quantity}x ${item.productName}$exemptTag"
            canvas.drawText(line, xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val itemTotalBs = item.priceUsd * rate * item.quantity
            canvas.drawText(String.format("Bs. %.2f", itemTotalBs), xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += if (is80mm) 24f else 19f
        }

        y += if (is80mm) 8f else 6f
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = if (is80mm) 16f else 13f

        val baseTaxableBs = quote.baseTaxableUsd * rate
        val baseExemptBs = quote.baseExemptUsd * rate
        val ivaBs = quote.ivaUsd * rate
        val totalBsCalculated = if (quote.totalBs > 0.001) quote.totalBs else quote.totalUsd * rate

        canvas.drawText("Tasa BCV del Día: Bs. ${String.format("%.2f", rate)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        canvas.drawText("Base Imponible (16%): Bs. ${String.format("%.2f", baseTaxableBs)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        if (quote.discountUsd > 0.001) {
            val discountBs = quote.discountUsd * rate
            canvas.drawText("Descuento (pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        canvas.drawText("IVA (16%): Bs. ${String.format("%.2f", ivaBs)}", xRight, y, paint)
        y += if (is80mm) 26f else 20f

        paint.textSize = if (is80mm) 20f else 16f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL PRESUPUESTO: Bs. ${String.format("%.2f", totalBsCalculated)}", xRight, y, paint)
        y += if (is80mm) 24f else 20f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("Equivalente USD: \$${String.format("%.2f", quote.totalUsd)}", xRight, y, paint)
        y += if (is80mm) 24f else 20f

        if (quote.notesOrConditions.isNotBlank()) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = if (is80mm) 14f else 11.5f
            canvas.drawText("Obs: ${quote.notesOrConditions.take(45)}", xLeft, y, paint)
            y += if (is80mm) 22f else 18f
        }

        y += if (is80mm) 14f else 10f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (is80mm) 14f else 11.5f
        canvas.drawText("Precios referenciales sujetos a disponibilidad", xCenter, y, paint)
        y += if (is80mm) 20f else 16f
        canvas.drawText("y variación de tasa BCV al momento de compra.", xCenter, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 35).toInt())
    }

    /**
     * Genera la imagen cuadrada de Presupuesto lista para compartir por WhatsApp.
     */
    fun generateWhatsAppSquareQuoteBitmap(
        context: Context,
        prefs: PreferencesManager,
        quote: Quote,
        items: List<QuoteItem>
    ): Bitmap {
        val width = 800
        val headerHeight = 300
        val itemRowHeight = 42
        val tableHeaderHeight = 44
        val notesExtraHeight = if (quote.notesOrConditions.isNotBlank()) 56 else 0
        val totalsHeight = 250 + notesExtraHeight
        val contentHeight = headerHeight + tableHeaderHeight + (items.size * itemRowHeight) + totalsHeight + 60
        val height = contentHeight.coerceAtLeast(820)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val cardMargin = 20f
        val cardPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, cardPaint)
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, borderPaint)

        var y = 50f

        // 1. Quote Banner Header
        val bannerPaint = Paint().apply {
            color = Color.parseColor("#EFF6FF")
            style = Paint.Style.FILL
        }
        val bannerBorderPaint = Paint().apply {
            color = Color.parseColor("#93C5FD")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 44f, 10f, 10f, bannerPaint)
        canvas.drawRoundRect(40f, y, width - 40f, y + 44f, 10f, 10f, bannerBorderPaint)

        paint.color = Color.parseColor("#1E40AF")
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PRESUPUESTO / COTIZACIÓN • NO VÁLIDO COMO FACTURA", width / 2f, y + 28f, paint)

        y += 66f

        // 2. Company Info vs Logo
        val leftX = 45f
        val logoWidth = 130f
        val logoHeight = 130f
        val logoX = width - 45f - logoWidth
        val logoY = y

        var hasLogo = false
        if (prefs.storeLogoPath.isNotBlank()) {
            try {
                val logoFile = File(prefs.storeLogoPath)
                if (logoFile.exists()) {
                    val originalBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (originalBitmap != null) {
                        val scaledLogo = Bitmap.createScaledBitmap(originalBitmap, logoWidth.toInt(), logoHeight.toInt(), true)
                        canvas.drawBitmap(scaledLogo, logoX, logoY, null)
                        hasLogo = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!hasLogo) {
            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val placeholderBorder = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderPaint)
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderBorder)

            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("LOGO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) - 5f, paint)
            canvas.drawText("COMERCIO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) + 12f, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }.take(22), leftX, y + 25f, paint)

        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#334155")
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", leftX, y + 52f, paint)
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress.take(30), leftX, y + 74f, paint)
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", leftX, y + 96f, paint)
        }

        y += 140f

        // 3. Quote Meta Info Box
        val metaPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 72f, 10f, 10f, metaPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date(quote.timestamp))
        val validStr = if (quote.validUntil > 0L) "${sdfDate.format(Date(quote.validUntil))} (${quote.validityDays} días)" else "${quote.validityDays} días"

        paint.textSize = 14f
        paint.color = Color.parseColor("#1E40AF")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Presupuesto N°: ${quote.quoteNumber.ifBlank { "PRE-${quote.id}" }}", leftX + 10f, y + 23f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("Emisión: $dateStr", leftX + 10f, y + 43f, paint)
        canvas.drawText("Válido hasta: $validStr", leftX + 10f, y + 62f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val customerDisplay = quote.customerName.ifBlank { "Consumidor Final" }.take(22)
        canvas.drawText("Cliente: $customerDisplay", width - 50f, y + 23f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("CI/RIF: ${quote.customerDocument.ifBlank { "V-00000000" }}", width - 50f, y + 43f, paint)
        if (quote.customerPhone.isNotBlank()) {
            canvas.drawText("Telf: ${quote.customerPhone}", width - 50f, y + 62f, paint)
        }

        y += 86f

        // 4. Products Table Header
        val rate = if (quote.rateBcv > 0.001) quote.rateBcv else 1.0

        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E3A8A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + tableHeaderHeight, 8f, 8f, tableHeaderPaint)

        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Producto", leftX + 10f, y + 28f, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Cant.", 380f, y + 28f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("P. Unit (Sin IVA)", 580f, y + 28f, paint)
        canvas.drawText("Total Bs.", width - 50f, y + 28f, paint)

        y += tableHeaderHeight + 8f

        paint.color = Color.parseColor("#1E293B")
        paint.typeface = Typeface.DEFAULT
        val rowLinePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        for (item in items) {
            val itemPriceBsTotal = item.priceUsd * rate
            val unitPriceNoTaxBs = if (!item.isTaxExempt) itemPriceBsTotal / 1.16 else itemPriceBsTotal
            val lineTotalBs = itemPriceBsTotal * item.quantity

            val exemptMark = if (item.isTaxExempt) " (E)" else ""
            val productNameDisplay = "${item.productName.take(20)}$exemptMark"

            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14f
            canvas.drawText(productNameDisplay, leftX + 10f, y + 26f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", 380f, y + 26f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format("Bs. %.2f", unitPriceNoTaxBs), 580f, y + 26f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(String.format("Bs. %.2f", lineTotalBs), width - 50f, y + 26f, paint)

            y += itemRowHeight
            canvas.drawLine(40f, y, width - 40f, y, rowLinePaint)
            y += 4f
        }

        y += 15f

        // 5. Totals Breakdown Box
        val subtotalBs = quote.baseTaxableUsd * rate
        val baseExemptBs = quote.baseExemptUsd * rate
        val discountBs = quote.discountUsd * rate
        val ivaBs = quote.ivaUsd * rate
        val totalBs = if (quote.totalBs > 0.001) quote.totalBs else quote.totalUsd * rate

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 14f
        paint.color = Color.parseColor("#475569")

        canvas.drawText("Base Imponible (Sin IVA): Bs. ${String.format("%.2f", subtotalBs)}", width - 50f, y, paint)
        y += 22f

        if (discountBs > 0.001) {
            paint.color = Color.parseColor("#059669")
            canvas.drawText("Descuento (Pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", width - 50f, y, paint)
            y += 22f
            paint.color = Color.parseColor("#475569")
        }

        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", width - 50f, y, paint)
            y += 22f
        }

        canvas.drawText("IVA Fiscal (16%): Bs. ${String.format("%.2f", ivaBs)}", width - 50f, y, paint)
        y += 28f

        // Grand Total Box
        val totalBoxPaint = Paint().apply {
            color = Color.parseColor("#1E3A8A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(width - 450f, y, width - 40f, y + 54f, 12f, 12f, totalBoxPaint)

        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL PRESUPUESTO: Bs. ${String.format("%.2f", totalBs)}", width - 60f, y + 34f, paint)

        y += 68f

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Equivalente USD (Tasa BCV Bs. ${String.format("%.2f", rate)}): \$ ${String.format("%.2f", quote.totalUsd)}", width - 50f, y, paint)

        if (quote.notesOrConditions.isNotBlank()) {
            y += 28f
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#334155")
            paint.textSize = 13f
            canvas.drawText("Observaciones: ${quote.notesOrConditions.take(75)}", 45f, y, paint)
        }

        return bitmap
    }

    fun saveQuoteBitmapToFile(context: Context, bitmap: Bitmap, quoteId: Int): File? {
        return try {
            val file = File(context.cacheDir, "presupuesto_$quoteId.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun shareFile(context: Context, file: File, mimeType: String, packageName: String? = null) {
        val uri = getUriForFile(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            packageName?.let { setPackage(it) }
        }
        try {
            val chooser = Intent.createChooser(intent, "Compartir archivo").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            val fallbackChooser = Intent.createChooser(intent.apply { setPackage(null) }, "Compartir archivo").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(fallbackChooser)
        }
    }

    /**
     * Revisa si el cliente tiene un número telefónico registrado en la aplicación.
     * De tenerlo, abre directamente WhatsApp enviando la imagen del ticket a dicho destinatario.
     * Si no tiene número o falla la integración directa, abre el selector habitual de WhatsApp.
     */
    fun shareWhatsAppFile(
        context: Context,
        file: File,
        phone: String? = null,
        captionText: String = "Comprobante de venta"
    ) {
        val uri = getUriForFile(context, file)
        val cleanPhone = phone?.replace(Regex("[^0-9]"), "") ?: ""
        val formattedPhone = when {
            cleanPhone.startsWith("58") -> cleanPhone
            cleanPhone.startsWith("0") -> "58" + cleanPhone.substring(1)
            cleanPhone.length == 10 -> "58" + cleanPhone
            cleanPhone.isNotBlank() -> "58" + cleanPhone
            else -> ""
        }

        if (formattedPhone.isNotBlank()) {
            val jid = "$formattedPhone@s.whatsapp.net"
            var sent = false
            // 1. Intentar WhatsApp Estándar con JID directo
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra("jid", jid)
                    putExtra(Intent.EXTRA_TEXT, captionText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
                sent = true
            } catch (e: Exception) {
                // 2. Intentar WhatsApp Business con JID directo
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra("jid", jid)
                        putExtra(Intent.EXTRA_TEXT, captionText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.whatsapp.w4b")
                    }
                    context.startActivity(intent)
                    sent = true
                } catch (e2: Exception) {
                    sent = false
                }
            }

            if (sent) {
                android.widget.Toast.makeText(context, "Enviando ticket directo a WhatsApp ($formattedPhone)", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                shareFile(context, file, "image/png", "com.whatsapp")
            }
        } else {
            shareFile(context, file, "image/png", "com.whatsapp")
        }
    }
}
