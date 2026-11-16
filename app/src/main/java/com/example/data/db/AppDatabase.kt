package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HeroProfileEntity::class,
        InventoryItemEntity::class,
        ComboSlotEntity::class,
        TalentRankEntity::class,
        ZoneProgressEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heroDao(): HeroDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun comboDao(): ComboDao
    abstract fun talentDao(): TalentDao
    abstract fun zoneDao(): ZoneDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ironbound_rpg.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
