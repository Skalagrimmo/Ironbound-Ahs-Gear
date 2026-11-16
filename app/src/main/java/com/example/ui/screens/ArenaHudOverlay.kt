package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.localization.GameStrings
import com.example.model.PressenType
import com.example.ui.theme.*
import com.example.viewmodel.ArenaModal
import com.example.viewmodel.GameUiState
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.LoadedGameAssetsHolder
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ArenaHudOverlay(
    state: GameUiState,
    viewModel: GameViewModel,
    onOpenModal: (ArenaModal) -> Unit,
    onReturnToMap: () -> Unit,
    onRetryZone: () -> Unit
) {
    val lang = state.language
    val hud = state.hud
    val sim = viewModel.simulation
    val haptics = LocalHapticFeedback.current
    var viewportSize by remember { mutableStateOf(IntSize(1280, 720)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
    ) {
        // Full-screen isometric tap target layer -> converts screen taps to SVDAG arena coordinates
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewportSize) {
                    detectTapGestures { offset ->
                        val scene = LoadedGameAssetsHolder.scene
                        if (scene != null && viewportSize.width > 0 && viewportSize.height > 0) {
                            val (wx, wy) = scene.screenToWorld(
                                offset.x,
                                offset.y,
                                viewportSize.width.toFloat(),
                                viewportSize.height.toFloat()
                            )
                            sim.handleWorldTap(wx, wy)
                        }
                    }
                }
        )

        // Floating Combat Text (Damage, Crits, Pressen Heals)
        val sceneRef = LoadedGameAssetsHolder.scene
        if (sceneRef != null && viewportSize.width > 0 && viewportSize.height > 0) {
            hud.floatingTexts.forEach { ft ->
                val t = (ft.age / ft.duration).coerceIn(0f, 1f)
                val (sx, sy) = sceneRef.worldToComposeScreen(
                    ft.worldX,
                    3.2f + t * 1.1f,
                    ft.worldY,
                    viewportSize.width.toFloat(),
                    viewportSize.height.toFloat()
                )
                Text(
                    text = ft.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(ft.colorHex).copy(alpha = 1f - t),
                    modifier = Modifier.offset {
                        IntOffset((sx - 24f).roundToInt(), (sy - 12f).roundToInt())
                    }
                )
            }
        }

        // Tactical Pause cyan border frame
        if (hud.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, AetherCyan.copy(alpha = 0.75f))
            )
        }

        // HUD safe area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // TOP HUD ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Hero Status Plate
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkForgeOverlay)
                        .border(1.dp, AgedBronzeOutline, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val portrait = LoadedGameAssetsHolder.assets?.heroPortraits?.get(state.selectedHeroClass)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SootCharcoal)
                            .border(1.dp, TarnishedBrass, RoundedCornerShape(10.dp))
                    ) {
                        if (portrait != null) {
                            Image(
                                bitmap = portrait,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.width(136.dp)) {
                        val profile = state.allHeroes[state.selectedHeroClass]
                        Text(
                            text = "${GameStrings.label("level", lang)} ${profile?.level ?: 1} • ${profile?.scrapCurrency ?: 0}⚙",
                            style = MaterialTheme.typography.labelMedium,
                            color = BurnishedGold
                        )
                        Spacer(Modifier.height(3.dp))
                        // HP Bar
                        LinearProgressIndicator(
                            progress = { (hud.heroHp.toFloat() / hud.heroMaxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CrimsonStrike,
                            trackColor = SootCharcoal
                        )
                        Spacer(Modifier.height(2.dp))
                        // Energy Bar
                        LinearProgressIndicator(
                            progress = { (hud.heroEnergy / hud.heroMaxEnergy.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AetherCyan,
                            trackColor = SootCharcoal
                        )
                        Text(
                            text = "${hud.heroHp}/${hud.heroMaxHp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ParchmentText
                        )
                    }
                }

                // Center Chronometer / Zone / Exynos Telemetry Pill
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (hud.isPaused) Color(0xDD132B34) else DarkForgeOverlay)
                            .border(
                                1.5.dp,
                                if (hud.isPaused) AetherGlow else AgedBronzeOutline,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (hud.isPaused) GameStrings.label("tactical_pause", lang)
                            else GameStrings.zoneName(state.selectedZone, lang),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hud.isPaused) AetherGlow else BurnishedGold
                        )
                        Text(
                            text = "${hud.enemiesRemaining}/${hud.enemiesTotal} ☠",
                            style = MaterialTheme.typography.labelMedium,
                            color = FurnaceEmber
                        )
                    }

                    if (state.showTelemetry) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${hud.fps} FPS • SVDAG ${hud.svdagNodes}n (${String.format("%.1f", hud.svdagRatio)}x) • ${state.svdagPreset.scale}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedAshText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SootCharcoal.copy(alpha = 0.72f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (hud.recentLootKey != null) {
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = {
                                viewModel.clearLootToast()
                                onOpenModal(ArenaModal.INVENTORY)
                            },
                            label = {
                                Text(
                                    "+ ${GameStrings.itemName(hud.recentLootKey, lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BurnishedGold
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BurnishedGold, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }

                // Right RPG Menu Strip (Combo Lab, Inventory, Talents, World Map, Settings)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudMenuIcon(Icons.Default.Bolt, GameStrings.label("combo_lab", lang), "hud_combo_lab_btn") {
                        onOpenModal(ArenaModal.COMBO_LAB)
                    }
                    HudMenuIcon(Icons.Default.Backpack, GameStrings.label("inventory", lang), "hud_inventory_btn") {
                        onOpenModal(ArenaModal.INVENTORY)
                    }
                    val pts = state.allHeroes[state.selectedHeroClass]?.talentPointsAvailable ?: 0
                    HudMenuIcon(
                        icon = Icons.Default.AccountTree,
                        desc = GameStrings.label("talents", lang),
                        tag = "hud_talents_btn",
                        badgeCount = pts
                    ) {
                        onOpenModal(ArenaModal.TALENTS)
                    }
                    HudMenuIcon(Icons.Default.Map, GameStrings.label("map", lang), "hud_map_btn", onClick = onReturnToMap)
                    HudMenuIcon(Icons.Default.Settings, GameStrings.label("settings", lang), "hud_settings_btn") {
                        onOpenModal(ArenaModal.SETTINGS)
                    }
                }
            }

            // PAUSE QUEUE BANNER
            if (hud.isPaused) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 118.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6142830))
                        .border(1.dp, AetherCyan, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${GameStrings.label("queued_orders", lang)}: ${hud.queuedOrders.size} • ${GameStrings.label("pause_hint", lang)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherGlow,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    if (hud.queuedOrders.isNotEmpty()) {
                        TextButton(
                            onClick = { sim.clearQueue() },
                            modifier = Modifier.testTag("clear_queue_button")
                        ) {
                            Text(GameStrings.label("clear_queue", lang), color = FurnaceEmber)
                        }
                    }
                }
            }

            // BOTTOM LEFT VIRTUAL JOYSTICK
            VirtualThumbJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp),
                onMove = { dx, dy ->
                    sim.joystickX = dx
                    sim.joystickY = dy
                }
            )

            // BOTTOM RIGHT COMBAT CLUSTER (Tactical Pause, Combo Attack, Potion, 3 Skills)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Remember Me Combo Step Strip + Skills Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active Class Skills (up to 3)
                    state.activeSkills.forEachIndexed { idx, skill ->
                        val cd = hud.skillCooldowns.getOrElse(idx) { 0f }
                        val canUse = cd <= 0f && hud.heroEnergy >= skill.energyCost
                        SkillCircleButton(
                            label = GameStrings.skillName(skill.id, lang).take(6),
                            cooldownSec = cd,
                            enabled = canUse,
                            accent = if (idx == 0) FurnaceEmber else if (idx == 1) AetherCyan else TarnishedBrass,
                            tag = "skill_btn_$idx"
                        ) {
                            if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sim.triggerSkill(idx)
                        }
                    }

                    // Potion Button
                    SkillCircleButton(
                        label = GameStrings.label("potion", lang).take(5),
                        cooldownSec = hud.potionCooldown,
                        enabled = hud.potionCooldown <= 0f,
                        accent = PressenRegenColor,
                        tag = "potion_button"
                    ) {
                        sim.triggerPotion()
                    }
                }

                // Primary Row: Chronometer Tactical Pause + Combo Attack Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Chronometer Tactical Pause Button
                    Button(
                        onClick = {
                            if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            sim.togglePause()
                        },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("tactical_pause_button"),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hud.isPaused) AetherCyan else RivetedSteelVariant,
                            contentColor = if (hud.isPaused) SootCharcoal else AetherGlow
                        ),
                        border = BorderStroke(1.5.dp, if (hud.isPaused) AetherGlow else AetherCyan)
                    ) {
                        Icon(
                            if (hud.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hud.isPaused) GameStrings.label("resume", lang) else GameStrings.label("pause", lang),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Remember Me Combo Strike Button
                    val activeSlot = state.comboSlots.filter { it.unlocked }
                        .getOrNull(hud.comboStep % hud.comboLength.coerceAtLeast(1))
                    val pressenCol = when (activeSlot?.type) {
                        PressenType.POWER -> PressenPowerColor
                        PressenType.REGEN -> PressenRegenColor
                        PressenType.COOLDOWN -> PressenCooldownColor
                        PressenType.CHAIN -> PressenChainColor
                        null -> TarnishedBrass
                    }
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    listOf(pressenCol.copy(alpha = 0.9f), CastIronSurface)
                                )
                            )
                            .border(2.5.dp, pressenCol, CircleShape)
                            .clickable {
                                if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sim.triggerBasicComboAttack()
                            }
                            .testTag("combo_attack_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Gavel, contentDescription = GameStrings.label("attack", lang), tint = ParchmentText, modifier = Modifier.size(22.dp))
                            Text(
                                text = "${hud.comboStep + 1}/${hud.comboLength}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ParchmentText
                            )
                        }
                    }
                }
            }
        }

        // Zone Cleared / Defeat Dialog Card
        if (hud.zoneCleared || hud.heroDead) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA080A0D)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CastIronSurface),
                    border = BorderStroke(2.dp, if (hud.zoneCleared) BurnishedGold else CrimsonStrike)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (hud.zoneCleared) Icons.Default.EmojiEvents else Icons.Default.Dangerous,
                            contentDescription = null,
                            tint = if (hud.zoneCleared) BurnishedGold else CrimsonStrike,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (hud.zoneCleared) GameStrings.label("zone_cleared", lang) else GameStrings.label("hero_fallen", lang),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (hud.zoneCleared) BurnishedGold else CrimsonStrike
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = GameStrings.zoneName(state.selectedZone, lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedAshText
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onRetryZone,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("retry_zone_button"),
                                border = BorderStroke(1.dp, TarnishedBrass)
                            ) {
                                Text(GameStrings.label("retry_zone", lang), color = BurnishedGold)
                            }
                            Button(
                                onClick = onReturnToMap,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("return_map_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = TarnishedBrass, contentColor = OnBrassDark)
                            ) {
                                Text(GameStrings.label("return_map", lang), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMenuIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tag: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkForgeOverlay)
                .border(1.dp, AgedBronzeOutline, RoundedCornerShape(12.dp))
                .testTag(tag)
        ) {
            Icon(icon, contentDescription = desc, tint = ParchmentText, modifier = Modifier.size(21.dp))
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(FurnaceEmber),
                contentAlignment = Alignment.Center
            ) {
                Text("$badgeCount", style = MaterialTheme.typography.labelSmall, color = ParchmentText)
            }
        }
    }
}

