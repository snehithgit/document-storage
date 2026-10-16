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
    fun documentFile(file:VaultFile):File = blob(file.id).also{
        if(!it.isFile)throw FileNotFoundException("Attachment missing: "+file.name)
    }
    fun hasDocument(file:VaultFile)=blob(file.id).isFile
    /** Source survives re-editing; it is never exposed as a duplicate attachment. */
    fun originalDocumentFile(file:VaultFile):File =
        blob(file.originalId.ifBlank{file.id}).also {
            if(!it.isFile)throw FileNotFoundException("Original image is unavailable")
        }
    fun removeOrphan(file:VaultFile){ blob(file.id).delete();meta(file.id).delete() }

    @Synchronized fun load() {
        cards.clear();profiles.clear();customTemplates.clear();recoveryError=null
        val tmp=File(context.filesDir,"vault.json.tmp")
        val candidates=listOf(data,tmp).filter { it.exists() }.sortedByDescending { it.lastModified() } +
            listOf(backup).filter { it.exists() }
        var accepted:File?=null
        var needsFieldIdMigration=false
        for (candidate in candidates) {
            try {
                val root=JSONObject(candidate.readText(Charsets.UTF_8))
                val loadedCards=mutableListOf<VaultCard>()
                val loadedProfiles=mutableListOf<VaultProfile>()
                val loadedTemplates=mutableListOf<CardTemplate>()
                val c=root.optJSONArray("cards")?:JSONArray()
                var legacy=false
                for(i in 0 until c.length()) {
                    val obj=c.getJSONObject(i)
                    val fs=obj.optJSONArray("fields")?:JSONArray()
                    if((0 until fs.length()).any{j->fs.getJSONObject(j).optString("id").isBlank()})
                        legacy=true
                    loadedCards.add(readCard(obj))
                }
                needsFieldIdMigration=legacy
                val p=root.optJSONArray("profiles")?:JSONArray()
                for(i in 0 until p.length()){
                    val e=p.getJSONObject(i);loadedProfiles.add(VaultProfile(e.getString("id"),e.getString("name")))
                }
                val t=root.optJSONArray("templates")?:JSONArray()
                for(i in 0 until t.length()){
                    val e=t.getJSONObject(i)
                    loadedTemplates.add(CardTemplate(e.getString("id"),e.getString("name"),strings(e.optJSONArray("fields"))))
                }
                cards.addAll(loadedCards);profiles.addAll(loadedProfiles);customTemplates.addAll(loadedTemplates)
                accepted=candidate;break
            }catch(_:Exception) { /* preserve all candidates for recovery */ }
        }
        if(candidates.isNotEmpty() && accepted==null) {
            recoveryError="The local vault index cannot be read. No documents were deleted."
        }
        if(accepted!=null && accepted!=data)try { accepted.copyTo(data,overwrite=true) }catch(_:Exception){}
        if(accepted!=null && needsFieldIdMigration) {
            // Make generated legacy IDs durable before a later process recreation.
            try{persist()}catch(_:Exception){ /* Cards remain usable; next save will retry. */ }
        }
        // The original valid index always remains available in the rolling backup.
        if(profiles.none { it.id=="self" })profiles.add(0,VaultProfile("self","Me"))
    }
    private fun strings(a:JSONArray?):List<String> = (0 until (a?.length()?:0)).map{a!!.optString(it)}
    private fun readCard(o:JSONObject):VaultCard {
        val fields=o.optJSONArray("fields")?:JSONArray()
        val files=o.optJSONArray("files")?:JSONArray()
        return VaultCard(
            id=o.getString("id"),title=o.optString("title"),kind=o.optString("kind","Custom"),
            category=o.optString("category","Personal"),profile=o.optString("profile","self"),
            tags=o.optString("tags"),notes=o.optString("notes"),expiry=o.optString("expiry"),
            previousId=o.optString("previousId"),created=o.optLong("created",System.currentTimeMillis()),
            fields=(0 until fields.length()).map { val f=fields.getJSONObject(it);DataField(f.optString("name"),f.optString("value"),f.optInt("page",-1),
                f.optString("source","manual"),f.optString("id").ifBlank{uid()},f.optDouble("confidence",0.0).toFloat()) },
            files=(0 until files.length()).map { val f=files.getJSONObject(it);VaultFile(f.getString("id"),f.optString("name"),f.optString("mime"),f.optLong("bytes"),f.optString("sha256"),f.optString("extractedText"),f.optString("ocrStatus","pending"),
                f.optString("originalId"),f.optString("cropCorners"),f.optInt("cropRotation",0)) }
        )
    }
    fun encodeCard(card:VaultCard):String=writeCard(card).toString()
    fun decodeCard(json:String):VaultCard=readCard(JSONObject(json))
    private fun writeCard(c:VaultCard):JSONObject=JSONObject()
        .put("id",c.id).put("title",c.title).put("kind",c.kind).put("category",c.category)
        .put("profile",c.profile).put("tags",c.tags).put("notes",c.notes).put("expiry",c.expiry)
        .put("previousId",c.previousId).put("created",c.created)
        .put("fields",JSONArray().apply { c.fields.forEach{ put(JSONObject().put("name",it.name).put("value",it.value).put("page",it.page).put("source",it.source).put("id",it.id).put("confidence",it.confidence.toDouble())) } })
        .put("files",JSONArray().apply { c.files.forEach{ put(JSONObject().put("id",it.id).put("name",it.name).put("mime",it.mime).put("bytes",it.bytes).put("sha256",it.sha256).put("extractedText",it.extractedText).put("ocrStatus",it.ocrStatus)
            .put("originalId",it.originalId).put("cropCorners",it.cropCorners).put("cropRotation",it.cropRotation)) } })
    private fun snapshot():String=JSONObject()
        .put("cards",JSONArray().apply { cards.forEach{put(writeCard(it))} })
        .put("profiles",JSONArray().apply { profiles.forEach{put(JSONObject().put("id",it.id).put("name",it.name))} })
        .put("templates",JSONArray().apply { customTemplates.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("fields",JSONArray(it.fieldNames)))} })
        .toString()

    @Synchronized fun persist() {
        check(recoveryError==null) { recoveryError ?: "Vault recovery required" }
        val tmp=File(context.filesDir,"vault.json.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(snapshot().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        // Retain a previous good index. Only replace it when the current index parses.
        if(data.isFile) {
            val valid=runCatching{JSONObject(data.readText(Charsets.UTF_8))}.isSuccess
            if(valid) {
                val stagedBackup=File(context.filesDir,"vault.json.bak.tmp")
                try {
                    data.copyTo(stagedBackup,overwrite=true)
                    FileOutputStream(stagedBackup,true).use{it.fd.sync()}
                    if(!stagedBackup.renameTo(backup))
                        throw IOException("Cannot atomically update the vault backup")
                }finally{stagedBackup.delete()}
            }
        }
        if(!tmp.renameTo(data))throw IOException("Unable to commit the vault index")
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
    @Synchronized fun removeFile(cardId:String,fileId:String) {
        val current=cards.firstOrNull{it.id==cardId}?:throw IOException("Document not found")
        val target=current.files.firstOrNull{it.id==fileId}?:throw IOException("Attachment not found")
        saveCard(current.copy(files=current.files.filterNot{it.id==fileId}))
        cleanupReplacedBlobs(target)
    }
    private fun referencedIds():Set<String> = cards.flatMap { card->
        card.files.flatMap{file->listOf(file.id,file.originalId).filter{it.isNotBlank()}}
    }.toSet()
    private fun cleanupReplacedBlobs(file:VaultFile){
        val kept=referencedIds()
        listOf(file.id,file.originalId).filter{it.isNotBlank() && it !in kept}.forEach{id->
            blob(id).delete();meta(id).delete()
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
        cards.removeAll{it.id==card.id}
        try{persist()}catch(e:Exception){cards.clear();cards.addAll(previous);throw e}
        card.files.forEach(::cleanupReplacedBlobs)
    }
    @Synchronized fun saveProfile(p:VaultProfile){
        val previous=profiles.toList()
        val i=profiles.indexOfFirst{it.id==p.id}
        if(i<0)profiles.add(p)else profiles[i]=p
        try{persist()}catch(e:Exception){profiles.clear();profiles.addAll(previous);throw e}
    }
    @Synchronized fun saveTemplate(t:CardTemplate){
        val previous=customTemplates.toList()
        val i=customTemplates.indexOfFirst{it.id==t.id}
        if(i<0)customTemplates.add(t)else customTemplates[i]=t
        try{persist()}catch(e:Exception){customTemplates.clear();customTemplates.addAll(previous);throw e}
    }
    fun isDuplicate(sha:String)=cards.any { c->c.files.any{it.sha256==sha} }
    fun importFile(uri:Uri):VaultFile {
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
        val digest=MessageDigest.getInstance("SHA-256")
        var size=0L
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(blob(id)).use{output->
                    val buffer=ByteArray(65536)
                    while(true) {
                        val n=input.read(buffer)
                        if(n<0)break
                        if(n==0)continue
                        output.write(buffer,0,n);digest.update(buffer,0,n);size+=n
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("Cannot read selected file")
            val file=VaultFile(id,name,mime,size,digest.digest().joinToString(""){"%02x".format(it)})
            try{writeMeta(file)}catch(_:Exception) { /* preserve imported bytes */ }
            return file
        }catch(e:Exception){blob(id).delete();meta(id).delete();throw e}
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
        listOf("preview","shared","camera").forEach {folder->
            File(context.cacheDir,folder).listFiles()?.filter{
                System.currentTimeMillis()-it.lastModified()>3600000
            }?.forEach{it.delete()}
        }
        context.cacheDir.listFiles()?.filter{
            it.isDirectory && it.name.startsWith("restore-") &&
                System.currentTimeMillis()-it.lastModified()>3600000
        }?.forEach{it.deleteRecursively()}
    }
    fun sweepOrphanBlobs() { /* Do not erase unknown imports automatically. */ }
    private fun readMeta(id:String):VaultFile?=try{
        val o=JSONObject(meta(id).readText())
        VaultFile(id,o.getString("name"),o.getString("mime"),o.getLong("size"),o.getString("sha"),"","Recovered attachment")
    }catch(_:Exception){null}
    fun recoverableFiles():List<VaultFile> {
        val linked=cards.flatMap{it.files}.map{it.id}.toSet()
        return blobDir.listFiles()?.filter {it.name.endsWith(".dat") && it.name.removeSuffix(".dat") !in linked}
            ?.map {f->
                val id=f.name.removeSuffix(".dat")
                readMeta(id)?:VaultFile(id,"Recovered-"+id.take(8),"application/octet-stream",f.length(),"","","Recovered attachment")
            }?:emptyList()
    }
    fun attachRecovered(id:String,cardTitle:String) {
        val found=recoverableFiles().firstOrNull{it.id==id}?:return
        val raw=documentFile(found)
        val magic=ByteArray(8)
        raw.inputStream().use{it.read(magic)}
        val pdf=magic.copyOfRange(0,4).contentEquals("%PDF".toByteArray())
        val png=magic.contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))
        val jpg=(magic[0].toInt()and 0xff)==0xff&&(magic[1].toInt()and 0xff)==0xd8
        val (ext,mime)=when {
            pdf->".pdf" to "application/pdf"
            png->".png" to "image/png"
            jpg->".jpg" to "image/jpeg"
            magic[0]==80.toByte()&&magic[1]==75.toByte()->".docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else->".txt" to "text/plain"
        }
        val title=cardTitle.ifBlank{found.name.substringBeforeLast('.',found.name)}
        val finalFile=if(found.mime=="application/octet-stream")
            found.copy(name=title+ext,mime=mime,bytes=raw.length())
        else found
        saveCard(VaultCard(title=title,kind="Recovered",category="Recovered",files=listOf(finalFile)))
    }
    fun newCard(kind:String="Custom",profile:String="self"):VaultCard {
        val template=(FieldSuggestions.templates+customTemplates).firstOrNull{it.name==kind}
        return VaultCard(title=if(kind=="Custom")"New Card" else kind,kind=kind,profile=profile,
            fields=template?.fieldNames?.map{DataField(it,"")}?:emptyList())
    }

    // This ZIP is deliberately not encrypted. Store it only where you trust the recipient.
    fun exportBackup(uri:Uri,password:String) {
        context.contentResolver.openOutputStream(uri,"w")!!.use{output->
            ZipOutputStream(output).use{zip->
                zip.putNextEntry(ZipEntry("index.json"))
                zip.write(snapshot().toByteArray(Charsets.UTF_8));zip.closeEntry()
                // A readable attachment list accompanies UUID-addressed storage blobs.
                val readable=cards.flatMap{it.files}.distinctBy{it.id}.joinToString("\n"){file->
                    file.id+" : "+VaultNames.label(file.name).replace(10.toChar(),' ').replace(13.toChar(),' ')
                }
                zip.putNextEntry(ZipEntry("filenames.txt"))
                zip.write(readable.toByteArray(Charsets.UTF_8));zip.closeEntry()
                val fileIds=referencedIds()
                fileIds.forEach{id->
                    zip.putNextEntry(ZipEntry("blobs/"+id))
                    blob(id).inputStream().use{it.copyTo(zip,65536)}
                    zip.closeEntry()
                }
            }
        }
    }
    fun restoreBackup(uri:Uri,password:String) {
        val staged=File(context.cacheDir,"restore-"+uid()).apply{mkdirs()}
        try{
            var root:String?=null
            val imported=mutableMapOf<String,File>()
            context.contentResolver.openInputStream(uri)!!.use{input->
                ZipInputStream(input).use{zip->
                    while(true){
                        val entry=zip.nextEntry?:break
                        if(entry.name=="index.json"){
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
                        }else if(entry.name=="filenames.txt"){
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
                            target.outputStream().use{zip.copyTo(it,65536)}
                            imported[id]=target
                        }else throw IOException("Backup contains unsupported or unsafe entry: "+entry.name.take(120))
                        zip.closeEntry()
                    }
                }
            }
            val json=JSONObject(root?:throw IOException("Backup index missing"))
            val array=json.getJSONArray("cards")
            val nextCards=(0 until array.length()).map{readCard(array.getJSONObject(it))}
            val ids=nextCards.flatMap{it.files}.flatMap{listOf(it.id,it.originalId)}
                .filter{it.isNotBlank()}.toSet()
            require(ids.all{imported[it]?.isFile==true}){"Backup has missing files"}
            val nextProfiles=mutableListOf<VaultProfile>()
            val p=json.optJSONArray("profiles")?:JSONArray()
            for(i in 0 until p.length()){val e=p.getJSONObject(i);nextProfiles.add(VaultProfile(e.getString("id"),e.getString("name")))}
            val nextTemplates=mutableListOf<CardTemplate>()
            val t=json.optJSONArray("templates")?:JSONArray()
            for(i in 0 until t.length()){
                val e=t.getJSONObject(i)
                nextTemplates.add(CardTemplate(e.getString("id"),e.getString("name"),strings(e.optJSONArray("fields"))))
            }
            // Preserve blobs that an attempted restore would overwrite, allowing rollback.
            val prior=File(staged,"previous").apply{mkdirs()}
            imported.keys.forEach{id->if(blob(id).isFile)blob(id).copyTo(File(prior,id),overwrite=true)}
            try{imported.forEach{(id,file)->file.copyTo(blob(id),overwrite=true)}}
            catch(e:Exception) {
                imported.keys.forEach{id->
                    val old=File(prior,id)
                    if(old.isFile)old.copyTo(blob(id),overwrite=true) else blob(id).delete()
                }
                throw e
            }
            val oldCards=cards.toList()
            val oldProfiles=profiles.toList()
            val oldTemplates=customTemplates.toList()
            cards.clear();cards.addAll(nextCards)
            profiles.clear();profiles.addAll(nextProfiles)
            if(profiles.none{it.id=="self"})profiles.add(VaultProfile("self","Me"))
            customTemplates.clear();customTemplates.addAll(nextTemplates)
            recoveryError=null
            try{persist()}catch(e:Exception){
                cards.clear();cards.addAll(oldCards)
                profiles.clear();profiles.addAll(oldProfiles)
                customTemplates.clear();customTemplates.addAll(oldTemplates)
                imported.keys.forEach{id->
                    val previous=File(staged,"previous/"+id)
                    if(previous.isFile)previous.copyTo(blob(id),overwrite=true) else blob(id).delete()
                }
                throw e
            }
            // Index is committed: only now may superseded blobs and metadata be deleted.
            blobDir.listFiles()?.filter{f->
                (f.name.endsWith(".dat")||f.name.endsWith(".meta")) &&
                    f.name.substringBeforeLast('.') !in ids
            }?.forEach{it.delete()}
            nextCards.flatMap{it.files}.distinctBy{it.id}.forEach{file->
                try{writeMeta(file)}catch(_:IOException){}
            }
        }finally{staged.deleteRecursively()}
    }
}
