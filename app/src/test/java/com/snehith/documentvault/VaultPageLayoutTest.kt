package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultPageLayoutTest {
    @Test fun idCardsUseLandscapeLayout(){
        assertEquals("ID card / landscape",VaultPageLayout.kind(1.586f))
        assertTrue(VaultPageLayout.widthFraction(1.586f)>.9f)
    }
    @Test fun a4PagesArePortraitAndReceiptsAreTall(){
        assertEquals("Portrait page",VaultPageLayout.kind(.707f))
        assertEquals("Long document",VaultPageLayout.kind(.45f))
        assertTrue(VaultPageLayout.widthFraction(.45f)<
            VaultPageLayout.widthFraction(.707f))
    }
    @Test fun missingExpiryDatesAreNotTracked(){
        assertEquals(0,VaultExpiryRules.trackedCount(listOf(VaultCard(expiry=""))))
    }
}