@Composable
private fun SkillCircleButton(
    label: String,
    cooldownSec: Float,
    enabled: Boolean,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (enabled) RivetedSteelVariant else SootCharcoal.copy(alpha = 0.8f))
            .border(1.5.dp, if (enabled) accent else AgedBronzeOutline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        if (cooldownSec > 0f) {
            Text(
                text = "${String.format("%.1f", cooldownSec)}",
                style = MaterialTheme.typography.labelMedium,
                color = MutedAshText
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) ParchmentText else MutedAshText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VirtualThumbJoystick(
    modifier: Modifier = Modifier,
    onMove: (Float, Float) -> Unit
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 96f

    Box(
        modifier = modifier
            .size(116.dp)
            .clip(CircleShape)
            .background(DarkForgeOverlay)
            .border(1.5.dp, AgedBronzeOutline, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knob = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val next = knob + dragAmount
                    val len = hypot(next.x, next.y)
                    knob = if (len > maxRadiusPx) {
                        Offset(next.x / len * maxRadiusPx, next.y / len * maxRadiusPx)
                    } else next
                    // Map screen drag (right/down) into isometric world axes
                    val nx = knob.x / maxRadiusPx
                    val ny = knob.y / maxRadiusPx
                    val wx = nx * 0.707f + ny * 0.707f
                    val wy = -nx * 0.707f + ny * 0.707f
                    onMove(wx, wy)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((knob.x * 0.45f).roundToInt(), (knob.y * 0.45f).roundToInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(TarnishedBrass.copy(alpha = 0.85f))
                .border(1.5.dp, BurnishedGold, CircleShape)
        )
    }
}
