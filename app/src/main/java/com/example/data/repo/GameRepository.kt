package com.example.data.repo

import androidx.room.withTransaction
import com.example.data.db.*
import com.example.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

class GameRepository(private val db: AppDatabase) {

    private val heroDao = db.heroDao()
    private val invDao = db.inventoryDao()
    private val comboDao = db.comboDao()
    private val talentDao = db.talentDao()
    private val zoneDao = db.zoneDao()
    private val settingsDao = db.settingsDao()

    fun observeSettings(): Flow<AppSettingsEntity> = settingsDao.observeSettings().map {
        it ?: AppSettingsEntity(
            id = 1,
            language = GameLanguage.UA.code,
            svdagPreset = SvdagQualityPreset.BALANCED.name,
            showTelemetry = true,
            hapticsEnabled = true,
            selectedHeroClass = HeroClass.KNIGHT.id
        )
    }

    fun observeAllHeroes(): Flow<List<HeroProfileEntity>> = heroDao.observeAllHeroes()

    fun observeHero(heroClass: HeroClass): Flow<HeroProfileEntity?> =
        heroDao.observeHero(heroClass.id)

    fun observeInventory(heroClass: HeroClass): Flow<List<LootItemModel>> =
        invDao.observeInventory(heroClass.id).map { list ->
            list.map { e ->
                LootItemModel(
                    id = e.id,
                    heroClass = HeroClass.valueOf(e.heroClass),
                    titleKey = e.titleKey,
                    slot = EquipSlot.valueOf(e.slot),
                    rarity = ItemRarity.valueOf(e.rarity),
                    itemLevel = e.itemLevel,
                    affixes = ItemAffixes(
                        bonusDamage = e.bonusDamage,
                        bonusArmor = e.bonusArmor,
                        bonusHp = e.bonusHp,
                        bonusCritPct = e.bonusCritPct,
                        bonusLifestealPct = e.bonusLifestealPct,
                        bonusCooldownHastePct = e.bonusCooldownHastePct
                    ),
                    isEquipped = e.isEquipped,
                    salvageScrap = e.salvageScrap
                )
            }
        }

    fun observeCombo(heroClass: HeroClass): Flow<List<ComboSlotModel>> =
        comboDao.observeCombo(heroClass.id).map { list ->
            list.map { e ->
                ComboSlotModel(
                    slotIndex = e.slotIndex,
                    type = PressenType.valueOf(e.pressenType),
                    tier = PressenTier.valueOf(e.tier),
                    unlocked = e.unlocked
                )
            }
        }

    fun observeTalents(heroClass: HeroClass): Flow<Map<String, Int>> =
        talentDao.observeTalents(heroClass.id).map { list ->
            list.associate { it.nodeId to it.rank }
        }

    fun observeZones(): Flow<List<ZoneProgressEntity>> = zoneDao.observeZones()

