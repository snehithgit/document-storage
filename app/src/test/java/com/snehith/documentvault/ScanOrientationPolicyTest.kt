package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class ScanOrientationPolicyTest {
    @Test fun uprightStrongSkipsLaterRecognition(){
        assertTrue(ScanOrientationPolicy.uprightStrong(180,7))
    }
    @Test fun sparseOrSidewaysTextStillNeedsOtherAngles(){
        assertFalse(ScanOrientationPolicy.uprightStrong(180,2))
        assertFalse(ScanOrientationPolicy.uprightStrong(20,7))
    }
}
