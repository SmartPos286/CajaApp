package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "delivery_note_items")
data class DeliveryNoteItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deliveryNoteId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Double,
    val priceUsd: Double
)
