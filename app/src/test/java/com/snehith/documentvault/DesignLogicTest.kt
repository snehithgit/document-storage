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
    @Test fun emptyVaultIsHealthy(){
        assertEquals(1f,DesignLogic.healthyShare(emptyList()),0.0001f)
    }
}
