package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.AppSettingsEntity
import com.example.data.db.HeroProfileEntity
import com.example.data.db.ZoneProgressEntity
import com.example.data.repo.GameRepository
import com.example.engine.assets.CombatAtlasAssets
import com.example.engine.assets.SpriteSheetLoader
import com.example.engine.assets.UiPreviewAssets
import com.example.engine.sim.CombatHudSnapshot
import com.example.engine.sim.CombatSimulation
import com.example.engine.svdag.SvdagBuilder
import com.example.engine.svdag.SvdagCompiledChunk
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class AppScreen {
    HERO_SELECT,
    WORLD_MAP,
    ARENA
}

enum class ArenaModal {
    NONE,
    COMBO_LAB,
    INVENTORY,
    TALENTS,
    SETTINGS
}

data class GameUiState(
    val isReady: Boolean = false,
    val initError: String? = null,
    val screen: AppScreen = AppScreen.HERO_SELECT,
    val activeModal: ArenaModal = ArenaModal.NONE,
    val language: GameLanguage = GameLanguage.UA,
    val svdagPreset: SvdagQualityPreset = SvdagQualityPreset.BALANCED,
    val showTelemetry: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val selectedHeroClass: HeroClass = HeroClass.KNIGHT,
    val selectedZone: ZoneId = ZoneId.FACTORY_ZONE,
    val allHeroes: Map<HeroClass, HeroProfileEntity> = emptyMap(),
    val inventory: List<LootItemModel> = emptyList(),
    val comboSlots: List<ComboSlotModel> = emptyList(),
    val talentRanks: Map<String, Int> = emptyMap(),
    val zoneProgress: Map<ZoneId, ZoneProgressEntity> = emptyMap(),
    val computedStats: ComputedHeroStats = RpgCatalog.computeStats(HeroClass.KNIGHT, 1, emptyList(), emptyMap()),
    val activeSkills: List<SkillDef> = emptyList(),
    val hud: CombatHudSnapshot = CombatHudSnapshot()
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameRepository(AppDatabase.getInstance(app))

    private data class InitStatus(
        val ready: Boolean = false,
        val error: String? = null
    )

    private val _screen = MutableStateFlow(AppScreen.HERO_SELECT)
    private val _modal = MutableStateFlow(ArenaModal.NONE)
    private val _selectedZone = MutableStateFlow(ZoneId.FACTORY_ZONE)
    private val _hud = MutableStateFlow(CombatHudSnapshot())
    private val _recentLootKey = MutableStateFlow<String?>(null)
    private val _initStatus = MutableStateFlow(InitStatus())
    private var initJob: Job? = null

    @Volatile
    var uiPreviews: UiPreviewAssets? = null
        private set

    @Volatile
    var combatAtlas: CombatAtlasAssets? = null
        private set

    val svdagChunks: ConcurrentHashMap<ZoneId, SvdagCompiledChunk> = ConcurrentHashMap()

    @Volatile
    var onZoneChangedForGl: ((ZoneId) -> Unit)? = null

    val simulation: CombatSimulation = CombatSimulation(
        onKillReward = { arch ->
            val current = uiState.value
            val xp = arch.xpReward + current.selectedZone.index * 10
            val scrap = 6 + current.selectedZone.index * 3 + if (arch.isElite) 18 else 0
            viewModelScope.launch(Dispatchers.IO) {
                repo.awardCombatProgress(current.selectedHeroClass, xp, scrap, 1)
                val loot = repo.dropLootForKill(
                    current.selectedHeroClass,
                    current.selectedZone.recommendedLevel,
                    arch.isElite
                )
                if (loot != null) {
                    _recentLootKey.value = loot.titleKey
                }
            }
        },
        onZoneCleared = {
            val z = _selectedZone.value
            val hc = uiState.value.selectedHeroClass
            viewModelScope.launch(Dispatchers.IO) {
                repo.markZoneCleared(z)
                repo.awardCombatProgress(hc, 65 + z.index * 30, 28 + z.index * 12, 0)
            }
        }
    )

    private val settingsFlow = repo.observeSettings()
    private val selectedClassFlow = settingsFlow.map {
        runCatching { HeroClass.valueOf(it.selectedHeroClass) }.getOrDefault(HeroClass.KNIGHT)
    }.distinctUntilChanged()

    private val inventoryFlow = selectedClassFlow.flatMapLatest { repo.observeInventory(it) }
    private val comboFlow = selectedClassFlow.flatMapLatest { repo.observeCombo(it) }
    private val talentFlow = selectedClassFlow.flatMapLatest { repo.observeTalents(it) }

    // REPAIR_HOOK: Агрегація Room Flow + стану ініціалізації та синхронізація білду в CombatSimulation
    val uiState: StateFlow<GameUiState> = combine(
        _initStatus,
        _screen,
        _modal,
        settingsFlow,
        repo.observeAllHeroes(),
        inventoryFlow,
        comboFlow,
        talentFlow,
        repo.observeZones(),
        _selectedZone,
        _hud
    ) { arr ->
        val initStatus = arr[0] as InitStatus
        val screen = arr[1] as AppScreen
        val modal = arr[2] as ArenaModal
        val settings = arr[3] as AppSettingsEntity
        @Suppress("UNCHECKED_CAST")
        val heroesList = arr[4] as List<HeroProfileEntity>
        @Suppress("UNCHECKED_CAST")
        val inv = arr[5] as List<LootItemModel>
        @Suppress("UNCHECKED_CAST")
        val combo = arr[6] as List<ComboSlotModel>
        @Suppress("UNCHECKED_CAST")
        val talents = arr[7] as Map<String, Int>
        @Suppress("UNCHECKED_CAST")
        val zones = arr[8] as List<ZoneProgressEntity>
        val selZone = arr[9] as ZoneId
        val hud = arr[10] as CombatHudSnapshot

        val hc = runCatching { HeroClass.valueOf(settings.selectedHeroClass) }.getOrDefault(HeroClass.KNIGHT)
        val heroesMap = heroesList.mapNotNull { h ->
            runCatching { HeroClass.valueOf(h.heroClass) to h }.getOrNull()
        }.toMap()
        val level = heroesMap[hc]?.level ?: 1
        val equipped = inv.filter { it.isEquipped }
        val stats = RpgCatalog.computeStats(hc, level, equipped, talents)
        val skills = RpgCatalog.skillsForClass(hc, talents)

        simulation.updateHeroBuild(stats, combo, skills)

        GameUiState(
            isReady = initStatus.ready,
            initError = initStatus.error,
            screen = screen,
            activeModal = modal,
            language = runCatching { GameLanguage.valueOf(settings.language) }.getOrDefault(GameLanguage.UA),
            svdagPreset = runCatching { SvdagQualityPreset.valueOf(settings.svdagPreset) }.getOrDefault(SvdagQualityPreset.BALANCED),
            showTelemetry = settings.showTelemetry,
            hapticsEnabled = settings.hapticsEnabled,
            selectedHeroClass = hc,
            selectedZone = selZone,
            allHeroes = heroesMap,
            inventory = inv,
            comboSlots = combo,
            talentRanks = talents,
            zoneProgress = zones.mapNotNull { zp ->
                runCatching { ZoneId.valueOf(zp.zoneId) to zp }.getOrNull()
            }.toMap(),
            computedStats = stats,
            activeSkills = skills,
            hud = hud
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GameUiState())

    // REPAIR_HOOK: Асинхронний старт БД та графічних асетів з діагностикою збоїв і кнопкою повтору
    init {
        retryInitialization()
    }

    fun retryInitialization() {
        if (initJob?.isActive == true) return
        _initStatus.value = InitStatus(ready = false, error = null)
        initJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fast path: seed DB & load lightweight UI previews so initial Window sync completes well under 1s
                repo.ensureSeeded()
                val previews = SpriteSheetLoader.loadUiPreviews(getApplication())
                uiPreviews = previews
                LoadedGameAssetsHolder.previews = previews
                _initStatus.value = InitStatus(ready = true, error = null)

                // Yield briefly so SurfaceSyncGroup and first Compose layout settle cleanly, then pre-warm combat assets
                delay(350)
                ensureCombatAssetsLoaded(ZoneId.FACTORY_ZONE)
            } catch (t: Throwable) {
                Log.e("GameViewModel", "Помилка стартової ініціалізації", t)
                val detail = t.localizedMessage?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
                _initStatus.value = InitStatus(
                    ready = false,
                    error = "Не вдалося завантажити ресурси або ініціалізувати базу даних ($detail). Перевірте наявність спрайт-листів у app/src/main/assets/ та повторіть спробу."
                )
            }
        }
    }

    private fun ensureCombatAssetsLoaded(priorityZone: ZoneId) {
        if (combatAtlas == null) {
            synchronized(this) {
                if (combatAtlas == null) {
                    val atlas = SpriteSheetLoader.loadCombatAtlas(getApplication())
                    combatAtlas = atlas
                    LoadedGameAssetsHolder.combatAtlas = atlas
                }
            }
        }
        svdagChunks.computeIfAbsent(priorityZone) { SvdagBuilder.buildForZone(it) }
    }

    fun pushGlHudSnapshot(fps: Int) {
        val chunk = svdagChunks[_selectedZone.value]
        _hud.value = simulation.snapshot(
            fps = fps,
            svdagNodes = chunk?.uniqueNodeCount ?: 0,
            svdagRatio = chunk?.compressionRatio ?: 1f,
            recentLootKey = _recentLootKey.value
        )
    }

    fun clearLootToast() {
        _recentLootKey.value = null
    }

    fun selectHeroClass(heroClass: HeroClass) {
        val s = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveSettings(
                AppSettingsEntity(
                    id = 1,
                    language = s.language.code,
                    svdagPreset = s.svdagPreset.name,
                    showTelemetry = s.showTelemetry,
                    hapticsEnabled = s.hapticsEnabled,
                    selectedHeroClass = heroClass.id
                )
            )
        }
    }

    fun navigateTo(screen: AppScreen) {
        _modal.value = ArenaModal.NONE
        _screen.value = screen
    }

    fun selectZone(zoneId: ZoneId) {
        _selectedZone.value = zoneId
    }

    // REPAIR_HOOK: Запуск зони, прогрів бойового атласу/SVDAG та переключення сцени
    fun deployToZone(zoneId: ZoneId) {
        _selectedZone.value = zoneId
        _modal.value = ArenaModal.NONE
        val st = uiState.value
        simulation.startZone(zoneId, st.selectedHeroClass, st.computedStats, st.comboSlots, st.activeSkills)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ensureCombatAssetsLoaded(zoneId)
                onZoneChangedForGl?.invoke(zoneId)
            } catch (t: Throwable) {
                Log.e("GameViewModel", "Помилка підготовки бойових асетів зони $zoneId", t)
                val detail = t.localizedMessage?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
                _initStatus.value = InitStatus(
                    ready = false,
                    error = "Не вдалося підготувати бойовий атлас або SVDAG-чанк для зони $zoneId ($detail)."
                )
            }
        }
        _screen.value = AppScreen.ARENA
    }

    fun openModal(modal: ArenaModal) {
        if (modal != ArenaModal.NONE && !simulation.isPaused && _screen.value == AppScreen.ARENA) {
            simulation.requestPause(true)
        }
        _modal.value = modal
    }

    fun closeModal() {
        _modal.value = ArenaModal.NONE
    }

    fun setLanguage(lang: GameLanguage) {
        val s = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveSettings(
                AppSettingsEntity(
                    id = 1,
                    language = lang.code,
                    svdagPreset = s.svdagPreset.name,
                    showTelemetry = s.showTelemetry,
                    hapticsEnabled = s.hapticsEnabled,
                    selectedHeroClass = s.selectedHeroClass.id
                )
            )
        }
    }

    fun setSvdagPreset(preset: SvdagQualityPreset) {
        val s = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveSettings(
                AppSettingsEntity(
                    id = 1,
                    language = s.language.code,
                    svdagPreset = preset.name,
                    showTelemetry = s.showTelemetry,
                    hapticsEnabled = s.hapticsEnabled,
                    selectedHeroClass = s.selectedHeroClass.id
                )
            )
        }
    }

    fun toggleTelemetry(enabled: Boolean) {
        val s = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveSettings(
                AppSettingsEntity(
                    id = 1,
                    language = s.language.code,
                    svdagPreset = s.svdagPreset.name,
                    showTelemetry = enabled,
                    hapticsEnabled = s.hapticsEnabled,
                    selectedHeroClass = s.selectedHeroClass.id
                )
            )
        }
    }

    fun toggleHaptics(enabled: Boolean) {
        val s = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveSettings(
                AppSettingsEntity(
                    id = 1,
                    language = s.language.code,
                    svdagPreset = s.svdagPreset.name,
                    showTelemetry = s.showTelemetry,
                    hapticsEnabled = enabled,
                    selectedHeroClass = s.selectedHeroClass.id
                )
            )
        }
    }

    fun assignComboSlot(slotIndex: Int, type: PressenType, tier: PressenTier) {
        val hc = uiState.value.selectedHeroClass
        viewModelScope.launch(Dispatchers.IO) {
            repo.setComboSlot(hc, slotIndex, type, tier)
        }
    }

    fun equipItem(item: LootItemModel) {
        viewModelScope.launch(Dispatchers.IO) { repo.equipItem(item) }
    }

    fun salvageItem(item: LootItemModel) {
        viewModelScope.launch(Dispatchers.IO) { repo.salvageItem(item) }
    }

    fun forgeGear() {
        val hc = uiState.value.selectedHeroClass
        viewModelScope.launch(Dispatchers.IO) {
            val forged = repo.forgeRandomGear(hc)
            if (forged != null) _recentLootKey.value = forged.titleKey
        }
    }

    fun upgradeTalent(node: TalentNodeDef) {
        val st = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.upgradeTalent(st.selectedHeroClass, node, st.talentRanks)
        }
    }

    fun respecTalents() {
        val st = uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.respecTalents(st.selectedHeroClass, st.talentRanks)
        }
    }
}

object LoadedGameAssetsHolder {
    @Volatile
    var previews: UiPreviewAssets? = null

    @Volatile
    var combatAtlas: CombatAtlasAssets? = null

    @Volatile
    var scene: com.example.engine.render.IronboundLittleKtScene? = null
}
