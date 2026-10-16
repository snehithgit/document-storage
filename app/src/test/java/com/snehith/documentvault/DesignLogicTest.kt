package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DesignLogicTest {
    @Test fun cardHueDoesNotDependOnObjectIdentity(){
        assertEquals(DesignLogic.hueIndex("Passport"),DesignLogic.hueIndex("PASSPORT"))
    }
    @Test fun cardNumberIsMaskedOnFront(){
        val card=VaultCard(fields=listOf(
            DataField("Passport Number","M72498123","manual".length,"manual")))
        assertEquals("•••• 8123",DesignLogic.maskedNumber(card))
    }
    @Test fun expiryIsDeterministic(){
        val card=VaultCard(expiry="2030-10-15")
        assertEquals("In 4 days",DesignLogic.expiryLabel(card,LocalDate.parse("2030-10-11")))
        assertEquals("Expired 2d ago",DesignLogic.expiryLabel(card,LocalDate.parse("2030-10-17")))
    }
    @Test fun noExpiryDatesDoNotClaimHundredPercentHealth(){
        val undated=listOf(VaultCard(expiry=""),VaultCard(expiry="invalid"))
        assertEquals(0,DesignLogic.trackedExpiryCount(undated))
        assertEquals(0f,DesignLogic.healthyShare(undated),0.0001f)
    }
    @Test fun healthPercentageOnlyUsesDocumentsWithValidExpiryDates(){
        val cards=listOf(
            VaultCard(expiry="2030-11-20"),
            VaultCard(expiry="2030-10-15"),
            VaultCard(expiry=""),
            VaultCard(expiry="bad-value")
        )
        val today=LocalDate.parse("2030-10-11")
        assertEquals(2,DesignLogic.trackedExpiryCount(cards))
        assertEquals(.5f,DesignLogic.healthyShare(cards,today),.0001f)
    }
}
