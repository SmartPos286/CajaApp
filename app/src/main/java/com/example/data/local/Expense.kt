package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // "Renta / Alquiler", "Servicio Eléctrico (Luz)", "Internet / Telecomunicaciones", "Sueldos / Nómina", "Mantenimiento", "Servicios Públicos", "Impuestos / Tasas", "Otros Gastos"
    val invoiceNumber: String = "", // Número o Código de Factura, RIF, o comprobante
    val amountUsd: Double,
    val amountBs: Double = 0.0,
    val bcvRateUsed: Double = 1.0,
    val paymentMethod: String = "Efectivo USD", // "Efectivo USD", "Pago Móvil", "Transferencia", "Punto / Débito"
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val registeredBy: String = "" // Usuario que registró el gasto (Admin o Supervisor)
)
