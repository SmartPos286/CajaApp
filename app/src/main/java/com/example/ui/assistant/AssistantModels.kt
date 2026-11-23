package com.example.ui.assistant

import java.util.UUID

enum class AssistantSender {
    USER,
    ASSISTANT,
    SYSTEM
}

data class DisambiguationItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val category: String = "",
    val priceUsd: Double = 0.0,
    val stock: Double = 0.0
)

data class QuoteDraftItem(
    val productId: Int,
    val productName: String,
    val quantity: Double,
    val priceUsd: Double,
    val isTaxExempt: Boolean = false,
    val isWholesale: Boolean = false
)

sealed class PendingAssistantAction {
    data class CreateProductAction(
        val name: String,
        val category: String,
        val stock: Double,
        val costPriceUsd: Double,
        val priceUsd: Double,
        val isRecipe: Boolean,
        val isTaxExempt: Boolean = false,
        val code: String = "",
        val profitPercentage: Double = 0.0,
        val wholesalePriceUsd: Double = 0.0,
        val autoCalculatedWithMinMargin: Boolean = false
    ) : PendingAssistantAction()

    data class CreateQuoteAction(
        val customerName: String = "Consumidor Final",
        val customerDocument: String = "V-00000000",
        val customerPhone: String = "",
        val items: List<QuoteDraftItem> = emptyList(),
        val validityDays: Int = 7,
        val discountUsd: Double = 0.0,
        val notesOrConditions: String = "Precios sujetos a disponibilidad y tasa BCV vigente.",
        val baseTaxableUsd: Double = 0.0,
        val baseExemptUsd: Double = 0.0,
        val ivaUsd: Double = 0.0,
        val totalUsd: Double = 0.0,
        val totalBs: Double = 0.0
    ) : PendingAssistantAction()

    data class AddStockAction(
        val productId: Int,
        val productName: String,
        val currentStock: Double,
        val quantityToAdd: Double,
        val newCostPriceUsd: Double? = null
    ) : PendingAssistantAction()

    data class CreatePurchaseInvoiceAction(
        val supplierId: Int,
        val supplierName: String,
        val invoiceNumber: String,
        val totalAmount: Double,
        val description: String
    ) : PendingAssistantAction()

    data class CreateCustomerAction(
        val name: String,
        val documentId: String,
        val phone: String,
        val address: String
    ) : PendingAssistantAction()

    data class RegisterExpenseAction(
        val description: String,
        val amountUsd: Double,
        val category: String = "Operativo"
    ) : PendingAssistantAction()

    data class UpdateBcvRateAction(
        val newRateBs: Double
    ) : PendingAssistantAction()

    data class DeleteProductAction(
        val productId: Int,
        val productName: String
    ) : PendingAssistantAction()

    data class UpdateProductPriceAction(
        val productId: Int,
        val productName: String,
        val currentPriceUsd: Double,
        val newPriceUsd: Double,
        val currentWholesaleUsd: Double = 0.0,
        val newWholesaleUsd: Double? = null
    ) : PendingAssistantAction()

    data class SelectDisambiguation(
        val candidates: List<DisambiguationItem>,
        val actionIntent: String, // "ADD_STOCK" or "QUERY_PRODUCT"
        val quantity: Double = 0.0,
        val newCost: Double? = null
    ) : PendingAssistantAction()
}

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: AssistantSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pendingAction: PendingAssistantAction? = null,
    val isConfirmedOrResolved: Boolean = false,
    val createdQuoteId: Int? = null
)
