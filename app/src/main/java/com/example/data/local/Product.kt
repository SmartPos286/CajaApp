package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ComboComponent(
    val productId: Int,
    val productName: String,
    val quantity: Double
)

@JsonClass(generateAdapter = true)
@Serializable
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val category: String,
    val priceUsd: Double,
    val stock: Double,
    val costPriceUsd: Double = 0.0,
    val invoiceNumber: String = "",
    val isTaxExempt: Boolean = false,
    val hasCommission: Boolean = false,
    val commissionPercentage: Double = 0.0,
    val commissionAmountUsd: Double = 0.0,
    val isRecipe: Boolean = false,
    val isCombo: Boolean = false,
    val comboItemsJson: String = "",
    val allowDecimalQuantity: Boolean = false,
    val isPriceModifiable: Boolean = false,
    val wholesalePriceUsd: Double = 0.0,
    val aisleNumber: String = "",
    val aisleQuantity: Double = 0.0,
    val depotQuantity: Double = 0.0,
    val counterQuantity: Double = 0.0,
    val minStockAlertQuantity: Double = 5.0,
    val isLowStockAlertEnabled: Boolean = true
) {
    fun calculateUnitCommission(): Double {
        if (!hasCommission) return 0.0
        return if (commissionPercentage > 0.0) {
            priceUsd * (commissionPercentage / 100.0)
        } else {
            commissionAmountUsd
        }
    }

    fun getComboComponents(): List<ComboComponent> {
        if (!isCombo || comboItemsJson.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<ComboComponent>>(comboItemsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun calculateCalculatedCostFromComponents(allProducts: List<Product>): Double {
        if (!isCombo) return costPriceUsd
        val components = getComboComponents()
        return components.sumOf { comp ->
            val prod = allProducts.find { it.id == comp.productId }
            (prod?.costPriceUsd ?: 0.0) * comp.quantity
        }
    }

    fun calculateAvailableComboStock(allProducts: List<Product>): Double {
        if (!isCombo) return stock
        val components = getComboComponents()
        if (components.isEmpty()) return 0.0
        return components.minOfOrNull { comp ->
            val prod = allProducts.find { it.id == comp.productId }
            if (prod == null || prod.isRecipe || prod.isCombo) 9999.0
            else prod.stock / maxOf(1.0, comp.quantity)
        } ?: 0.0
    }

    fun effectiveStockForAlert(allProducts: List<Product> = emptyList()): Double {
        if (isRecipe) return Double.MAX_VALUE
        return if (isCombo && allProducts.isNotEmpty()) {
            calculateAvailableComboStock(allProducts)
        } else {
            stock
        }
    }

    fun isLowStockAlertTriggered(allProducts: List<Product> = emptyList()): Boolean {
        if (isRecipe || !isLowStockAlertEnabled) return false
        if (minStockAlertQuantity < 0.0) return false
        return effectiveStockForAlert(allProducts) <= (minStockAlertQuantity + 1e-6)
    }

    fun isOutOfStockAlertTriggered(allProducts: List<Product> = emptyList()): Boolean {
        if (isRecipe || !isLowStockAlertEnabled) return false
        return effectiveStockForAlert(allProducts) <= 1e-6
    }
}
