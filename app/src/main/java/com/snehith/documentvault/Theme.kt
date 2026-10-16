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
import androidx.compose.ui.unit.sp

/** Shared, accessible palette. OLED is the default; Graphite is an optional surface lift. */
object VaultColors {
    val darkBg = Color(0xFF000000)
    val darkSurface = Color(0xFF101114)
    val darkPrimary = Color.White
    val darkInk = Color.White
    val darkMuted = Color(0xFFB6BCC7)
    val darkOutline = Color(0xFF292D35)

    val bgTop = Color(0xFF0B0D10)
    val bgBottom = bgTop
    val surface = Color(0xFF191B20)
    val primary = Color.White
    val onPrimary = Color.Black
    val primaryText = Color.White
    val ink = Color.White
    val muted = Color(0xFFB6BCC7)
    val outline = darkOutline
    val tagPrivateBg = Color(0xFF191B20)

    val syntax = Color(0xFF9FD7F2)
    val keyword = syntax // legacy token; not used for decorated user text
    val valid = Color(0xFF89D9A7)
    val expiring = Color(0xFFFFD18A)
    val expired = Color(0xFFFF9898)
    val lineNumber = Color(0xFF858D9A)
    fun statusTint(color: Color) = color.copy(alpha = .12f)
}

private fun palette(bg:Color,panel:Color,raised:Color)=darkColorScheme(
    primary=Color.White,onPrimary=Color.Black,
    primaryContainer=raised,onPrimaryContainer=Color.White,
    secondary=VaultColors.syntax,onSecondary=Color.Black,
    secondaryContainer=raised,onSecondaryContainer=Color.White,
    tertiary=VaultColors.valid,onTertiary=Color.Black,
    background=bg,onBackground=Color.White,
    surface=panel,onSurface=Color.White,
    surfaceVariant=raised,onSurfaceVariant=VaultColors.muted,
    surfaceContainer=panel,surfaceContainerLow=panel,surfaceContainerHigh=raised,
    outline=VaultColors.darkOutline,outlineVariant=VaultColors.darkOutline,
    inverseSurface=Color.White,inverseOnSurface=Color.Black,
    inversePrimary=VaultColors.syntax,
    error=VaultColors.expired,onError=Color.Black,
    surfaceTint=Color.Transparent,scrim=Color.Black
)

val VaultDarkScheme=palette(VaultColors.darkBg,VaultColors.darkSurface,Color(0xFF191B20))
val VaultLightScheme=palette(VaultColors.bgTop,VaultColors.surface,Color(0xFF22252B))
val VaultShapes=Shapes(
    extraSmall=RoundedCornerShape(8.dp),
    small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(13.dp),
    large=RoundedCornerShape(16.dp),
    extraLarge=RoundedCornerShape(20.dp)
)
val VaultTypography=Typography(
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=25.sp,lineHeight=31.sp),
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=26.sp,lineHeight=32.sp),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=18.sp,lineHeight=24.sp),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=23.sp),
    bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=15.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=13.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=14.sp),
    labelMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=13.sp),
    labelSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp)
)
@Composable fun VaultTheme(dark:Boolean,content:@Composable ()->Unit) {
    MaterialTheme(
        colorScheme=if(dark) VaultDarkScheme else VaultLightScheme,
        shapes=VaultShapes,typography=VaultTypography,content=content
    )
}
@Composable fun vaultWarmBrush(dark:Boolean):Brush =
    Brush.verticalGradient(if(dark)listOf(VaultColors.darkBg,VaultColors.darkBg)
        else listOf(VaultColors.bgTop,VaultColors.bgBottom))
