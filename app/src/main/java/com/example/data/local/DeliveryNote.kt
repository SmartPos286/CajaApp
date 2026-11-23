package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "delivery_notes")
data class DeliveryNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noteNumber: String = "", // e.g. "NE-00001"
    val customerName: String = "Cliente General",
    val customerDocument: String = "V-00000000",
    val recipientName: String = "", // Persona que recibe
    val reasonOrObservation: String = "Despacho de mercancía sin factura fiscal",
    val timestamp: Long = System.currentTimeMillis(),
    val totalEstimatedUsd: Double = 0.0,
    val totalEstimatedBs: Double = 0.0,
    val rateBcv: Double = 1.0,
    val status: String = "ENTREGADO", // "ENTREGADO", "FACTURADO", "ANULADO"
    val cashierName: String = "",
    val cashRegisterNumber: String = "",
    val displayCurrency: String = "USD" // "USD" o "BS"
)
