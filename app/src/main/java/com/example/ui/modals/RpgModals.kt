package com.example.ui.modals

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.GameStrings
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.ArenaModal
import com.example.viewmodel.GameUiState

@Composable
fun RpgModalHost(
    state: GameUiState,
    onDismiss: () -> Unit,
    onAssignCombo: (Int, PressenType, PressenTier) -> Unit,
    onEquipItem: (LootItemModel) -> Unit,
    onSalvageItem: (LootItemModel) -> Unit,
    onForgeGear: () -> Unit,
    onUpgradeTalent: (TalentNodeDef) -> Unit,
    onRespecTalents: () -> Unit,
    onSetLanguage: (GameLanguage) -> Unit,
    onSetSvdagPreset: (SvdagQualityPreset) -> Unit,
    onToggleTelemetry: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit
) {
    if (state.activeModal == ArenaModal.NONE) return
    val lang = state.language

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC8080A0D))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CastIronSurface),
            border = BorderStroke(1.5.dp, AgedBronzeOutline)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val title = when (state.activeModal) {
                        ArenaModal.COMBO_LAB -> GameStrings.label("combo_lab", lang)
                        ArenaModal.INVENTORY -> GameStrings.label("inventory", lang)
                        ArenaModal.TALENTS -> GameStrings.label("talents", lang)
                        ArenaModal.SETTINGS -> GameStrings.label("settings", lang)
                        ArenaModal.NONE -> ""
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = BurnishedGold)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RivetedSteelVariant)
                            .testTag("modal_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = GameStrings.label("next_wave", lang), tint = ParchmentText)
                    }
                }

                HorizontalDivider(color = AgedBronzeOutline, modifier = Modifier.padding(vertical = 8.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.activeModal) {
                        ArenaModal.COMBO_LAB -> ComboLabContent(state, onAssignCombo)
                        ArenaModal.INVENTORY -> InventoryContent(state, onEquipItem, onSalvageItem, onForgeGear)
                        ArenaModal.TALENTS -> TalentTreeContent(state, onUpgradeTalent, onRespecTalents)
                        ArenaModal.SETTINGS -> SettingsContent(state, onSetLanguage, onSetSvdagPreset, onToggleTelemetry, onToggleHaptics)
                        ArenaModal.NONE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ComboLabContent(
    state: GameUiState,
    onAssignCombo: (Int, PressenType, PressenTier) -> Unit
) {
    val lang = state.language
    var selectedSlot by remember { mutableIntStateOf(0) }
    var selectedTier by remember { mutableStateOf(PressenTier.MK1) }
    val summary = remember(state.comboSlots) { RpgCatalog.evaluateCombo(state.comboSlots) }
    val level = state.allHeroes[state.selectedHeroClass]?.level ?: 1

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = GameStrings.label("combo_hint", lang),
                style = MaterialTheme.typography.bodySmall,
                color = MutedAshText
            )
            Spacer(Modifier.height(10.dp))

            // 6-slot Remember Me Combo Chain Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0 until 6).forEach { idx ->
                    val slot = state.comboSlots.firstOrNull { it.slotIndex == idx }
                    val unlocked = slot?.unlocked == true || (idx < 4) || (idx == 4 && level >= 2) || (idx == 5 && level >= 3)
                    val pType = slot?.type ?: PressenType.POWER
                    val pTier = slot?.tier ?: PressenTier.MK1
                    val col = pressenColor(pType)
                    val isSelected = selectedSlot == idx

                    Card(
                        onClick = { if (unlocked) selectedSlot = idx },
                        enabled = unlocked,
                        modifier = Modifier
                            .width(106.dp)
                            .height(86.dp)
                            .testTag("combo_slot_$idx"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF28303D) else RivetedSteelVariant
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (!unlocked) AgedBronzeOutline else col
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "#${idx + 1} ${if (unlocked) pTier.label else "🔒"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (unlocked) col else MutedAshText
                            )
                            Text(
                                text = if (unlocked) GameStrings.pressenName(pType, lang) else "Lv.${if (idx == 4) 2 else 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ParchmentText,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Calculated Chain Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SootCharcoal)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCol(GameStrings.label("combo_mult", lang), "${String.format("%.2f", summary.totalDamageMult)}x", PressenPowerColor)
                MetricCol(GameStrings.label("combo_heal", lang), "+${summary.totalHealPerChain} HP", PressenRegenColor)
                MetricCol(GameStrings.label("combo_cdr", lang), "-${String.format("%.1f", summary.totalCooldownSavedSec)}s", PressenCooldownColor)
            }
        }

        // Pressen Tier & Type Picker
        Column {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PressenTier.entries.forEach { t ->
                    val maxAllowed = when (t) {
                        PressenTier.MK1 -> true
                        PressenTier.MK2 -> level >= 2
                        PressenTier.MK3 -> level >= 4
                    }
                    FilterChip(
                        selected = selectedTier == t,
                        onClick = { if (maxAllowed) selectedTier = t },
                        enabled = maxAllowed,
                        label = { Text("MK ${t.label} (×${t.multiplier})") },
                        modifier = Modifier.height(48.dp).testTag("pressen_tier_${t.name}")
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressenType.entries.forEach { pt ->
                    val c = pressenColor(pt)
                    Card(
                        onClick = { onAssignCombo(selectedSlot, pt, selectedTier) },
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .testTag("assign_pressen_${pt.name}"),
                        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.16f)),
                        border = BorderStroke(1.dp, c)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(GameStrings.pressenName(pt, lang), style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
                            Text(GameStrings.pressenDesc(pt, lang), style = MaterialTheme.typography.labelSmall, color = ParchmentText, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryContent(
    state: GameUiState,
    onEquipItem: (LootItemModel) -> Unit,
    onSalvageItem: (LootItemModel) -> Unit,
    onForgeGear: () -> Unit
) {
    val lang = state.language
    val profile = state.allHeroes[state.selectedHeroClass]
    val scrap = profile?.scrapCurrency ?: 0
    val equippedBySlot = state.inventory.filter { it.isEquipped }.associateBy { it.slot }
    val bagItems = state.inventory.filter { !it.isEquipped }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 560.dp

        val paperDollPane: @Composable (Modifier) -> Unit = { mod ->
            Column(
                modifier = mod.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SootCharcoal)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricCol(GameStrings.label("dmg", lang), "${state.computedStats.attackDamage}", FurnaceEmber)
                    MetricCol(GameStrings.label("armor", lang), "${state.computedStats.armor}", AetherCyan)
                    MetricCol(GameStrings.label("hp", lang), "${state.computedStats.maxHp}", PressenRegenColor)
                    MetricCol(GameStrings.label("crit", lang), "${(state.computedStats.critChance * 100).toInt()}%", BurnishedGold)
                }

                Button(
                    onClick = onForgeGear,
                    enabled = scrap >= 40,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("forge_gear_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TarnishedBrass, contentColor = OnBrassDark)
                ) {
                    Icon(Icons.Default.Hardware, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("${GameStrings.label("forge_item", lang)} • $scrap⚙", fontWeight = FontWeight.Bold)
                }

                EquipSlot.entries.forEach { slot ->
                    val eq = equippedBySlot[slot]
                    val rColor = eq?.let { rarityColor(it.rarity) } ?: AgedBronzeOutline
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RivetedSteelVariant)
                            .border(1.dp, rColor, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(GameStrings.slotName(slot, lang), style = MaterialTheme.typography.labelSmall, color = MutedAshText)
                            Text(
                                text = eq?.let { GameStrings.itemName(it.titleKey, lang) } ?: "—",
                                style = MaterialTheme.typography.labelMedium,
                                color = rColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (eq != null) {
                            Text(
                                text = formatAffixesShort(eq.affixes),
                                style = MaterialTheme.typography.labelSmall,
                                color = ParchmentText
                            )
                        }
                    }
                }
            }
        }

        val bagPane: @Composable (Modifier) -> Unit = { mod ->
            Column(modifier = mod) {
                if (bagItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(GameStrings.label("empty_bag", lang), style = MaterialTheme.typography.bodyMedium, color = MutedAshText)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bagItems, key = { it.id }) { item ->
                            val rc = rarityColor(item.rarity)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RivetedSteelVariant),
                                border = BorderStroke(1.dp, rc)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${GameStrings.rarityName(item.rarity, lang)} ${GameStrings.itemName(item.titleKey, lang)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = rc,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${GameStrings.slotName(item.slot, lang)} • ${GameStrings.label("level", lang)} ${item.itemLevel} • ${formatAffixesShort(item.affixes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ParchmentText
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onEquipItem(item) },
                                            modifier = Modifier.height(48.dp).testTag("equip_item_${item.id}"),
                                            colors = ButtonDefaults.buttonColors(containerColor = AetherCyan, contentColor = SootCharcoal)
                                        ) {
                                            Text(GameStrings.label("equip", lang), style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(
                                            onClick = { onSalvageItem(item) },
                                            modifier = Modifier.height(48.dp).testTag("salvage_item_${item.id}"),
                                            border = BorderStroke(1.dp, FurnaceEmber)
                                        ) {
                                            Text("+${item.salvageScrap}⚙", color = FurnaceEmber, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                paperDollPane(Modifier.weight(0.95f).fillMaxHeight())
                bagPane(Modifier.weight(1.1f).fillMaxHeight())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paperDollPane(Modifier.weight(0.9f).fillMaxWidth())
                bagPane(Modifier.weight(1.1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TalentTreeContent(
    state: GameUiState,
    onUpgradeTalent: (TalentNodeDef) -> Unit,
    onRespecTalents: () -> Unit
) {
    val lang = state.language
    val hc = state.selectedHeroClass
    val nodes = RpgCatalog.allTalents.filter { it.heroClass == hc }
    val pts = state.allHeroes[hc]?.talentPointsAvailable ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${GameStrings.label("points_avail", lang)}: $pts",
                style = MaterialTheme.typography.titleMedium,
                color = BurnishedGold
            )
            OutlinedButton(
                onClick = onRespecTalents,
                modifier = Modifier.height(48.dp).testTag("respec_talents_button"),
                border = BorderStroke(1.dp, FurnaceEmber)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = FurnaceEmber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(GameStrings.label("respec", lang), color = FurnaceEmber)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0..1).forEach { branchIdx ->
                val branchNodes = nodes.filter { it.branchIndex == branchIdx }.sortedBy { it.tierRow }
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = SootCharcoal.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, AgedBronzeOutline)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        // Draw connecting tree spine
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width * 0.5f
                            drawLine(
                                color = AgedBronzeOutline,
                                start = Offset(cx, 50f),
                                end = Offset(cx, size.height - 40f),
                                strokeWidth = 5f
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = GameStrings.talentBranchName(hc, branchIdx, lang),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (branchIdx == 0) AetherCyan else FurnaceEmber
                            )

                            branchNodes.forEach { node ->
                                val rank = state.talentRanks[node.id] ?: 0
                                val prereqMet = node.prerequisiteId == null || (state.talentRanks[node.prerequisiteId] ?: 0) > 0
                                val canUpgrade = pts > 0 && prereqMet && rank < node.maxRank

                                Card(
                                    onClick = { if (canUpgrade) onUpgradeTalent(node) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("talent_node_${node.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (rank > 0) Color(0xFF1F2A33) else RivetedSteelVariant
                                    ),
                                    border = BorderStroke(
                                        1.5.dp,
                                        when {
                                            rank >= node.maxRank -> BurnishedGold
                                            canUpgrade -> AetherCyan
                                            else -> AgedBronzeOutline
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = GameStrings.talentName(node.id, lang),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (rank > 0) BurnishedGold else ParchmentText
                                            )
                                            Text(
                                                text = GameStrings.talentDesc(node.id, lang),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedAshText,
                                                maxLines = 2
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SootCharcoal)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "$rank/${node.maxRank}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (rank > 0) PressenRegenColor else MutedAshText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: GameUiState,
    onSetLanguage: (GameLanguage) -> Unit,
    onSetSvdagPreset: (SvdagQualityPreset) -> Unit,
    onToggleTelemetry: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit
) {
    val lang = state.language

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bilingual UA / EN Switcher
        Text(GameStrings.label("language", lang), style = MaterialTheme.typography.titleSmall, color = BurnishedGold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameLanguage.entries.forEach { gl ->
                val sel = state.language == gl
                Button(
                    onClick = { onSetLanguage(gl) },
                    modifier = Modifier.height(48.dp).testTag("settings_lang_${gl.code}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sel) TarnishedBrass else RivetedSteelVariant,
                        contentColor = if (sel) OnBrassDark else ParchmentText
                    )
                ) {
                    Text(if (gl == GameLanguage.UA) "Українська (UA)" else "English (EN)", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Exynos 7870 / Mali-T830 SVDAG Preset
        Text(GameStrings.label("svdag_preset", lang), style = MaterialTheme.typography.titleSmall, color = BurnishedGold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SvdagQualityPreset.entries.forEach { preset ->
                val sel = state.svdagPreset == preset
                val label = when (preset) {
                    SvdagQualityPreset.PERF_EXYNOS_7870 -> "Exynos 7870 (0.50x)"
                    SvdagQualityPreset.BALANCED -> "Balanced (0.75x)"
                    SvdagQualityPreset.NATIVE -> "Native (1.00x)"
                }
                FilterChip(
                    selected = sel,
                    onClick = { onSetSvdagPreset(preset) },
                    label = { Text(label) },
                    modifier = Modifier.height(48.dp).testTag("settings_svdag_${preset.name}")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(GameStrings.label("telemetry_toggle", lang), style = MaterialTheme.typography.bodyMedium, color = ParchmentText)
            Switch(
                checked = state.showTelemetry,
                onCheckedChange = onToggleTelemetry,
                modifier = Modifier.testTag("settings_telemetry_switch")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(GameStrings.label("haptics_toggle", lang), style = MaterialTheme.typography.bodyMedium, color = ParchmentText)
            Switch(
                checked = state.hapticsEnabled,
                onCheckedChange = onToggleHaptics,
                modifier = Modifier.testTag("settings_haptics_switch")
            )
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedAshText)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun pressenColor(p: PressenType): Color = when (p) {
    PressenType.POWER -> PressenPowerColor
    PressenType.REGEN -> PressenRegenColor
    PressenType.COOLDOWN -> PressenCooldownColor
    PressenType.CHAIN -> PressenChainColor
}

private fun rarityColor(r: ItemRarity): Color = when (r) {
    ItemRarity.IRON -> RarityIron
    ItemRarity.BRASS -> RarityBrass
    ItemRarity.AETHER -> RarityAether
    ItemRarity.RELIC -> RarityRelic
    ItemRarity.SUNLESS -> RaritySunless
}

private fun formatAffixesShort(a: ItemAffixes): String {
    val parts = mutableListOf<String>()
    if (a.bonusDamage > 0) parts.add("+${a.bonusDamage} DMG")
    if (a.bonusArmor > 0) parts.add("+${a.bonusArmor} ARM")
    if (a.bonusHp > 0) parts.add("+${a.bonusHp} HP")
    if (a.bonusCritPct > 0) parts.add("+${a.bonusCritPct}% CRIT")
    if (a.bonusLifestealPct > 0) parts.add("+${a.bonusLifestealPct}% LS")
    if (a.bonusCooldownHastePct > 0) parts.add("+${a.bonusCooldownHastePct}% CDR")
    return parts.joinToString(" • ")
}
