package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class CaptureRecoveryPolicyTest {
    @Test fun pendingCameraSuppressesRecovery(){
        assertFalse(CaptureRecoveryPolicy.shouldAttempt("crop",true,true,false))
        assertFalse(CaptureRecoveryPolicy.shouldAttempt("crop",true,false,true))
        assertFalse(CaptureRecoveryPolicy.shouldAttempt("scan",true,false,false))
        assertTrue(CaptureRecoveryPolicy.shouldAttempt("crop",true,false,false))
    }
    @Test fun placeholderCaptureCannotBeDecoded(){
        assertFalse(CaptureRecoveryPolicy.hasPhotoBytes(0L))
        assertTrue(CaptureRecoveryPolicy.hasPhotoBytes(1024L))
    }
}
