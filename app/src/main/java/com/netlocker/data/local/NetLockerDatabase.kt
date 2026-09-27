package com.netlocker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.netlocker.data.local.dao.AppRuleDao
import com.netlocker.data.local.entity.AppRuleEntity

@Database(entities = [AppRuleEntity::class], version = 1, exportSchema = true)
abstract class NetLockerDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao

    companion object {
        @Volatile private var instance: NetLockerDatabase? = null

        fun getInstance(context: Context): NetLockerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetLockerDatabase::class.java,
                    "netlocker.db",
                ).build().also { instance = it }
            }
    }
}
