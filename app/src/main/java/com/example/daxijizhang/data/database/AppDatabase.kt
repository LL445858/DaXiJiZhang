package com.example.daxijizhang.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.daxijizhang.data.dao.BillDao
import com.example.daxijizhang.data.dao.BillItemDao
import com.example.daxijizhang.data.dao.PaymentRecordDao
import com.example.daxijizhang.data.dao.ProjectDictionaryDao
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.model.ProjectDictionary
import com.example.daxijizhang.data.util.DateConverter

@Database(
    entities = [Bill::class, BillItem::class, PaymentRecord::class, ProjectDictionary::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao
    abstract fun paymentRecordDao(): PaymentRecordDao
    abstract fun projectDictionaryDao(): ProjectDictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daxijizhang_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // 从版本2迁移到版本3：添加waivedAmount字段到bills表
        private val MIGRATION_2_3 = androidx.room.migration.Migration(2, 3) { database ->
            database.execSQL("ALTER TABLE bills ADD COLUMN waivedAmount REAL NOT NULL DEFAULT 0.0")
        }

        // 从版本3迁移到版本4：添加项目词典表
        private val MIGRATION_3_4 = androidx.room.migration.Migration(3, 4) { database ->
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
        }
    }
}
