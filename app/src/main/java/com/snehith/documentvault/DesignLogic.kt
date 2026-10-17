package com.snehith.documentvault

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

object DesignLogic{
    val faceHues=listOf(
        0xFF0B5E70L to 0xFF0A3440L,
        0xFF3C4AA8L to 0xFF222B66L,
        0xFF8A5A14L to 0xFF4A2F0AL,
        0xFF1F7A57L to 0xFF0F4533L,
        0xFF4A5A60L to 0xFF222B2EL
    )
    fun hueIndex(kind:String):Int=(kind.lowercase(Locale.ROOT).hashCode().toLong().absoluteValue%
        faceHues.size).toInt()
    /** Never display a relative's name as the document holder. */
    private val relatives=listOf("father","mother","spouse","husband","wife","guardian","nominee","parent")
    private fun relativeLabel(name:String)=relatives.any{name.contains(it,true)}
    fun holder(card:VaultCard):String{
        val priority=listOf("name of applicant","applicant name","cardholder","holder name",
            "account holder","name of holder","certificate name","full name","name")
        val relativeValues=card.fields.filter{relativeLabel(it.name)&&it.value.isNotBlank()}
            .map{it.value.trim().lowercase(Locale.ROOT)}.toSet()
        return priority.firstNotNullOfOrNull{label->
            card.fields.firstOrNull{f->
                f.name.trim().equals(label,true)&&f.value.isNotBlank()&&!relativeLabel(f.name) &&
                    f.value.trim().lowercase(Locale.ROOT) !in relativeValues
            }?.value
        }.orEmpty()
    }
    /** A saved Full Name equal to a parent's field is likely an old OCR mistake. */
    fun hasHolderConflict(card:VaultCard):Boolean{
        val full=card.fields.firstOrNull{it.name.trim().equals("full name",true)}?.value?.trim()
        return !full.isNullOrEmpty() && card.fields.any{relativeLabel(it.name)&&
            it.value.trim().equals(full,true)}
    }
    fun number(card:VaultCard):String{
        val preferred=listOf("passport number","cdc number","pan number","aadhaar number",
            "aadhar number","licence number","license number","account number",
            "identification number","certificate number","document number","number")
        val fromKnown=preferred.firstNotNullOfOrNull{label->
            card.fields.firstOrNull{it.name.trim().equals(label,true)&&it.value.isNotBlank()}?.value
        }
        return fromKnown?:card.fields.firstOrNull{f->
            f.value.isNotBlank()&&!relativeLabel(f.name)&&
                Regex("(?i)\\b(number|no\\.?)$").containsMatchIn(f.name.trim())
        }?.value.orEmpty()
    }
    /** Match the mockup's theme per document kind without using its demo documents. */
    fun facePalette(card:VaultCard):Triple<Long,Long,Long>{
        val value=(card.title+" "+card.kind+" "+card.category).lowercase(Locale.ROOT)
        return when {
            "passport" in value->Triple(0xFF12263FL,0xFF2A5079L,0xFFE8F1F7L)
            "aadhaar" in value->Triple(0xFFF2EBDAL,0xFFDCCFAEL,0xFF2A2418L)
            "pan card" in value||card.kind.equals("PAN",true)->
                Triple(0xFFC9DDECL,0xFF8EB3D3L,0xFF0E2236L)
            "licence" in value||"license" in value||"transport" in value->
                Triple(0xFFE8D4A6L,0xFFC9A262L,0xFF2B1F08L)
            "cdc" in value||"seafarer" in value||"work" in value->
                Triple(0xFF143331L,0xFF2F6A60L,0xFFE4F2EFL)
            "notes" in value||"emergency" in value->
                Triple(0xFF262D30L,0xFF4A565BL,0xFFE8F1F3L)
            else->Triple(0xFF1F3A40L,0xFF3C7480L,0xFFE8F1F3L)
        }
    }
    /** "1 yr 3 mo", "5 mo", "12 days": a short human span for expiry rows. */
    fun humanSpan(days:Long):String{
        val d=days.coerceAtLeast(0L)
        return when {
            d>=365->{val y=d/365;val m=(d%365)/30;if(m>0)"$y yr $m mo" else "$y yr"}
            d>=60->"${d/30} mo"
            d==1L->"1 day"
            else->"$d days"
        }
    }
    fun maskedNumber(card:VaultCard)=number(card).let{if(it.isBlank())"" else ProfileLogic.mask(it)}
    fun daysLeft(card:VaultCard,today:LocalDate=LocalDate.now()):Long?=
        runCatching{ChronoUnit.DAYS.between(today,LocalDate.parse(card.expiry))}.getOrNull()
    fun expiryLabel(card:VaultCard,today:LocalDate=LocalDate.now()):String{
        val days=daysLeft(card,today)?:return if(card.expiry.isBlank())"No expiry recorded" else "Check date"
        return when {
            days<0->"Expired "+(-days)+"d ago"
            days==0L->"Expires today"
            days<=90->"In $days days"
            else->LocalDate.parse(card.expiry)
                .format(DateTimeFormatter.ofPattern("MMM yyyy",Locale.ENGLISH))
        }
    }
    /** Only cards with valid expiry dates contribute to the percentage. */
    fun trackedExpiryCount(cards:List<VaultCard>):Int =
        VaultExpiryRules.trackedCount(cards)
    fun healthyShare(cards:List<VaultCard>,today:LocalDate=LocalDate.now(),
        remindDays:Int=30):Float =
        VaultExpiryRules.healthyShare(cards,remindDays,today)
    fun initials(name:String)=name.trim().split(Regex("\\s+"))
        .filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase(Locale.ROOT)}
        .ifBlank{"?"}
}
