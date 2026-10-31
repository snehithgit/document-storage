package com.snehith.documentvault

import java.time.LocalDate
import java.util.Locale

/** Pure browse operations; no storage reads or Compose state in the sorting path. */
object DocumentBrowseLogic {
    /** A category left with no documents (deleted, re-categorized, other person)
     * would show an empty list with no visible chip to clear it. */
    fun effectiveCategory(cards:List<VaultCard>,category:String):String =
        if(category=="All"||cards.any{it.category.ifBlank{"Other"}==category})category else "All"
    fun browse(cards:List<VaultCard>,category:String,sort:String):List<VaultCard> {
        val shown=cards.filter {
            category=="All"||it.category.ifBlank{"Other"}==category
        }
        return when(sort) {
            "Name" -> shown.sortedWith(compareBy<VaultCard> { it.title.lowercase(Locale.ROOT) }
                .thenByDescending { it.created })
            "Category" -> shown.sortedWith(compareBy<VaultCard> { it.category.lowercase(Locale.ROOT) }
                .thenBy { it.title.lowercase(Locale.ROOT) })
            "Expiry" -> shown.sortedWith(compareBy<VaultCard> {
                runCatching { LocalDate.parse(it.expiry).toEpochDay() }.getOrDefault(Long.MAX_VALUE)
            }.thenBy { it.title.lowercase(Locale.ROOT) })
            else -> shown.sortedWith(compareByDescending<VaultCard> { it.created }.thenBy { it.id })
        }
    }
}
