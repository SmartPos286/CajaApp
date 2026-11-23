package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "held_sale_items")
data class HeldSaleItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val heldSaleId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Double,
    val priceUsd: Double,
    val isTaxExempt: Boolean = false
)
