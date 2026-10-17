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

/** Teal Paper: warm-cool paper light mode and OLED-friendly dark surfaces. */
object VaultColors{
    val lightBg=Color(0xFFE6ECED)
    val lightPanel=Color(0xFFF4F7F7)
    val lightPanel2=Color.White
    val lightInk=Color(0xFF0B1416)
    val lightInk2=Color(0xFF44565B)
    val lightInk3=Color(0xFF7F9096)
    val lightLine=Color(0xFFD3DCDE)
    val lightAccent=Color(0xFF07687C)
    val lightAccentSoft=Color(0xFFCFE6EB)
    val darkBg=Color(0xFF040809)
    val darkPanel=Color(0xFF0C1316)
    val darkSurface=Color(0xFF131C20)
    val darkInk=Color(0xFFE8F1F3)
    val darkInk2=Color(0xFF9DB0B5)
    val darkMuted=darkInk2
    val darkLine=Color(0xFF1C292D)
    val darkOutline=darkLine
    val darkAccent=Color(0xFF58CBE4)
    val darkAccentSoft=Color(0xFF0F2B33)
    // Backward-compatible tokens used by the existing scanner and utility screens.
    val bgTop=lightBg
    val bgBottom=lightBg
    val surface=lightPanel2
    val primary=lightAccent
    val onPrimary=Color.White
    val primaryText=Color(0xFF2A8FA6)
    val ink=lightInk
    val muted=lightInk2
    val outline=lightLine
    val tagPrivateBg=lightAccentSoft
    val darkPrimary=darkAccent
    val valid=Color(0xFF1F9D6E)
    val expiring=Color(0xFFD98A1F)
    val expired=Color(0xFFD9503A)
    val syntax=Color(0xFF2A8FA6)
    val keyword=Color(0xFF2A8FA6)
    val lineNumber=Color(0xFF7F9096)
    fun statusTint(c:Color)=c.copy(alpha=.12f)
}
val VaultLightScheme=lightColorScheme(
    primary=VaultColors.lightAccent,onPrimary=Color.White,
    primaryContainer=VaultColors.lightAccentSoft,onPrimaryContainer=VaultColors.lightAccent,
    secondary=VaultColors.lightAccent,onSecondary=Color.White,
    secondaryContainer=VaultColors.lightAccentSoft,onSecondaryContainer=VaultColors.lightInk,
    tertiaryContainer=VaultColors.lightAccentSoft,onTertiaryContainer=VaultColors.lightInk,
    background=VaultColors.lightPanel,onBackground=VaultColors.lightInk,
    surface=VaultColors.lightPanel2,onSurface=VaultColors.lightInk,
    surfaceVariant=VaultColors.lightPanel,onSurfaceVariant=VaultColors.lightInk2,
    surfaceContainer=VaultColors.lightPanel2,surfaceContainerLow=VaultColors.lightPanel,
    surfaceContainerHigh=VaultColors.lightAccentSoft,
    outline=VaultColors.lightLine,outlineVariant=VaultColors.lightLine,
    inverseSurface=VaultColors.lightInk,inverseOnSurface=VaultColors.lightPanel,
    inversePrimary=VaultColors.darkAccent,
    tertiary=VaultColors.valid,error=VaultColors.expired,onError=Color.White,
    surfaceTint=VaultColors.lightAccent,scrim=Color.Black
)
val VaultDarkScheme=darkColorScheme(
    primary=VaultColors.darkAccent,onPrimary=Color(0xFF031418),
    primaryContainer=VaultColors.darkAccentSoft,onPrimaryContainer=VaultColors.darkAccent,
    secondary=VaultColors.darkAccent,onSecondary=Color(0xFF031418),
    secondaryContainer=VaultColors.darkAccentSoft,onSecondaryContainer=VaultColors.darkInk,
    tertiaryContainer=VaultColors.darkAccentSoft,onTertiaryContainer=VaultColors.darkInk,
    background=VaultColors.darkPanel,onBackground=VaultColors.darkInk,
    surface=VaultColors.darkSurface,onSurface=VaultColors.darkInk,
    surfaceVariant=VaultColors.darkPanel,onSurfaceVariant=VaultColors.darkInk2,
    surfaceContainer=VaultColors.darkSurface,surfaceContainerLow=VaultColors.darkPanel,
    surfaceContainerHigh=VaultColors.darkAccentSoft,
    outline=VaultColors.darkLine,outlineVariant=VaultColors.darkLine,
    inverseSurface=VaultColors.darkPanel,inverseOnSurface=VaultColors.darkInk,
    inversePrimary=VaultColors.lightAccent,
    tertiary=Color(0xFF6FD9A9),error=Color(0xFFF08A72),onError=Color.Black,
    surfaceTint=VaultColors.darkAccent,scrim=Color.Black
)
val VaultShapes=Shapes(
    extraSmall=RoundedCornerShape(6.dp),
    small=RoundedCornerShape(10.dp),
    medium=RoundedCornerShape(16.dp),
    large=RoundedCornerShape(22.dp),
    extraLarge=RoundedCornerShape(28.dp)
)
val VaultTypography=Typography(
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,
        fontSize=30.sp,lineHeight=34.sp),
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,
        fontSize=25.sp,lineHeight=30.sp),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=20.sp),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp),
    bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp),
    bodySmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.5.sp),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=14.sp),
    labelMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=12.sp),
    labelSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=11.sp)
)
@Composable fun VaultTheme(dark:Boolean,content:@Composable ()->Unit){
    MaterialTheme(colorScheme=if(dark)VaultDarkScheme else VaultLightScheme,
        shapes=VaultShapes,typography=VaultTypography,content=content)
}
@Composable fun vaultWarmBrush(dark:Boolean):Brush =
    Brush.verticalGradient(if(dark)listOf(VaultColors.darkPanel,VaultColors.darkPanel)
        else listOf(VaultColors.lightPanel,VaultColors.lightPanel))
