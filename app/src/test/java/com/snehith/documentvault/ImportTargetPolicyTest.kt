package com.snehith.documentvault

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportTargetPolicyTest {
    @Test fun generalImportCanCreateNewDocument() {
        ImportTargetPolicy.requireAvailable(null,emptySet())
    }
    @Test fun existingTargetAccepted() {
        ImportTargetPolicy.requireAvailable("passport",setOf("passport","license"))
    }
    @Test(expected=IllegalStateException::class)
    fun missingTargetRefusedBeforeImport() {
        ImportTargetPolicy.requireAvailable("removed",setOf("passport"))
    }
}
