package com.snehith.documentvault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Literal colors from the user's Document Vault.html :root and dark :root.
 * These are the phone-device surface tokens, not the HTML page's outer shadow.
 */
data class MockupPalette(
    val panel:Color,val panelRaised:Color,val ink:Color,val inkMuted:Color,
    val inkFaint:Color,val line:Color,val accent:Color,val accentInk:Color,
    val accentSoft:Color,val good:Color,val goodSoft:Color,
    val warning:Color,val warningSoft:Color,val danger:Color,val dangerSoft:Color,
    val dock:Color,val dockInk:Color,val dockDim:Color
)
object MockupColors {
    val light=MockupPalette(
        panel=Color(0xFFF4F7F7),panelRaised=Color(0xFFFFFFFF),
        ink=Color(0xFF0B1416),inkMuted=Color(0xFF44565B),
        inkFaint=Color(0xFF7F9096),line=Color(0xFFD3DCDE),
        accent=Color(0xFF07687C),accentInk=Color.White,accentSoft=Color(0xFFCFE6EB),
        good=Color(0xFF1B7A57),goodSoft=Color(0xFFD2EBDF),
        warning=Color(0xFFA85D08),warningSoft=Color(0xFFF6E3C6),
        danger=Color(0xFFB23C28),dangerSoft=Color(0xFFF5D9D2),
        dock=Color(0xFF0B1416),dockInk=Color(0xFFE8F1F3),dockDim=Color(0xFF7E9298)
    )
    val dark=MockupPalette(
        panel=Color(0xFF0C1316),panelRaised=Color(0xFF131C20),
        ink=Color(0xFFE8F1F3),inkMuted=Color(0xFF9DB0B5),
        inkFaint=Color(0xFF627479),line=Color(0xFF1C292D),
        accent=Color(0xFF58CBE4),accentInk=Color(0xFF031418),accentSoft=Color(0xFF0F2B33),
        good=Color(0xFF6FD9A9),goodSoft=Color(0xFF0F2E22),
        warning=Color(0xFFF0B45A),warningSoft=Color(0xFF33270F),
        danger=Color(0xFFF08A72),dangerSoft=Color(0xFF38190F),
        dock=Color(0xFFE8F1F3),dockInk=Color(0xFF0B1416),dockDim=Color(0xFF5E7176)
    )
}
val mockupPalette:MockupPalette
    @Composable get()=if(MaterialTheme.colorScheme.primary==VaultColors.darkAccent)
        MockupColors.dark else MockupColors.light
