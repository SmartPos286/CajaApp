package com.example.data.local

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class UserLicenseRecord(
    val googleAccount: String,
    val licenseKey: String = "12345678",
    val activatedAtIso: String = "",
    val expirationDateIso: String = "",
    val expiryTimestamp: Long = 0L,
    val daysRemaining: Int = 0,
    val status: String = "ACTIVE", // "ACTIVE", "EXPIRED", "LIMITED"
    val storeName: String = "",
    val storeRif: String = "",
    val deviceModel: String = "",
    val lastCheckedAt: Long = System.currentTimeMillis()
)
