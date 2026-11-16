package com.example.engine.sim

import com.example.engine.assets.AnimKind
import com.example.model.*
import kotlin.math.*
import kotlin.random.Random

data class EnemyActor(
    val id: Int,
    val archetype: EnemyArchetype,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    var anim: AnimKind = AnimKind.IDLE,
    var animTime: Float = 0f,
    var facingLeft: Boolean = false,
    var attackCooldown: Float = 0.6f,
    var windupTimer: Float = 0f,
    var isWindingUp: Boolean = false,
    var hurtFlash: Float = 0f,
    var deathTimer: Float = 0f
) {
    val isDead: Boolean get() = hp <= 0
}

data class ProjectileVfx(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var life: Float,
    val colorHex: Int,
    val radius: Float = 0.25f,
    val damage: Int = 0,
    val fromHero: Boolean = true,
    var hit: Boolean = false
)

data class SlashPulseVfx(
    val x: Float,
    val y: Float,
    val maxRadius: Float,
    var age: Float = 0f,
    val duration: Float = 0.35f,
    val colorHex: Int
)

data class FloatingTextEvent(
    val id: Long,
    val worldX: Float,
    val worldY: Float,
    val text: String,
    val colorHex: Int,
    var age: Float = 0f,
    val duration: Float = 0.85f
)

data class CombatHudSnapshot(
    val heroHp: Int = 100,
    val heroMaxHp: Int = 100,
    val heroEnergy: Float = 100f,
    val heroMaxEnergy: Int = 100,
    val isPaused: Boolean = false,
    val comboStep: Int = 0,
    val comboLength: Int = 4,
    val lastPressen: PressenType? = null,
    val comboWindowRemaining: Float = 0f,
    val queuedOrders: List<QueuedOrder> = emptyList(),
    val skillCooldowns: List<Float> = listOf(0f, 0f, 0f),
    val potionCooldown: Float = 0f,
    val enemiesRemaining: Int = 0,
    val enemiesTotal: Int = 0,
    val selectedTargetId: Int? = null,
    val zoneCleared: Boolean = false,
    val heroDead: Boolean = false,
    val elapsedSec: Float = 0f,
    val fps: Int = 60,
    val svdagNodes: Int = 0,
    val svdagRatio: Float = 1f,
    val recentLootKey: String? = null,
    val floatingTexts: List<FloatingTextEvent> = emptyList()
)

