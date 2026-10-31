package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultSaveVisibilityTest {
    private val source=VaultFile("image-1","page.jpg","image/jpeg",1234,"hash-1")
    private val added=VaultFile("pdf-2","additional.pdf","application/pdf",4321,"hash-2")
    private val original=VaultCard(id="document-1",title="Old title",category="Identity",
        profile="self",created=1000L,files=listOf(source),
        fields=listOf(DataField(name="Full Name",value="Original",id="name")))

    @Test fun editPreservesAttachmentsAndExistingIdentity(){
        val edited=original.copy(title="Updated title",category="Personal",profile="asha",
            fields=listOf(original.fields.first().copy(value="Updated")))
        val latest=original.copy(files=listOf(source,added),created=1000L,
            fields=original.fields+DataField(name="OCR",value="New OCR",id="ocr"))
        val result=VaultEditedCard.merge(original,edited,latest)
        assertEquals("document-1",result.id)
        assertEquals("Updated title",result.title)
        assertEquals("Personal",result.category)
        assertEquals("asha",result.profile)
        assertEquals(1000L,result.created)
        assertEquals(listOf(source,added),result.files)
        assertEquals(listOf("Updated","New OCR"),result.fields.map{it.value})
    }
    @Test fun reassignmentSwitchesFromRestrictedProfileToEveryone(){
        val edited=original.copy(profile="asha",category="Personal")
        val result=VaultEditedCard.merge(original,edited,original)
        val filter=VaultSavedDocumentVisibility.profileAfterSave("self",result.profile)
        assertEquals("all",filter)
        assertTrue(VaultPersonFilter.filter(listOf(result),filter).any{it.id==result.id})
        assertEquals(listOf(result),DocumentBrowseLogic.browse(
            VaultPersonFilter.filter(listOf(result),filter),"All","Recent"))
    }
    @Test fun keepMatchingProfileAndEveryoneSelections(){
        assertEquals("self",VaultSavedDocumentVisibility.profileAfterSave("self","self"))
        assertEquals("all",VaultSavedDocumentVisibility.profileAfterSave("all","asha"))
        assertEquals("asha",VaultSavedDocumentVisibility.profileAfterSave("asha","asha"))
    }
    @Test(expected=IllegalArgumentException::class)
    fun editingCannotReplaceDocumentId(){
        VaultEditedCard.merge(original,original.copy(id="different"),original)
    }
}
