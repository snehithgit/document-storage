package com.snehith.documentvault

/** Pure navigation history rules for replacing a failed/completed screen without looping. */
object VaultRouteHistory {
    fun replace(stack:List<String>,destination:String):List<String> =
        if(stack.lastOrNull()==destination)stack.dropLast(1)else stack
    fun completedCardStack():List<String> = listOf("cards")
}
