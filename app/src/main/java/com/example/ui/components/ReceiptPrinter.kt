package com.example.ui.components

import android.content.Context
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.PreferencesManager
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.utils.PdfExportManager
import java.io.File

class ReceiptPrinter(private val context: Context, private val prefs: PreferencesManager) {

    fun generateReceiptPdf(
        sale: Sale,
        items: List<SaleItem>,
        customerPhone: String = "",
        customerAddress: String = ""
    ): File? {
        return PdfExportManager.generateSaleInvoicePdf(
            context = context,
            prefs = prefs,
            sale = sale,
            items = items,
            customerPhone = customerPhone,
            customerAddress = customerAddress
        )
    }

    fun generateQuotePdf(quote: Quote, items: List<QuoteItem>): File? {
        return PdfExportManager.generateQuotePdf(
            context = context,
            prefs = prefs,
            quote = quote,
            items = items
        )
    }

    fun generateDeliveryNotePdf(note: DeliveryNote, items: List<DeliveryNoteItem>): File? {
        return PdfExportManager.generateDeliveryNotePdf(
            context = context,
            prefs = prefs,
            note = note,
            items = items
        )
    }
}
