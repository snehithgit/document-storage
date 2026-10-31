package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UserMessagesTest {
    private val damaged="This backup file is damaged or incomplete. Your vault was not changed."
    private fun of(message:String?)=UserMessages.of(IOException(message),"Fallback")

    @Test fun restoreValidationFailuresBecomeOnePlainMessage() {
        listOf("Backup attachment checksum mismatch","Backup attachment size mismatch",
            "Backup checksums manifest does not match payloads","Backup OCR text missing",
            "Duplicate or excessive backup entries","Backup exceeds total extraction limit",
            "Backup contains unsupported or unsafe entry: blobs/x","Backup index missing")
            .forEach{assertEquals(it,damaged,of(it))}
    }
    @Test fun brokenFilesAreDescribedPlainly() {
        assertEquals("The file is damaged or is not in a supported format.",
            UserMessages.of(java.util.zip.ZipException("invalid entry CRC (expected 0x1)"),"Fallback"))
        assertEquals("The file is damaged or is not in a supported format.",
            UserMessages.of(org.json.JSONException("Value <html> of type java.lang.String"),"Fallback"))
    }
    @Test fun permissionAndMemoryFailuresAreExplained() {
        assertEquals("The app can no longer read that file. Please choose it again.",
            UserMessages.of(SecurityException("Permission Denial: opening provider"),"Fallback"))
        assertEquals("This file is too large to open on this phone.",UserMessages.of(OutOfMemoryError(),"Fallback"))
    }
    @Test fun internalDetailsAndIdsFallBackToTheScreensWording() {
        assertEquals("Fallback",of(null))
        assertEquals("Fallback",of("java.lang.IllegalStateException: closed"))
        assertEquals("Fallback",of("Attempt to invoke virtual method on a null object reference"))
        assertEquals("Fallback",of("Missing 11111111-1111-4111-8111-111111111111"))
        assertEquals("Fallback",of("Document identity may not change"))
    }
    @Test fun ocrAndScannerStartupHaveDedicatedText() {
        assertTrue(of("Tesseract language data missing: hin").startsWith("The text-recognition pack"))
        assertTrue(UserMessages.of(IllegalStateException("OpenCV could not initialize"),"F").startsWith("The scanner could not start"))
    }
    @Test fun messagesWrittenForUsersPassThrough() {
        listOf("The selected PDF cannot be read","This vault is larger than the 2 GB backup limit.",
            "This vault has too many files for one backup file.",
            "\"Passport.pdf\" appears damaged in the vault. No backup was written.",
            "This backup contains a different version of a file already in your vault. Your vault was not changed.",
            "Backup cancelled: required attachments are missing")
            .forEach{assertEquals(it,of(it))}
    }
    @Test fun restoreNoteNoLongerClaimsSkippedAttachments() {
        assertEquals("Restore complete",UserMessages.restored(emptyList()))
        val note=UserMessages.restored(listOf("Optional originals were not included in this backup"))
        assertFalse(note.contains("skipped"))
        assertTrue(note.contains("all document pages were restored"))
    }
}
