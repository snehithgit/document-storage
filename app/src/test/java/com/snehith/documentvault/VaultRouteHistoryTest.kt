package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultRouteHistoryTest {
    @Test fun cameraRecoveryDoesNotCreateCropLoop(){
        assertEquals(listOf("home"),VaultRouteHistory.replace(listOf("home","scan"),"scan"))
    }
    @Test fun nonMatchingHistoryIsNotDiscarded(){
        assertEquals(listOf("home","detail"),VaultRouteHistory.replace(listOf("home","detail"),"scan"))
    }
    @Test fun completedImportHasDocumentsAsBackDestination(){
        assertEquals(listOf("cards"),VaultRouteHistory.completedCardStack())
    }
}