class CombatSimulation(
    private val onKillReward: (EnemyArchetype) -> Unit,
    private val onZoneCleared: () -> Unit
) {
    var activeZone: ZoneId = ZoneId.FACTORY_ZONE
        private set
    var heroClass: HeroClass = HeroClass.KNIGHT
        private set
    var heroStats: ComputedHeroStats = RpgCatalog.computeStats(HeroClass.KNIGHT, 1, emptyList(), emptyMap())
        private set
    var comboSlots: List<ComboSlotModel> = emptyList()
    var activeSkills: List<SkillDef> = emptyList()

    // Hero live state in normalized 0..32 arena coordinates (play area 4..28)
    var heroX: Float = 16f
    var heroY: Float = 20f
    var heroHp: Int = 340
    var heroEnergy: Float = 100f
    var heroAnim: AnimKind = AnimKind.IDLE
    var heroAnimTime: Float = 0f
    var heroFacingLeft: Boolean = false
    var heroAttackLock: Float = 0f
    var heroHurtFlash: Float = 0f

    var joystickX: Float = 0f
    var joystickY: Float = 0f
    var moveTargetX: Float? = null
    var moveTargetY: Float? = null

    var isPaused: Boolean = false
    var selectedTargetId: Int? = null
    val queuedOrders = ArrayList<QueuedOrder>()

    var comboStep: Int = 0
    var comboResetTimer: Float = 0f
    var lastPressenTriggered: PressenType? = null
    val skillCooldowns = FloatArray(3)
    var potionCooldown: Float = 0f

    val enemies = ArrayList<EnemyActor>()
    val projectiles = ArrayList<ProjectileVfx>()
    val pulses = ArrayList<SlashPulseVfx>()
    val floatingTexts = ArrayList<FloatingTextEvent>()

    private var nextEnemyId = 1
    private var nextFloatId = 1L
    private var elapsedZoneSec = 0f
    private var zoneClearFired = false

    fun startZone(
        zone: ZoneId,
        hClass: HeroClass,
        stats: ComputedHeroStats,
        combos: List<ComboSlotModel>,
        skills: List<SkillDef>
    ) {
        activeZone = zone
        heroClass = hClass
        heroStats = stats
        comboSlots = combos
        activeSkills = skills

        heroX = 16f
        heroY = 23f
        heroHp = stats.maxHp
        heroEnergy = stats.maxEnergy.toFloat()
        heroAnim = AnimKind.IDLE
        heroAnimTime = 0f
        heroAttackLock = 0f
        heroHurtFlash = 0f
        moveTargetX = null
        moveTargetY = null
        isPaused = false
        selectedTargetId = null
        queuedOrders.clear()
        comboStep = 0
        comboResetTimer = 0f
        lastPressenTriggered = null
        skillCooldowns.fill(0f)
        potionCooldown = 0f
        elapsedZoneSec = 0f
        zoneClearFired = false

        enemies.clear()
        projectiles.clear()
        pulses.clear()
        floatingTexts.clear()
        spawnZoneEnemies(zone)
    }

    fun updateHeroBuild(stats: ComputedHeroStats, combos: List<ComboSlotModel>, skills: List<SkillDef>) {
        val ratio = if (heroStats.maxHp > 0) heroHp.toFloat() / heroStats.maxHp else 1f
        heroStats = stats
        heroHp = (stats.maxHp * ratio).toInt().coerceIn(1, stats.maxHp)
        comboSlots = combos
        activeSkills = skills
    }

    private fun spawnZoneEnemies(zone: ZoneId) {
        val count = zone.enemyCount
        val scale = 1f + zone.index * 0.34f
        for (i in 0 until count) {
            val arch = when {
                i == count - 1 -> EnemyArchetype.CONSTRUCT
                zone == ZoneId.FACTORY_ZONE -> if (i % 3 == 0) EnemyArchetype.RAT_SWARM else EnemyArchetype.SCRAPPER
                zone == ZoneId.INTERIOR_LAB -> when (i % 3) {
                    0 -> EnemyArchetype.SCRAPPER
                    1 -> EnemyArchetype.CULTIST
                    else -> EnemyArchetype.CONSTRUCT
                }
                zone == ZoneId.RESIDENTIAL_DISTRICT -> if (i % 2 == 0) EnemyArchetype.SCRAPPER else EnemyArchetype.CULTIST
                zone == ZoneId.THE_RUINS -> if (i % 3 == 0) EnemyArchetype.RAT_SWARM else EnemyArchetype.CULTIST
                else -> when (i % 4) {
                    0 -> EnemyArchetype.RAT_SWARM
                    1 -> EnemyArchetype.SCRAPPER
                    2 -> EnemyArchetype.CULTIST
                    else -> EnemyArchetype.CONSTRUCT
                }
            }
            val angle = (i.toFloat() / count) * (PI.toFloat() * 2f)
            val dist = 5.5f + (i % 3) * 2.8f
            val ex = (16f + cos(angle) * dist).coerceIn(5.5f, 26.5f)
            val ey = (14f + sin(angle) * (dist * 0.72f)).coerceIn(5.5f, 22.5f)
            val hp = (arch.maxHp * scale).toInt()
            enemies.add(
                EnemyActor(
                    id = nextEnemyId++,
                    archetype = arch,
                    x = ex,
                    y = ey,
                    hp = hp,
                    maxHp = hp,
                    attackCooldown = 0.5f + (i % 4) * 0.25f
                )
            )
        }
    }

    fun togglePause() {
        isPaused = !isPaused
    }

    fun clearQueue() {
        queuedOrders.clear()
    }

    fun handleWorldTap(worldX: Float, worldY: Float) {
        val clampedX = worldX.coerceIn(4.5f, 27.5f)
        val clampedY = worldY.coerceIn(4.5f, 27.5f)
        val tappedEnemy = enemies.filter { !it.isDead }
            .minByOrNull { hypot(it.x - clampedX, it.y - clampedY) }
            ?.takeIf { hypot(it.x - clampedX, it.y - clampedY) < 2.2f }

        if (isPaused) {
            if (tappedEnemy != null) {
                selectedTargetId = tappedEnemy.id
                queuedOrders.add(QueuedOrder.AttackTarget(tappedEnemy.id))
            } else {
                queuedOrders.add(QueuedOrder.MoveTo(clampedX, clampedY))
            }
        } else {
            if (tappedEnemy != null) {
                selectedTargetId = tappedEnemy.id
                triggerBasicComboAttack(tappedEnemy)
            } else {
                moveTargetX = clampedX
                moveTargetY = clampedY
            }
        }
    }

    fun triggerBasicComboAttack(explicitTarget: EnemyActor? = null) {
        if (heroHp <= 0) return
        if (isPaused) {
            val target = explicitTarget ?: findNearestEnemy()
            if (target != null) {
                selectedTargetId = target.id
                queuedOrders.add(QueuedOrder.AttackTarget(target.id))
            }
            return
        }
        val target = explicitTarget ?: enemies.firstOrNull { it.id == selectedTargetId && !it.isDead } ?: findNearestEnemy()
        if (target == null) {
            // Swing in place
            heroAnim = AnimKind.ATTACK
            heroAttackLock = 0.28f
            pulses.add(SlashPulseVfx(heroX, heroY, 1.4f, colorHex = 0xFFD49B42.toInt()))
            return
        }
        selectedTargetId = target.id
        val dist = hypot(target.x - heroX, target.y - heroY)
        if (dist > heroStats.attackRange + 0.4f) {
            moveTargetX = target.x
            moveTargetY = target.y
            return
        }
        executeComboStrikeOn(target)
    }

    fun triggerSkill(skillIndex: Int) {
        val skill = activeSkills.getOrNull(skillIndex) ?: return
        if (skillCooldowns[skillIndex] > 0f || heroEnergy < skill.energyCost || heroHp <= 0) return

        if (isPaused) {
            val target = enemies.firstOrNull { it.id == selectedTargetId && !it.isDead } ?: findNearestEnemy()
            val tx = target?.x ?: heroX
            val ty = target?.y ?: (heroY - 2f)
            queuedOrders.add(QueuedOrder.CastSkill(skillIndex, tx, ty))
            return
        }
        executeSkillNow(skillIndex, heroX, heroY)
    }

    fun triggerPotion() {
        if (potionCooldown > 0f || heroHp <= 0) return
        if (isPaused) {
            queuedOrders.add(QueuedOrder.UsePotion)
            return
        }
        executePotionNow()
    }

    private fun executePotionNow() {
        if (potionCooldown > 0f || heroHp <= 0) return
        val heal = (heroStats.maxHp * 0.42f).toInt().coerceAtLeast(45)
        heroHp = (heroHp + heal).coerceAtMost(heroStats.maxHp)
        heroEnergy = (heroEnergy + 35f).coerceAtMost(heroStats.maxEnergy.toFloat())
        potionCooldown = 9.0f
        pulses.add(SlashPulseVfx(heroX, heroY, 1.8f, colorHex = 0xFF47B875.toInt()))
        spawnFloatingText(heroX, heroY - 0.8f, "+$heal HP", 0xFF56E390.toInt())
    }

    private fun executeSkillNow(skillIndex: Int, aimX: Float, aimY: Float) {
        val skill = activeSkills.getOrNull(skillIndex) ?: return
        if (skillCooldowns[skillIndex] > 0f || heroEnergy < skill.energyCost) return
        heroEnergy -= skill.energyCost
        val cdrScale = 1f - (heroStats.cooldownHastePct / 100f)
        skillCooldowns[skillIndex] = (skill.cooldownSec * cdrScale).coerceAtLeast(1.2f)
        heroAnim = AnimKind.ATTACK
        heroAttackLock = 0.35f

        val target = enemies.firstOrNull { it.id == selectedTargetId && !it.isDead } ?: findNearestEnemy()
        val cx = target?.x ?: aimX
        val cy = target?.y ?: aimY

        if (skill.isDash) {
            val dx = (cx - heroX)
            val dy = (cy - heroY)
            val d = hypot(dx, dy).coerceAtLeast(0.01f)
            val dashDist = min(d, 4.2f)
            heroX = (heroX + (dx / d) * dashDist).coerceIn(4.5f, 27.5f)
            heroY = (heroY + (dy / d) * dashDist).coerceIn(4.5f, 27.5f)
        }

        if (skill.isHealOrShield) {
            val heal = (heroStats.maxHp * 0.22f).toInt()
            heroHp = (heroHp + heal).coerceAtMost(heroStats.maxHp)
            spawnFloatingText(heroX, heroY - 0.9f, "+$heal", 0xFF47B875.toInt())
        }

        val pulseColor = when (heroClass) {
            HeroClass.KNIGHT -> 0xFFD94A38.toInt()
            HeroClass.RIFLEMAN -> 0xFFE5A93B.toInt()
            HeroClass.TECHNOMANCER -> 0xFF4FA8B8.toInt()
        }
        val centerX = if (skill.isDash) heroX else cx
        val centerY = if (skill.isDash) heroY else cy
        pulses.add(SlashPulseVfx(centerX, centerY, skill.radius, colorHex = pulseColor))

        enemies.filter { !it.isDead && hypot(it.x - centerX, it.y - centerY) <= skill.radius }
            .forEach { enemy ->
                val raw = (heroStats.attackDamage * skill.damageMultiplier).toInt()
                applyDamageToEnemy(enemy, raw, isCrit = Random.nextFloat() < heroStats.critChance)
            }
    }

    private fun executeComboStrikeOn(target: EnemyActor) {
        if (heroAttackLock > 0f) return
        val unlockedSlots = comboSlots.filter { it.unlocked }.sortedBy { it.slotIndex }
        val activeSlot = unlockedSlots.getOrNull(comboStep % unlockedSlots.size.coerceAtLeast(1))
        val prevSlot = if (comboStep > 0) unlockedSlots.getOrNull((comboStep - 1) % unlockedSlots.size) else null

        var dmgMult = 1.0f
        var bonusHeal = 0
        var cdrSaved = 0f

        if (activeSlot != null) {
            lastPressenTriggered = activeSlot.type
            val tm = activeSlot.tier.multiplier
            when (activeSlot.type) {
                PressenType.POWER -> dmgMult += 0.35f * tm
                PressenType.REGEN -> {
                    bonusHeal += (14 * tm).toInt()
                    heroEnergy = (heroEnergy + 6f * tm).coerceAtMost(heroStats.maxEnergy.toFloat())
                }
                PressenType.COOLDOWN -> cdrSaved += 1.2f * tm
                PressenType.CHAIN -> {
                    val amp = 0.75f * tm
                    when (prevSlot?.type) {
                        PressenType.POWER -> dmgMult += 0.35f * prevSlot.tier.multiplier * (1f + amp)
                        PressenType.REGEN -> bonusHeal += (14 * prevSlot.tier.multiplier * (1f + amp)).toInt()
                        PressenType.COOLDOWN -> cdrSaved += 1.2f * prevSlot.tier.multiplier * (1f + amp)
                        else -> dmgMult += 0.25f * tm
                    }
                }
            }
        }

        if (bonusHeal > 0) {
            heroHp = (heroHp + bonusHeal).coerceAtMost(heroStats.maxHp)
            spawnFloatingText(heroX, heroY - 0.9f, "+$bonusHeal", 0xFF47B875.toInt())
        }
        if (cdrSaved > 0f) {
            for (i in skillCooldowns.indices) {
                skillCooldowns[i] = (skillCooldowns[i] - cdrSaved).coerceAtLeast(0f)
            }
        }

        val isCrit = Random.nextFloat() < heroStats.critChance
        val dmg = (heroStats.attackDamage * dmgMult * (if (isCrit) 1.65f else 1f)).toInt()

        heroFacingLeft = target.x < heroX
        heroAnim = AnimKind.ATTACK
        heroAttackLock = 0.32f
        comboStep = (comboStep + 1) % unlockedSlots.size.coerceAtLeast(1)
        comboResetTimer = 2.6f

        if (heroClass == HeroClass.KNIGHT) {
            applyDamageToEnemy(target, dmg, isCrit)
            pulses.add(SlashPulseVfx(target.x, target.y, 1.2f, colorHex = pressenColor(activeSlot?.type)))
        } else {
            val dx = target.x - heroX
            val dy = target.y - heroY
            val d = hypot(dx, dy).coerceAtLeast(0.01f)
            val speed = 16f
            projectiles.add(
                ProjectileVfx(
                    x = heroX,
                    y = heroY,
                    vx = (dx / d) * speed,
                    vy = (dy / d) * speed,
                    life = 0.9f,
                    colorHex = pressenColor(activeSlot?.type),
                    damage = dmg,
                    fromHero = true
                )
            )
        }
    }

    private fun applyDamageToEnemy(enemy: EnemyActor, rawDamage: Int, isCrit: Boolean) {
        if (enemy.isDead) return
        val finalDmg = rawDamage.coerceAtLeast(4)
        enemy.hp = (enemy.hp - finalDmg).coerceAtLeast(0)
        enemy.hurtFlash = 0.18f
        if (!enemy.isDead) {
            enemy.anim = AnimKind.HURT
        } else {
            enemy.anim = AnimKind.DEATH
            enemy.deathTimer = 1.6f
            onKillReward(enemy.archetype)
        }

        if (heroStats.lifestealPct > 0f) {
            val leech = (finalDmg * heroStats.lifestealPct / 100f).toInt()
            if (leech > 0) heroHp = (heroHp + leech).coerceAtMost(heroStats.maxHp)
        }

        val text = if (isCrit) "$finalDmg!" else "$finalDmg"
        val color = if (isCrit) 0xFFF0BE6A.toInt() else 0xFFEAE3D5.toInt()
        spawnFloatingText(enemy.x, enemy.y - 0.7f, text, color)
    }

    fun step(rawDt: Float) {
        val dt = rawDt.coerceIn(0f, 0.05f)

        // Update floating text & pulses subtly even slowly, or freeze simulation completely when paused
        if (isPaused || heroHp <= 0) {
            heroAnimTime += dt * 0.25f
            return
        }

        elapsedZoneSec += dt
        heroAnimTime += dt
        heroEnergy = (heroEnergy + 9f * dt).coerceAtMost(heroStats.maxEnergy.toFloat())
        for (i in skillCooldowns.indices) {
            skillCooldowns[i] = (skillCooldowns[i] - dt).coerceAtLeast(0f)
        }
        potionCooldown = (potionCooldown - dt).coerceAtLeast(0f)
        heroHurtFlash = (heroHurtFlash - dt).coerceAtLeast(0f)
        heroAttackLock = (heroAttackLock - dt).coerceAtLeast(0f)
        if (comboResetTimer > 0f) {
            comboResetTimer -= dt
            if (comboResetTimer <= 0f) comboStep = 0
        }

        // Execute queued Tactical Pause orders sequentially
        if (queuedOrders.isNotEmpty() && heroAttackLock <= 0f) {
            val next = queuedOrders.first()
            when (next) {
                is QueuedOrder.UsePotion -> {
                    executePotionNow()
                    queuedOrders.removeAt(0)
                }
                is QueuedOrder.CastSkill -> {
                    executeSkillNow(next.skillIndex, next.targetX, next.targetY)
                    queuedOrders.removeAt(0)
                }
                is QueuedOrder.MoveTo -> {
                    val d = hypot(next.worldX - heroX, next.worldY - heroY)
                    if (d < 0.45f) {
                        queuedOrders.removeAt(0)
                    } else {
                        moveHeroToward(next.worldX, next.worldY, dt)
                    }
                }
                is QueuedOrder.AttackTarget -> {
                    val t = enemies.firstOrNull { it.id == next.enemyId && !it.isDead }
                    if (t == null) {
                        queuedOrders.removeAt(0)
                    } else if (hypot(t.x - heroX, t.y - heroY) > heroStats.attackRange) {
                        moveHeroToward(t.x, t.y, dt)
                    } else {
                        executeComboStrikeOn(t)
                        queuedOrders.removeAt(0)
                    }
                }
            }
        } else {
            // Joystick or tap-to-move
            val jMag = hypot(joystickX, joystickY)
            if (jMag > 0.12f) {
                moveTargetX = null
                moveTargetY = null
                val nx = joystickX / jMag
                val ny = joystickY / jMag
                heroX = (heroX + nx * heroStats.moveSpeed * dt).coerceIn(4.5f, 27.5f)
                heroY = (heroY + ny * heroStats.moveSpeed * dt).coerceIn(4.5f, 27.5f)
                heroFacingLeft = nx < -0.05f
                if (heroAttackLock <= 0f) heroAnim = AnimKind.WALK
            } else if (moveTargetX != null && moveTargetY != null) {
                val tx = moveTargetX!!
                val ty = moveTargetY!!
                if (hypot(tx - heroX, ty - heroY) < 0.35f) {
                    moveTargetX = null
                    moveTargetY = null
                    if (heroAttackLock <= 0f) heroAnim = AnimKind.IDLE
                } else {
                    moveHeroToward(tx, ty, dt)
                }
            } else if (heroAttackLock <= 0f) {
                heroAnim = AnimKind.IDLE
            }
        }

        // Update enemies
        var aliveCount = 0
        enemies.forEach { e ->
            e.animTime += dt
            if (e.isDead) {
                e.deathTimer = (e.deathTimer - dt).coerceAtLeast(0f)
                return@forEach
            }
            aliveCount++
            e.hurtFlash = (e.hurtFlash - dt).coerceAtLeast(0f)
            e.attackCooldown = (e.attackCooldown - dt).coerceAtLeast(0f)

            val dx = heroX - e.x
            val dy = heroY - e.y
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            e.facingLeft = dx < 0f

            if (e.isWindingUp) {
                e.windupTimer -= dt
                e.anim = AnimKind.ATTACK
                if (e.windupTimer <= 0f) {
                    e.isWindingUp = false
                    e.attackCooldown = e.archetype.attackCooldown
                    if (e.archetype == EnemyArchetype.CULTIST) {
                        val sp = 9.5f
                        projectiles.add(
                            ProjectileVfx(
                                x = e.x, y = e.y,
                                vx = (dx / dist) * sp,
                                vy = (dy / dist) * sp,
                                life = 1.4f,
                                colorHex = 0xFFE05344.toInt(),
                                damage = e.archetype.damage + activeZone.index * 5,
                                fromHero = false
                            )
                        )
                    } else if (dist <= e.archetype.attackRange + 0.65f) {
                        damageHero(e.archetype.damage + activeZone.index * 5)
                    }
                }
            } else if (dist <= e.archetype.attackRange) {
                if (e.attackCooldown <= 0f) {
                    e.isWindingUp = true
                    e.windupTimer = 0.42f // Telegraph window readable in Tactical Pause!
                } else if (e.hurtFlash <= 0f) {
                    e.anim = AnimKind.IDLE
                }
            } else if (dist < 14f) {
                e.x = (e.x + (dx / dist) * e.archetype.speed * dt).coerceIn(4.5f, 27.5f)
                e.y = (e.y + (dy / dist) * e.archetype.speed * dt).coerceIn(4.5f, 27.5f)
                if (e.hurtFlash <= 0f) e.anim = AnimKind.WALK
            }
        }

        if (aliveCount == 0 && enemies.isNotEmpty() && !zoneClearFired) {
            zoneClearFired = true
            onZoneCleared()
        }

        // Projectiles
        val pIter = projectiles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.fromHero) {
                val hitEnemy = enemies.firstOrNull { !it.isDead && hypot(it.x - p.x, it.y - p.y) < 0.9f }
                if (hitEnemy != null) {
                    applyDamageToEnemy(hitEnemy, p.damage, isCrit = Random.nextFloat() < heroStats.critChance)
                    pulses.add(SlashPulseVfx(p.x, p.y, 0.85f, colorHex = p.colorHex))
                    p.hit = true
                }
            } else {
                if (hypot(heroX - p.x, heroY - p.y) < 0.8f) {
                    damageHero(p.damage)
                    p.hit = true
                }
            }
            if (p.hit || p.life <= 0f) pIter.remove()
        }

        // Pulses & floating text
        val vIter = pulses.iterator()
        while (vIter.hasNext()) {
            val v = vIter.next()
            v.age += dt
            if (v.age >= v.duration) vIter.remove()
        }
        val fIter = floatingTexts.iterator()
        while (fIter.hasNext()) {
            val f = fIter.next()
            f.age += dt
            if (f.age >= f.duration) fIter.remove()
        }
    }

    private fun moveHeroToward(tx: Float, ty: Float, dt: Float) {
        val dx = tx - heroX
        val dy = ty - heroY
        val d = hypot(dx, dy).coerceAtLeast(0.001f)
        heroX = (heroX + (dx / d) * heroStats.moveSpeed * dt).coerceIn(4.5f, 27.5f)
        heroY = (heroY + (dy / d) * heroStats.moveSpeed * dt).coerceIn(4.5f, 27.5f)
        heroFacingLeft = dx < 0f
        if (heroAttackLock <= 0f) heroAnim = AnimKind.WALK
    }

    private fun damageHero(raw: Int) {
        val mitigated = (raw - heroStats.armor * 0.45f).toInt().coerceAtLeast(3)
        heroHp = (heroHp - mitigated).coerceAtLeast(0)
        heroHurtFlash = 0.22f
        if (heroHp <= 0) {
            heroAnim = AnimKind.DEATH
            isPaused = false
        }
        spawnFloatingText(heroX, heroY - 0.8f, "-$mitigated", 0xFFE05344.toInt())
    }

    private fun findNearestEnemy(): EnemyActor? =
        enemies.filter { !it.isDead }.minByOrNull { hypot(it.x - heroX, it.y - heroY) }

    private fun spawnFloatingText(wx: Float, wy: Float, text: String, colorHex: Int) {
        floatingTexts.add(FloatingTextEvent(nextFloatId++, wx, wy, text, colorHex))
    }

    private fun pressenColor(p: PressenType?): Int = when (p) {
        PressenType.POWER -> 0xFFD94A38.toInt()
        PressenType.REGEN -> 0xFF47B875.toInt()
        PressenType.COOLDOWN -> 0xFF489FD9.toInt()
        PressenType.CHAIN -> 0xFFE5A93B.toInt()
        null -> 0xFFD49B42.toInt()
    }

    fun snapshot(fps: Int, svdagNodes: Int, svdagRatio: Float, recentLootKey: String?): CombatHudSnapshot {
        val unlockedLen = comboSlots.count { it.unlocked }.coerceAtLeast(4)
        return CombatHudSnapshot(
            heroHp = heroHp,
            heroMaxHp = heroStats.maxHp,
            heroEnergy = heroEnergy,
            heroMaxEnergy = heroStats.maxEnergy,
            isPaused = isPaused,
            comboStep = comboStep,
            comboLength = unlockedLen,
            lastPressen = lastPressenTriggered,
            comboWindowRemaining = comboResetTimer,
            queuedOrders = queuedOrders.toList(),
            skillCooldowns = skillCooldowns.toList(),
            potionCooldown = potionCooldown,
            enemiesRemaining = enemies.count { !it.isDead },
            enemiesTotal = enemies.size,
            selectedTargetId = selectedTargetId,
            zoneCleared = zoneClearFired,
            heroDead = heroHp <= 0,
            elapsedSec = elapsedZoneSec,
            fps = fps,
            svdagNodes = svdagNodes,
            svdagRatio = svdagRatio,
            recentLootKey = recentLootKey,
            floatingTexts = floatingTexts.map { it.copy() }
        )
    }
}
