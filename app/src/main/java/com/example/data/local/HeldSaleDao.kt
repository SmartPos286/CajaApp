package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HeldSaleDao {
    @Insert
    suspend fun insertHeldSale(heldSale: HeldSale): Long

    @Insert
    suspend fun insertHeldSaleItems(items: List<HeldSaleItem>)

    @Query("SELECT * FROM held_sales ORDER BY timestamp DESC")
    fun getAllHeldSales(): Flow<List<HeldSale>>

    @Query("SELECT * FROM held_sale_items WHERE heldSaleId = :heldSaleId")
    suspend fun getItemsForHeldSaleDirect(heldSaleId: Int): List<HeldSaleItem>

    @Query("SELECT * FROM held_sale_items WHERE heldSaleId = :heldSaleId")
    fun getItemsForHeldSale(heldSaleId: Int): Flow<List<HeldSaleItem>>

    @Query("DELETE FROM held_sales WHERE id = :id")
    suspend fun deleteHeldSale(id: Int)

    @Query("DELETE FROM held_sale_items WHERE heldSaleId = :heldSaleId")
    suspend fun deleteHeldSaleItems(heldSaleId: Int)

    @Transaction
    suspend fun deleteHeldSaleWithItems(heldSaleId: Int) {
        deleteHeldSaleItems(heldSaleId)
        deleteHeldSale(heldSaleId)
    }
}
