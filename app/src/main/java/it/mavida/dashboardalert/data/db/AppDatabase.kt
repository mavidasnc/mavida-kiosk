package it.mavida.dashboardalert.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MonitorEntity::class,
        RuleEntity::class,
        TriggerEntity::class,
        LogEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun monitorDao(): MonitorDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dashboard_alert.db",
                ).build().also { instance = it }
            }
    }
}
