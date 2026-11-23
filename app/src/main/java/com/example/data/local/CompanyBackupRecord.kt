package com.example.data.local

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class AccountFullBackup(
    val googleAccount: String = "",
    val storeName: String = "",
    val storeRif: String = "",
    val storePhone: String = "",
    val storeAddress: String = "",
    val companyRegisteredAt: Long = 0L,
    val isCompanyRegistered: Boolean = false,
    val lastBackupTimestamp: Long = System.currentTimeMillis(),
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val saleItems: List<SaleItem> = emptyList(),
    val purchaseInvoices: List<PurchaseInvoice> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val users: List<User> = emptyList(),
    val posTerminalRecords: List<PosTerminalRecord> = emptyList()
)

@Serializable
@JsonClass(generateAdapter = true)
data class CompanyBackupRecord(
    val googleAccount: String = "",
    val storeName: String = "",
    val storeRif: String = "",
    val backupJson: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
