package com.netlocker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.netlocker.data.local.dao.AppRuleDao
import com.netlocker.data.local.dao.BlockedStatDao
import com.netlocker.data.local.entity.AppRuleEntity
import com.netlocker.data.local.entity.BlockedStatEntity

@Database(entities = [AppRuleEntity::class, BlockedStatEntity::class], version = 5, exportSchema = true)
abstract class NetLockerDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun blockedStatDao(): BlockedStatDao

    companion object {
        @Volatile private var instance: NetLockerDatabase? = null

        /**
         * v1 -> v2: adds `isEnabled` (existing rules stay enabled, i.e. keep being
         * enforced exactly as before) and `createdAt` (backfilled from `updatedAt`, the
         * best information a v1 row has). Existing users' saved rules are preserved —
         * there is deliberately no destructive-migration fallback: silently wiping the
         * rules of a firewall would be a security regression.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_network_rules ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_network_rules ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE app_network_rules SET createdAt = updatedAt")
            }
        }

        /** v2 -> v3: adds the per-day blocked-attempt counters. A new table only — the
         *  saved rules table is not touched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blocked_stats (" +
                        "packageName TEXT NOT NULL, day INTEGER NOT NULL, count INTEGER NOT NULL, " +
                        "lastBlockedAt INTEGER NOT NULL, PRIMARY KEY(packageName, day))",
                )
            }
        }

        /** v3 -> v4: adds the optional per-rule "block during this time window" schedule
         *  (off for every existing row, so nothing changes for current users). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_network_rules ADD COLUMN scheduleEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_network_rules ADD COLUMN scheduleStartMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_network_rules ADD COLUMN scheduleEndMinute INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 -> v5: adds which days of the week the schedule applies to. Existing
         *  schedules default to every day (127 = all 7 day-bits set), i.e. exactly what
         *  they already did before per-day selection existed — no behaviour change. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_network_rules ADD COLUMN scheduleDays INTEGER NOT NULL DEFAULT 127")
            }
        }

        fun getInstance(context: Context): NetLockerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetLockerDatabase::class.java,
                    "netlocker.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
