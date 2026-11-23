package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)

    var storeName: String
        get() = prefs.getString("storeName", "Mi Tienda") ?: "Mi Tienda"
        set(value) = prefs.edit().putString("storeName", value).apply()

    var storeRif: String
        get() = prefs.getString("storeRif", "J-00000000-0") ?: "J-00000000-0"
        set(value) = prefs.edit().putString("storeRif", value).apply()

    var storeAddress: String
        get() = prefs.getString("storeAddress", "Dirección") ?: "Dirección"
        set(value) = prefs.edit().putString("storeAddress", value).apply()

    var storePhone: String
        get() = prefs.getString("storePhone", "0000-0000000") ?: "0000-0000000"
        set(value) = prefs.edit().putString("storePhone", value).apply()

    var storeLogoPath: String
        get() = prefs.getString("storeLogoPath", "") ?: ""
        set(value) = prefs.edit().putString("storeLogoPath", value).apply()

    var cashRegisterNumber: String
        get() = prefs.getString("cashRegisterNumber", "01") ?: "01"
        set(value) = prefs.edit().putString("cashRegisterNumber", value).apply()

    var isCashRegisterCommissionEnabled: Boolean
        get() = prefs.getBoolean("isCashRegisterCommissionEnabled", true)
        set(value) = prefs.edit().putBoolean("isCashRegisterCommissionEnabled", value).apply()

    var commissionEnabledRegisters: String
        get() = prefs.getString("commissionEnabledRegisters", "") ?: ""
        set(value) = prefs.edit().putString("commissionEnabledRegisters", value).apply()

    var paymentMobileData: String
        get() = prefs.getString("paymentMobileData", "Banco / Telf / Cédula") ?: "Banco / Telf / Cédula"
        set(value) = prefs.edit().putString("paymentMobileData", value).apply()

    var lastBcvRate: Float
        get() = prefs.getFloat("lastBcvRate", 1.0f)
        set(value) = prefs.edit().putFloat("lastBcvRate", value).apply()

    var appTheme: String
        get() = prefs.getString("appTheme", "DARK") ?: "DARK"
        set(value) = prefs.edit().putString("appTheme", value).apply()

    var backupMode: String
        get() = prefs.getString("backupMode", "") ?: ""
        set(value) = prefs.edit().putString("backupMode", value).apply()

    var googleAccount: String
        get() = prefs.getString("googleAccount", "") ?: ""
        set(value) = prefs.edit().putString("googleAccount", value).apply()

    // --- COMPANY PROFILE (REGISTRO ÚNICO) ---

    var isCompanyRegistered: Boolean
        get() = prefs.getBoolean("isCompanyRegistered", false)
        set(value) = prefs.edit().putBoolean("isCompanyRegistered", value).apply()

    var companyGoogleAccount: String
        get() = prefs.getString("companyGoogleAccount", "") ?: ""
        set(value) = prefs.edit().putString("companyGoogleAccount", value).apply()

    var companyRegisteredAt: Long
        get() = prefs.getLong("companyRegisteredAt", 0L)
        set(value) = prefs.edit().putLong("companyRegisteredAt", value).apply()

    // --- LICENSE AND USAGE LIMITS ---

    var isLicensed: Boolean
        get() = prefs.getBoolean("isLicensed", false)
        set(value) = prefs.edit().putBoolean("isLicensed", value).apply()

    var licenseKey: String
        get() = prefs.getString("licenseKey", "") ?: ""
        set(value) = prefs.edit().putString("licenseKey", value).apply()

    var licenseAdminUser: String
        get() = prefs.getString("licenseAdminUser", "") ?: ""
        set(value) = prefs.edit().putString("licenseAdminUser", value).apply()

    var licenseGoogleAccount: String
        get() = prefs.getString("licenseGoogleAccount", "") ?: ""
        set(value) = prefs.edit().putString("licenseGoogleAccount", value).apply()

    var licenseActivatedAt: Long
        get() = prefs.getLong("licenseActivatedAt", 0L)
        set(value) = prefs.edit().putLong("licenseActivatedAt", value).apply()

    var isUnlimitedInventory: Boolean
        get() = prefs.getBoolean("isUnlimitedInventory", true)
        set(value) = prefs.edit().putBoolean("isUnlimitedInventory", value).apply()

    var customProductLimit: Int
        get() = prefs.getInt("customProductLimit", 50)
        set(value) = prefs.edit().putInt("customProductLimit", value).apply()

    var isUnlimitedDailyInvoices: Boolean
        get() = prefs.getBoolean("isUnlimitedDailyInvoices", true)
        set(value) = prefs.edit().putBoolean("isUnlimitedDailyInvoices", value).apply()

    var customDailyInvoiceLimit: Int
        get() = prefs.getInt("customDailyInvoiceLimit", 30)
        set(value) = prefs.edit().putInt("customDailyInvoiceLimit", value).apply()

    var isFullTimeLicense: Boolean
        get() = prefs.getBoolean("isFullTimeLicense", false)
        set(value) = prefs.edit().putBoolean("isFullTimeLicense", value).apply()

    var hasUsedTrialCode12345678: Boolean
        get() = prefs.getBoolean("hasUsedTrialCode12345678", false)
        set(value) = prefs.edit().putBoolean("hasUsedTrialCode12345678", value).apply()

    var licenseExpiryTimestamp: Long
        get() = prefs.getLong("licenseExpiryTimestamp", 0L)
        set(value) = prefs.edit().putLong("licenseExpiryTimestamp", value).apply()

    var licenseStatus: String
        get() = prefs.getString("licenseStatus", "LIMITED") ?: "LIMITED"
        set(value) = prefs.edit().putString("licenseStatus", value).apply()

    var lastLicenseServerCheck: Long
        get() = prefs.getLong("lastLicenseServerCheck", 0L)
        set(value) = prefs.edit().putLong("lastLicenseServerCheck", value).apply()

    // --- BLUETOOTH & USB THERMAL PRINTER ---

    var printerConnectionType: String
        get() = prefs.getString("printerConnectionType", "BLE_FUNPRINT") ?: "BLE_FUNPRINT"
        set(value) = prefs.edit().putString("printerConnectionType", value).apply()

    var printerPaperWidth: String
        get() = prefs.getString("printerPaperWidth", "58mm") ?: "58mm"
        set(value) = prefs.edit().putString("printerPaperWidth", value).apply()

    var printerMacAddress: String
        get() = prefs.getString("printerMacAddress", "") ?: ""
        set(value) = prefs.edit().putString("printerMacAddress", value).apply()

    var printerName: String
        get() = prefs.getString("printerName", "") ?: ""
        set(value) = prefs.edit().putString("printerName", value).apply()

    var printerUsbVendorId: Int
        get() = prefs.getInt("printerUsbVendorId", -1)
        set(value) = prefs.edit().putInt("printerUsbVendorId", value).apply()

    var printerUsbProductId: Int
        get() = prefs.getInt("printerUsbProductId", -1)
        set(value) = prefs.edit().putInt("printerUsbProductId", value).apply()

    var printerUsbDeviceName: String
        get() = prefs.getString("printerUsbDeviceName", "") ?: ""
        set(value) = prefs.edit().putString("printerUsbDeviceName", value).apply()

    // --- FISCAL PRINTER SETTINGS (VENEZUELA: The Factory HKA / Bixolon / Dascom) ---

    var isFiscalPrinter: Boolean
        get() = prefs.getBoolean("isFiscalPrinter", false)
        set(value) = prefs.edit().putBoolean("isFiscalPrinter", value).apply()

    var fiscalPrinterBrand: String
        get() = prefs.getString("fiscalPrinterBrand", "The Factory HKA") ?: "The Factory HKA"
        set(value) = prefs.edit().putString("fiscalPrinterBrand", value).apply()

    var nextFiscalInvoiceNumber: Int
        get() = prefs.getInt("nextFiscalInvoiceNumber", 1)
        set(value) = prefs.edit().putInt("nextFiscalInvoiceNumber", value).apply()

    // --- DEVICE REGISTRATION & PERIODIC BACKUP ---

    var deviceUniqueId: String
        get() {
            var id = prefs.getString("deviceUniqueId", "") ?: ""
            if (id.isBlank()) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("deviceUniqueId", id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString("deviceUniqueId", value).apply()

    var lastPartialBackupTimestamp: Long
        get() = prefs.getLong("lastPartialBackupTimestamp", 0L)
        set(value) = prefs.edit().putLong("lastPartialBackupTimestamp", value).apply()

    var lastFirebaseEncryptedBackupTimestamp: Long
        get() = prefs.getLong("lastFirebaseEncryptedBackupTimestamp", 0L)
        set(value) = prefs.edit().putLong("lastFirebaseEncryptedBackupTimestamp", value).apply()

    var lastFirebaseEncryptedBackupSummary: String
        get() = prefs.getString("lastFirebaseEncryptedBackupSummary", "") ?: ""
        set(value) = prefs.edit().putString("lastFirebaseEncryptedBackupSummary", value).apply()

    var isForceLiveStreaming: Boolean
        get() = prefs.getBoolean("isForceLiveStreaming", true)
        set(value) = prefs.edit().putBoolean("isForceLiveStreaming", value).apply()

    // --- GEMINI AI ASSISTANT SETTINGS ---
    var isGeminiLearningAiEnabled: Boolean
        get() = prefs.getBoolean("isGeminiLearningAiEnabled", false)
        set(value) = prefs.edit().putBoolean("isGeminiLearningAiEnabled", value).apply()

    var geminiCustomApiKey: String
        get() = prefs.getString("geminiCustomApiKey", "") ?: ""
        set(value) = prefs.edit().putString("geminiCustomApiKey", value).apply()

    var geminiSelectedModel: String
        get() {
            val saved = prefs.getString("geminiSelectedModel", "gemini-3.5-flash-lite") ?: "gemini-3.5-flash-lite"
            return if (saved == "gemini-3.5-flash" || saved == "gemini-2.5-flash" || saved.isBlank()) {
                "gemini-3.5-flash-lite"
            } else {
                saved
            }
        }
        set(value) = prefs.edit().putString("geminiSelectedModel", value).apply()

    // --- SUPABASE / CLOUD NETWORK CREDENTIALS ---
    var cloudUsername: String
        get() = prefs.getString("cloudUsername", "") ?: ""
        set(value) = prefs.edit().putString("cloudUsername", value).apply()

    var cloudPassword: String
        get() = prefs.getString("cloudPassword", "") ?: ""
        set(value) = prefs.edit().putString("cloudPassword", value).apply()

    var isCloudConnected: Boolean
        get() = prefs.getBoolean("isCloudConnected", false)
        set(value) = prefs.edit().putBoolean("isCloudConnected", value).apply()

    // --- AUTOMATIC MINIMUM PROFIT MARGIN (OPTIONAL) ---
    var isMinProfitMarginEnabled: Boolean
        get() = prefs.getBoolean("isMinProfitMarginEnabled", false)
        set(value) = prefs.edit().putBoolean("isMinProfitMarginEnabled", value).apply()

    var minProfitMarginPercent: Float
        get() = prefs.getFloat("minProfitMarginPercent", 30.0f)
        set(value) = prefs.edit().putFloat("minProfitMarginPercent", value).apply()
}
