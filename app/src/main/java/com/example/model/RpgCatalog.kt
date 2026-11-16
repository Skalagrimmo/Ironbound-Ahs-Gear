package com.example.model

object RpgCatalog {

    val allTalents: List<TalentNodeDef> = listOf(
        // Armored Knight - Branch 0 (Bulwark Vanguard) & Branch 1 (Siegebreaker)
        TalentNodeDef("k_b1", HeroClass.KNIGHT, 0, 0, 3),
        TalentNodeDef("k_b2", HeroClass.KNIGHT, 0, 1, 1, prerequisiteId = "k_b1", unlocksSkillId = "knight_aegis"),
        TalentNodeDef("k_b3", HeroClass.KNIGHT, 0, 2, 3, prerequisiteId = "k_b2"),
        TalentNodeDef("k_s1", HeroClass.KNIGHT, 1, 0, 3),
        TalentNodeDef("k_s2", HeroClass.KNIGHT, 1, 1, 1, prerequisiteId = "k_s1", unlocksSkillId = "knight_charge"),
        TalentNodeDef("k_s3", HeroClass.KNIGHT, 1, 2, 3, prerequisiteId = "k_s2"),

        // Rifleman-Archer - Branch 0 (Marksman) & Branch 1 (Volatile Munitions)
        TalentNodeDef("r_m1", HeroClass.RIFLEMAN, 0, 0, 3),
        TalentNodeDef("r_m2", HeroClass.RIFLEMAN, 0, 1, 1, prerequisiteId = "r_m1", unlocksSkillId = "rifle_volley"),
        TalentNodeDef("r_m3", HeroClass.RIFLEMAN, 0, 2, 3, prerequisiteId = "r_m2"),
        TalentNodeDef("r_v1", HeroClass.RIFLEMAN, 1, 0, 3),
        TalentNodeDef("r_v2", HeroClass.RIFLEMAN, 1, 1, 1, prerequisiteId = "r_v1", unlocksSkillId = "rifle_roll"),
        TalentNodeDef("r_v3", HeroClass.RIFLEMAN, 1, 2, 3, prerequisiteId = "r_v2"),

        // Technomancer Mage - Branch 0 (Aether Conduit) & Branch 1 (Pyro-Alchemy)
        TalentNodeDef("m_a1", HeroClass.TECHNOMANCER, 0, 0, 3),
        TalentNodeDef("m_a2", HeroClass.TECHNOMANCER, 0, 1, 1, prerequisiteId = "m_a1", unlocksSkillId = "mage_arc"),
        TalentNodeDef("m_a3", HeroClass.TECHNOMANCER, 0, 2, 3, prerequisiteId = "m_a2"),
        TalentNodeDef("m_p1", HeroClass.TECHNOMANCER, 1, 0, 3),
        TalentNodeDef("m_p2", HeroClass.TECHNOMANCER, 1, 1, 1, prerequisiteId = "m_p1", unlocksSkillId = "mage_blink"),
        TalentNodeDef("m_p3", HeroClass.TECHNOMANCER, 1, 2, 3, prerequisiteId = "m_p2")
    )

    val allSkills: List<SkillDef> = listOf(
        SkillDef("knight_cleave", HeroClass.KNIGHT, 20, 4.5f, 1.85f, 2.4f),
        SkillDef("knight_aegis", HeroClass.KNIGHT, 30, 8.0f, 1.55f, 2.8f, isHealOrShield = true),
        SkillDef("knight_charge", HeroClass.KNIGHT, 22, 6.0f, 1.65f, 2.0f, isDash = true),

        SkillDef("rifle_trap", HeroClass.RIFLEMAN, 25, 5.5f, 2.1f, 2.6f),
        SkillDef("rifle_volley", HeroClass.RIFLEMAN, 32, 7.5f, 2.35f, 3.2f),
        SkillDef("rifle_roll", HeroClass.RIFLEMAN, 18, 4.5f, 1.15f, 1.8f, isDash = true),

        SkillDef("mage_nova", HeroClass.TECHNOMANCER, 30, 6.0f, 2.25f, 3.0f),
        SkillDef("mage_arc", HeroClass.TECHNOMANCER, 28, 6.5f, 2.1f, 3.6f),
        SkillDef("mage_blink", HeroClass.TECHNOMANCER, 20, 5.0f, 1.3f, 2.2f, isDash = true)
    )

