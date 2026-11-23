package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CasheaSettlementDao {
    @Query("SELECT * FROM cashea_settlements ORDER BY settlementDate DESC, createdAt DESC")
    fun getAllSettlements(): Flow<List<CasheaSettlement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: CasheaSettlement): Long

    @Delete
    suspend fun deleteSettlement(settlement: CasheaSettlement)

    @Query("DELETE FROM cashea_settlements WHERE id = :id")
    suspend fun deleteSettlementById(id: Int)
}
