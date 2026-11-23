package com.example.data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SupabaseInventoryProductItem(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    val category: String = "GENERAL",
    @SerialName("price_usd") val priceUsd: Double = 0.0,
    @SerialName("cost_price_usd") val costPriceUsd: Double = 0.0,
    val stock: Double = 0.0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("is_tax_exempt") val isTaxExempt: Boolean = false,
    @SerialName("has_commission") val hasCommission: Boolean = false,
    @SerialName("commission_percentage") val commissionPercentage: Double = 0.0,
    @SerialName("commission_amount_usd") val commissionAmountUsd: Double = 0.0,
    @SerialName("is_recipe") val isRecipe: Boolean = false,
    @SerialName("is_combo") val isCombo: Boolean = false,
    @SerialName("combo_items_json") val comboItemsJson: String = "",
    @SerialName("aisle_number") val aisleNumber: String = "",
    @SerialName("aisle_quantity") val aisleQuantity: Double = 0.0,
    @SerialName("depot_quantity") val depotQuantity: Double = 0.0,
    @SerialName("counter_quantity") val counterQuantity: Double = 0.0,
    @SerialName("wholesale_price_usd") val wholesalePriceUsd: Double = 0.0
)

@Serializable
data class SupabaseInventoryPayload(
    @SerialName("store_name") val storeName: String = "",
    @SerialName("store_rif") val storeRif: String = "",
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("total_stock_units") val totalStockUnits: Double = 0.0,
    @SerialName("valuation_usd") val valuationUsd: Double = 0.0,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val products: List<SupabaseInventoryProductItem> = emptyList()
)

@Serializable
data class SupabaseTicketItem(
    @SerialName("product_id") val productId: Int = 0,
    @SerialName("product_name") val productName: String = "",
    val quantity: Double = 0.0,
    @SerialName("price_usd") val priceUsd: Double = 0.0,
    @SerialName("is_tax_exempt") val isTaxExempt: Boolean = false,
    @SerialName("commission_usd") val commissionUsd: Double = 0.0
)

@Serializable
data class SupabaseTicketRecord(
    val id: Int = 0,
    @SerialName("customer_name") val customerName: String = "",
    @SerialName("customer_document") val customerDocument: String = "",
    val timestamp: Long = 0L,
    @SerialName("rate_bcv") val rateBcv: Double = 0.0,
    @SerialName("total_usd") val totalUsd: Double = 0.0,
    @SerialName("total_bs") val totalBs: Double = 0.0,
    @SerialName("payment_cash_usd") val paymentCashUsd: Double = 0.0,
    @SerialName("payment_mobile_bs") val paymentMobileBs: Double = 0.0,
    @SerialName("payment_card_bs") val paymentCardBs: Double = 0.0,
    @SerialName("payment_credit_usd") val paymentCreditUsd: Double = 0.0,
    @SerialName("payment_cashea_usd") val paymentCasheaUsd: Double = 0.0,
    @SerialName("cashier_name") val cashierName: String = "",
    @SerialName("cash_register_number") val cashRegisterNumber: String = "",
    @SerialName("igtf_usd") val igtfUsd: Double = 0.0,
    @SerialName("iva_usd") val ivaUsd: Double = 0.0,
    @SerialName("discount_usd") val discountUsd: Double = 0.0,
    @SerialName("withholding_voucher_number") val withholdingVoucherNumber: String = "",
    @SerialName("withholding_status") val withholdingStatus: String = "SIN_RETENCION",
    val items: List<SupabaseTicketItem> = emptyList()
)

@Serializable
data class SupabaseTicketsLogPayload(
    @SerialName("total_tickets_count") val totalTicketsCount: Int = 0,
    @SerialName("total_sales_usd") val totalSalesUsd: Double = 0.0,
    @SerialName("total_sales_bs") val totalSalesBs: Double = 0.0,
    @SerialName("last_ticket_at") val lastTicketAt: Long = 0L,
    val tickets: List<SupabaseTicketRecord> = emptyList()
)

/**
 * Primary sync payload sent to Supabase backend containing:
 * - 'user_id'
 * - 'data_inventory' (JSON object/string)
 * - 'tickets_log' (JSON object/string)
 */
@Serializable
data class SupabaseUserBackendSyncRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("data_inventory") val dataInventory: JsonElement,
    @SerialName("tickets_log") val ticketsLog: JsonElement,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SupabaseUserBackendSyncStringRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("data_inventory") val dataInventory: String,
    @SerialName("tickets_log") val ticketsLog: String,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)
