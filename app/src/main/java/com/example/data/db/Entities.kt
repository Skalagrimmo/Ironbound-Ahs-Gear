package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hero_profiles")
data class HeroProfileEntity(
    @PrimaryKey val heroClass: String,
    val level: Int,
    val xp: Int,
    val scrapCurrency: Int,
    val talentPointsAvailable: Int,
    val killsTotal: Int
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val heroClass: String,
    val titleKey: String,
    val slot: String,
    val rarity: String,
    val itemLevel: Int,
    val bonusDamage: Int,
    val bonusArmor: Int,
    val bonusHp: Int,
    val bonusCritPct: Int,
    val bonusLifestealPct: Int,
    val bonusCooldownHastePct: Int,
    val isEquipped: Boolean,
    val salvageScrap: Int
)

@Entity(tableName = "combo_slots", primaryKeys = ["heroClass", "slotIndex"])
data class ComboSlotEntity(
    val heroClass: String,
    val slotIndex: Int,
    val pressenType: String,
    val tier: String,
    val unlocked: Boolean
)

@Entity(tableName = "talent_ranks", primaryKeys = ["heroClass", "nodeId"])
data class TalentRankEntity(
    val heroClass: String,
    val nodeId: String,
    val rank: Int
)

@Entity(tableName = "zone_progress")
data class ZoneProgressEntity(
    @PrimaryKey val zoneId: String,
    val unlocked: Boolean,
    val clearedCount: Int
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val language: String,
    val svdagPreset: String,
    val showTelemetry: Boolean,
    val hapticsEnabled: Boolean,
    val selectedHeroClass: String
)