    // REPAIR_HOOK: Ідемпотентне транзакційне засівання БД (відновлює частково ініціалізований профіль/предмети/зони без перезапису прогресу)
    suspend fun ensureSeeded() {
        db.withTransaction {
            if (settingsDao.getSettingsOnce() == null) {
                settingsDao.upsertSettings(
                    AppSettingsEntity(
                        id = 1,
                        language = GameLanguage.UA.code,
                        svdagPreset = SvdagQualityPreset.BALANCED.name,
                        showTelemetry = true,
                        hapticsEnabled = true,
                        selectedHeroClass = HeroClass.KNIGHT.id
                    )
                )
            }

            HeroClass.entries.forEach { hc ->
                if (heroDao.getHeroOnce(hc.id) == null) {
                    heroDao.upsertHero(
                        HeroProfileEntity(
                            heroClass = hc.id,
                            level = 1,
                            xp = 0,
                            scrapCurrency = 60,
                            talentPointsAvailable = 2,
                            killsTotal = 0
                        )
                    )
                }

                if (invDao.countItemsForHero(hc.id) == 0) {
                    val weaponKey = when (hc) {
                        HeroClass.KNIGHT -> "starter_sword"
                        HeroClass.RIFLEMAN -> "starter_crossbow"
                        HeroClass.TECHNOMANCER -> "starter_staff"
                    }
                    val offhandKey = when (hc) {
                        HeroClass.KNIGHT -> "starter_shield"
                        HeroClass.RIFLEMAN -> "starter_quiver"
                        HeroClass.TECHNOMANCER -> "starter_core"
                    }
                    invDao.insertItem(
                        InventoryItemEntity(
                            heroClass = hc.id,
                            titleKey = weaponKey,
                            slot = EquipSlot.WEAPON.name,
                            rarity = ItemRarity.BRASS.name,
                            itemLevel = 1,
                            bonusDamage = 10,
                            bonusArmor = 0,
                            bonusHp = 0,
                            bonusCritPct = 4,
                            bonusLifestealPct = 0,
                            bonusCooldownHastePct = 0,
                            isEquipped = true,
                            salvageScrap = 12
                        )
                    )
                    invDao.insertItem(
                        InventoryItemEntity(
                            heroClass = hc.id,
                            titleKey = offhandKey,
                            slot = EquipSlot.OFFHAND.name,
                            rarity = ItemRarity.IRON.name,
                            itemLevel = 1,
                            bonusDamage = 3,
                            bonusArmor = 6,
                            bonusHp = 20,
                            bonusCritPct = 0,
                            bonusLifestealPct = 0,
                            bonusCooldownHastePct = 0,
                            isEquipped = true,
                            salvageScrap = 8
                        )
                    )
                    invDao.insertItem(
                        InventoryItemEntity(
                            heroClass = hc.id,
                            titleKey = "starter_armor",
                            slot = EquipSlot.ARMOR.name,
                            rarity = ItemRarity.IRON.name,
                            itemLevel = 1,
                            bonusDamage = 0,
                            bonusArmor = 10,
                            bonusHp = 35,
                            bonusCritPct = 0,
                            bonusLifestealPct = 0,
                            bonusCooldownHastePct = 0,
                            isEquipped = true,
                            salvageScrap = 10
                        )
                    )
                }

                if (comboDao.countSlotsForHero(hc.id) == 0) {
                    val defaults = listOf(
                        PressenType.POWER to PressenTier.MK1,
                        PressenType.CHAIN to PressenTier.MK1,
                        PressenType.REGEN to PressenTier.MK1,
                        PressenType.COOLDOWN to PressenTier.MK1,
                        PressenType.POWER to PressenTier.MK2,
                        PressenType.CHAIN to PressenTier.MK2
                    )
                    comboDao.upsertSlots(
                        defaults.mapIndexed { idx, (ptype, ptier) ->
                            ComboSlotEntity(
                                heroClass = hc.id,
                                slotIndex = idx,
                                pressenType = ptype.name,
                                tier = ptier.name,
                                unlocked = idx < 4
                            )
                        }
                    )
                }
            }

            // Seed/repair all 5 zones from the Overworld Map without overwriting clearedCount
            zoneDao.insertZonesIgnore(
                ZoneId.entries.map { z ->
                    ZoneProgressEntity(
                        zoneId = z.name,
                        unlocked = true,
                        clearedCount = 0
                    )
                }
            )
        }
    }

    suspend fun saveSettings(settings: AppSettingsEntity) {
        settingsDao.upsertSettings(settings)
    }

    suspend fun setComboSlot(heroClass: HeroClass, slotIndex: Int, type: PressenType, tier: PressenTier) {
        comboDao.upsertSlot(
            ComboSlotEntity(
                heroClass = heroClass.id,
                slotIndex = slotIndex,
                pressenType = type.name,
                tier = tier.name,
                unlocked = true
            )
        )
    }

    // REPAIR_HOOK: Атомарна заміна екіпірованого предмета у слоті та переробка на брухт
    suspend fun equipItem(item: LootItemModel) {
        db.withTransaction {
            val prev = invDao.getEquippedInSlot(item.heroClass.id, item.slot.name)
            val updated = prev.map { it.copy(isEquipped = false) }
            if (updated.isNotEmpty()) {
                invDao.updateItems(updated)
            }
            invDao.insertItem(
                InventoryItemEntity(
                    id = item.id,
                    heroClass = item.heroClass.id,
                    titleKey = item.titleKey,
                    slot = item.slot.name,
                    rarity = item.rarity.name,
                    itemLevel = item.itemLevel,
                    bonusDamage = item.affixes.bonusDamage,
                    bonusArmor = item.affixes.bonusArmor,
                    bonusHp = item.affixes.bonusHp,
                    bonusCritPct = item.affixes.bonusCritPct,
                    bonusLifestealPct = item.affixes.bonusLifestealPct,
                    bonusCooldownHastePct = item.affixes.bonusCooldownHastePct,
                    isEquipped = true,
                    salvageScrap = item.salvageScrap
                )
            )
        }
    }

