package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseInvoiceDao {
    @Insert
    suspend fun insertInvoice(invoice: PurchaseInvoice)

    @Update
    suspend fun updateInvoice(invoice: PurchaseInvoice)

    @Query("SELECT * FROM purchase_invoices ORDER BY timestamp DESC")
    fun getAllInvoices(): Flow<List<PurchaseInvoice>>

    @Query("UPDATE purchase_invoices SET isPaid = :isPaid, paidTimestamp = :paidTimestamp, paymentNotes = :notes WHERE id = :id")
    suspend fun updatePaymentStatus(id: Int, isPaid: Boolean, paidTimestamp: Long, notes: String = "")

    @Query("UPDATE purchase_invoices SET withholdingVoucherNumber = :voucherNumber, withholdingStatus = :status WHERE id = :id")
    suspend fun updateWithholding(id: Int, voucherNumber: String, status: String)

    @Query("DELETE FROM purchase_invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Int)
}
