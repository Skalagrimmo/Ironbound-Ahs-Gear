package com.example

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.HeroProfileEntity
import com.example.data.db.ZoneProgressEntity
import com.example.data.repo.GameRepository
import com.example.engine.assets.AnimKind
import com.example.engine.assets.SpriteSheetLoader
import com.example.engine.sim.CombatSimulation
import com.example.engine.svdag.SvdagBuilder
import com.example.model.*
import com.example.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameMatchesIronboundRpg() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ironbound RPG", appName)
    }

    @Test
    fun assetsContainValidSpriteAndBiomeSheetsAndLoadPreviewsAndAtlas() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val spriteBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("sprites_characters.jpg").use {
            BitmapFactory.decodeStream(it, null, spriteBounds)
        }
        assertEquals(1408, spriteBounds.outWidth)
        assertEquals(768, spriteBounds.outHeight)

        val biomeBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("world_biomes.jpg").use {
            BitmapFactory.decodeStream(it, null, biomeBounds)
        }
        assertEquals(1408, biomeBounds.outWidth)
        assertEquals(768, biomeBounds.outHeight)

        val previews = SpriteSheetLoader.loadUiPreviews(context)
        assertEquals(3, previews.heroPortraits.size)
        assertEquals(5, previews.zoneThumbnails.size)
        assertTrue(previews.worldMapBitmap.width > 0 && previews.worldMapBitmap.height > 0)

        val atlas = SpriteSheetLoader.loadCombatAtlas(context)
        assertEquals(1024, atlas.atlasBitmap.width)
        assertEquals(1024, atlas.atlasBitmap.height)
        try {
            HeroClass.entries.forEach { hc ->
                val uvNeg = atlas.getHeroUv(hc, AnimKind.IDLE, -2.5f)
                assertTrue(uvNeg.u1 > uvNeg.u0 && uvNeg.v1 > uvNeg.v0)
            }
            EnemyArchetype.entries.forEach { arch ->
                val uv = atlas.getEnemyUv(arch, AnimKind.ATTACK, -1.0f)
                assertTrue(uv.u1 > uv.u0 && uv.v1 > uv.v0)
            }
        } finally {
            atlas.atlasBitmap.recycle()
        }
    }

    @Test
    fun svdagBuildsAllFiveZonesWithDeduplication() {
        ZoneId.entries.forEach { zone ->
            val chunk = SvdagBuilder.buildForZone(zone)
            assertTrue("Root node must exist for $zone", chunk.rootNodeIndex > 0)
            assertTrue("Compression ratio should exceed 1.0 for $zone", chunk.compressionRatio > 1.0f)
            assertEquals(chunk.texWidth * chunk.texHeight * 4, chunk.packedRgbaBytes.size)
        }
    }

    @Test
    fun rememberMeComboEvaluationAmplifiesChainPressens() {
        val slots = listOf(
            ComboSlotModel(0, PressenType.POWER, PressenTier.MK2, unlocked = true),
            ComboSlotModel(1, PressenType.CHAIN, PressenTier.MK3, unlocked = true),
            ComboSlotModel(2, PressenType.REGEN, PressenTier.MK1, unlocked = true),
            ComboSlotModel(3, PressenType.COOLDOWN, PressenTier.MK2, unlocked = true)
        )
        val summary = RpgCatalog.evaluateCombo(slots)
        assertTrue(summary.totalDamageMult > 5.0f)
        assertTrue(summary.totalHealPerChain >= 14)
        assertTrue(summary.totalCooldownSavedSec > 1.0f)
    }

    @Test
    fun combatSimulationRtwpPauseQueuesAndExecutesOrders() {
        val sim = CombatSimulation(onKillReward = {}, onZoneCleared = {})
        val stats = RpgCatalog.computeStats(HeroClass.KNIGHT, 1, emptyList(), emptyMap())
        val skills = RpgCatalog.skillsForClass(HeroClass.KNIGHT, emptyMap())
        val combos = listOf(
            ComboSlotModel(0, PressenType.POWER, PressenTier.MK1, true),
            ComboSlotModel(1, PressenType.REGEN, PressenTier.MK1, true)
        )
        sim.startZone(ZoneId.FACTORY_ZONE, HeroClass.KNIGHT, stats, combos, skills)
        sim.drainPendingCommands()
        val startEnemies = sim.enemies.size
        assertEquals(ZoneId.FACTORY_ZONE.enemyCount, startEnemies)

        sim.togglePause()
        sim.drainPendingCommands()
        assertTrue(sim.isPaused)
        val heroXBefore = sim.heroX
        sim.handleWorldTap(18f, 21f)
        sim.drainPendingCommands()
        assertEquals(1, sim.queuedOrders.size)

        // Stepping while paused must keep hero stationary and retain queue
        sim.step(0.05f)
        assertEquals(heroXBefore, sim.heroX, 0.0001f)
        assertEquals(1, sim.queuedOrders.size)

        // Resume and step so queued movement runs
        sim.togglePause()
        sim.drainPendingCommands()
        assertFalse(sim.isPaused)
        repeat(40) { sim.step(0.03f) }
        assertTrue(sim.queuedOrders.isEmpty())
    }

    @Test
    fun combatSimulationCommandQueueIsThreadSafeUnderConcurrentWritesAndSteps() {
        val sim = CombatSimulation(onKillReward = {}, onZoneCleared = {})
        val stats = RpgCatalog.computeStats(HeroClass.TECHNOMANCER, 2, emptyList(), emptyMap())
        val skills = RpgCatalog.skillsForClass(HeroClass.TECHNOMANCER, emptyMap())
        val combos = listOf(ComboSlotModel(0, PressenType.POWER, PressenTier.MK1, true))
        sim.startZone(ZoneId.THE_RUINS, HeroClass.TECHNOMANCER, stats, combos, skills)
        sim.drainPendingCommands()

        val failure = AtomicReference<Throwable?>(null)
        val startLatch = CountDownLatch(1)

        val uiWriter = thread(start = true) {
            try {
                startLatch.await()
                for (i in 0 until 200) {
                    sim.handleWorldTap(10f + (i % 12), 12f + (i % 8))
                    sim.setJoystick((i % 3 - 1) * 0.5f, 0.25f)
                    if (i % 15 == 0) sim.triggerBasicComboAttack()
                    if (i % 25 == 0) sim.triggerSkill(0)
                    if (i % 40 == 0) sim.togglePause()
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }

        val simThread = thread(start = true) {
            try {
                startLatch.await()
                for (i in 0 until 200) {
                    sim.step(0.016f)
                    sim.snapshot(60, 128, 2.0f, null)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }

        startLatch.countDown()
        uiWriter.join(5000)
        simThread.join(5000)
        sim.drainPendingCommands()

        assertNull("Concurrent command enqueue + step threw an exception: ${failure.get()}", failure.get())
        assertTrue(sim.enemies.size == ZoneId.THE_RUINS.enemyCount)
    }

    @Test
    fun roomDatabaseSeedsAndPersistsZonesTalentsAndForge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = GameRepository(db)
            repo.ensureSeeded()

            val heroes = repo.observeAllHeroes().first()
            assertEquals(3, heroes.size)

            val zones = repo.observeZones().first()
            assertEquals(5, zones.size)

            repo.markZoneCleared(ZoneId.FACTORY_ZONE)
            repo.markZoneCleared(ZoneId.FACTORY_ZONE)
            val updatedZones = repo.observeZones().first().associateBy { it.zoneId }
            assertEquals(2, updatedZones[ZoneId.FACTORY_ZONE.name]?.clearedCount)

            val forged = repo.forgeRandomGear(HeroClass.KNIGHT)
            assertNotNull(forged)
            val knightAfter = repo.observeHero(HeroClass.KNIGHT).first()!!
            assertEquals(20, knightAfter.scrapCurrency)
        } finally {
            db.close()
        }
    }

    @Test
    fun roomEnsureSeededRepairsPartialDatabaseWithoutOverwritingProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // Simulate partial seed: only KNIGHT hero and 1 zone with 3 clears exist
            db.heroDao().upsertHero(
                HeroProfileEntity(
                    heroClass = HeroClass.KNIGHT.id,
                    level = 4,
                    xp = 25,
                    scrapCurrency = 190,
                    talentPointsAvailable = 1,
                    killsTotal = 17
                )
            )
            db.zoneDao().upsertZone(
                ZoneProgressEntity(zoneId = ZoneId.FACTORY_ZONE.name, unlocked = true, clearedCount = 3)
            )

            val repo = GameRepository(db)
            repo.ensureSeeded()

            val heroes = repo.observeAllHeroes().first().associateBy { it.heroClass }
            assertEquals(3, heroes.size)
            assertEquals(4, heroes[HeroClass.KNIGHT.id]?.level)
            assertEquals(190, heroes[HeroClass.KNIGHT.id]?.scrapCurrency)

            val zones = repo.observeZones().first().associateBy { it.zoneId }
            assertEquals(5, zones.size)
            assertEquals(3, zones[ZoneId.FACTORY_ZONE.name]?.clearedCount)

            assertTrue(repo.observeInventory(HeroClass.KNIGHT).first().isNotEmpty())
            assertTrue(repo.observeCombo(HeroClass.RIFLEMAN).first().size == 6)
        } finally {
            db.close()
        }
    }

    @Test
    fun roomEquipAndSalvageAreAtomicAndCreditScrap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = GameRepository(db)
            repo.ensureSeeded()
            repo.awardCombatProgress(HeroClass.KNIGHT, 0, 100, 0)

            val forgedFirst = repo.forgeRandomGear(HeroClass.KNIGHT)!!
            val forgedSecond = repo.forgeRandomGear(HeroClass.KNIGHT)!!
            val scrapBeforeEquip = repo.observeHero(HeroClass.KNIGHT).first()!!.scrapCurrency
            repo.equipItem(forgedFirst)

            val invAfterEquip = repo.observeInventory(HeroClass.KNIGHT).first()
            val equippedInSlot = invAfterEquip.filter { it.slot == forgedFirst.slot && it.isEquipped }
            assertEquals(1, equippedInSlot.size)
            assertEquals(forgedFirst.id, equippedInSlot.first().id)

            val unequipped = invAfterEquip.firstOrNull { !it.isEquipped } ?: forgedSecond
            repo.salvageItem(unequipped)
            val scrapAfterSalvage = repo.observeHero(HeroClass.KNIGHT).first()!!.scrapCurrency
            assertEquals(scrapBeforeEquip + unequipped.salvageScrap, scrapAfterSalvage)
        } finally {
            db.close()
        }
    }

    @Test
    fun uiPreviewsAndInitRecoveryStatePropagatesCleanly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        var attempts = 0
        while (!vm.uiState.value.isReady && vm.uiState.value.initError == null && attempts < 120) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }
        ShadowLooper.idleMainLooper()
        assertNull("Не очікувалась помилка ініціалізації: ${vm.uiState.value.initError}", vm.uiState.value.initError)
        assertTrue("GameViewModel має стати ready після засівання та завантаження асетів", vm.uiState.value.isReady)
        assertNotNull(vm.uiPreviews)
    }
}
