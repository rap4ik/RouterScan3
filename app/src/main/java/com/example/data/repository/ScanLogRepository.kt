package com.example.data.repository
import com.example.data.db.ScanLog
import com.example.data.db.ScanLogDao
import kotlinx.coroutines.flow.Flow
class ScanLogRepository(private val dao: ScanLogDao) {
    val allLogs: Flow<List<ScanLog>> = dao.getAllLogs()
    suspend fun insert(log: ScanLog) = dao.insertLog(log)
    suspend fun deleteById(id: Long) = dao.deleteLogById(id)
    suspend fun clearAll() = dao.clearAllLogs()
}
