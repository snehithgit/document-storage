package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VaultIndexStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val valid: (String) -> Boolean = { it.startsWith("valid:") }
    @Test fun interruptedCommitPreservesPreviousOcr() {
        val store=VaultIndexStore(folder.root)
        store.commit("valid:old OCR".toByteArray(),valid)
        try {
            store.commit("valid:new OCR".toByteArray(),valid) { throw IOException("Interrupted") }
            fail("Expected interruption")
        } catch (_:IOException) {}
        assertEquals("valid:old OCR",File(folder.root,"vault.json").readText())
        assertEquals("valid:old OCR",File(folder.root,"vault.json.bak").readText())
        assertFalse(File(folder.root,"vault.json.tmp").exists())
    }
    @Test fun corruptedCurrentDoesNotReplaceGoodBackup() {
        val store=VaultIndexStore(folder.root)
        store.commit("valid:first".toByteArray(),valid)
        store.commit("valid:second".toByteArray(),valid)
        File(folder.root,"vault.json").writeText("corrupt")
        store.commit("valid:third".toByteArray(),valid)
        assertEquals("valid:first",File(folder.root,"vault.json.bak").readText())
    }
    @Test fun failedStagingPreservesOldData() {
        val store=VaultIndexStore(folder.root)
        store.commit("valid:old".toByteArray(),valid)
        File(folder.root,"vault.json.tmp").mkdir()
        try { store.commit("valid:new".toByteArray(),valid); fail("Expected write failure") }
        catch (_:IOException) {}
        assertEquals("valid:old",File(folder.root,"vault.json").readText())
    }
}
