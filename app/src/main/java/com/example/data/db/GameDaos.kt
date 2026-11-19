package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HeroDao {
    @Query("SELECT * FROM hero_profiles")
    fun observeAllHeroes(): Flow<List<HeroProfileEntity>>

    @Query("SELECT * FROM hero_profiles WHERE heroClass = :heroClass")
    fun observeHero(heroClass: String): Flow<HeroProfileEntity?>

    @Query("SELECT * FROM hero_profiles WHERE heroClass = :heroClass")
    suspend fun getHeroOnce(heroClass: String): HeroProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHero(hero: HeroProfileEntity)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE heroClass = :heroClass ORDER BY isEquipped DESC, itemLevel DESC, id DESC")
    fun observeInventory(heroClass: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE heroClass = :heroClass AND slot = :slot AND isEquipped = 1")
    suspend fun getEquippedInSlot(heroClass: String, slot: String): List<InventoryItemEntity>

    @Query("SELECT COUNT(*) FROM inventory_items WHERE heroClass = :heroClass")
    suspend fun countItemsForHero(heroClass: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Update
    suspend fun updateItems(items: List<InventoryItemEntity>)

    @Query("DELETE FROM inventory_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)
}

@Dao
interface ComboDao {
    @Query("SELECT * FROM combo_slots WHERE heroClass = :heroClass ORDER BY slotIndex ASC")
    fun observeCombo(heroClass: String): Flow<List<ComboSlotEntity>>

    @Query("SELECT COUNT(*) FROM combo_slots WHERE heroClass = :heroClass")
    suspend fun countSlotsForHero(heroClass: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlot(slot: ComboSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlots(slots: List<ComboSlotEntity>)
}

@Dao
interface TalentDao {
    @Query("SELECT * FROM talent_ranks WHERE heroClass = :heroClass")
    fun observeTalents(heroClass: String): Flow<List<TalentRankEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRank(rank: TalentRankEntity)

    @Query("DELETE FROM talent_ranks WHERE heroClass = :heroClass")
    suspend fun clearRanksForHero(heroClass: String)
}

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zone_progress")
    fun observeZones(): Flow<List<ZoneProgressEntity>>

    @Query("SELECT * FROM zone_progress WHERE zoneId = :zoneId")
    suspend fun getZoneOnce(zoneId: String): ZoneProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertZone(zone: ZoneProgressEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertZonesIgnore(zones: List<ZoneProgressEntity>)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)
}
