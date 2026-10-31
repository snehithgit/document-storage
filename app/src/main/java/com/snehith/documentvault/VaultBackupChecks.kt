package com.snehith.documentvault

import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest

/** Pure, JVM-testable integrity checks for backup payloads. */
object VaultBackupChecks {
    const val MAX_BLOB_BYTES = 512L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 10000
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n > 0) digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(file: File, expectedSize: Long, expectedHash: String) {
        if (expectedSize < 0 || file.length() != expectedSize)
            throw IOException("Backup attachment size mismatch")
        if (expectedHash.isBlank() || expectedHash.length != 64 ||
            !sha256(file.inputStream()).equals(expectedHash, ignoreCase = true))
            throw IOException("Backup attachment checksum mismatch")
    }
    fun boundedCopy(input: InputStream, target: File, limit: Long): Long {
        var copied = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                if (copied > limit - n) throw IOException("Backup entry exceeds size limit")
                output.write(buffer, 0, n)
                copied += n
            }
            output.flush()
        }
        return copied
    }
}
