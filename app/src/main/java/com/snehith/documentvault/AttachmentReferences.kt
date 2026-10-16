package com.snehith.documentvault

/** An attachment can reference a separately retained raw image, including across cards. */
object AttachmentReferences {
    fun referencedIds(cards:List<VaultCard>):Set<String> =
        cards.asSequence().flatMap { it.files.asSequence() }
            .flatMap { sequenceOf(it.id,it.originalId) }
            .filter { it.isNotBlank() }.toSet()

    fun unusedAfter(cards:List<VaultCard>,file:VaultFile):Set<String> {
        val stillUsed=referencedIds(cards)
        return setOf(file.id,file.originalId).filter { it.isNotBlank() && it !in stillUsed }.toSet()
    }
}
