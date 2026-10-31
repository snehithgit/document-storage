package com.snehith.documentvault

/** Plain-language errors. Storage internals, IDs and Java exception text are not for users. */
object UserMessages {
    private val id=Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    // Restore validation rejects these before the vault is touched.
    private val damagedBackup=listOf("checksum","manifest","payload","mismatch","unsafe entry",
        "excessive backup","extraction limit","backup index missing","backup ocr")
    private val internal=listOf("exception","java.","kotlin.","null object","identity","identifier",
        "viewing session","index is out of range")
    fun of(e:Throwable,fallback:String):String {
        val message=e.message.orEmpty()
        val lower=message.lowercase()
        return when {
            e is OutOfMemoryError -> "This file is too large to open on this phone."
            e is SecurityException -> "The app can no longer read that file. Please choose it again."
            e is java.util.zip.ZipException||e is org.json.JSONException||e is java.io.EOFException ->
                "The file is damaged or is not in a supported format."
            damagedBackup.any{it in lower} -> "This backup file is damaged or incomplete. Your vault was not changed."
            "tesseract" in lower -> "The text-recognition pack for this language is not installed. Add it in Settings or choose another language."
            "opencv" in lower -> "The scanner could not start on this phone. Please restart the app and try again."
            message.isBlank()||id.containsMatchIn(message)||internal.any{it in lower} -> fallback
            else -> message
        }
    }
    /** Restore only warns about optional originals; visible pages are never skipped. */
    fun restored(warnings:List<String>):String =
        if(warnings.isEmpty())"Restore complete"
        else "Restore complete. Some original photos used for re-cropping were not in this backup; all document pages were restored."
}