    fun skillsForClass(heroClass: HeroClass, talentRanks: Map<String, Int>): List<SkillDef> {
        val baseId = when (heroClass) {
            HeroClass.KNIGHT -> "knight_cleave"
            HeroClass.RIFLEMAN -> "rifle_trap"
            HeroClass.TECHNOMANCER -> "mage_nova"
        }
        val unlockedIds = mutableSetOf(baseId)
        allTalents.filter { it.heroClass == heroClass && it.unlocksSkillId != null }.forEach { node ->
            if ((talentRanks[node.id] ?: 0) > 0) {
                node.unlocksSkillId?.let { unlockedIds.add(it) }
            }
        }
        return allSkills.filter { it.heroClass == heroClass && it.id in unlockedIds }
    }

    fun evaluateCombo(slots: List<ComboSlotModel>): ComboSummary {
        var multSum = 0f
        var healSum = 0
        var cdrSum = 0f
        var lastMultiplierFactor = 1f
        var lastType: PressenType? = null

        slots.filter { it.unlocked }.sortedBy { it.slotIndex }.forEach { slot ->
            val tMult = slot.tier.multiplier
            when (slot.type) {
                PressenType.POWER -> {
                    val v = 0.35f * tMult
                    multSum += 1f + v
                    lastMultiplierFactor = v
                    lastType = PressenType.POWER
                }
                PressenType.REGEN -> {
                    val h = (14f * tMult).toInt()
                    multSum += 0.95f
                    healSum += h
                    lastMultiplierFactor = h.toFloat()
                    lastType = PressenType.REGEN
                }
                PressenType.COOLDOWN -> {
                    val c = 1.2f * tMult
                    multSum += 0.95f
                    cdrSum += c
                    lastMultiplierFactor = c
                    lastType = PressenType.COOLDOWN
                }
                PressenType.CHAIN -> {
                    val chainAmp = 0.75f * tMult
                    multSum += 1.05f
                    when (lastType) {
                        PressenType.POWER -> multSum += lastMultiplierFactor * chainAmp
                        PressenType.REGEN -> healSum += (lastMultiplierFactor * chainAmp).toInt()
                        PressenType.COOLDOWN -> cdrSum += lastMultiplierFactor * chainAmp
                        else -> multSum += 0.3f * tMult
                    }
                }
            }
        }
        return ComboSummary(
            totalDamageMult = multSum.coerceAtLeast(1f),
            totalHealPerChain = healSum,
            totalCooldownSavedSec = cdrSum
        )
    }

    fun computeStats(
        heroClass: HeroClass,
        level: Int,
        equipped: List<LootItemModel>,
        talentRanks: Map<String, Int>
    ): ComputedHeroStats {
        var maxHp = heroClass.baseHp + (level - 1) * 28
        val maxEnergy = heroClass.baseEnergy + (level - 1) * 6
        var dmg = heroClass.baseDamage + (level - 1) * 5
        var armor = heroClass.baseArmor + (level - 1) * 2
        var crit = heroClass.critChance
        var lifesteal = 0f
        var cdr = 0f
        var range = heroClass.attackRange

        equipped.forEach { item ->
            maxHp += item.affixes.bonusHp
            dmg += item.affixes.bonusDamage
            armor += item.affixes.bonusArmor
            crit += item.affixes.bonusCritPct / 100f
            lifesteal += item.affixes.bonusLifestealPct.toFloat()
            cdr += item.affixes.bonusCooldownHastePct.toFloat()
        }

        talentRanks.forEach { (id, r) ->
            if (r > 0) {
                when (id) {
                    "k_b1" -> { armor += 10 * r; maxHp += 35 * r }
                    "k_b3" -> { lifesteal += 4f * r }
                    "k_s1" -> { dmg += 8 * r; crit += 0.04f * r }
                    "k_s3" -> { dmg = (dmg * (1f + 0.12f * r)).toInt(); cdr += 5f * r }
                    "r_m1" -> { crit += 0.06f * r }
                    "r_m3" -> { dmg += 10 * r; range += 0.4f * r }
                    "r_v1" -> { cdr += 8f * r }
                    "r_v3" -> { maxHp += 25 * r; lifesteal += 4f * r }
                    "m_a1" -> { dmg += 9 * r }
                    "m_a3" -> { cdr += 10f * r; crit += 0.05f * r }
                    "m_p1" -> { maxHp += 35 * r; armor += 6 * r }
                    "m_p3" -> { dmg += 12 * r; lifesteal += 4f * r }
                }
            }
        }

        return ComputedHeroStats(
            maxHp = maxHp,
            maxEnergy = maxEnergy,
            attackDamage = dmg,
            armor = armor,
            critChance = crit.coerceAtMost(0.75f),
            lifestealPct = lifesteal.coerceAtMost(35f),
            cooldownHastePct = cdr.coerceAtMost(45f),
            moveSpeed = heroClass.moveSpeed,
            attackRange = range
        )
    }
}
