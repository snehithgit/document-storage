package com.snehith.documentvault

/** One profile filter is shared by documents and search. */
object VaultPersonFilter {
    fun filter(cards:List<VaultCard>,personId:String):List<VaultCard> =
        if(personId=="all")cards else cards.filter{it.profile==personId}
}
