package com.example

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.render.IronboundLittleKtScene
import com.example.ui.modals.RpgModalHost
import com.example.ui.screens.ArenaHudOverlay
import com.example.ui.screens.HeroSelectScreen
import com.example.ui.screens.WorldMapScreen
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.CastIronSurface
import com.example.ui.theme.CrimsonStrike
import com.example.ui.theme.IronboundTheme
import com.example.ui.theme.OnBrassDark
import com.example.ui.theme.ParchmentText
import com.example.ui.theme.SootCharcoal
import com.example.ui.theme.TarnishedBrass
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
    private var glSurfaceView: GLSurfaceView? = null

    // REPAIR_HOOK: Хост LittleKt OpenGL ES + накладений ComposeView і керування GLSurfaceView.renderMode
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Host LittleKt OpenGL ES surface underneath Jetpack Compose UI
        littleKtApp = createLittleKtApp {
            activity = this@MainActivity
            title = "Ironbound RPG"
            backgroundColor = Color(0.05f, 0.06f, 0.08f, 1f)
            showStatusBar = true
            useImmersiveMode = false
        }.start { ctx ->
            IronboundLittleKtScene(ctx, viewModel)
        }

        // Locate LittleKt's GLSurfaceView so we can throttle renderMode off-arena
        // and keep menu screens free of continuous SurfaceView buffer churn
        glSurfaceView = findGlSurfaceView(window.decorView)
        glSurfaceView?.setPreserveEGLContextOnPause(true)
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        val composeOverlay = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                IronboundTheme {
                    IronboundRootContent(
                        viewModel = viewModel,
                        onScreenChanged = { screen ->
                            glSurfaceView?.let { glv ->
                                if (screen == AppScreen.ARENA) {
                                    glv.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                    glv.requestRender()
                                } else {
                                    glv.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                    glv.requestRender()
                                }
                            }
                        }
                    )
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

    private fun findGlSurfaceView(root: View): GLSurfaceView? {
        if (root is GLSurfaceView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findGlSurfaceView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    // REPAIR_HOOK: Життєвий цикл Android -> синхронізація pause/resume/destroy контексту LittleKt
    override fun onResume() {
        super.onResume()
        (littleKtApp?.context as? AndroidContext)?.resume()
        glSurfaceView?.let { glv ->
            if (viewModel.uiState.value.screen == AppScreen.ARENA) {
                glv.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                glv.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glv.requestRender()
            }
        }
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
private fun IronboundRootContent(
    viewModel: GameViewModel,
    onScreenChanged: (AppScreen) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.screen) {
        onScreenChanged(state.screen)
    }

    // REPAIR_HOOK: Стартова перевірка помилок ініціалізації (запобігає нескінченному спінеру завантаження)
    val initError = state.initError
    if (initError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SootCharcoal)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .testTag("init_error_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CastIronSurface),
                border = BorderStroke(1.5.dp, CrimsonStrike)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Помилка ініціалізації",
                        tint = CrimsonStrike,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Помилка запуску Ironbound",
                        style = MaterialTheme.typography.titleLarge,
                        color = BurnishedGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = initError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ParchmentText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retryInitialization() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("retry_init_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TarnishedBrass,
                            contentColor = OnBrassDark
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Повторити спробу", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

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
