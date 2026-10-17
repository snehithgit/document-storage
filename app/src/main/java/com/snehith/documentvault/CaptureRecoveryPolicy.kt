package com.snehith.documentvault

/** Guards Retake's empty placeholder file against accidental OpenCV decoding. */
object CaptureRecoveryPolicy {
    fun shouldAttempt(route:String,needsPreview:Boolean,pending:Boolean,retaking:Boolean):Boolean =
        route=="crop"&&needsPreview&&!pending&&!retaking
    fun hasPhotoBytes(length:Long):Boolean = length>0L
}
