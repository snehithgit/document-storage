package com.snehith.documentvault

object VaultImportGrouping {
    fun nextTarget(explicitId:String?,previousId:String?,groupAsOne:Boolean):String? =
        explicitId?:if(groupAsOne)previousId else null
}
