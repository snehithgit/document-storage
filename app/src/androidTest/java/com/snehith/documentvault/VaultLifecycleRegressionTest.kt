package com.snehith.documentvault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Executes against the instrumentation APK's own sandbox, never the user's vault.
 * These are real Android persistence tests, not mocks or screenshots.
 */
@RunWith(AndroidJUnit4::class)
class VaultLifecycleRegressionTest {
    private fun sandbox(): Pair<File, VaultRepository> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val root = context.filesDir
        // The test APK owns this directory. Never run cleanup against targetContext.
        File(root, "vault.json").delete()
        File(root, "vault.json.tmp").delete()
        File(root, "vault.json.bak").delete()
        File(root, "blobs").deleteRecursively()
        return root to VaultRepository(context)
    }

    @Test fun ownerReassignmentPersistsAndRemainsDiscoverable() {
        val (_, repo) = sandbox()
        val person = VaultProfile("person-" + UUID.randomUUID(), "Test person")
        repo.saveProfile(person)
        val original = repo.newCard("Custom", "self").copy(title = "Test passport")
        repo.saveCard(original)
        val edited = original.copy(profile = person.id, category = "Identity")
        repo.updateCard(original.id) { latest -> VaultEditedCard.merge(original, edited, latest) }
        val saved = repo.cards.single { it.id == original.id }
        val nextFilter = VaultSavedDocumentVisibility.profileAfterSave("self", saved.profile)
        assertEquals("all", nextFilter)
        assertTrue(VaultPersonFilter.filter(repo.cards.toList(), nextFilter).any { it.id == original.id })
        val reloaded = VaultRepository(InstrumentationRegistry.getInstrumentation().context)
        assertEquals(person.id, reloaded.cards.single { it.id == original.id }.profile)
        assertEquals("Identity", reloaded.cards.single { it.id == original.id }.category)
    }

    @Test fun removingPersonDoesNotRemoveTheirDocuments() {
        val (_, repo) = sandbox()
        val person = VaultProfile("person-" + UUID.randomUUID(), "Test owner")
        repo.saveProfile(person)
        val card = repo.newCard(profile = person.id).copy(title = "Test record")
        repo.saveCard(card)
        repo.removePerson(person.id)
        val reloaded = VaultRepository(InstrumentationRegistry.getInstrumentation().context)
        assertEquals("self", reloaded.cards.single { it.id == card.id }.profile)
    }

    @Test fun editingMetadataDoesNotDropConcurrentFieldChanges() {
        val (_, repo) = sandbox()
        val initial = repo.newCard().copy(
            title = "Original",
            fields = listOf(DataField("Full Name", "Original person", id = "full-name"))
        )
        repo.saveCard(initial)
        repo.updateCard(initial.id) { it.copy(
            fields = it.fields + DataField("Passport Number", "TEST123", id = "ocr-passport")
        ) }
        val edited = initial.copy(title = "Updated",
            fields = initial.fields.map { it.copy(value = "Edited person") })
        repo.updateCard(initial.id) { latest -> VaultEditedCard.merge(initial, edited, latest) }
        val saved = VaultRepository(InstrumentationRegistry.getInstrumentation().context)
            .cards.single { it.id == initial.id }
        assertEquals("Updated", saved.title)
        assertEquals("Edited person", saved.fields.single { it.id == "full-name" }.value)
        assertEquals("TEST123", saved.fields.single { it.id == "ocr-passport" }.value)
    }

    @Test fun invalidExpiryCannotModifyPersistedDocument() {
        val (_, repo) = sandbox()
        val original = repo.newCard().copy(title = "Expiry test")
        repo.saveCard(original)
        try {
            repo.saveCard(original.copy(expiry = "2026-99-99"))
            fail("Expected expiry validation")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        val reloaded = VaultRepository(InstrumentationRegistry.getInstrumentation().context)
        assertEquals("", reloaded.cards.single { it.id == original.id }.expiry)
    }
}
