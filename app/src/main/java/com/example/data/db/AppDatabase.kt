package com.example.data.db
import android.content.Context
import androidx.room.*
@Database(entities = [ScanLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "routerscan_database")
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
