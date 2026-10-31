package com.snehith.documentvault

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DataField(val name: String, val value: String, val page: Int = -1,
    val source: String = "manual", val id: String = UUID.randomUUID().toString(),
    val confidence:Float=0f)
data class VaultFile(val id: String, val name: String, val mime: String, val bytes: Long, val sha256: String, val extractedText: String = "", val ocrStatus: String = "pending",
    val originalId:String = "", val cropCorners:String = "", val cropRotation:Int = 0)
data class VaultCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New card",
    val kind: String = "Custom",
    val category: String = "Personal",
    val profile: String = "self",
    val tags: String = "",
    val notes: String = "",
    val expiry: String = "",
    val previousId: String = "",
    val created: Long = System.currentTimeMillis(),
    val fields: List<DataField> = emptyList(),
    val files: List<VaultFile> = emptyList()
)
data class VaultProfile(val id: String, val name: String)
data class CardTemplate(val id: String, val name: String, val fieldNames: List<String>)

object FieldSuggestions {
    val templates = listOf(
        CardTemplate("passport", "Passport", listOf("Full Name","Passport Number","Date of Birth","Place of Birth","Issue Date","Expiry Date","Place of Issue","Address")),
        CardTemplate("cdc", "Seafarer CDC", listOf("Full Name","CDC Number","Nationality","Issue Date","Expiry Date","Issuing Authority")),
        CardTemplate("coc", "COC / STCW", listOf("Certificate Name","Certificate Number","Issued By","Issue Date","Expiry Date")),
        CardTemplate("medical", "Medical", listOf("Certificate Number","Exam Date","Expiry Date","Doctor / Clinic")),
        CardTemplate("bank", "Bank Information", listOf("Account Holder","Bank Name","Account Number","IFSC / SWIFT","Branch")),
        CardTemplate("id", "Identity Card", listOf("Full Name","Identification Number","Date of Birth","Address")),
        CardTemplate("aadhaar", "Aadhaar", listOf("Full Name","Aadhaar Number","Date of Birth","Address")),
        CardTemplate("pan", "PAN", listOf("Full Name","PAN Number","Date of Birth","Father's Name")),
        CardTemplate("driving", "Driving Licence", listOf("Full Name","Licence Number","Date of Birth","Expiry Date","Address")),
        CardTemplate("custom", "Custom", emptyList())
    )
    fun parse(text: String): List<DataField> {
        val found = linkedMapOf<String,DataField>()
        fun put(key:String, value:String) { val v=value.trim().trim(':','-',' '); if(v.isNotBlank() && !found.containsKey(key)) found[key]=DataField(key,v,-1,"extracted") }
        val source = text.take(100000)
        val patterns = listOf(
            "Email" to Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "IFSC" to Regex("\\b[A-Z]{4}0[A-Z0-9]{6}\\b"),
            "Phone Number" to Regex("(?<![0-9])(?:\\+?91[- ]?)?[6-9][0-9]{9}(?![0-9])")
        )
        patterns.forEach { (key,re)->re.find(source)?.value?.let { put(key,it) } }
        val lines=source.lines()
        val labelPattern=Regex("(?i)^\\s*(full\\s*name|name|passport\\s*(?:no|number)|document\\s*(?:no|number)|date of birth|dob|expiry\\s*date|date of expiry|issue\\s*date|date of issue|place of issue|address|account\\s*(?:no|number)|certificate\\s*(?:no|number))\\s*[:=-]\\s*(.+)$")
        for((page,line) in lines.withIndex()) {
            val m=labelPattern.find(line.trim()) ?: continue
            val rawName=m.groupValues[1].trim()
            val key=when(rawName.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")){
                "dob","date of birth"->"Date of Birth"
                "expiry date","date of expiry"->"Expiry Date"
                "issue date","date of issue"->"Issue Date"
                "full name","name"->"Full Name"
                "passport no","passport number"->"Passport Number"
                "document no","document number"->"Document Number"
                "place of issue"->"Place of Issue"
                else->rawName.replaceFirstChar{it.uppercase()}
            }
            val rawValue=m.groupValues[2].trim().take(250)
            val value=if(key=="Date of Birth" || key=="Expiry Date" || key=="Issue Date")
                SmartDocuments.normalizeDate(rawValue)?:rawValue else rawValue
            if(value.isNotBlank() && value.length<251) found[key] = DataField(key,value,-1,"extracted")
        }
        return found.values.toList()
    }
}


class VaultRepository(private val context: Context) {
    val cards = mutableStateListOf<VaultCard>()
    val profiles = mutableStateListOf<VaultProfile>()
    val customTemplates = mutableStateListOf<CardTemplate>()
    val entries = mutableStateListOf<ProfileEntry>()
    private val data = File(context.filesDir,"vault.json")
    private val backup = File(context.filesDir,"vault.json.bak")
    private val blobDir = File(context.filesDir,"blobs").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("vault_lock",Context.MODE_PRIVATE)
    private val random=SecureRandom()
    private val pinPrefs=context.getSharedPreferences("vault_pin_attempts",Context.MODE_PRIVATE)
    var recoveryError: String? = null
        private set
    private fun uid() = UUID.randomUUID().toString()

    init { load() }

