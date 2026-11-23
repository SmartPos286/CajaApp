package com.example.data.local

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey

@Serializable
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val pin: String,
    val role: String, // "ADMIN", "SUPERVISOR", "CASHIER"
    val avatarUrl: String = "",
    val canEarnCommission: Boolean = true,
    val allowedModules: String = ""
)
