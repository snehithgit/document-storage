package com.snehith.documentvault

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentBrowseLogicTest {
    private val cards=listOf(
        VaultCard(id="1",title="Zeta",category="Travel",expiry="2032-01-01",created=3),
        VaultCard(id="2",title="Alpha",category="Identity",expiry="2027-03-15",created=1),
        VaultCard(id="3",title="Beta",category="Travel",expiry="bad date",created=2),
        VaultCard(id="4",title="Gamma",category="Personal",created=4)
    )
    @Test fun recentIsNewestFirst() {
        assertEquals(listOf("4","1","3","2"),DocumentBrowseLogic.browse(cards,"All","Recent").map{it.id})
    }
    @Test fun filtersUseActualCategories() {
        assertEquals(listOf("1","3"),DocumentBrowseLogic.browse(cards,"Travel","Recent").map{it.id})
    }
    @Test fun expirySortLeavesMissingAndInvalidLast() {
        assertEquals(listOf("2","1","3","4"),
            DocumentBrowseLogic.browse(cards,"All","Expiry").map{it.id})
    }
    @Test fun nameSortIsCaseInsensitive() {
        assertEquals(listOf("Alpha","Beta","Gamma","Zeta"),
            DocumentBrowseLogic.browse(cards,"All","Name").map{it.title})
    }
}
