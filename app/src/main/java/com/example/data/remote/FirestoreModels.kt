package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

@Keep
@IgnoreExtraProperties
data class FsUserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "email" to email,
        "displayName" to displayName,
        "lastActiveAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsCompanyProfile(
    @DocumentId val id: String = "main",
    val userId: String = "",
    val googleEmail: String = "",
    val storeName: String = "",
    val storeRif: String = "",
    val storePhone: String = "",
    val storeAddress: String = "",
    val bcvRate: Double = 1.0,
    val minProfitMarginEnabled: Boolean = false,
    val minProfitMarginPercent: Double = 30.0,
    val cashRegisterCommissionEnabled: Boolean = false,
    val commissionEnabledRegisters: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "googleEmail" to googleEmail,
        "storeName" to storeName,
        "storeRif" to storeRif,
        "storePhone" to storePhone,
        "storeAddress" to storeAddress,
        "bcvRate" to bcvRate,
        "minProfitMarginEnabled" to minProfitMarginEnabled,
        "minProfitMarginPercent" to minProfitMarginPercent,
        "cashRegisterCommissionEnabled" to cashRegisterCommissionEnabled,
        "commissionEnabledRegisters" to commissionEnabledRegisters,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsProduct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val code: String = "",
    val name: String = "",
    val category: String = "General",
    val priceUsd: Double = 0.0,
    val stock: Double = 0.0,
    val costPriceUsd: Double = 0.0,
    val invoiceNumber: String = "",
    val taxExempt: Boolean = false,
    val hasCommission: Boolean = false,
    val commissionPercentage: Double = 0.0,
    val commissionAmountUsd: Double = 0.0,
    val recipe: Boolean = false,
    val combo: Boolean = false,
    val comboItemsJson: String = "",
    val allowDecimalQuantity: Boolean = false,
    val priceModifiable: Boolean = false,
    val wholesalePriceUsd: Double = 0.0,
    val aisleNumber: String = "",
    val aisleQuantity: Double = 0.0,
    val depotQuantity: Double = 0.0,
    val counterQuantity: Double = 0.0,
    val minStockAlertQuantity: Double = 5.0,
    val lowStockAlertEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "code" to code,
        "name" to name,
        "category" to category,
        "priceUsd" to priceUsd,
        "stock" to stock,
        "costPriceUsd" to costPriceUsd,
        "invoiceNumber" to invoiceNumber,
        "taxExempt" to taxExempt,
        "hasCommission" to hasCommission,
        "commissionPercentage" to commissionPercentage,
        "commissionAmountUsd" to commissionAmountUsd,
        "recipe" to recipe,
        "combo" to combo,
        "comboItemsJson" to comboItemsJson,
        "allowDecimalQuantity" to allowDecimalQuantity,
        "priceModifiable" to priceModifiable,
        "wholesalePriceUsd" to wholesalePriceUsd,
        "aisleNumber" to aisleNumber,
        "aisleQuantity" to aisleQuantity,
        "depotQuantity" to depotQuantity,
        "counterQuantity" to counterQuantity,
        "minStockAlertQuantity" to minStockAlertQuantity,
        "lowStockAlertEnabled" to lowStockAlertEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsSale(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerDocument: String = "",
    val timestampMs: Long = 0L,
    val rateBcv: Double = 1.0,
    val totalUsd: Double = 0.0,
    val totalBs: Double = 0.0,
    val paymentCashUsd: Double = 0.0,
    val paymentMobileBs: Double = 0.0,
    val paymentCardBs: Double = 0.0,
    val paymentCreditUsd: Double = 0.0,
    val paymentCasheaUsd: Double = 0.0,
    val creditPaid: Boolean = true,
    val creditPaidAmountUsd: Double = 0.0,
    val creditDueDate: Long = 0L,
    val creditNotes: String = "",
    val igtfUsd: Double = 0.0,
    val igtfBs: Double = 0.0,
    val baseTaxableUsd: Double = 0.0,
    val baseExemptUsd: Double = 0.0,
    val ivaUsd: Double = 0.0,
    val discountUsd: Double = 0.0,
    val cashierName: String = "",
    val cashRegisterNumber: String = "",
    val totalCommissionUsd: Double = 0.0,
    val retencionIvaUsd: Double = 0.0,
    val retencionIvaPercentage: Double = 0.0,
    val retencionIslrUsd: Double = 0.0,
    val retencionIslrPercentage: Double = 0.0,
    val withholdingVoucherNumber: String = "",
    val withholdingStatus: String = "NINGUNA",
    val deviceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "customerName" to customerName,
        "customerDocument" to customerDocument,
        "timestampMs" to timestampMs,
        "rateBcv" to rateBcv,
        "totalUsd" to totalUsd,
        "totalBs" to totalBs,
        "paymentCashUsd" to paymentCashUsd,
        "paymentMobileBs" to paymentMobileBs,
        "paymentCardBs" to paymentCardBs,
        "paymentCreditUsd" to paymentCreditUsd,
        "paymentCasheaUsd" to paymentCasheaUsd,
        "creditPaid" to creditPaid,
        "creditPaidAmountUsd" to creditPaidAmountUsd,
        "creditDueDate" to creditDueDate,
        "creditNotes" to creditNotes,
        "igtfUsd" to igtfUsd,
        "igtfBs" to igtfBs,
        "baseTaxableUsd" to baseTaxableUsd,
        "baseExemptUsd" to baseExemptUsd,
        "ivaUsd" to ivaUsd,
        "discountUsd" to discountUsd,
        "cashierName" to cashierName,
        "cashRegisterNumber" to cashRegisterNumber,
        "totalCommissionUsd" to totalCommissionUsd,
        "retencionIvaUsd" to retencionIvaUsd,
        "retencionIvaPercentage" to retencionIvaPercentage,
        "retencionIslrUsd" to retencionIslrUsd,
        "retencionIslrPercentage" to retencionIslrPercentage,
        "withholdingVoucherNumber" to withholdingVoucherNumber,
        "withholdingStatus" to withholdingStatus,
        "deviceId" to deviceId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsSaleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val saleCloudId: String = "",
    val productCloudId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val priceUsd: Double = 0.0,
    val taxExempt: Boolean = false,
    val commissionUsd: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "saleCloudId" to saleCloudId,
        "productCloudId" to productCloudId,
        "productName" to productName,
        "quantity" to quantity,
        "priceUsd" to priceUsd,
        "taxExempt" to taxExempt,
        "commissionUsd" to commissionUsd,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsCustomer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val documentId: String = "",
    val address: String = "",
    val phone: String = "",
    val clientType: String = "ORDINARIO",
    val withholdingAgent: Boolean = false,
    val ivaRetentionPercentage: Double = 0.0,
    val islrRetentionPercentage: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name,
        "documentId" to documentId,
        "address" to address,
        "phone" to phone,
        "clientType" to clientType,
        "withholdingAgent" to withholdingAgent,
        "ivaRetentionPercentage" to ivaRetentionPercentage,
        "islrRetentionPercentage" to islrRetentionPercentage,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsQuote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val quoteNumber: String = "",
    val customerName: String = "",
    val customerDocument: String = "",
    val customerPhone: String = "",
    val timestampMs: Long = 0L,
    val validityDays: Int = 15,
    val validUntil: Long = 0L,
    val rateBcv: Double = 1.0,
    val baseTaxableUsd: Double = 0.0,
    val baseExemptUsd: Double = 0.0,
    val ivaUsd: Double = 0.0,
    val discountUsd: Double = 0.0,
    val totalUsd: Double = 0.0,
    val totalBs: Double = 0.0,
    val notesOrConditions: String = "",
    val status: String = "VIGENTE",
    val cashierName: String = "",
    val cashRegisterNumber: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "quoteNumber" to quoteNumber,
        "customerName" to customerName,
        "customerDocument" to customerDocument,
        "customerPhone" to customerPhone,
        "timestampMs" to timestampMs,
        "validityDays" to validityDays,
        "validUntil" to validUntil,
        "rateBcv" to rateBcv,
        "baseTaxableUsd" to baseTaxableUsd,
        "baseExemptUsd" to baseExemptUsd,
        "ivaUsd" to ivaUsd,
        "discountUsd" to discountUsd,
        "totalUsd" to totalUsd,
        "totalBs" to totalBs,
        "notesOrConditions" to notesOrConditions,
        "status" to status,
        "cashierName" to cashierName,
        "cashRegisterNumber" to cashRegisterNumber,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsQuoteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val quoteCloudId: String = "",
    val productCloudId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val priceUsd: Double = 0.0,
    val taxExempt: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "quoteCloudId" to quoteCloudId,
        "productCloudId" to productCloudId,
        "productName" to productName,
        "quantity" to quantity,
        "priceUsd" to priceUsd,
        "taxExempt" to taxExempt,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsDeliveryNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val noteNumber: String = "",
    val customerName: String = "",
    val customerDocument: String = "",
    val recipientName: String = "",
    val reasonOrObservation: String = "",
    val timestampMs: Long = 0L,
    val totalEstimatedUsd: Double = 0.0,
    val totalEstimatedBs: Double = 0.0,
    val rateBcv: Double = 1.0,
    val status: String = "ENTREGADO",
    val cashierName: String = "",
    val cashRegisterNumber: String = "",
    val displayCurrency: String = "USD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "noteNumber" to noteNumber,
        "customerName" to customerName,
        "customerDocument" to customerDocument,
        "recipientName" to recipientName,
        "reasonOrObservation" to reasonOrObservation,
        "timestampMs" to timestampMs,
        "totalEstimatedUsd" to totalEstimatedUsd,
        "totalEstimatedBs" to totalEstimatedBs,
        "rateBcv" to rateBcv,
        "status" to status,
        "cashierName" to cashierName,
        "cashRegisterNumber" to cashRegisterNumber,
        "displayCurrency" to displayCurrency,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsDeliveryNoteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deliveryNoteCloudId: String = "",
    val productCloudId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val priceUsd: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "deliveryNoteCloudId" to deliveryNoteCloudId,
        "productCloudId" to productCloudId,
        "productName" to productName,
        "quantity" to quantity,
        "priceUsd" to priceUsd,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsExpense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "",
    val invoiceNumber: String = "",
    val amountUsd: Double = 0.0,
    val amountBs: Double = 0.0,
    val bcvRateUsed: Double = 1.0,
    val paymentMethod: String = "",
    val description: String = "",
    val timestampMs: Long = 0L,
    val registeredBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "category" to category,
        "invoiceNumber" to invoiceNumber,
        "amountUsd" to amountUsd,
        "amountBs" to amountBs,
        "bcvRateUsed" to bcvRateUsed,
        "paymentMethod" to paymentMethod,
        "description" to description,
        "timestampMs" to timestampMs,
        "registeredBy" to registeredBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsSupplier(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val documentId: String = "",
    val phone: String = "",
    val address: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name,
        "documentId" to documentId,
        "phone" to phone,
        "address" to address,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsPurchaseInvoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val supplierCloudId: String = "",
    val invoiceNumber: String = "",
    val timestampMs: Long = 0L,
    val totalAmount: Double = 0.0,
    val description: String = "",
    val paid: Boolean = false,
    val paidTimestamp: Long = 0L,
    val dueDate: Long = 0L,
    val paymentNotes: String = "",
    val subtotalUsd: Double = 0.0,
    val taxAmountUsd: Double = 0.0,
    val retencionIvaUsd: Double = 0.0,
    val retencionIvaPercentage: Double = 0.0,
    val retencionIslrUsd: Double = 0.0,
    val retencionIslrPercentage: Double = 0.0,
    val withholdingVoucherNumber: String = "",
    val withholdingStatus: String = "NO_APLICA",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "supplierCloudId" to supplierCloudId,
        "invoiceNumber" to invoiceNumber,
        "timestampMs" to timestampMs,
        "totalAmount" to totalAmount,
        "description" to description,
        "paid" to paid,
        "paidTimestamp" to paidTimestamp,
        "dueDate" to dueDate,
        "paymentNotes" to paymentNotes,
        "subtotalUsd" to subtotalUsd,
        "taxAmountUsd" to taxAmountUsd,
        "retencionIvaUsd" to retencionIvaUsd,
        "retencionIvaPercentage" to retencionIvaPercentage,
        "retencionIslrUsd" to retencionIslrUsd,
        "retencionIslrPercentage" to retencionIslrPercentage,
        "withholdingVoucherNumber" to withholdingVoucherNumber,
        "withholdingStatus" to withholdingStatus,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsPosUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val pin: String = "",
    val role: String = "CAJERO",
    val avatarUrl: String = "",
    val earnCommission: Boolean = true,
    val allowedModules: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "username" to username,
        "pin" to pin,
        "role" to role,
        "avatarUrl" to avatarUrl,
        "earnCommission" to earnCommission,
        "allowedModules" to allowedModules,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceModel: String = "",
    val googleEmail: String = "",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "deviceModel" to deviceModel,
        "googleEmail" to googleEmail,
        "lastActiveAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FsEncryptedBackup(
    @DocumentId val id: String = "latest",
    val userId: String = "",
    val googleEmail: String = "",
    val storeName: String = "",
    val storeRif: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val formatVersion: Int = 2,
    val productsCount: Int = 0,
    val salesCount: Int = 0,
    val customersCount: Int = 0,
    val exportedAtMs: Long = 0L,
    val encryptedPayload: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "googleEmail" to googleEmail.take(200),
        "storeName" to storeName.take(120),
        "storeRif" to storeRif.take(40),
        "deviceId" to deviceId.take(128),
        "deviceModel" to deviceModel.take(120),
        "formatVersion" to formatVersion,
        "productsCount" to productsCount,
        "salesCount" to salesCount,
        "customersCount" to customersCount,
        "exportedAtMs" to exportedAtMs,
        "encryptedPayload" to encryptedPayload,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "googleEmail" to googleEmail.take(200),
        "storeName" to storeName.take(120),
        "storeRif" to storeRif.take(40),
        "deviceId" to deviceId.take(128),
        "deviceModel" to deviceModel.take(120),
        "formatVersion" to formatVersion,
        "productsCount" to productsCount,
        "salesCount" to salesCount,
        "customersCount" to customersCount,
        "exportedAtMs" to exportedAtMs,
        "encryptedPayload" to encryptedPayload,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
