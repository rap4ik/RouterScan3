package com.example.data.db
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface ScanLogDao {
    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ScanLog>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScanLog)
    @Query("DELETE FROM scan_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
    @Query("DELETE FROM scan_logs")
    suspend fun clearAllLogs()
}
