package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repo.GameRepository
import com.example.engine.sim.CombatSimulation
import com.example.engine.svdag.SvdagBuilder
import com.example.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        val startEnemies = sim.enemies.size
        assertEquals(ZoneId.FACTORY_ZONE.enemyCount, startEnemies)

        sim.togglePause()
        assertTrue(sim.isPaused)
        val heroXBefore = sim.heroX
        sim.handleWorldTap(18f, 21f)
        assertEquals(1, sim.queuedOrders.size)

        // Stepping while paused must keep hero stationary and retain queue
        sim.step(0.05f)
        assertEquals(heroXBefore, sim.heroX, 0.0001f)
        assertEquals(1, sim.queuedOrders.size)

        // Resume and step so queued movement runs
        sim.togglePause()
        assertFalse(sim.isPaused)
        repeat(40) { sim.step(0.03f) }
        assertTrue(sim.queuedOrders.isEmpty())
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
}
