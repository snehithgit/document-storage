package com.snehith.documentvault

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One self-contained index (including OCR) is the commit record. Temporary files
 * are never recovery candidates. A failed commit leaves the old record readable. */
internal class VaultIndexStore(private val directory: File) {
    private fun write(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    private fun replace(source:File, target:File) {
        Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
    }
    fun commit(bytes: ByteArray, valid: (String) -> Boolean,
               beforeCommit: () -> Unit = {}) {
        val current = File(directory, "vault.json")
        val staged = File(directory, "vault.json.tmp")
        val backupStage = File(directory, "vault.json.bak.tmp")
        try {
            write(staged, bytes)
            if (current.isFile && valid(current.readText(Charsets.UTF_8))) {
                write(backupStage, current.readBytes())
                replace(backupStage,File(directory, "vault.json.bak"))
            }
            beforeCommit()
            replace(staged,current)
        } finally {
            staged.delete()
            backupStage.delete()
        }
    }
}
