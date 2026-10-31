package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class ScanPreviewModeTest {
    private val original=ScanSettings()
    @Test fun anyFilterOrAdjustmentShowsTheProcessedPreview() {
        listOf(original.copy(preset="Auto"),original.copy(preset="B&W"),original.copy(removeShadows=true),
            original.copy(brightness=10),original.copy(contrast=1.2f),original.copy(sharpness=.4f))
            .forEach{assertTrue(it.toString(),ScanPreviewMode.afterSettingsChange(false,original,it))}
    }
    @Test fun unchangedSettingsKeepTheCornerView() =
        assertFalse(ScanPreviewMode.afterSettingsChange(false,original,original))
    @Test fun previewStaysOpenOnFurtherChanges() =
        assertTrue(ScanPreviewMode.afterSettingsChange(true,original,original))
}