    suspend fun salvageItem(item: LootItemModel) {
        if (item.isEquipped) return
        db.withTransaction {
            invDao.deleteItemById(item.id)
            val hero = heroDao.getHeroOnce(item.heroClass.id) ?: return@withTransaction
            heroDao.upsertHero(hero.copy(scrapCurrency = hero.scrapCurrency + item.salvageScrap))
        }
    }

    suspend fun forgeRandomGear(heroClass: HeroClass): LootItemModel? {
        val hero = heroDao.getHeroOnce(heroClass.id) ?: return null
        if (hero.scrapCurrency < 40) return null
        heroDao.upsertHero(hero.copy(scrapCurrency = hero.scrapCurrency - 40))
        val generated = createProceduralLoot(heroClass, hero.level, guaranteedRare = true)
        val newId = invDao.insertItem(generated)
        return LootItemModel(
            id = newId,
            heroClass = heroClass,
            titleKey = generated.titleKey,
            slot = EquipSlot.valueOf(generated.slot),
            rarity = ItemRarity.valueOf(generated.rarity),
            itemLevel = generated.itemLevel,
            affixes = ItemAffixes(
                generated.bonusDamage,
                generated.bonusArmor,
                generated.bonusHp,
                generated.bonusCritPct,
                generated.bonusLifestealPct,
                generated.bonusCooldownHastePct
            ),
            isEquipped = false,
            salvageScrap = generated.salvageScrap
        )
    }

    suspend fun dropLootForKill(heroClass: HeroClass, zoneLevel: Int, isElite: Boolean): LootItemModel? {
        val chance = if (isElite) 0.90f else 0.34f
        if (Random.nextFloat() > chance) return null
        val entity = createProceduralLoot(heroClass, zoneLevel, guaranteedRare = isElite)
        val id = invDao.insertItem(entity)
        return LootItemModel(
            id = id,
            heroClass = heroClass,
            titleKey = entity.titleKey,
            slot = EquipSlot.valueOf(entity.slot),
            rarity = ItemRarity.valueOf(entity.rarity),
            itemLevel = entity.itemLevel,
            affixes = ItemAffixes(
                entity.bonusDamage,
                entity.bonusArmor,
                entity.bonusHp,
                entity.bonusCritPct,
                entity.bonusLifestealPct,
                entity.bonusCooldownHastePct
            ),
            isEquipped = false,
            salvageScrap = entity.salvageScrap
        )
    }

    suspend fun awardCombatProgress(
        heroClass: HeroClass,
        xpEarned: Int,
        scrapEarned: Int,
        killsEarned: Int
    ): Boolean {
        val hero = heroDao.getHeroOnce(heroClass.id) ?: return false
        var lvl = hero.level
        var xp = hero.xp + xpEarned
        var pts = hero.talentPointsAvailable
        var leveledUp = false

        while (xp >= xpNeededForLevel(lvl)) {
            xp -= xpNeededForLevel(lvl)
            lvl += 1
            pts += 1
            leveledUp = true
        }

        // Unlock combo slots 5 & 6 as player levels up
        if (lvl >= 2) {
            comboDao.upsertSlot(ComboSlotEntity(heroClass.id, 4, PressenType.POWER.name, PressenTier.MK2.name, true))
        }
        if (lvl >= 3) {
            comboDao.upsertSlot(ComboSlotEntity(heroClass.id, 5, PressenType.CHAIN.name, PressenTier.MK2.name, true))
        }

        heroDao.upsertHero(
            hero.copy(
                level = lvl,
                xp = xp,
                scrapCurrency = hero.scrapCurrency + scrapEarned,
                talentPointsAvailable = pts,
                killsTotal = hero.killsTotal + killsEarned
            )
        )
        return leveledUp
    }

    suspend fun markZoneCleared(zoneId: ZoneId) {
        val existing = zoneDao.getZoneOnce(zoneId.name)
        val nextCount = (existing?.clearedCount ?: 0) + 1
        zoneDao.upsertZone(
            ZoneProgressEntity(
                zoneId = zoneId.name,
                unlocked = true,
                clearedCount = nextCount
            )
        )
    }

