package com.snehith.documentvault

import java.io.File
import java.io.IOException

/**
 * Scan output files are ephemeral until imported into the vault.
 *
 * Android can clear cache files/directories between the camera capture and
 * crop review. Recreate the camera directory at the time of saving rather than
 * assuming it still exists from camera launch.
 */
object ScanOutputFiles {
    fun ensureParent(file:File) {
        val directory=file.parentFile ?: throw IOException("Scan destination has no directory")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the scan output directory")
        }
        if (!directory.isDirectory) {
            throw IOException("Scan output directory is not available")
        }
    }

    /** Creates a unique, writable JPEG path without depending on prior camera setup. */
    fun newScan(cacheDir:File):File {
        val directory=File(cacheDir,"camera")
        ensureParent(File(directory,"placeholder.jpg"))
        return File.createTempFile("scan-", ".jpg",directory)
    }
}
