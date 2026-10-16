package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class FieldSuggestionsTest {
    @Test fun extractsDigitalTextLabelsAndNormalizesDates() {
        val results=FieldSuggestions.parse("Full Name: TEST PERSON\nPassport Number: A1234567\nDate of Birth: 26/03/1990")
        assertEquals("TEST PERSON",results.first{it.name.equals("Full Name",true)}.value)
        assertTrue(results.any{it.value=="A1234567"})
        assertTrue(results.any{it.value=="1990-03-26"})
    }
    @Test fun blankContentDoesNotInventFields() {
        assertTrue(FieldSuggestions.parse("").isEmpty())
    }
    @Test fun customTemplatesOfferRequiredEditableFields() {
        assertTrue(FieldSuggestions.templates.first{it.name=="Passport"}.fieldNames.contains("Passport Number"))
        assertTrue(FieldSuggestions.templates.first{it.name=="Custom"}.fieldNames.isEmpty())
    }
    @Test fun duplicateLabelsKeepOneSuggestion() {
        val results=FieldSuggestions.parse("Name: FIRST\nName: SECOND")
        assertEquals(1,results.count{it.name.equals("Full Name",true)})
    }
}
