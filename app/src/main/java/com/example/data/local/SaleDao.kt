package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Update
    suspend fun updateSale(sale: Sale)

    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: Int): Sale?

    @Query("SELECT * FROM sales WHERE paymentCreditUsd > 0 ORDER BY timestamp DESC")
    fun getCreditSales(): Flow<List<Sale>>

    @Query("UPDATE sales SET isCreditPaid = :isPaid, creditPaidAmountUsd = :paidAmount, creditNotes = :notes WHERE id = :saleId")
    suspend fun updateCreditPayment(saleId: Int, isPaid: Boolean, paidAmount: Double, notes: String = "")

    @Query("UPDATE sales SET withholdingVoucherNumber = :voucherNumber, withholdingStatus = :status WHERE id = :saleId")
    suspend fun updateWithholding(saleId: Int, voucherNumber: String, status: String)

    @Query("SELECT * FROM sale_items")
    fun getAllSaleItems(): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE saleId IN (:saleIds)")
    fun getItemsForSales(saleIds: List<Int>): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getItemsForSale(saleId: Int): Flow<List<SaleItem>>
}
