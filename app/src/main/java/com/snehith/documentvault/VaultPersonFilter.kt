package com.snehith.documentvault

/** One profile filter is shared by documents and search. */
object VaultPersonFilter {
    fun filter(cards:List<VaultCard>,personId:String):List<VaultCard> =
        if(personId=="all")cards else cards.filter{it.profile==personId}
    /** A removed or not-restored person cannot keep hiding every document. */
    fun effective(personId:String,profiles:List<VaultProfile>):String =
        if(personId=="all"||personId=="self"||profiles.any{it.id==personId})personId else "all"
}
