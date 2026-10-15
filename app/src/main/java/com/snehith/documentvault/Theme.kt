package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

object VaultColors {
    val bgTop=Color(0xFFF8F4EC)
    val bgBottom=Color(0xFFEADDC6)
    val surface=Color(0xFFFCFAF6)
    val primary=Color(0xFFF39A2D)
    val onPrimary=Color(0xFF1B1830)
    val primaryText=Color(0xFFB45309)
    val ink=Color(0xFF1B1830)
    val muted=Color(0xFF8B8578)
    val outline=Color(0xFFE6DDCC)
    val tagPrivateBg=Color(0xFFEFEAE0)
    val valid=Color(0xFF2E7D4F)
    val expiring=Color(0xFFB45309)
    val expired=Color(0xFFB3261E)
    val darkBg=Color(0xFF17140F)
    val darkSurface=Color(0xFF221E17)
    val darkPrimary=Color(0xFFF5A94A)
    val darkInk=Color(0xFFF3EDE0)
    val darkMuted=Color(0xFFA39B8B)
    val darkOutline=Color(0xFF3A342A)
    fun statusTint(color:Color)=color.copy(alpha=.12f)
}
val VaultLightScheme=lightColorScheme(
    primary=VaultColors.primary,onPrimary=VaultColors.onPrimary,
    primaryContainer=Color(0xFFFFE4BF),onPrimaryContainer=VaultColors.ink,
    secondary=VaultColors.primaryText,onSecondary=Color.White,
    secondaryContainer=Color(0xFFF9E8D1),onSecondaryContainer=VaultColors.ink,
    tertiary=VaultColors.valid,onTertiary=Color.White,
    background=VaultColors.bgTop,onBackground=VaultColors.ink,
    surface=VaultColors.surface,onSurface=VaultColors.ink,
    surfaceVariant=VaultColors.tagPrivateBg,onSurfaceVariant=VaultColors.muted,
    surfaceContainer=VaultColors.surface,surfaceContainerLow=VaultColors.surface,
    surfaceContainerHigh=Color(0xFFF6F0E6),
    outline=VaultColors.outline,outlineVariant=VaultColors.outline,
    inverseSurface=VaultColors.ink,inverseOnSurface=VaultColors.surface,
    inversePrimary=VaultColors.darkPrimary,
    error=VaultColors.expired,onError=Color.White,
    surfaceTint=VaultColors.primary,
    scrim=VaultColors.ink
)
val VaultDarkScheme=darkColorScheme(
    primary=VaultColors.darkPrimary,onPrimary=VaultColors.onPrimary,
    primaryContainer=Color(0xFF59401E),onPrimaryContainer=VaultColors.darkInk,
    secondary=VaultColors.darkPrimary,onSecondary=VaultColors.onPrimary,
    secondaryContainer=Color(0xFF3A2D1B),onSecondaryContainer=VaultColors.darkInk,
    tertiary=Color(0xFF75D1A0),onTertiary=VaultColors.onPrimary,
    background=VaultColors.darkBg,onBackground=VaultColors.darkInk,
    surface=VaultColors.darkSurface,onSurface=VaultColors.darkInk,
    surfaceVariant=Color(0xFF302A21),onSurfaceVariant=VaultColors.darkMuted,
    surfaceContainer=VaultColors.darkSurface,surfaceContainerLow=VaultColors.darkSurface,
    surfaceContainerHigh=Color(0xFF302A21),
    outline=VaultColors.darkOutline,outlineVariant=VaultColors.darkOutline,
    inverseSurface=VaultColors.darkInk,inverseOnSurface=VaultColors.onPrimary,
    inversePrimary=VaultColors.primaryText,
    error=Color(0xFFFF938A),onError=VaultColors.onPrimary,
    surfaceTint=VaultColors.darkPrimary,
    scrim=Color.Black
)
val VaultShapes=Shapes(
    extraSmall=RoundedCornerShape(8.dp),
    small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(24.dp),
    large=RoundedCornerShape(24.dp),
    extraLarge=RoundedCornerShape(32.dp)
)
val VaultTypography=Typography(
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold)
)
@Composable fun VaultTheme(dark:Boolean,content:@Composable()->Unit){
    MaterialTheme(
        colorScheme=if(dark)VaultDarkScheme else VaultLightScheme,
        shapes=VaultShapes,
        typography=VaultTypography,
        content=content
    )
}
@Composable fun vaultWarmBrush(dark:Boolean):Brush =
    if(dark)Brush.verticalGradient(listOf(VaultColors.darkBg,VaultColors.darkBg))
    else Brush.verticalGradient(listOf(VaultColors.bgTop,VaultColors.bgBottom))
