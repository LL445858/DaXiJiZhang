package com.example.daxijizhang.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.daxijizhang.data.dao.BillDao
import com.example.daxijizhang.data.dao.BillItemDao
import com.example.daxijizhang.data.dao.PaymentRecordDao
import com.example.daxijizhang.data.dao.ProjectDictionaryDao
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.model.ProjectDictionary
import com.example.daxijizhang.data.util.DateConverter
import java.util.concurrent.Executors

@Database(
    entities = [Bill::class, BillItem::class, PaymentRecord::class, ProjectDictionary::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao
    abstract fun paymentRecordDao(): PaymentRecordDao
    abstract fun projectDictionaryDao(): ProjectDictionaryDao

    companion object {
        private const val TAG = "AppDatabase"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            Log.i(TAG, "Building database instance")
            
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "daxijizhang_database"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(DatabaseCallback())
                .setJournalMode(JournalMode.TRUNCATE)
                .setQueryExecutor(Executors.newFixedThreadPool(4))
                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .fallbackToDestructiveMigration()
                .build()
                .also { 
                    Log.i(TAG, "Database built successfully")
                }
        }
        
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.i(TAG, "Database created")
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL("PRAGMA journal_mode = TRUNCATE")
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    Log.i(TAG, "Database opened with optimizations")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting database pragmas", e)
                }
            }
        }

        private val MIGRATION_2_3 = androidx.room.migration.Migration(2, 3) { database ->
            Log.i(TAG, "Running migration from version 2 to 3")
            try {
                database.execSQL("ALTER TABLE bills ADD COLUMN waivedAmount REAL NOT NULL DEFAULT 0.0")
            } catch (e: Exception) {
                Log.e(TAG, "Migration 2->3 error (column may already exist)", e)
            }
        }

        private val MIGRATION_3_4 = androidx.room.migration.Migration(3, 4) { database ->
            Log.i(TAG, "Running migration from version 3 to 4")
            try {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS project_dictionary (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        createTime INTEGER NOT NULL DEFAULT 0,
                        updateTime INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_dictionary_name ON project_dictionary(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_dictionary_usageCount ON project_dictionary(usageCount)")
            } catch (e: Exception) {
                Log.e(TAG, "Migration 3->4 error (table may already exist)", e)
            }
        }

        private val MIGRATION_4_5 = androidx.room.migration.Migration(4, 5) { database ->
            Log.i(TAG, "Running migration from version 4 to 5")
            try {
                database.execSQL("ALTER TABLE bill_items ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                Log.e(TAG, "Migration 4->5 error (column may already exist)", e)
            }
        }
        
        fun clearInstance() {
            INSTANCE = null
            Log.i(TAG, "Database instance cleared")
        }
    }
}
