package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class BcvRateResponse(
    val current: CurrentRate?
)

@JsonClass(generateAdapter = true)
data class CurrentRate(
    val usd: Double?
)

interface DolarVzlaService {
    @GET("bcv/current.json")
    suspend fun getCurrentRate(): BcvRateResponse
}
