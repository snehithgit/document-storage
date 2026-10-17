package com.snehith.documentvault

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.UUID

/** User-authored profile records are plaintext, like every other vault index value. */
data class ProfileEntry(
    val id:String=UUID.randomUUID().toString(), val personId:String,
    val section:String,val label:String,val cardId:String="",val fieldId:String="",
    val value:String="",val sensitive:Boolean=false
){val linked get()=cardId.isNotBlank()&&fieldId.isNotBlank()}
data class ResolvedEntry(val entry:ProfileEntry,val value:String,val sourceTitle:String,val sourceMissing:Boolean)
data class SourceAlternative(val cardId:String,val fieldId:String,val cardTitle:String,val value:String)
class RevealGate(private val windowMs:Long=60_000L){
    private var until=0L
    fun isOpen(now:Long)=now<until
    fun open(now:Long){until=now+windowMs}
    fun close(){until=0}
}
object ProfileLogic{
    val sections=listOf("Identity","Contact","Travel","Work","Other")
    const val CLIPBOARD_CLEAR_MS=30_000L
    private fun key(label:String)=label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"),"")
    fun keyGroup(label:String):String=when(val k=key(label)){
        "dob","dateofbirth","birthdate"->"dob"
        "fullname","name","holdername","accountholder"->"name"
        "passportno","passportnumber"->"passportno"
        "expirydate","dateofexpiry","validuntil"->"expiry"
        "issuedate","dateofissue"->"issue"
        "phone","phonenumber","mobile","mobilenumber"->"phone"
        "email","emailaddress","emailid"->"email"
        "address","residentialaddress"->"address"
        else->k
    }
    private fun tokens(s:String)=s.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter{it.isNotBlank()}.toSet()
    fun sectionFor(label:String):String {
        val t=tokens(label)
        fun any(vararg words:String)=words.any{it in t}
        return when{
            any("name","dob","birth","gender","nationality","aadhaar","aadhar","pan","identification","id")->"Identity"
            any("phone","mobile","email","address")->"Contact"
            any("passport","visa","cdc","expiry","issue","issued")->"Travel"
            any("employer","company","certificate","coc","stcw","designation","rank","medical")->"Work"
            else->"Other"
        }
    }
    private val privateWords=setOf("number","no","account","ifsc","swift","iban","passport",
        "aadhaar","aadhar","pan","ssn","cdc","license","licence","cvv","password","pin")
    fun isSensitive(label:String)=tokens(label).any{it in privateWords}
    fun mask(value:String):String{
        if(value.isBlank())return "—"
        val tail=value.filter{it.isLetterOrDigit()}.takeLast(4)
        return "••••"+if(value.length>6)" $tail" else ""
    }
    private val dates=listOf("yyyy-MM-dd","d/M/yyyy","d-M-yyyy","d.M.yyyy","d MMM yyyy","MMM d, yyyy")
        .map{DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH)}
    fun normalize(value:String):String{
        val v=value.trim()
        for(format in dates){
            val parsed=runCatching{LocalDate.parse(v,format)}.getOrNull()
            if(parsed!=null)return parsed.toString()
        }
        return v.lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    }
    fun resolve(e:ProfileEntry,cards:List<VaultCard>):ResolvedEntry {
        if(!e.linked)return ResolvedEntry(e,e.value,"",false)
        val card=cards.firstOrNull{it.id==e.cardId}
        val field=card?.fields?.firstOrNull{it.id==e.fieldId}
        return if(card==null||field==null)ResolvedEntry(e,e.value,card?.title.orEmpty(),true)
        else ResolvedEntry(e,field.value,card.title,false)
    }
    /** Drops the stored last-known value of sensitive entries linked to a deleted card. */
    fun scrubSensitive(list:List<ProfileEntry>,deletedCardId:String):List<ProfileEntry> =
        list.map{e-> if(e.cardId==deletedCardId&&(e.sensitive||isSensitive(e.label))) e.copy(value="") else e}
    fun alternatives(e:ProfileEntry,cards:List<VaultCard>):List<SourceAlternative>{
        val current=normalize(resolve(e,cards).value)
        val group=keyGroup(e.label)
        // Pool: the person's own documents plus the document this entry is linked to,
        // so a card filed under another profile can still offer its alternatives.
        return cards.filter{it.profile==e.personId||it.id==e.cardId}.flatMap{c->
            c.fields.filter{keyGroup(it.name)==group &&it.value.isNotBlank() &&
                !(c.id==e.cardId&&it.id==e.fieldId)}.map{
                SourceAlternative(c.id,it.id,c.title,it.value)
            }
        }.filter{normalize(it.value)!=current}
    }
    fun buildEntries(personId:String,card:VaultCard,fields:List<DataField>,
        existing:List<ProfileEntry>):List<ProfileEntry>{
        val existingLinks=existing.filter{it.personId==personId&&it.linked}
            .map{it.cardId to it.fieldId}.toSet()
        return fields.filter{it.value.isNotBlank()&&(card.id to it.id) !in existingLinks}
            .map{ProfileEntry(personId=personId,section=sectionFor(it.name),label=it.name,
                cardId=card.id,fieldId=it.id,value=it.value,sensitive=isSensitive(it.name))}
    }
    fun asText(rows:List<ResolvedEntry>,includeSensitive:Boolean)=
        rows.filter{includeSensitive||!it.entry.sensitive}
            .joinToString("\n"){it.entry.label+": "+it.value}
}
