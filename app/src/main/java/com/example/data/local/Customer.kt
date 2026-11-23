package com.example.data.local

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val documentId: String,
    val address: String,
    val phone: String,
    val clientType: String = "PERSONA_NATURAL", // "PERSONA_NATURAL", "PERSONA_JURIDICA", "GOBIERNO"
    val isWithholdingAgent: Boolean = false, // Agente de Retención Especial SENIAT
    val ivaRetentionPercentage: Double = 75.0, // 75% o 100% de IVA
    val islrRetentionPercentage: Double = 2.0 // % Retención ISLR (0%, 1%, 2%, 3%, 5%)
)
