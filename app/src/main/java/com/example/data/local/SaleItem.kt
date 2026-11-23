package com.example.data.local

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val saleId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Double,
    val priceUsd: Double,
    val isTaxExempt: Boolean = false,
    val commissionUsd: Double = 0.0
)
