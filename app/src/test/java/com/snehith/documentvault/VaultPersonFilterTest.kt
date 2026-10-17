package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultPersonFilterTest {
    @Test fun documentsAndSearchFilterTheSamePerson(){
        val cards=listOf(VaultCard(id="self",profile="self"),
            VaultCard(id="family",profile="family"))
        assertEquals(listOf("family"),VaultPersonFilter.filter(cards,"family").map{it.id})
        assertEquals(2,VaultPersonFilter.filter(cards,"all").size)
    }
}
