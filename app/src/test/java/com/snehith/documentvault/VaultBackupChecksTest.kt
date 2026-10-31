package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class VaultBackupChecksTest {
    @Test fun knownSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            VaultBackupChecks.sha256(ByteArrayInputStream("abc".toByteArray())))
    }
    @Test fun mismatchRejectsRestore() {
        val file=File.createTempFile("vault-phase1",".dat")
        try {
            file.writeText("actual bytes")
            try {
                VaultBackupChecks.verify(file,file.length(),"0".repeat(64))
                fail("Must reject a tampered attachment")
            }catch(_:IOException) {}
        }finally{file.delete()}
    }
    @Test fun matchingFileVerifies() {
        val file=File.createTempFile("vault-phase1",".dat")
        try {
            file.writeText("original")
            val sha=VaultBackupChecks.sha256(file.inputStream())
            VaultBackupChecks.verify(file,file.length(),sha)
        }finally{file.delete()}
    }
    @Test fun boundedExtractionRejectsOversize() {
        val file=File.createTempFile("vault-phase1",".dat")
        try {
            try {
                VaultBackupChecks.boundedCopy(ByteArrayInputStream(ByteArray(40)),file,20)
                fail("Limit not enforced")
            }catch(_:IOException) {}
        }finally{file.delete()}
    }
    @Test fun boundedExtractionPreservesContent() {
        val file=File.createTempFile("vault-phase1",".dat")
        try {
            assertEquals(3L,VaultBackupChecks.boundedCopy(
                ByteArrayInputStream("abc".toByteArray()),file,4))
            assertEquals("abc",file.readText())
        }finally{file.delete()}
    }
}
