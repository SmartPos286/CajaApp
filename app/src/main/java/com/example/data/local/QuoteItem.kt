package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@Entity(
    tableName = "quote_items",
    foreignKeys = [
        ForeignKey(
            entity = Quote::class,
            parentColumns = ["id"],
            childColumns = ["quoteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quoteId")]
)
data class QuoteItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quoteId: Int = 0,
    val productId: Int = 0,
    val productName: String = "",
    val quantity: Double = 1.0,
    val priceUsd: Double = 0.0,
    val isTaxExempt: Boolean = false
)
