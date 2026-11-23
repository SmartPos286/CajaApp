package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@Entity(tableName = "pos_terminal_records")
data class PosTerminalRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionDateTime: String = "",
    val transactionTimestamp: Long = System.currentTimeMillis(),
    val receiptNumber: String = "",
    val referenceNumber: String = "",
    val approvalNumber: String = "",
    val maskedCardNumber: String = "",
    val operationType: String = "COMPRA",
    val totalAmount: Double = 0.0,
    val currency: String = "VES",
    val transactionStatus: String = "APROBADA",
    val bankOrTerminal: String = "",
    val notes: String = "",
    val rawOcrText: String = "",
    val registeredBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
