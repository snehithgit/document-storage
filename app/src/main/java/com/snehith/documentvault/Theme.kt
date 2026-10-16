package com.snehith.documentvault

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Terminal / code-editor palette. Both appearances are deliberately dark:
 * Graphite is gently lifted black; OLED is a true, deeper black.
 * Pure white is the dominant action colour. Cyan is a restrained data accent.
 */
object VaultColors {
    val bgTop=Color(0xFF0D1015)
    val bgBottom=Color(0xFF0D1015)
    val surface=Color(0xFF171B22)
    val primary=Color(0xFFF3F6FA)
    val onPrimary=Color(0xFF0A0D12)
    val primaryText=Color(0xFFE8EFF8)
    val ink=Color(0xFFF3F6FA)
    val muted=Color(0xFF9EA9B9)
    val outline=Color(0xFF303743)
    val tagPrivateBg=Color(0xFF202631)
    val valid=Color(0xFF8BDDAD)
    val expiring=Color(0xFFFFCF8D)
    val expired=Color(0xFFFF9D9D)
    val darkBg=Color(0xFF080A0D)
    val darkSurface=Color(0xFF14181E)
    val darkPrimary=Color(0xFFFFFFFF)
    val darkInk=Color(0xFFF7FAFF)
    val darkMuted=Color(0xFF9EA9B9)
    val darkOutline=Color(0xFF303743)
    val syntax=Color(0xFF9FD7F2)
    val keyword=Color(0xFFC3B5FD)
    val lineNumber=Color(0xFF697484)
    fun statusTint(color:Color)=color.copy(alpha=.12f)
}
val VaultLightScheme=darkColorScheme(
    primary=VaultColors.primary,onPrimary=VaultColors.onPrimary,
    primaryContainer=Color(0xFF2A313B),onPrimaryContainer=VaultColors.ink,
    secondary=VaultColors.syntax,onSecondary=VaultColors.onPrimary,
    secondaryContainer=Color(0xFF202D38),onSecondaryContainer=Color(0xFFE3F4FF),
    tertiary=VaultColors.valid,onTertiary=VaultColors.onPrimary,
    background=VaultColors.bgTop,onBackground=VaultColors.ink,
    surface=VaultColors.surface,onSurface=VaultColors.ink,
    surfaceVariant=VaultColors.tagPrivateBg,onSurfaceVariant=VaultColors.muted,
    surfaceContainer=VaultColors.surface,surfaceContainerLow=Color(0xFF13171D),
    surfaceContainerHigh=Color(0xFF222832),
    outline=VaultColors.outline,outlineVariant=VaultColors.outline,
    inverseSurface=VaultColors.ink,inverseOnSurface=VaultColors.onPrimary,
    inversePrimary=VaultColors.syntax,
    error=VaultColors.expired,onError=VaultColors.onPrimary,
    surfaceTint=VaultColors.primary,scrim=Color.Black
)
val VaultDarkScheme=darkColorScheme(
    primary=VaultColors.darkPrimary,onPrimary=VaultColors.onPrimary,
    primaryContainer=Color(0xFF242B35),onPrimaryContainer=VaultColors.darkInk,
    secondary=VaultColors.syntax,onSecondary=VaultColors.onPrimary,
    secondaryContainer=Color(0xFF1D2A34),onSecondaryContainer=Color(0xFFE3F4FF),
    tertiary=VaultColors.valid,onTertiary=VaultColors.onPrimary,
    background=VaultColors.darkBg,onBackground=VaultColors.darkInk,
    surface=VaultColors.darkSurface,onSurface=VaultColors.darkInk,
    surfaceVariant=Color(0xFF1B2028),onSurfaceVariant=VaultColors.darkMuted,
    surfaceContainer=VaultColors.darkSurface,surfaceContainerLow=Color(0xFF10141A),
    surfaceContainerHigh=Color(0xFF202630),
    outline=VaultColors.darkOutline,outlineVariant=VaultColors.darkOutline,
    inverseSurface=VaultColors.darkInk,inverseOnSurface=VaultColors.onPrimary,
    inversePrimary=VaultColors.syntax,
    error=VaultColors.expired,onError=VaultColors.onPrimary,
    surfaceTint=VaultColors.darkPrimary,scrim=Color.Black
)
val VaultShapes=Shapes(
    extraSmall=RoundedCornerShape(5.dp),
    small=RoundedCornerShape(8.dp),
    medium=RoundedCornerShape(13.dp),
    large=RoundedCornerShape(16.dp),
    extraLarge=RoundedCornerShape(20.dp)
)
val VaultTypography=Typography(
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    labelLarge=TextStyle(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium),
    labelMedium=TextStyle(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium),
    labelSmall=TextStyle(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Normal),
    bodySmall=TextStyle(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Normal)
)
@Composable fun VaultTheme(dark:Boolean,content: @Composable () -> Unit){
    MaterialTheme(
        colorScheme=if(dark)VaultDarkScheme else VaultLightScheme,
        shapes=VaultShapes,
        typography=VaultTypography,
        content=content
    )
}
@Composable fun vaultWarmBrush(dark:Boolean):Brush =
    Brush.verticalGradient(
        if(dark)listOf(VaultColors.darkBg,VaultColors.darkBg)
        else listOf(VaultColors.bgTop,VaultColors.bgBottom)
    )
