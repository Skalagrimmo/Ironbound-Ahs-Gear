package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IronboundDarkScheme = darkColorScheme(
    primary = TarnishedBrass,
    onPrimary = OnBrassDark,
    primaryContainer = RivetedSteelVariant,
    onPrimaryContainer = BurnishedGold,
    secondary = AetherCyan,
    onSecondary = SootCharcoal,
    secondaryContainer = ColorTokens.AetherContainer,
    onSecondaryContainer = AetherGlow,
    tertiary = FurnaceEmber,
    onTertiary = ParchmentText,
    background = SootCharcoal,
    onBackground = ParchmentText,
    surface = CastIronSurface,
    onSurface = ParchmentText,
    surfaceVariant = RivetedSteelVariant,
    onSurfaceVariant = MutedAshText,
    outline = AgedBronzeOutline,
    error = CrimsonStrike,
    onError = ParchmentText
)

private object ColorTokens {
    val AetherContainer = androidx.compose.ui.graphics.Color(0xFF183038)
}

@Composable
fun IronboundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IronboundDarkScheme,
        typography = Typography,
        content = content
    )
}
