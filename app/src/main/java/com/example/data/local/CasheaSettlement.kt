package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@Entity(tableName = "cashea_settlements")
data class CasheaSettlement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val settlementDate: Long,
    val grossAmountUsd: Double,
    val commissionPercentage: Double,
    val commissionAmountUsd: Double,
    val netReceivedUsd: Double,
    val rateBcv: Double = 1.0,
    val notes: String = "",
    val registeredBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
