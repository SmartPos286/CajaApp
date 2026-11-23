package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryNoteDao {
    @Insert
    suspend fun insertDeliveryNote(note: DeliveryNote): Long

    @Insert
    suspend fun insertDeliveryNoteItems(items: List<DeliveryNoteItem>)

    @Query("SELECT * FROM delivery_notes ORDER BY timestamp DESC")
    fun getAllDeliveryNotes(): Flow<List<DeliveryNote>>

    @Query("SELECT * FROM delivery_note_items WHERE deliveryNoteId = :noteId")
    fun getItemsForDeliveryNote(noteId: Int): Flow<List<DeliveryNoteItem>>

    @Query("SELECT * FROM delivery_note_items WHERE deliveryNoteId = :noteId")
    suspend fun getItemsForDeliveryNoteDirect(noteId: Int): List<DeliveryNoteItem>

    @Query("SELECT COUNT(*) FROM delivery_notes")
    suspend fun getDeliveryNotesCount(): Int

    @Query("UPDATE delivery_notes SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Int, newStatus: String)

    @Query("DELETE FROM delivery_notes WHERE id = :id")
    suspend fun deleteDeliveryNote(id: Int)
}
