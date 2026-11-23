package com.example.data.local

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val customerDocument: String,
    val timestamp: Long,
    val rateBcv: Double,
    val totalUsd: Double,
    val totalBs: Double,
    val paymentCashUsd: Double,
    val paymentMobileBs: Double,
    val paymentCardBs: Double,
    val paymentCreditUsd: Double = 0.0,
    val paymentCasheaUsd: Double = 0.0,
    val isCreditPaid: Boolean = false,
    val creditPaidAmountUsd: Double = 0.0,
    val creditDueDate: Long = 0L,
    val creditNotes: String = "",
    val igtfUsd: Double = 0.0,
    val igtfBs: Double = 0.0,
    val baseTaxableUsd: Double = 0.0,
    val baseExemptUsd: Double = 0.0,
    val ivaUsd: Double = 0.0,
    val discountUsd: Double = 0.0,
    val cashierName: String = "",
    val cashRegisterNumber: String = "",
    val totalCommissionUsd: Double = 0.0,
    val retencionIvaUsd: Double = 0.0,
    val retencionIvaPercentage: Double = 0.0,
    val retencionIslrUsd: Double = 0.0,
    val retencionIslrPercentage: Double = 0.0,
    val withholdingVoucherNumber: String = "",
    val withholdingStatus: String = "SIN_RETENCION" // "SIN_RETENCION", "PENDIENTE_COMPROBANTE", "COMPROBANTE_RECIBIDO"
)