    suspend fun upgradeTalent(heroClass: HeroClass, node: TalentNodeDef, currentRanks: Map<String, Int>) {
        val hero = heroDao.getHeroOnce(heroClass.id) ?: return
        if (hero.talentPointsAvailable <= 0) return
        val cur = currentRanks[node.id] ?: 0
        if (cur >= node.maxRank) return
        if (node.prerequisiteId != null && (currentRanks[node.prerequisiteId] ?: 0) <= 0) return

        talentDao.upsertRank(TalentRankEntity(heroClass.id, node.id, cur + 1))
        heroDao.upsertHero(hero.copy(talentPointsAvailable = hero.talentPointsAvailable - 1))
    }

    suspend fun respecTalents(heroClass: HeroClass, currentRanks: Map<String, Int>) {
        val spent = currentRanks.values.sum()
        if (spent <= 0) return
        val hero = heroDao.getHeroOnce(heroClass.id) ?: return
        talentDao.clearRanksForHero(heroClass.id)
        heroDao.upsertHero(hero.copy(talentPointsAvailable = hero.talentPointsAvailable + spent))
    }

    fun xpNeededForLevel(level: Int): Int = 80 + (level - 1) * 65

    private fun createProceduralLoot(
        heroClass: HeroClass,
        itemLevel: Int,
        guaranteedRare: Boolean
    ): InventoryItemEntity {
        val slots = EquipSlot.entries
        val slot = slots[Random.nextInt(slots.size)]
        val roll = Random.nextFloat()
        val rarity = when {
            guaranteedRare && roll < 0.16f -> ItemRarity.SUNLESS
            guaranteedRare && roll < 0.48f -> ItemRarity.RELIC
            guaranteedRare -> ItemRarity.AETHER
            roll < 0.05f -> ItemRarity.SUNLESS
            roll < 0.16f -> ItemRarity.RELIC
            roll < 0.36f -> ItemRarity.AETHER
            roll < 0.72f -> ItemRarity.BRASS
            else -> ItemRarity.IRON
        }

        val titleKey = when (slot) {
            EquipSlot.WEAPON -> when (heroClass) {
                HeroClass.KNIGHT -> "loot_halberd"
                HeroClass.RIFLEMAN -> "loot_repeater"
                HeroClass.TECHNOMANCER -> "loot_tesla"
            }
            EquipSlot.OFFHAND -> "loot_aegis"
            EquipSlot.HELM -> "loot_visage"
            EquipSlot.ARMOR -> "loot_cuirass"
            EquipSlot.BOOTS -> "loot_greaves"
            EquipSlot.RELIC -> "loot_chronogear"
        }

        val scale = (1f + itemLevel * 0.25f) * (1f + rarity.tierOrder * 0.42f)
        val dmg = if (slot == EquipSlot.WEAPON || slot == EquipSlot.RELIC || Random.nextBoolean()) {
            (Random.nextInt(6, 13) * scale).toInt()
        } else 0
        val armor = if (slot == EquipSlot.ARMOR || slot == EquipSlot.OFFHAND || slot == EquipSlot.HELM || slot == EquipSlot.BOOTS) {
            (Random.nextInt(5, 11) * scale).toInt()
        } else 0
        val hp = if (rarity.tierOrder >= 1) (Random.nextInt(15, 34) * scale).toInt() else 0
        val crit = if (rarity.tierOrder >= 2) Random.nextInt(3, 7 + rarity.tierOrder) else 0
        val life = if (rarity.tierOrder >= 3) Random.nextInt(2, 5) else 0
        val cdr = if (rarity.tierOrder >= 2 && (slot == EquipSlot.RELIC || slot == EquipSlot.BOOTS || Random.nextBoolean())) {
            Random.nextInt(4, 10)
        } else 0

        return InventoryItemEntity(
            heroClass = heroClass.id,
            titleKey = titleKey,
            slot = slot.name,
            rarity = rarity.name,
            itemLevel = itemLevel,
            bonusDamage = dmg,
            bonusArmor = armor,
            bonusHp = hp,
            bonusCritPct = crit,
            bonusLifestealPct = life,
            bonusCooldownHastePct = cdr,
            isEquipped = false,
            salvageScrap = 10 + rarity.tierOrder * 12 + itemLevel * 3
        )
    }
}
