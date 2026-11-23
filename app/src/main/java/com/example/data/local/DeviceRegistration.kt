package com.example.data.local

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistration(
    val deviceId: String,
    val googleAccount: String,
    val deviceModel: String,
    val storeName: String,
    val storeRif: String,
    val registeredAt: Long,
    val lastSyncAt: Long
)
