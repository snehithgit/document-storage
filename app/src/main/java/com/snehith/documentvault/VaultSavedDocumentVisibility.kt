package com.snehith.documentvault

/** Keep edited cards visible without rewriting their assigned owner or category. */
object VaultSavedDocumentVisibility {
    fun profileAfterSave(activeProfile:String,savedProfile:String):String =
        if(activeProfile=="all"||activeProfile==savedProfile)activeProfile else "all"
}

/** Edit only metadata. Document identity, import time and attached files remain unchanged. */
object VaultEditedCard {
    fun merge(original:VaultCard,edited:VaultCard,latest:VaultCard):VaultCard {
        require(original.id==edited.id && original.id==latest.id){
            "A document's identity may not change during editing"
        }
        return edited.copy(
            id=latest.id,
            created=latest.created,
            files=latest.files,
            fields=VaultFieldUpdates.merge(original.fields,edited.fields,latest.fields)
        )
    }
}
