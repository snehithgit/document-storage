package com.snehith.documentvault

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The same renewed-card and reminder-window rule for Home, Expiry and the worker. */
object VaultExpiryRules {
    fun active(cards:List<VaultCard>):List<VaultCard>{
        val renewed=cards.map{it.previousId}.filter{it.isNotBlank()}.toSet()
        return cards.filterNot{it.id in renewed}
    }
    fun daysLeft(card:VaultCard,today:LocalDate=LocalDate.now()):Long?=
        runCatching{ChronoUnit.DAYS.between(today,LocalDate.parse(card.expiry))}.getOrNull()
    fun status(card:VaultCard,remindDays:Int,today:LocalDate=LocalDate.now()):String {
        if(card.expiry.isBlank())return "Saved"
        val left=daysLeft(card,today)?:return "Check expiry"
        return when {
            left<0->"Expired"
            left<=remindDays.coerceIn(1,365)->"Expiring"
            else->"Valid"
        }
    }
    fun needsAttention(cards:List<VaultCard>,remindDays:Int,today:LocalDate=LocalDate.now()):List<VaultCard> =
        active(cards).filter {status(it,remindDays,today) in setOf("Expired","Expiring","Check expiry")}
    fun trackedCount(cards:List<VaultCard>,today:LocalDate=LocalDate.now()):Int =
        active(cards).count{daysLeft(it,today)!=null}
    fun healthyShare(cards:List<VaultCard>,remindDays:Int,today:LocalDate=LocalDate.now()):Float{
        val activeDates=active(cards).mapNotNull{daysLeft(it,today)}
        if(activeDates.isEmpty())return 0f
        return activeDates.count{it>remindDays.coerceIn(1,365)}.toFloat()/activeDates.size
    }
}
