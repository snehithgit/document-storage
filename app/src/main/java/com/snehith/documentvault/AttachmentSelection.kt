package com.snehith.documentvault

/** Preserve a user's selected slot when an enhanced image receives a new blob ID. */
object AttachmentSelection {
    fun select(files:List<VaultFile>,selectedId:String,slot:Int):VaultFile? {
        if(files.isEmpty())return null
        return files.firstOrNull{it.id==selectedId} ?: files[slot.coerceIn(0,files.lastIndex)]
    }
}
