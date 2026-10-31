package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The corner view shows the untouched photo, so a filter change must switch to the
 * processed preview or it looks as if nothing happened. */
object ScanPreviewMode {
    fun afterSettingsChange(showingResult:Boolean,old:ScanSettings,next:ScanSettings):Boolean =
        showingResult||old!=next
}

@Composable fun ScanEnhanceControls(settings:ScanSettings,onChange:(ScanSettings)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Filters",color=mockupPalette.ink,style=MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            ScanEnhance.presets.forEach{preset->
                MockupChip(if(preset=="B&W")"Black & White" else preset,
                    settings.preset==preset){onChange(settings.copy(preset=preset))}
            }
        }
        var showAdjust by remember{mutableStateOf(false)}
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={showAdjust=!showAdjust}){
                // Short label: the long one pushed the shadow switch off narrow screens.
                Text(if(showAdjust)"Hide adjustments" else "Adjust")
            }
            Spacer(Modifier.weight(1f))
            Text("Remove shadows",style=MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(5.dp))
            Switch(checked=settings.removeShadows,
                onCheckedChange={onChange(settings.copy(removeShadows=it))})
        }
        if(showAdjust){
            Text("Brightness ${settings.brightness}",style=MaterialTheme.typography.labelSmall)
            Slider(value=settings.brightness.toFloat(),onValueChange={onChange(settings.copy(brightness=it.roundToInt()))},
                valueRange=-70f..70f)
            Text("Contrast ${(settings.contrast*100).roundToInt()}%",style=MaterialTheme.typography.labelSmall)
            Slider(value=settings.contrast,onValueChange={onChange(settings.copy(contrast=it))},valueRange=.6f..1.8f)
            Text("Sharpness ${(settings.sharpness*100).roundToInt()}%",style=MaterialTheme.typography.labelSmall)
            Slider(value=settings.sharpness,onValueChange={onChange(settings.copy(sharpness=it))},valueRange=0f..1.5f)
        }
    }
}
