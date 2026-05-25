package com.example.data.db
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "scan_logs")
data class ScanLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val logType: String,
    val target: String,
    val results: String,
    val payload: String = ""
)
