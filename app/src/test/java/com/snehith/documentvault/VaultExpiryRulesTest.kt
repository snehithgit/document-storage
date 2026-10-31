package com.snehith.documentvault

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class VaultExpiryRulesTest {
    private val today=LocalDate.of(2026,10,16)
    @Test fun renewedVersionSuppressesOldExpiredVersion(){
        val old=VaultCard(id="old",expiry="2026-01-01")
        val renewed=VaultCard(id="new",expiry="2028-01-01",previousId="old")
        assertEquals(listOf("new"),VaultExpiryRules.active(listOf(old,renewed)).map{it.id})
        assertTrue(VaultExpiryRules.needsAttention(listOf(old,renewed),30,today).isEmpty())
    }
    @Test fun customReminderWindowIsRespected(){
        val card=VaultCard(expiry="2026-12-01")
        assertEquals("Valid",VaultExpiryRules.status(card,30,today))
        assertEquals("Expiring",VaultExpiryRules.status(card,60,today))
    }
    @Test fun invalidDatesCountAsAttentionButNotHealthy(){
        val card=VaultCard(expiry="2026-99-99")
        assertEquals(listOf(card),VaultExpiryRules.needsAttention(listOf(card),30,today))
        assertEquals(0,VaultExpiryRules.trackedCount(listOf(card),today))
    }
}
