package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "held_sales")
data class HeldSale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val note: String = "", // e.g. "Mesa 1", "Sr. Pedro", "Paga en 10 min"
    val customerName: String = "Cliente General",
    val customerDocument: String = "V-00000000",
    val timestamp: Long = System.currentTimeMillis(),
    val discountUsd: Double = 0.0,
    val totalEstimatedUsd: Double = 0.0,
    val itemCount: Double = 0.0,
    val cashierName: String = "",
    val cashRegisterNumber: String = ""
)
