package com.snehith.documentvault
import org.junit.Assert.*
import org.junit.Test
class ProfileLogicTest{
    @Test fun maskedValuesNeverExposeFullId(){
        assertEquals("•••• 4567",ProfileLogic.mask("A1234567"))
    }
    @Test fun editingAFieldUpdatesLinkedRecord(){
        val field=DataField("Passport Number","A1234567",-1,"manual",id="field")
        val card=VaultCard(id="card",fields=listOf(field))
        val entry=ProfileEntry(personId="self",section="Travel",label=field.name,
            cardId=card.id,fieldId=field.id,value=field.value)
        assertEquals("A1234567",ProfileLogic.resolve(entry,listOf(card)).value)
        assertEquals("B9999999",ProfileLogic.resolve(entry,listOf(card.copy(fields=listOf(field.copy(value="B9999999"))))).value)
    }
    @Test fun caseInsensitiveDateNormalizationPreventsFalseConflict(){
        assertEquals(ProfileLogic.normalize("12 APR 2030"),ProfileLogic.normalize("2030-04-12"))
    }
    @Test fun revealsExpireAtWindowBoundary(){
        val gate=RevealGate();gate.open(1000)
        assertTrue(gate.isOpen(60999));assertFalse(gate.isOpen(61000))
    }
}
