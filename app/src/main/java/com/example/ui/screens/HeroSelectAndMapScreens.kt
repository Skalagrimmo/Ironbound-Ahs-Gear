package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.GameStrings
import com.example.model.GameLanguage
import com.example.model.HeroClass
import com.example.model.ZoneId
import com.example.ui.theme.*
import com.example.viewmodel.GameUiState
import com.example.viewmodel.LoadedGameAssetsHolder

@Composable
fun HeroSelectScreen(
    state: GameUiState,
    onSelectHero: (HeroClass) -> Unit,
    onEnterWorldMap: () -> Unit,
    onToggleLanguage: (GameLanguage) -> Unit,
    onOpenSettings: () -> Unit
) {
    val lang = state.language
    val assets = LoadedGameAssetsHolder.assets

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SootCharcoal, Color(0xFF151922), SootCharcoal)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = GameStrings.appTitle(lang),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BurnishedGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = GameStrings.subtitle(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedAshText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Quick UA / EN toggle pill
                    FilledTonalButton(
                        onClick = {
                            onToggleLanguage(if (lang == GameLanguage.UA) GameLanguage.EN else GameLanguage.UA)
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("language_quick_toggle"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = RivetedSteelVariant,
                            contentColor = BurnishedGold
                        ),
                        border = BorderStroke(1.dp, AgedBronzeOutline)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(lang.code, style = MaterialTheme.typography.labelMedium)
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RivetedSteelVariant)
                            .border(1.dp, AgedBronzeOutline, RoundedCornerShape(12.dp))
                            .testTag("hero_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = GameStrings.label("settings", lang), tint = ParchmentText)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = GameStrings.label("choose_hero", lang),
                style = MaterialTheme.typography.titleMedium,
                color = ParchmentText
            )

            Spacer(Modifier.height(10.dp))

            // 3 Hero Class Cards
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(HeroClass.entries) { hc ->
                    val selected = state.selectedHeroClass == hc
                    val profile = state.allHeroes[hc]
                    val level = profile?.level ?: 1
                    val scrap = profile?.scrapCurrency ?: 60

                    Card(
                        onClick = { onSelectHero(hc) },
                        modifier = Modifier
                            .width(238.dp)
                            .fillMaxHeight()
                            .testTag("hero_card_${hc.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) Color(0xFF1F2530) else CastIronSurface
                        ),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) TarnishedBrass else AgedBronzeOutline
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val portrait = assets?.heroPortraits?.get(hc)
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SootCharcoal)
                                        .border(1.dp, if (selected) BurnishedGold else AgedBronzeOutline, RoundedCornerShape(12.dp))
                                ) {
                                    if (portrait != null) {
                                        Image(
                                            bitmap = portrait,
                                            contentDescription = GameStrings.heroName(hc, lang),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = GameStrings.heroName(hc, lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) BurnishedGold else ParchmentText,
                                        maxLines = 2
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${GameStrings.label("level", lang)} $level • $scrap ${GameStrings.label("scrap", lang)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AetherCyan
                                    )
                                }
                            }

                            Text(
                                text = GameStrings.heroRole(hc, lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedAshText,
                                maxLines = 2
                            )

                            // Compact base stats row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SootCharcoal.copy(alpha = 0.7f))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatPill("HP", "${hc.baseHp}", PressenRegenColor)
                                StatPill("DMG", "${hc.baseDamage}", FurnaceEmber)
                                StatPill("ARM", "${hc.baseArmor}", AetherCyan)
                                StatPill("CRIT", "${(hc.critChance * 100).toInt()}%", BurnishedGold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Primary Enter World Map CTA
            Button(
                onClick = onEnterWorldMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("enter_world_map_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TarnishedBrass,
                    contentColor = OnBrassDark
                )
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${GameStrings.label("enter_world", lang)} — ${GameStrings.heroName(state.selectedHeroClass, lang)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedAshText)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun WorldMapScreen(
    state: GameUiState,
    onBack: () -> Unit,
    onSelectZone: (ZoneId) -> Unit,
    onDeployZone: (ZoneId) -> Unit,
    onOpenSettings: () -> Unit
) {
    val lang = state.language
    val assets = LoadedGameAssetsHolder.assets
    val selectedZone = state.selectedZone
    val zp = state.zoneProgress[selectedZone]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SootCharcoal)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RivetedSteelVariant)
                            .border(1.dp, AgedBronzeOutline, RoundedCornerShape(12.dp))
                            .testTag("world_map_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = GameStrings.label("switch_hero", lang), tint = ParchmentText)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = GameStrings.label("enter_world", lang),
                            style = MaterialTheme.typography.headlineSmall,
                            color = BurnishedGold
                        )
                        Text(
                            text = "${GameStrings.heroName(state.selectedHeroClass, lang)} • ${GameStrings.label("level", lang)} ${state.allHeroes[state.selectedHeroClass]?.level ?: 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AetherCyan
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RivetedSteelVariant)
                        .border(1.dp, AgedBronzeOutline, RoundedCornerShape(12.dp))
                        .testTag("world_map_settings")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = GameStrings.label("settings", lang), tint = ParchmentText)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Split: Adaptive Column on narrow portrait phones, Row on landscape/tablets
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWide = maxWidth >= 560.dp

                val mapPanel: @Composable (Modifier) -> Unit = { mod ->
                    BoxWithConstraints(
                        modifier = mod
                            .clip(RoundedCornerShape(16.dp))
                            .background(CastIronSurface)
                            .border(1.5.dp, AgedBronzeOutline, RoundedCornerShape(16.dp))
                    ) {
                        val mapBmp = assets?.worldMapBitmap
                        if (mapBmp != null) {
                            Image(
                                bitmap = mapBmp,
                                contentDescription = GameStrings.label("enter_world", lang),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        val boxW = maxWidth
                        val boxH = maxHeight
                        ZoneId.entries.forEach { z ->
                            val isSel = z == selectedZone
                            val cleared = (state.zoneProgress[z]?.clearedCount ?: 0) > 0
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (boxW * z.mapPinX) - 24.dp,
                                        y = (boxH * z.mapPinY) - 24.dp
                                    )
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSel) TarnishedBrass.copy(alpha = 0.92f)
                                        else SootCharcoal.copy(alpha = 0.85f)
                                    )
                                    .border(
                                        width = if (isSel) 2.5.dp else 1.5.dp,
                                        color = if (cleared) PressenRegenColor else BurnishedGold,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectZone(z) }
                                    .testTag("zone_pin_${z.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${z.index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSel) OnBrassDark else ParchmentText
                                )
                            }
                        }
                    }
                }

                val detailCard: @Composable (Modifier) -> Unit = { mod ->
                    Card(
                        modifier = mod,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CastIronSurface),
                        border = BorderStroke(1.dp, AgedBronzeOutline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                                val thumb = assets?.zoneThumbnails?.get(selectedZone)
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = GameStrings.zoneName(selectedZone, lang),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(if (isWide) 126.dp else 104.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, AgedBronzeOutline, RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }

                                Text(
                                    text = GameStrings.zoneName(selectedZone, lang),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = BurnishedGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BadgeChip("${GameStrings.label("rec_lvl", lang)} ${selectedZone.recommendedLevel}", AetherCyan)
                                    BadgeChip("${GameStrings.label("clears", lang)}: ${zp?.clearedCount ?: 0}", PressenRegenColor)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = GameStrings.zoneLore(selectedZone, lang),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ParchmentText,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = { onDeployZone(selectedZone) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("deploy_zone_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FurnaceEmber,
                                    contentColor = ParchmentText
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = GameStrings.label("deploy_zone", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        mapPanel(Modifier.weight(1.15f).fillMaxHeight())
                        detailCard(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        mapPanel(Modifier.weight(0.95f).fillMaxWidth())
                        detailCard(Modifier.weight(1.05f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
