package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Insert
    suspend fun insertQuote(quote: Quote): Long

    @Insert
    suspend fun insertQuoteItems(items: List<QuoteItem>)

    @Query("SELECT * FROM quotes ORDER BY timestamp DESC")
    fun getAllQuotes(): Flow<List<Quote>>

    @Query("SELECT * FROM quote_items WHERE quoteId = :quoteId")
    fun getItemsForQuote(quoteId: Int): Flow<List<QuoteItem>>

    @Query("SELECT * FROM quote_items WHERE quoteId = :quoteId")
    suspend fun getItemsForQuoteDirect(quoteId: Int): List<QuoteItem>

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun getQuotesCount(): Int

    @Query("UPDATE quotes SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Int, newStatus: String)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: Int)
}
