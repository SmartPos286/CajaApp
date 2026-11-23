package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PosTerminalRecordDao {
    @Query("SELECT * FROM pos_terminal_records ORDER BY transactionTimestamp DESC, id DESC")
    fun getAllRecords(): Flow<List<PosTerminalRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PosTerminalRecord): Long

    @Update
    suspend fun updateRecord(record: PosTerminalRecord)

    @Delete
    suspend fun deleteRecord(record: PosTerminalRecord)

    @Query("DELETE FROM pos_terminal_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)
}