    // Optional UI access lock. File contents and backups are NOT encrypted.
    fun hasPin() = prefs.contains("pinHash")
    fun setPin(pin:String,currentPin:String?=null) {
        require(pin.length>=4)
        require(!hasPin() || (currentPin != null && checkPin(currentPin))) { "Current PIN is incorrect" }
        val salt=ByteArray(16).also(random::nextBytes)
        prefs.edit().putString("pinSalt",android.util.Base64.encodeToString(salt,2))
            .putString("pinHash",android.util.Base64.encodeToString(pinHash(pin,salt),2)).apply()
    }
    fun clearPin(currentPin:String) {
        require(checkPin(currentPin)) { "Current PIN is incorrect" }
        prefs.edit().remove("pinSalt").remove("pinHash").apply()
        pinPrefs.edit().clear().apply()
    }
    fun checkPin(pin:String):Boolean {
        if(!hasPin())return true
        val now=System.currentTimeMillis()
        if(now<pinPrefs.getLong("blockedUntil",0L))return false
        val success=try {
            val salt=android.util.Base64.decode(prefs.getString("pinSalt",""),2)
            val expected=android.util.Base64.decode(prefs.getString("pinHash",""),2)
            MessageDigest.isEqual(expected,pinHash(pin,salt))
        }catch(_:Exception){false}
        if(success)pinPrefs.edit().clear().apply()
        else {
            val attempts=pinPrefs.getInt("failures",0)+1
            val wait=if(attempts>=5) (30_000L * (1 shl (attempts-5).coerceAtMost(4))) else 0L
            pinPrefs.edit().putInt("failures",attempts)
                .putLong("blockedUntil",if(wait>0)now+wait else 0L).apply()
        }
        return success
    }
    fun pinWaitSeconds():Long = ((pinPrefs.getLong("blockedUntil",0L)-System.currentTimeMillis())
        .coerceAtLeast(0L)+999L)/1000L
    private fun pinHash(pin:String,salt:ByteArray):ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(),salt,150000,256)).encoded

    private fun blob(id:String) = File(blobDir,id+".dat")
    private fun meta(id:String) = File(blobDir,id+".meta")
    private fun textFile(id:String)=File(blobDir,id+".txt")
    fun documentFile(file:VaultFile):File = blob(file.id).also{
        if(!it.isFile)throw FileNotFoundException("Attachment missing: "+file.name)
    }
    fun hasDocument(file:VaultFile)=blob(file.id).isFile
    /** Source survives re-editing; it is never exposed as a duplicate attachment. */
    fun originalDocumentFile(file:VaultFile):File =
        blob(file.originalId.ifBlank{file.id}).also {
            if(!it.isFile)throw FileNotFoundException("Original image is unavailable")
        }
    @Synchronized fun removeOrphan(file:VaultFile){
        require(file.id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid attachment identifier" }
        if(file.id in referencedIds())throw IOException("This file is now attached to a document. Refresh the list.")
        if(file.id in previousReferencedIds())throw IOException("This file is still kept for vault recovery.")
        blob(file.id).delete();meta(file.id).delete();textFile(file.id).delete()
    }

    @Synchronized fun load() {
        cards.clear();profiles.clear();customTemplates.clear();entries.clear();recoveryError=null
        val candidates=listOf(data).filter { it.exists() } +
            listOf(backup).filter { it.exists() }
        var accepted:File?=null
        var needsFieldIdMigration=false
        var needsTextMigration=false
        for (candidate in candidates) {
            try {
                val root=JSONObject(candidate.readText(Charsets.UTF_8))
                val loadedCards=mutableListOf<VaultCard>()
                val loadedProfiles=mutableListOf<VaultProfile>()
                val loadedTemplates=mutableListOf<CardTemplate>()
                val loadedEntries=readEntries(root.optJSONArray("entries"))
                val c=root.getJSONArray("cards")
                var legacy=false
                for(i in 0 until c.length()) {
                    val obj=c.getJSONObject(i)
                    val fs=obj.optJSONArray("fields")?:JSONArray()
                    if((0 until fs.length()).any{j->fs.getJSONObject(j).optString("id").isBlank()})
                        legacy=true
                    loadedCards.add(readCard(obj))
                }
                needsFieldIdMigration=legacy
                needsTextMigration=(0 until c.length()).any { i ->
                    val files=c.getJSONObject(i).optJSONArray("files")?:JSONArray()
                    (0 until files.length()).any { !files.getJSONObject(it).has("extractedText") }
                }
                val p=root.optJSONArray("profiles")?:JSONArray()
                for(i in 0 until p.length()){
                    val e=p.getJSONObject(i);loadedProfiles.add(VaultProfile(e.getString("id"),e.getString("name")))
                }
                val t=root.optJSONArray("templates")?:JSONArray()
                for(i in 0 until t.length()){
                    val e=t.getJSONObject(i)
                    loadedTemplates.add(CardTemplate(e.getString("id"),e.getString("name"),strings(e.optJSONArray("fields"))))
                }
                cards.addAll(loadedCards);profiles.addAll(loadedProfiles);customTemplates.addAll(loadedTemplates);entries.addAll(loadedEntries)
                accepted=candidate;break
            }catch(_:Exception) { /* preserve all candidates for recovery */ }
        }
        if(candidates.isNotEmpty() && accepted==null) {
            recoveryError="The local vault index cannot be read. No documents were deleted."
        }
        if(accepted!=null && accepted!=data) {
            recoveryError="Recovered the previous index. Review Storage Health before saving."
            try {
                VaultIndexStore(context.filesDir).commit(accepted.readBytes(), valid={false})
                recoveryError=null
            } catch(_:Exception) { /* Keep recovery read-only if repair cannot commit. */ }
        }
        if(accepted!=null && (needsFieldIdMigration||needsTextMigration)) {
            // Make generated legacy IDs durable before a later process recreation.
            try{persist()}catch(_:Exception){ /* Cards remain usable; next save will retry. */ }
        }
        // The original valid index always remains available in the rolling backup.
        if(profiles.none { it.id=="self" })profiles.add(0,VaultProfile("self","Me"))
    }
    private fun strings(a:JSONArray?):List<String> = (0 until (a?.length()?:0)).map{a!!.optString(it)}
    private fun readCard(o:JSONObject,useSidecars:Boolean=true):VaultCard {
        val fields=o.optJSONArray("fields")?:JSONArray()
        val files=o.optJSONArray("files")?:JSONArray()
        return VaultCard(
            id=o.getString("id"),title=o.optString("title"),kind=o.optString("kind","Custom"),
            category=o.optString("category","Personal"),profile=o.optString("profile","self"),
            tags=o.optString("tags"),notes=o.optString("notes"),expiry=o.optString("expiry"),
            previousId=o.optString("previousId"),created=o.optLong("created",System.currentTimeMillis()),
            fields=(0 until fields.length()).map { val f=fields.getJSONObject(it);DataField(f.optString("name"),f.optString("value"),f.optInt("page",-1),
                f.optString("source","manual"),f.optString("id").ifBlank{uid()},f.optDouble("confidence",0.0).toFloat()) },
            files=(0 until files.length()).map { val f=files.getJSONObject(it)
                val id=f.getString("id")
                require(id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid attachment identifier" }
                val original=f.optString("originalId")
                require(original.isBlank()||original.matches(Regex("[a-f0-9-]{36}"))) { "Invalid original identifier" }
                val inline=f.optString("extractedText")
                val content=if(useSidecars&&!f.has("extractedText")&&textFile(id).exists())
                    textFile(id).readText(Charsets.UTF_8) else inline
                VaultFile(id,f.optString("name"),f.optString("mime"),f.optLong("bytes"),
                    f.optString("sha256"),content,f.optString("ocrStatus","pending"),
                    f.optString("originalId"),f.optString("cropCorners"),f.optInt("cropRotation",0))
            }
        )
    }
    fun encodeCard(card:VaultCard):String=writeCard(card).toString()
    fun decodeCard(json:String):VaultCard=readCard(JSONObject(json),useSidecars=false)
    private fun writeCard(c:VaultCard):JSONObject=JSONObject()
        .put("id",c.id).put("title",c.title).put("kind",c.kind).put("category",c.category)
        .put("profile",c.profile).put("tags",c.tags).put("notes",c.notes).put("expiry",c.expiry)
        .put("previousId",c.previousId).put("created",c.created)
        .put("fields",JSONArray().apply { c.fields.forEach{ put(JSONObject().put("name",it.name).put("value",it.value).put("page",it.page).put("source",it.source).put("id",it.id).put("confidence",it.confidence.toDouble())) } })
        .put("files",JSONArray().apply { c.files.forEach{ put(JSONObject().put("id",it.id).put("name",it.name).put("mime",it.mime).put("bytes",it.bytes).put("sha256",it.sha256).put("ocrStatus",it.ocrStatus)
            .put("extractedText",it.extractedText).put("originalId",it.originalId).put("cropCorners",it.cropCorners).put("cropRotation",it.cropRotation)) } })
    private fun readEntries(array:JSONArray?):List<ProfileEntry> = (0 until (array?.length()?:0)).mapNotNull{i->
        val obj=array!!.optJSONObject(i)?:return@mapNotNull null
        val person=obj.optString("personId");val label=obj.optString("label")
        if(person.isBlank()||label.isBlank())null else ProfileEntry(
            id=obj.optString("id").ifBlank{uid()},personId=person,
            section=obj.optString("section","Other"),label=label,
            cardId=obj.optString("cardId"),fieldId=obj.optString("fieldId"),
            value=obj.optString("value"),sensitive=obj.optBoolean("sensitive",false))
    }
    private fun writeEntry(e:ProfileEntry):JSONObject=JSONObject()
        .put("id",e.id).put("personId",e.personId).put("section",e.section)
        .put("label",e.label).put("cardId",e.cardId).put("fieldId",e.fieldId)
        .put("value",e.value).put("sensitive",e.sensitive)
    private fun snapshot(sourceCards:List<VaultCard> = cards.toList()):String=JSONObject()
        .put("formatVersion",2)
        .put("cards",JSONArray().apply { sourceCards.forEach{put(writeCard(it))} })
        .put("profiles",JSONArray().apply { profiles.forEach{put(JSONObject().put("id",it.id).put("name",it.name))} })
        .put("templates",JSONArray().apply { customTemplates.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("fields",JSONArray(it.fieldNames)))} })
        .put("entries",JSONArray().apply { entries.forEach{put(writeEntry(it))} })
        .toString()

    @Synchronized fun persist() {
        check(recoveryError==null) { recoveryError ?: "Vault recovery required" }
        // Inline OCR makes the index a single atomic commit record. Legacy sidecars
        // remain untouched so a previous index can still recover its original text.
        VaultIndexStore(context.filesDir).commit(snapshot().toByteArray(Charsets.UTF_8), valid=::validIndex)
    }
    private fun validIndex(text:String):Boolean=runCatching {
        val root=JSONObject(text)
        val array=root.getJSONArray("cards")
        (0 until array.length()).forEach { readCard(array.getJSONObject(it)) }
        val people=root.optJSONArray("profiles")?:JSONArray()
        (0 until people.length()).forEach { people.getJSONObject(it).getString("id");people.getJSONObject(it).getString("name") }
        val templates=root.optJSONArray("templates")?:JSONArray()
        (0 until templates.length()).forEach {
            val template=templates.getJSONObject(it)
            template.getString("id");template.getString("name");strings(template.optJSONArray("fields"))
        }
        readEntries(root.optJSONArray("entries"))
    }.isSuccess
    /** Update only the current version while holding the same lock as all vault writes. */
    @Synchronized fun updateCard(id:String,transform:(VaultCard)->VaultCard):VaultCard {
        val latest=cards.firstOrNull{it.id==id}
            ?:throw IOException("Document no longer exists")
        val revised=transform(latest)
        require(revised.id==id){"Document identity may not change"}
        saveCard(revised)
        return revised
    }
    @Synchronized fun saveCard(card:VaultCard) {
        val i=cards.indexOfFirst{it.id==card.id}
        val old=if(i<0)null else cards[i]
        if(card.expiry!=old?.expiry && card.expiry.isNotBlank()) {
            require(VaultExpiry.isIsoDate(card.expiry)) {
                "Expiry date must be a valid ISO date (YYYY-MM-DD); got: "+card.expiry
            }
        }
        // Publish attachment-list changes atomically to Compose observers.
        // A half-updated list while the attachment manager recomposes can crash UI.
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            if(i<0)cards.add(0,card) else cards[i]=card
            try{persist()}catch(e:Exception){
                if(i<0)cards.removeAll{it.id==card.id}
                else if(old!=null)cards[i]=old
                throw e
            }
        }
    }
    /** Commit list changes before unlinking blobs, including hidden preserved originals. */
    /** Hidden raw captures are optional. Remove their bytes without touching edited pages. */
    @Synchronized fun retainedOriginalBytes():Long =
        cards.flatMap{it.files}.map{it.originalId}.filter{it.isNotBlank()}
            .distinct().sumOf{blob(it).takeIf(File::isFile)?.length()?:0L}

    @Synchronized fun removeRetainedOriginals():Int {
        val originals=cards.flatMap{it.files}.map{it.originalId}
            .filter{it.isNotBlank()}.distinct()
        if(originals.isEmpty())return 0
        val before=cards.toList()
        val updated=before.map{card->card.copy(files=card.files.map{file->
            if(file.originalId.isNotBlank())file.copy(originalId="",
                cropCorners="",cropRotation=0) else file
        })}
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            cards.clear();cards.addAll(updated)
            try{persist()}
            catch(e:Exception){cards.clear();cards.addAll(before);throw e}
        }
        val stillUsed=referencedIds()
        originals.filterNot{it in stillUsed}.forEach{id->
            blob(id).delete();meta(id).delete();textFile(id).delete()
        }
        return originals.size
    }
    @Synchronized fun removeFile(cardId:String,fileId:String) {
        val current=cards.firstOrNull{it.id==cardId}?:throw IOException("Document not found")
        val target=current.files.firstOrNull{it.id==fileId}?:throw IOException("Attachment not found")
        saveCard(current.copy(files=current.files.filterNot{it.id==fileId}))
        cleanupReplacedBlobs(target)
    }
    private fun referencedIds():Set<String> = AttachmentReferences.referencedIds(cards.toList())
    private fun previousReferencedIds():Set<String> = runCatching {
        val a=JSONObject(backup.readText()).getJSONArray("cards")
        AttachmentReferences.referencedIds((0 until a.length()).map{readCard(a.getJSONObject(it))})
    }.getOrDefault(emptySet())
    private fun cleanupReplacedBlobs(file:VaultFile){
        // The rolling index must keep its attachments too, until a later commit.
        val kept=referencedIds()+previousReferencedIds()
        listOf(file.id,file.originalId).filter{it.isNotBlank() && it !in kept}.forEach{id->
            blob(id).delete();meta(id).delete()
            // OCR text is document content: it must go with the document.
            textFile(id).delete()
        }
    }
    /** A re-edit replaces the attachment at its original position, not a new page. */
    @Synchronized fun replaceImageFile(
        cardId:String,fileId:String,updated:VaultFile,
        corners:String,rotation:Int
    ){
        val card=cards.firstOrNull{it.id==cardId}?:throw IOException("Document not found")
        val existing=card.files.firstOrNull{it.id==fileId}?:throw IOException("Attachment not found")
        require(existing.mime.startsWith("image/")){"Only photos can be enhanced"}
        val origin=existing.originalId.ifBlank{existing.id}
        val replacement=updated.copy(name=existing.name,originalId=origin,
            cropCorners=corners,cropRotation=((rotation%360)+360)%360,
            extractedText="",ocrStatus="pending")
        saveCard(card.copy(files=card.files.map{if(it.id==fileId)replacement else it}))
        cleanupReplacedBlobs(existing)
    }
    /** Retain the raw capture outside the visible page list for later re-cropping. */
    @Synchronized fun keepScanSource(cardId:String,fileId:String,raw:VaultFile,
        corners:String,rotation:Int) {
        val card=cards.firstOrNull{it.id==cardId}?:throw IOException("Document not found")
        if(card.files.none{it.id==fileId})throw IOException("Processed scan missing")
        saveCard(card.copy(files=card.files.map{
            if(it.id==fileId)it.copy(originalId=raw.id,
                cropCorners=corners,cropRotation=((rotation%360)+360)%360) else it
        }))
    }
    @Synchronized fun renameFile(cardId:String,fileId:String,newName:String) {
        val name=newName.trim()
        require(name.isNotBlank() && name.length<=150 && !name.contains('/') &&
            !name.contains(92.toChar()) && !name.contains(10.toChar()) && !name.contains(13.toChar()) &&
            name!="." && name!=".."){"Invalid attachment name"}
        val current=cards.firstOrNull{it.id==cardId}?:throw IOException("Card not found")
        require(current.files.any{it.id==fileId}){"Attachment not found"}
        val revised=current.files.map{if(it.id==fileId)it.copy(name=name)else it}
        saveCard(current.copy(files=revised))
        val renamed=revised.first{it.id==fileId}
        try { writeMeta(renamed) }catch(_:IOException){ /* Indexed filename is authoritative. */ }
    }
    @Synchronized fun reorderFiles(cardId:String,orderedIds:List<String>) {
        val current=cards.firstOrNull{it.id==cardId}?:throw IOException("Card not found")
        saveCard(current.copy(files=VaultOrder.reorder(current.files,orderedIds)))
    }
    private fun writeMeta(file:VaultFile) {
        meta(file.id).writeText(JSONObject().put("name",file.name).put("mime",file.mime)
            .put("size",file.bytes).put("sha",file.sha256).toString())
    }
    @Synchronized fun deleteCard(card:VaultCard) {
        val previous=cards.toList()
        val previousEntries=entries.toList()
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            cards.removeAll{it.id==card.id}
            // Linked profile entries keep a last-known copy of the value. For sensitive
            // fields that copy must not outlive the document it came from.
            val scrubbed=ProfileLogic.scrubSensitive(entries.toList(),card.id)
            entries.clear();entries.addAll(scrubbed)
            try{persist()}catch(e:Exception){
                cards.clear();cards.addAll(previous)
                entries.clear();entries.addAll(previousEntries)
                throw e
            }
        }
        card.files.forEach(::cleanupReplacedBlobs)
    }
    @Synchronized fun saveProfile(p:VaultProfile){
        val previous=profiles.toList()
        val i=profiles.indexOfFirst{it.id==p.id}
        if(i<0)profiles.add(p)else profiles[i]=p
        try{persist()}catch(e:Exception){profiles.clear();profiles.addAll(previous);throw e}
    }
    @Synchronized fun changeEntries(add:List<ProfileEntry> = emptyList(),removeIds:Set<String> = emptySet()){
        val before=entries.toList()
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            entries.removeAll{it.id in removeIds||add.any{update->update.id==it.id}}
            entries.addAll(add)
            try{persist()}catch(e:Exception){entries.clear();entries.addAll(before);throw e}
        }
    }
    @Synchronized fun removePerson(personId:String){
        require(personId!="self"){"The default profile cannot be removed"}
        val beforeProfiles=profiles.toList();val beforeEntries=entries.toList();val beforeCards=cards.toList()
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            profiles.removeAll{it.id==personId};entries.removeAll{it.personId==personId}
            // Documents stay in the vault: hand them back to the default profile so
            // they remain visible under every filter.
            cards.indices.filter{cards[it].profile==personId}.forEach{i->
                cards[i]=cards[i].copy(profile="self")
            }
            try{persist()}catch(e:Exception){
                profiles.clear();profiles.addAll(beforeProfiles)
                entries.clear();entries.addAll(beforeEntries)
                cards.clear();cards.addAll(beforeCards)
                throw e
            }
        }
    }
    @Synchronized fun saveTemplate(t:CardTemplate){
        val previous=customTemplates.toList()
        val i=customTemplates.indexOfFirst{it.id==t.id}
        if(i<0)customTemplates.add(t)else customTemplates[i]=t
        try{persist()}catch(e:Exception){customTemplates.clear();customTemplates.addAll(previous);throw e}
    }
    fun isDuplicate(sha:String)=cards.any { c->c.files.any{it.sha256==sha} }
    /** The same file may legitimately belong to two people (shared address proof). */
    fun isDuplicate(sha:String,profile:String)=
        cards.any { c->c.profile==profile && c.files.any{it.sha256==sha} }
    @Synchronized fun importFile(uri:Uri,checkCancelled:()->Unit={}):VaultFile {
        require(uri.scheme=="content"){"Import only from Android's file picker or share menu"}
        val name=VaultNames.importLabel(filename(uri),
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm",java.util.Locale.ENGLISH)))
        val mime=context.contentResolver.getType(uri) ?: when(name.substringAfterLast('.', "").lowercase()){
            "pdf"->"application/pdf";"txt"->"text/plain"
            "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc"->"application/msword"
            "png"->"image/png";"jpg","jpeg"->"image/jpeg";else->"application/octet-stream"
        }
        val id=uid()
        val staged=File(blobDir,id+".import.tmp")
        val digest=MessageDigest.getInstance("SHA-256")
        var size=0L
        val sample=ByteArray(32)
        var sampleLength=0
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staged).use{output->
                    val buffer=ByteArray(65536)
                    while(true) {
                        checkCancelled()
                        val n=input.read(buffer)
                        if(n<0)break
                        if(n==0)continue
                        if(sampleLength<sample.size) {
                            val copy=minOf(n,sample.size-sampleLength)
                            System.arraycopy(buffer,0,sample,sampleLength,copy)
                            sampleLength+=copy
                        }
                        if(size>VaultBackupChecks.MAX_BLOB_BYTES-n)throw IOException("The selected file exceeds the 512 MB import limit")
                        output.write(buffer,0,n);digest.update(buffer,0,n);size+=n
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("Cannot read selected file")
            val media=VaultFileTypes.resolve(name,mime,sample.copyOf(sampleLength))
            VaultImportValidation.validate(staged,media)
            checkCancelled()
            if(!staged.renameTo(blob(id)))throw IOException("Cannot store the selected file")
            val file=VaultFile(id,media.name,media.mime,size,digest.digest().joinToString(""){"%02x".format(it)})
            try{writeMeta(file)}catch(_:Exception) { /* preserve imported bytes */ }
            return file
        }catch(e:Exception){blob(id).delete();meta(id).delete();throw e}
        finally{staged.delete()}
    }
    /** Stage the entire selection and commit its document changes exactly once. */
    @Synchronized fun importDocuments(
        uris:List<Uri>,targetId:String?,profile:String,originalUri:Uri?=null,
        corners:String="",rotation:Int=0,groupAsOne:Boolean=false,
        checkCancelled:()->Unit={}
    ):VaultLongOperations.ImportResult {
        check(recoveryError==null) { recoveryError ?: "Vault recovery required" }
        ImportTargetPolicy.requireAvailable(targetId,cards.map{it.id}.toSet())
        val owner=targetId?.let{id->cards.first{it.id==id}.profile}?:profile
        require(profiles.any{it.id==owner}) { "The selected person is no longer available" }
        val before=cards.toList()
        val next=before.toMutableList()
        val staged=mutableListOf<VaultFile>()
        val hashes=before.filter{it.profile==owner}.flatMap{it.files}.map{it.sha256}.toMutableSet()
        var last:VaultCard?=null
        var skipped=0
        var count=0
        var committed=false
        try {
            uris.forEach { uri ->
                checkCancelled()
                val file=importFile(uri,checkCancelled)
                staged.add(file)
                if(!hashes.add(file.sha256)) { skipped++;return@forEach }
                val nextTarget=VaultImportGrouping.nextTarget(targetId,last?.id,groupAsOne)
                val index=next.indexOfFirst{it.id==nextTarget}
                val card=if(index>=0)next[index].copy(files=next[index].files+file) else {
                    val kind=when {
                        file.name.contains("passport",true)->"Passport"
                        file.name.contains("cdc",true)->"Seafarer CDC"
                        file.name.contains("medical",true)->"Medical"
                        file.name.contains("certificate",true)->"COC / STCW"
                        else->"Custom"
                    }
                    newCard(kind,owner).copy(title=if(file.name.startsWith("scan-",true)||file.name.startsWith("capture-",true))
                        "Scanned document · "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm"))
                        else file.name.substringBeforeLast('.',file.name),files=listOf(file))
                }
                if(index>=0)next[index]=card else next.add(0,card)
                last=card;count++
            }
            if(last==null)return VaultLongOperations.ImportResult(null,0,skipped)
            if(originalUri!=null) {
                try {
                    val original=importFile(originalUri,checkCancelled).also{staged.add(it)}
                    val card=last!!
                    val revised=card.copy(files=card.files.mapIndexed { i,file ->
                        if(i==card.files.lastIndex)file.copy(originalId=original.id,cropCorners=corners,
                            cropRotation=((rotation%360)+360)%360) else file
                    })
                    next[next.indexOfFirst{it.id==card.id}]=revised
                    last=revised
                } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(_:Exception) { skipped++ } // Optional originals never discard a valid page.
            }
            checkCancelled()
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                cards.clear();cards.addAll(next)
                try{persist()}catch(e:Exception){cards.clear();cards.addAll(before);throw e}
            }
            committed=true
            return VaultLongOperations.ImportResult(last!!.id,count,skipped)
        } finally {
            val kept=if(committed)referencedIds() else emptySet()
            staged.filter{it.id !in kept}.forEach{removeOrphan(it)}
        }
    }
    private fun filename(uri:Uri):String {
        var name=""
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
            if(cursor.moveToFirst())name=cursor.getString(0)?:""
        }
        return name.ifBlank{uri.lastPathSegment?:"document"}
            .replace(Regex("[\\r\\n\\u0000-\\u001f/]"),"_").trim('.', ' ')
            .ifBlank{"document"}.take(150)
    }
    fun cleanupPreviews() {
        listOf("preview","shared","camera","view-only").forEach {folder->
            File(context.cacheDir,folder).listFiles()?.filter{
                System.currentTimeMillis()-it.lastModified()>3600000
            }?.forEach{it.deleteRecursively()}
        }
        context.cacheDir.listFiles()?.filter{
            it.isDirectory && it.name.startsWith("restore-") &&
                System.currentTimeMillis()-it.lastModified()>3600000
        }?.forEach{it.deleteRecursively()}
    }
    @Synchronized fun storageHealth():String {
        val files=cards.flatMap{it.files}
        val index=if(recoveryError!=null) recoveryError!! else if(data.isFile)
            if(validIndex(data.readText())) "Readable" else "Unreadable"
            else "Not saved yet"
        val prior=if(backup.isFile)runCatching {
            val array=JSONObject(backup.readText()).getJSONArray("cards")
            (0 until array.length()).forEach { readCard(array.getJSONObject(it)) }
            "Previous index available"
        }.getOrDefault("Previous index unreadable") else "No previous index yet"
        return "Documents: ${cards.size}\nAttachments: ${files.size}\nMissing attachments: ${files.count{!blob(it.id).isFile}}\nRecoverable unlinked attachments: ${recoverableFiles().size}\nIndex: $index\nRecovery: $prior\nExternal backup status: unknown\nNo files were changed."
    }
    fun sweepOrphanBlobs() { /* Do not erase unknown imports automatically. */ }
    private fun readMeta(id:String):VaultFile?=try{
        val o=JSONObject(meta(id).readText())
        VaultFile(id,o.getString("name"),o.getString("mime"),o.getLong("size"),o.getString("sha"),"","Recovered attachment")
    }catch(_:Exception){null}
    @Synchronized fun recoverableFiles():List<VaultFile> {
        val linked=referencedIds() // Includes deliberately hidden retained scan originals.
        val previous=runCatching {
            val array=JSONObject(backup.readText()).getJSONArray("cards")
            (0 until array.length()).flatMap{readCard(array.getJSONObject(it)).files}.associateBy{it.id}
        }.getOrDefault(emptyMap())
        return blobDir.listFiles()?.filter {it.isFile&&it.name.endsWith(".dat") &&
            it.name.removeSuffix(".dat").matches(Regex("[a-f0-9-]{36}"))&&it.name.removeSuffix(".dat") !in linked}
            ?.map {f->
                val id=f.name.removeSuffix(".dat")
                previous[id]?:readMeta(id)?:VaultFile(id,"Recovered-"+id.take(8),"application/octet-stream",f.length(),"","","Recovered attachment")
            }?:emptyList()
    }
    @Synchronized fun attachRecovered(id:String,cardTitle:String) {
        val found=recoverableFiles().firstOrNull{it.id==id}?:return
        val raw=documentFile(found)
        val magic=raw.inputStream().use{input->ByteArray(32).let{bytes->bytes.copyOf(input.read(bytes).coerceAtLeast(0))}}
        val media=VaultFileTypes.resolve(found.name,found.mime,magic)
        VaultImportValidation.validate(raw,media)
        val title=cardTitle.ifBlank{found.name.substringBeforeLast('.',found.name)}
        val finalFile=found.copy(name=media.name,mime=media.mime,bytes=raw.length(),
            sha256=VaultBackupChecks.sha256(raw.inputStream()),
            extractedText=if(found.extractedText.isNotBlank())found.extractedText else
                if(textFile(id).exists())textFile(id).readText(Charsets.UTF_8) else "",
            originalId=found.originalId.takeIf{it.isNotBlank()&&blob(it).isFile}?:"")
        saveCard(VaultCard(title=title,kind="Recovered",category="Recovered",files=listOf(finalFile)))
    }
    fun newCard(kind:String="Custom",profile:String="self"):VaultCard {
        val template=(FieldSuggestions.templates+customTemplates).firstOrNull{it.name==kind}
        return VaultCard(title=if(kind=="Custom")"New Card" else kind,kind=kind,profile=profile,
            fields=template?.fieldNames?.map{DataField(it,"")}?:emptyList())
    }

    // This ZIP is deliberately not encrypted. Store it only where you trust the recipient.
    /** A missing raw original is optional; missing visible pages are reported explicitly. */
    @Synchronized fun exportBackup(uri:Uri,password:String):List<String> {
        check(recoveryError==null) { recoveryError ?: "Vault recovery required" }
        val missing=BackupIntegrity.missingPrimaries(cards.toList()){blob(it).isFile}
        if(missing.isNotEmpty())throw IOException("Backup cancelled: required attachments are missing")
        val planned=BackupIntegrity.plan(cards.toList()){id->blob(id).isFile}
        val hashes=planned.availableIds.associateWith{id->
            require(blob(id).length()<=VaultBackupChecks.MAX_BLOB_BYTES) {
                "An attachment is larger than the 512 MB backup limit. No backup was written."
            }
            VaultBackupChecks.sha256(blob(id).inputStream())
        }
        // A recorded hash that differs means corruption. Legacy and recovered entries may
        // have no hash or a stale size; back them up with their measured values instead.
        val plan=planned.copy(cards=planned.cards.map{card->card.copy(files=card.files.map{f->
            val hash=hashes[f.id]?:return@map f
            require(f.sha256.isBlank()||hash.equals(f.sha256,true)) {
                "\""+VaultNames.label(f.name)+"\" appears damaged in the vault. No backup was written."
            }
            f.copy(bytes=blob(f.id).length(),sha256=hash)
        })})
        val indexBytes=snapshot(plan.cards).toByteArray(Charsets.UTF_8)
        val textPayloads=plan.cards.flatMap{it.files}.distinctBy{it.id}
            .filter{it.extractedText.isNotBlank()}.map{it.extractedText.toByteArray(Charsets.UTF_8)}
        require(indexBytes.size<=25_000_000 && textPayloads.all{it.size<=5_000_000}) {
            "The recognized text in this vault is too large for one backup file."
        }
        require(plan.availableIds.size+textPayloads.size+4<=VaultBackupChecks.MAX_ENTRIES) {
            "This vault has too many files for one backup file."
        }
        val total=plan.availableIds.sumOf{blob(it).length()}+textPayloads.sumOf{it.size.toLong()}+indexBytes.size
        require(total<=VaultBackupChecks.MAX_TOTAL_BYTES-6_000_000L) { "This vault is larger than the 2 GB backup limit." }
        val stagedZip=File(context.cacheDir,"export-"+uid()+".zip")
        try {
        stagedZip.outputStream().use{output->
            ZipOutputStream(output).use{zip->
                zip.putNextEntry(ZipEntry("index.json"))
                zip.write(indexBytes);zip.closeEntry()
                val readable=plan.cards.flatMap{it.files}.distinctBy{it.id}.joinToString("\n"){file->
                    file.id+" : "+VaultNames.label(file.name).replace(10.toChar(),' ').replace(13.toChar(),' ')
                }
                zip.putNextEntry(ZipEntry("filenames.txt"))
                zip.write(readable.toByteArray(Charsets.UTF_8));zip.closeEntry()
                val manifest=JSONObject()
                plan.availableIds.forEach{id->
                    val source=blob(id)
                    manifest.put(id,JSONObject().put("size",source.length()).put("sha256",hashes.getValue(id)))
                    zip.putNextEntry(ZipEntry("blobs/"+id))
                    source.inputStream().use{it.copyTo(zip,65536)}
                    zip.closeEntry()
                }
                plan.cards.flatMap{it.files}.distinctBy{it.id}.forEach{file->
                    if(file.extractedText.isNotBlank()){
                        val bytes=file.extractedText.toByteArray(Charsets.UTF_8)
                        manifest.put("texts/"+file.id+".txt",JSONObject().put("size",bytes.size)
                            .put("sha256",VaultBackupChecks.sha256(bytes.inputStream())))
                        zip.putNextEntry(ZipEntry("texts/"+file.id+".txt"))
                        zip.write(bytes);zip.closeEntry()
                    }
                }
                zip.putNextEntry(ZipEntry("checksums.json"))
                val manifestBytes=manifest.toString().toByteArray(Charsets.UTF_8)
                require(manifestBytes.size<=2_000_000) { "This vault has too many files for one backup file." }
                zip.write(manifestBytes);zip.closeEntry()
                if(plan.warnings.isNotEmpty()){
                    zip.putNextEntry(ZipEntry("missing-files.txt"))
                    zip.write(plan.warnings.joinToString("\n").toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        // Validate our complete archive before opening the user's destination.
        context.contentResolver.openOutputStream(uri,"w")?.use { output ->
            stagedZip.inputStream().use { it.copyTo(output,65536) }
        } ?: throw IOException("Cannot write backup destination")
        return plan.warnings
        } finally { stagedZip.delete() }
    }
    @Synchronized fun restoreBackup(uri:Uri,password:String):List<String> {
        val restoreWarnings=mutableListOf<String>()
        val staged=File(context.cacheDir,"restore-"+uid()).apply{mkdirs()}
        try{
            var root:String?=null
            var manifestText:String?=null
            var totalExtracted=0L
            val seenEntries=mutableSetOf<String>()
            val imported=mutableMapOf<String,File>()
            val importedTexts=mutableMapOf<String,File>()
            context.contentResolver.openInputStream(uri)!!.use{input->
                ZipInputStream(input).use{zip->
                    while(true){
                        val entry=zip.nextEntry?:break
                        if(!seenEntries.add(entry.name) || seenEntries.size>VaultBackupChecks.MAX_ENTRIES)
                            throw IOException("Duplicate or excessive backup entries")
                        if(entry.name=="checksums.json") {
                            val buffer=ByteArrayOutputStream()
                            val bytes=ByteArray(8192)
                            while(true) {
                                val n=zip.read(bytes)
                                if(n<0)break
                                if(buffer.size()+n>2_000_000)
                                    throw IOException("Backup checksums manifest too large")
                                buffer.write(bytes,0,n)
                            }
                            manifestText=buffer.toString("UTF-8")
                        }else if(entry.name=="index.json"){
                            val buffer=ByteArrayOutputStream()
                            val bytes=ByteArray(65536)
                            while(true) {
                                val count=zip.read(bytes)
                                if(count<0)break
                                if(buffer.size().toLong()+count>25_000_000L)
                                    throw IOException("Backup index exceeds 25 MB; split your backup into smaller vaults")
                                buffer.write(bytes,0,count)
                            }
                            root=buffer.toString("UTF-8")
                        }else if(entry.name=="filenames.txt"||entry.name=="missing-files.txt"){
                            // The human-readable list is optional and not trusted for restore.
                            val chunk=ByteArray(8192)
                            var total=0L
                            while(true){
                                val n=zip.read(chunk)
                                if(n<0)break
                                total+=n
                                if(total>2_000_000L)throw IOException("Backup filename listing exceeds 2 MB")
                            }
                        }else if(entry.name.matches(Regex("blobs/[a-f0-9-]{36}"))) {
                            val id=entry.name.substringAfter("/")
                            val target=File(staged,id)
                            val count=VaultBackupChecks.boundedCopy(zip,target,VaultBackupChecks.MAX_BLOB_BYTES)
                            if(totalExtracted>VaultBackupChecks.MAX_TOTAL_BYTES-count)
                                throw IOException("Backup exceeds total extraction limit")
                            totalExtracted+=count
                            imported[id]=target
                        }else if(entry.name.matches(Regex("texts/[a-f0-9-]{36}\\.txt"))){
                            val id=entry.name.removePrefix("texts/").removeSuffix(".txt")
                            val target=File(staged,"text-"+id)
                            target.outputStream().use{output->
                                val bytes=ByteArray(8192)
                                var size=0L
                                while(true){
                                    val n=zip.read(bytes)
                                    if(n<0)break
                                    size+=n
                                    if(size>5_000_000L)throw IOException("OCR text exceeds 5 MB")
                                    output.write(bytes,0,n)
                                }
                            }
                            importedTexts[id]=target
                        }else throw IOException("Backup contains unsupported or unsafe entry: "+entry.name.take(120))
                        val extracted=when {
                            entry.name=="index.json" -> root!!.toByteArray(Charsets.UTF_8).size.toLong()
                            entry.name=="checksums.json" -> manifestText!!.toByteArray(Charsets.UTF_8).size.toLong()
                            entry.name.startsWith("texts/") -> importedTexts[entry.name.removePrefix("texts/").removeSuffix(".txt")]!!.length()
                            entry.name.endsWith(".txt") -> 2_000_000L
                            else -> 0L // blobs were counted while streaming
                        }
                        if(totalExtracted>VaultBackupChecks.MAX_TOTAL_BYTES-extracted)throw IOException("Backup exceeds total extraction limit")
                        totalExtracted+=extracted
                        zip.closeEntry()
                    }
                }
            }
            val json=JSONObject(root?:throw IOException("Backup index missing"))
            val array=json.getJSONArray("cards")
            val readCards=(0 until array.length()).map{readCard(array.getJSONObject(it),
                useSidecars=false)}.map{card->card.copy(files=card.files.map{file->
                    val recognized=importedTexts[file.id]?.readText(Charsets.UTF_8)
                    if(recognized!=null)file.copy(extractedText=recognized)else file
                })}
            val available:(String)->Boolean={id->imported[id]?.isFile==true}
            val manifest=manifestText?.let(::JSONObject)
            val referenced=AttachmentReferences.referencedIds(readCards)
            if(json.optInt("formatVersion",1)>=2) {
                val expectedTexts=readCards.flatMap{it.files}.filter{it.extractedText.isNotBlank()}
                    .map{"texts/"+it.id+".txt"}.toSet()
                val actualTexts=manifest?.keys()?.asSequence()?.filter{it.startsWith("texts/")}?.toSet()
                if(manifest==null || actualTexts!=expectedTexts ||
                    importedTexts.keys.map{"texts/"+it+".txt"}.toSet()!=expectedTexts)
                    throw IOException("Backup OCR checksums or payloads are missing")
            }
            if(manifest!=null) {
                val textKeys=manifest.keys().asSequence().filter{it.startsWith("texts/")}.toSet()
                if(manifest.length()!=imported.size+textKeys.size || imported.keys.any{!manifest.has(it)})
                    throw IOException("Backup checksums manifest does not match payloads")
                // Older manifests covered blobs only. New exports also cover every OCR payload.
                if(textKeys.isNotEmpty() && textKeys != importedTexts.keys.map{"texts/"+it+".txt"}.toSet())
                    throw IOException("Backup OCR manifest does not match payloads")
                textKeys.forEach { key ->
                    val record=manifest.getJSONObject(key)
                    val file=importedTexts[key.removePrefix("texts/").removeSuffix(".txt")]
                        ?:throw IOException("Backup OCR text missing")
                    VaultBackupChecks.verify(file,record.getLong("size"),record.getString("sha256"))
                }
                imported.forEach{(id,file)->
                    val record=manifest.optJSONObject(id)
                        ?:throw IOException("Backup checksum entry invalid")
                    VaultBackupChecks.verify(file,record.getLong("size"),record.getString("sha256"))
                }
            }
            // Strict by default: never silently drop a visible document during restore.
            val missing=BackupIntegrity.missingPrimaries(readCards,available)
            if(missing.isNotEmpty())throw IOException(
                "Backup is incomplete: "+missing.size+" attachment(s) missing. Existing vault unchanged."
            )
            val cleared=BackupIntegrity.clearMissingOriginals(readCards,available)
            // Missing optional scan originals do not remove visible pages.
            val nextCards=cleared.map { card -> card.copy(files=card.files.map { file ->
                val stored=imported.getValue(file.id)
                VaultBackupChecks.verifyLegacy(stored,file.bytes,file.sha256)
                file.copy(bytes=stored.length(),sha256=VaultBackupChecks.sha256(stored.inputStream()))
            }) }
            if(referenced.any{it !in imported}) {
                restoreWarnings.add("Optional originals were not included in this backup")
            }
            val nextProfiles=mutableListOf<VaultProfile>()
            val p=json.optJSONArray("profiles")?:JSONArray()
            for(i in 0 until p.length()){val e=p.getJSONObject(i);nextProfiles.add(VaultProfile(e.getString("id"),e.getString("name")))}
            val nextEntries=readEntries(json.optJSONArray("entries"))
            val nextTemplates=mutableListOf<CardTemplate>()
            val t=json.optJSONArray("templates")?:JSONArray()
            for(i in 0 until t.length()){
                val e=t.getJSONObject(i)
                nextTemplates.add(CardTemplate(e.getString("id"),e.getString("name"),strings(e.optJSONArray("fields"))))
            }
            // Attachment IDs are immutable. Never overwrite live bytes before the index
            // commits; identical IDs from repeated restores can safely reuse their bytes.
            imported.forEach { (id,file) ->
                if(blob(id).isFile && (blob(id).length()!=file.length() ||
                    VaultBackupChecks.sha256(blob(id).inputStream())!=VaultBackupChecks.sha256(file.inputStream())))
                    throw IOException("This backup contains a different version of a file already in your vault. Your vault was not changed.")
            }
            val created=mutableSetOf<String>()
            try{imported.forEach{(id,file)->if(!blob(id).isFile) {
                val target=File(blobDir,id+".restore.tmp")
                try {
                    FileOutputStream(target).use{out->file.inputStream().use{it.copyTo(out)};out.fd.sync()}
                    if(!target.renameTo(blob(id)))throw IOException("Cannot commit restored attachment")
                    created.add(id)
                } finally { target.delete() }
            }}}
            catch(e:Exception) {
                created.forEach { blob(it).delete() }
                throw e
            }
            val oldRecoveryError=recoveryError
            val oldCards=cards.toList()
            val oldProfiles=profiles.toList()
            val oldTemplates=customTemplates.toList()
            val oldEntries=entries.toList()
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            cards.clear();cards.addAll(nextCards)
            profiles.clear();profiles.addAll(nextProfiles)
            if(profiles.none{it.id=="self"})profiles.add(VaultProfile("self","Me"))
            customTemplates.clear();customTemplates.addAll(nextTemplates)
            entries.clear();entries.addAll(nextEntries)
            recoveryError=null
            try{persist()}catch(e:Exception){
                cards.clear();cards.addAll(oldCards)
                profiles.clear();profiles.addAll(oldProfiles)
                customTemplates.clear();customTemplates.addAll(oldTemplates)
                entries.clear();entries.addAll(oldEntries)
                recoveryError=oldRecoveryError
                created.forEach { blob(it).delete() }
                throw e
            }
            }
            // Keep unlinked files for recovery and for the previous index. Health is
            // read-only; users decide when to remove recoverable imports.
            nextCards.flatMap{it.files}.distinctBy{it.id}.forEach{file->
                try{writeMeta(file)}catch(_:IOException){}
            }
            return restoreWarnings
        }finally{staged.deleteRecursively()}
    }
}
