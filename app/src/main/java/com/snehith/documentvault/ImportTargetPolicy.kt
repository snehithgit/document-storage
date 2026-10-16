package com.snehith.documentvault

/** Prevents a lost Add Page destination from silently creating another card. */
object ImportTargetPolicy {
    fun requireAvailable(targetId:String?,cardIds:Set<String>) {
        if(targetId!=null && targetId !in cardIds)
            throw IllegalStateException(
                "The selected document is no longer available. Nothing was added."
            )
    }
}
