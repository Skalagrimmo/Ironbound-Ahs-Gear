package com.example.model

enum class GameLanguage(val code: String) {
    UA("UA"),
    EN("EN")
}

enum class SvdagQualityPreset(val scale: Float, val maxSteps: Int) {
    PERF_EXYNOS_7870(0.50f, 30),
    BALANCED(0.75f, 40),
    NATIVE(1.00f, 48)
}

enum class HeroClass(
    val id: String,
    val baseHp: Int,
    val baseEnergy: Int,
    val baseDamage: Int,
    val baseArmor: Int,
    val moveSpeed: Float,
    val attackRange: Float,
    val critChance: Float
) {
    KNIGHT(
        id = "KNIGHT",
        baseHp = 340,
        baseEnergy = 100,
        baseDamage = 34,
        baseArmor = 28,
        moveSpeed = 3.2f,
        attackRange = 1.65f,
        critChance = 0.12f
    ),
    RIFLEMAN(
        id = "RIFLEMAN",
        baseHp = 230,
        baseEnergy = 120,
        baseDamage = 31,
        baseArmor = 14,
        moveSpeed = 3.65f,
        attackRange = 5.4f,
        critChance = 0.20f
    ),
    TECHNOMANCER(
        id = "TECHNOMANCER",
        baseHp = 210,
        baseEnergy = 150,
        baseDamage = 36,
        baseArmor = 11,
        moveSpeed = 3.45f,
        attackRange = 4.6f,
        critChance = 0.16f
    )
}

enum class EnemyArchetype(
    val id: String,
    val maxHp: Int,
    val damage: Int,
    val speed: Float,
    val attackRange: Float,
    val attackCooldown: Float,
    val xpReward: Int,
    val isElite: Boolean = false
) {
    RAT_SWARM("RAT_SWARM", 68, 11, 3.3f, 1.15f, 1.05f, 18),
    SCRAPPER("SCRAPPER", 135, 19, 2.35f, 1.45f, 1.45f, 34),
    CULTIST("CULTIST", 120, 23, 2.0f, 3.8f, 1.85f, 42),
    CONSTRUCT("CONSTRUCT", 320, 34, 1.45f, 1.85f, 2.25f, 95, isElite = true)
}

enum class ZoneId(
    val index: Int,
    val recommendedLevel: Int,
    val mapPinX: Float,
    val mapPinY: Float,
    val enemyCount: Int
) {
    FACTORY_ZONE(0, 1, 0.26f, 0.44f, 8),
    INTERIOR_LAB(1, 3, 0.67f, 0.72f, 10),
    RESIDENTIAL_DISTRICT(2, 5, 0.45f, 0.34f, 11),
    THE_RUINS(3, 7, 0.44f, 0.65f, 12),
    DARK_CONFLUENCE(4, 9, 0.80f, 0.48f, 14)
}

enum class PressenType {
    POWER,
    REGEN,
    COOLDOWN,
    CHAIN
}

enum class PressenTier(val multiplier: Float, val label: String) {
    MK1(1.0f, "I"),
    MK2(1.35f, "II"),
    MK3(1.75f, "III")
}

data class ComboSlotModel(
    val slotIndex: Int,
    val type: PressenType,
    val tier: PressenTier,
    val unlocked: Boolean = true
)

data class ComboSummary(
    val totalDamageMult: Float,
    val totalHealPerChain: Int,
    val totalCooldownSavedSec: Float
)

enum class EquipSlot {
    WEAPON,
    OFFHAND,
    HELM,
    ARMOR,
    BOOTS,
    RELIC
}

enum class ItemRarity(val tierOrder: Int, val affixCount: Int) {
    IRON(0, 1),
    BRASS(1, 2),
    AETHER(2, 3),
    RELIC(3, 4),
    SUNLESS(4, 5)
}

data class ItemAffixes(
    val bonusDamage: Int = 0,
    val bonusArmor: Int = 0,
    val bonusHp: Int = 0,
    val bonusCritPct: Int = 0,
    val bonusLifestealPct: Int = 0,
    val bonusCooldownHastePct: Int = 0
)

data class LootItemModel(
    val id: Long,
    val heroClass: HeroClass,
    val titleKey: String,
    val slot: EquipSlot,
    val rarity: ItemRarity,
    val itemLevel: Int,
    val affixes: ItemAffixes,
    val isEquipped: Boolean,
    val salvageScrap: Int
)

data class ComputedHeroStats(
    val maxHp: Int,
    val maxEnergy: Int,
    val attackDamage: Int,
    val armor: Int,
    val critChance: Float,
    val lifestealPct: Float,
    val cooldownHastePct: Float,
    val moveSpeed: Float,
    val attackRange: Float
)

data class TalentNodeDef(
    val id: String,
    val heroClass: HeroClass,
    val branchIndex: Int, // 0 or 1
    val tierRow: Int,     // 0..2
    val maxRank: Int,
    val prerequisiteId: String? = null,
    val unlocksSkillId: String? = null
)

data class SkillDef(
    val id: String,
    val heroClass: HeroClass,
    val energyCost: Int,
    val cooldownSec: Float,
    val damageMultiplier: Float,
    val radius: Float,
    val isHealOrShield: Boolean = false,
    val isDash: Boolean = false
)

sealed class QueuedOrder {
    data class MoveTo(val worldX: Float, val worldY: Float) : QueuedOrder()
    data class AttackTarget(val enemyId: Int) : QueuedOrder()
    data class CastSkill(val skillIndex: Int, val targetX: Float, val targetY: Float) : QueuedOrder()
    data object UsePotion : QueuedOrder()
}
