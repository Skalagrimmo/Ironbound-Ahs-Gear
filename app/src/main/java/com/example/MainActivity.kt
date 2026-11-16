package com.example

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.render.IronboundLittleKtScene
import com.example.ui.modals.RpgModalHost
import com.example.ui.screens.ArenaHudOverlay
import com.example.ui.screens.HeroSelectScreen
import com.example.ui.screens.WorldMapScreen
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.IronboundTheme
import com.example.ui.theme.SootCharcoal
import com.example.viewmodel.AppScreen
import com.example.viewmodel.ArenaModal
import com.example.viewmodel.GameViewModel
import com.lehaine.littlekt.AndroidContext
import com.lehaine.littlekt.LittleKtApp
import com.lehaine.littlekt.createLittleKtApp
import com.lehaine.littlekt.graphics.Color

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()
    private var littleKtApp: LittleKtApp? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Host LittleKt OpenGL ES surface underneath transparent Jetpack Compose HUD
        littleKtApp = createLittleKtApp {
            activity = this@MainActivity
            title = "Ironbound RPG"
            backgroundColor = Color(0.05f, 0.06f, 0.08f, 1f)
            showStatusBar = true
            useImmersiveMode = false
        }.start { ctx ->
            IronboundLittleKtScene(ctx, viewModel)
        }

        val composeOverlay = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                IronboundTheme {
                    IronboundRootContent(viewModel)
                }
            }
        }
        addContentView(
            composeOverlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onResume() {
        super.onResume()
        (littleKtApp?.context as? AndroidContext)?.resume()
    }

    override fun onPause() {
        (littleKtApp?.context as? AndroidContext)?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        littleKtApp?.close()
        super.onDestroy()
    }
}

@Composable
private fun IronboundRootContent(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (!state.isReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SootCharcoal),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = BurnishedGold)
                Spacer(Modifier.height(12.dp))
                Text("Ironbound: Ash & Gear", style = MaterialTheme.typography.headlineSmall, color = BurnishedGold)
            }
        }
        return
    }

    BackHandler(enabled = state.activeModal != ArenaModal.NONE || state.screen != AppScreen.HERO_SELECT) {
        when {
            state.activeModal != ArenaModal.NONE -> viewModel.closeModal()
            state.screen == AppScreen.ARENA -> viewModel.navigateTo(AppScreen.WORLD_MAP)
            state.screen == AppScreen.WORLD_MAP -> viewModel.navigateTo(AppScreen.HERO_SELECT)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            AppScreen.HERO_SELECT -> HeroSelectScreen(
                state = state,
                onSelectHero = { viewModel.selectHeroClass(it) },
                onEnterWorldMap = { viewModel.navigateTo(AppScreen.WORLD_MAP) },
                onToggleLanguage = { viewModel.setLanguage(it) },
                onOpenSettings = { viewModel.openModal(ArenaModal.SETTINGS) }
            )
            AppScreen.WORLD_MAP -> WorldMapScreen(
                state = state,
                onBack = { viewModel.navigateTo(AppScreen.HERO_SELECT) },
                onSelectZone = { viewModel.selectZone(it) },
                onDeployZone = { viewModel.deployToZone(it) },
                onOpenSettings = { viewModel.openModal(ArenaModal.SETTINGS) }
            )
            AppScreen.ARENA -> ArenaHudOverlay(
                state = state,
                viewModel = viewModel,
                onOpenModal = { viewModel.openModal(it) },
                onReturnToMap = { viewModel.navigateTo(AppScreen.WORLD_MAP) },
                onRetryZone = { viewModel.deployToZone(state.selectedZone) }
            )
        }

        RpgModalHost(
            state = state,
            onDismiss = { viewModel.closeModal() },
            onAssignCombo = { idx, type, tier -> viewModel.assignComboSlot(idx, type, tier) },
            onEquipItem = { viewModel.equipItem(it) },
            onSalvageItem = { viewModel.salvageItem(it) },
            onForgeGear = { viewModel.forgeGear() },
            onUpgradeTalent = { viewModel.upgradeTalent(it) },
            onRespecTalents = { viewModel.respecTalents() },
            onSetLanguage = { viewModel.setLanguage(it) },
            onSetSvdagPreset = { viewModel.setSvdagPreset(it) },
            onToggleTelemetry = { viewModel.toggleTelemetry(it) },
            onToggleHaptics = { viewModel.toggleHaptics(it) }
        )
    }
}
