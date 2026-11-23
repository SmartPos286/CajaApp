package com.example.data.local

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey

@Serializable
@Entity(tableName = "purchase_invoices")
data class PurchaseInvoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supplierId: Int,
    val invoiceNumber: String,
    val timestamp: Long,
    val totalAmount: Double,
    val description: String,
    val isPaid: Boolean = false,
    val paidTimestamp: Long = 0L,
    val dueDate: Long = 0L,
    val paymentNotes: String = "",
    val subtotalUsd: Double = 0.0,
    val taxAmountUsd: Double = 0.0,
    val retencionIvaUsd: Double = 0.0,
    val retencionIvaPercentage: Double = 0.0,
    val retencionIslrUsd: Double = 0.0,
    val retencionIslrPercentage: Double = 0.0,
    val withholdingVoucherNumber: String = "",
    val withholdingStatus: String = "NO_APLICA" // "NO_APLICA", "PENDIENTE_ENTERA", "ENTERADO_SENIAT"
)
