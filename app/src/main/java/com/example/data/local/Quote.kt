package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quoteNumber: String = "", // e.g. "PRE-00001"
    val customerName: String = "Consumidor Final",
    val customerDocument: String = "V-00000000",
    val customerPhone: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val validityDays: Int = 7,
    val validUntil: Long = 0L,
    val rateBcv: Double = 1.0,
    val baseTaxableUsd: Double = 0.0,
    val baseExemptUsd: Double = 0.0,
    val ivaUsd: Double = 0.0,
    val discountUsd: Double = 0.0,
    val totalUsd: Double = 0.0,
    val totalBs: Double = 0.0,
    val notesOrConditions: String = "",
    val status: String = "VIGENTE", // "VIGENTE", "ACEPTADO", "FACTURADO", "ANULADO"
    val cashierName: String = "",
    val cashRegisterNumber: String = ""
)
