package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class BackupIntegrityTest {
    private val present=VaultFile("current","passport.jpg","image/jpeg",120,"sha",
        originalId="lost-original")
    @Test fun missingOriginalIsClearedButPrimaryRestorable(){
        val plan=BackupIntegrity.plan(listOf(VaultCard(files=listOf(present)))){it=="current"}
        assertEquals("",plan.cards.first().files.first().originalId)
        assertEquals(setOf("current"),plan.availableIds)
        assertEquals(1,plan.warnings.size)
    }
    @Test fun missingPrimaryIsReportedWithoutThrowing(){
        val plan=BackupIntegrity.plan(listOf(VaultCard(files=listOf(present)))){false}
        assertTrue(plan.availableIds.isEmpty())
        assertTrue(plan.warnings.any{it.contains("Required")})
    }
    @Test fun completeBackupRetainsHiddenOriginal(){
        val plan=BackupIntegrity.plan(listOf(VaultCard(files=listOf(present)))){true}
        assertEquals("lost-original",plan.cards.first().files.first().originalId)
        assertEquals(setOf("current","lost-original"),plan.availableIds)
        assertTrue(plan.warnings.isEmpty())
    }
}
