package com.snehith.documentvault

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DataField(val name: String, val value: String, val page: Int = -1, val source: String = "manual")
data class VaultFile(val id: String, val name: String, val mime: String, val bytes: Long, val sha256: String, val extractedText: String = "", val ocrStatus: String = "pending")
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
            "Date" to Regex("\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}\\b"),
            "Passport Number (verify)" to Regex("\\b[A-Z][0-9]{7}\\b"),
            "Phone (verify)" to Regex("(?<![0-9])(?:\\+?91[- ]?)?[6-9][0-9]{9}(?![0-9])")
        )
        patterns.forEach { (key,re)->re.find(source)?.value?.let { put(key,it) } }
        val lines=source.lines()
        val labelPattern=Regex("(?i)^\\s*(full\\s*name|name|passport\\s*(?:no|number)|document\\s*(?:no|number)|date of birth|dob|expiry\\s*date|date of expiry|issue\\s*date|date of issue|place of issue|address|account\\s*(?:no|number)|certificate\\s*(?:no|number))\\s*[:=-]\\s*(.+)$")
        for((page,line) in lines.withIndex()) {
            val m=labelPattern.find(line.trim()) ?: continue
            val key=m.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val value=m.groupValues[2].trim().take(250)
            if(value.isNotBlank() && value.length<251) found[key] = DataField(key,value,page,"extracted")
        }
        return found.values.toList()
    }
}

class VaultRepository(private val context: Context) {
    val cards = mutableStateListOf<VaultCard>()
    val profiles = mutableStateListOf<VaultProfile>()
    val customTemplates = mutableStateListOf<CardTemplate>()
    private val data = File(context.filesDir,"vault.state")
    private val blobDir = File(context.filesDir,"blobs").apply{mkdirs()}
    private val random = SecureRandom()
    private val prefs = context.getSharedPreferences("vault_lock",Context.MODE_PRIVATE)
    private fun uid() = UUID.randomUUID().toString()

    init { load() }
    fun hasPin() = prefs.contains("pinHash")
    fun setPin(pin:String) {
        require(pin.length>=4)
        val salt=ByteArray(16).also(random::nextBytes)
        prefs.edit().putString("pinSalt",android.util.Base64.encodeToString(salt,2))
            .putString("pinHash",android.util.Base64.encodeToString(pinHash(pin,salt),2)).apply()
    }
    fun clearPin() { prefs.edit().remove("pinSalt").remove("pinHash").apply() }
    fun checkPin(pin:String):Boolean {
        if(!hasPin()) return true
        val salt=android.util.Base64.decode(prefs.getString("pinSalt",""),2)
        val expected=android.util.Base64.decode(prefs.getString("pinHash",""),2)
        return MessageDigest.isEqual(expected,pinHash(pin,salt))
    }
    private fun pinHash(pin:String,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,150000,256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("vault-aes-v1",null) as? SecretKey)?.let{return it}
        val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder("vault-aes-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }
    private fun cipher(mode:Int, iv:ByteArray? = null):Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            if(iv==null) init(mode,key()) else init(mode,key(),GCMParameterSpec(128,iv))
        }
    private fun encrypt(input:InputStream, output:OutputStream) {
        val enc=cipher(Cipher.ENCRYPT_MODE)
        output.write(enc.iv)
        CipherOutputStream(output,enc).use { sealed -> input.copyTo(sealed, 65536) }
    }
    private fun decrypt(input:InputStream, output:OutputStream) {
        val iv=ByteArray(12)
        if(input.read(iv)!=12) throw IOException("Invalid encrypted vault data")
        CipherInputStream(input,cipher(Cipher.DECRYPT_MODE,iv)).use{sealed->sealed.copyTo(output,65536)}
    }
    fun load() {
        cards.clear();profiles.clear();customTemplates.clear()
        if(data.exists()) try {
            val bytes=ByteArrayOutputStream()
            decrypt(data.inputStream(),bytes)
            val root=JSONObject(bytes.toString("UTF-8"))
            val c=root.optJSONArray("cards") ?: JSONArray()
            for(i in 0 until c.length()) cards.add(readCard(c.getJSONObject(i)))
            val p=root.optJSONArray("profiles") ?: JSONArray()
            for(i in 0 until p.length()) { val e=p.getJSONObject(i); profiles.add(VaultProfile(e.getString("id"),e.getString("name"))) }
            val t=root.optJSONArray("templates") ?: JSONArray()
            for(i in 0 until t.length()){ val e=t.getJSONObject(i);customTemplates.add(CardTemplate(e.getString("id"),e.getString("name"),strings(e.optJSONArray("fields")))) }
        } catch(e:Exception) { throw IllegalStateException("Cannot decrypt the vault. Data was preserved.",e) }
        if(profiles.none{it.id=="self"}) profiles.add(0,VaultProfile("self","Me"))
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
            fields=(0 until fields.length()).map { val f=fields.getJSONObject(it); DataField(f.optString("name"),f.optString("value"),f.optInt("page",-1),f.optString("source","manual")) },
            files=(0 until files.length()).map{ val f=files.getJSONObject(it); VaultFile(f.getString("id"),f.optString("name"),f.optString("mime"),f.optLong("bytes"),f.optString("sha256"),f.optString("extractedText"),f.optString("ocrStatus","pending")) }
        )
    }
    private fun writeCard(c:VaultCard):JSONObject=JSONObject()
        .put("id",c.id).put("title",c.title).put("kind",c.kind).put("category",c.category)
        .put("profile",c.profile).put("tags",c.tags).put("notes",c.notes).put("expiry",c.expiry)
        .put("previousId",c.previousId).put("created",c.created)
        .put("fields",JSONArray().apply { c.fields.forEach{put(JSONObject().put("name",it.name).put("value",it.value).put("page",it.page).put("source",it.source))} })
        .put("files",JSONArray().apply { c.files.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("mime",it.mime).put("bytes",it.bytes).put("sha256",it.sha256).put("extractedText",it.extractedText).put("ocrStatus",it.ocrStatus))} })
    private fun snapshot():String = JSONObject()
        .put("cards",JSONArray().apply{cards.forEach{put(writeCard(it))}})
        .put("profiles",JSONArray().apply{profiles.forEach{put(JSONObject().put("id",it.id).put("name",it.name))}})
        .put("templates",JSONArray().apply{customTemplates.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("fields",JSONArray(it.fieldNames)))} })
        .toString()

    @Synchronized fun persist() {
        val tmp=File(context.filesDir,"vault.state.tmp")
        ByteArrayInputStream(snapshot().toByteArray(Charsets.UTF_8)).use { src -> FileOutputStream(tmp).use{ output ->encrypt(src,output);output.fd.sync()} }
        if(!tmp.renameTo(data)) { tmp.copyTo(data,overwrite=true);tmp.delete() }
    }
    @Synchronized fun saveCard(card:VaultCard) {
        val index=cards.indexOfFirst{it.id==card.id}
        if(index<0) cards.add(0,card) else cards[index]=card
        persist()
    }
    @Synchronized fun deleteCard(card:VaultCard) {
        cards.removeAll{it.id==card.id}
        card.files.forEach{f->blob(f.id).delete()}
        persist()
    }
    fun saveProfile(p:VaultProfile) {val i=profiles.indexOfFirst{it.id==p.id};if(i<0)profiles.add(p) else profiles[i]=p;persist()}
    fun saveTemplate(t:CardTemplate){val i=customTemplates.indexOfFirst{it.id==t.id};if(i<0)customTemplates.add(t) else customTemplates[i]=t;persist()}
    private fun blob(id:String)=File(blobDir,id+".dat")
    fun isDuplicate(sha:String)=cards.any{c->c.files.any{it.sha256==sha}}
    fun importFile(uri:Uri):VaultFile {
        val name=filename(uri)
        val mime=context.contentResolver.getType(uri) ?: when(name.substringAfterLast('.',"").lowercase()){
            "pdf"->"application/pdf";"txt"->"text/plain";"docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";"doc"->"application/msword"
            "png"->"image/png";"jpg","jpeg"->"image/jpeg";else->"application/octet-stream"
        }
        val id=uid()
        val dig=MessageDigest.getInstance("SHA-256")
        var size=0L
        try {
            context.contentResolver.openInputStream(uri)?.use { src ->
                val input=object:FilterInputStream(src){
                    override fun read():Int {val x=super.read();if(x>=0){dig.update(x.toByte());size++};return x}
                    override fun read(b:ByteArray,off:Int,len:Int):Int {val n=super.read(b,off,len);if(n>0){dig.update(b,off,n);size+=n};return n}
                }
                FileOutputStream(blob(id)).use{encrypt(input,it)}
            } ?: throw IOException("Cannot read selected file")
            val sha=dig.digest().joinToString(""){ "%02x".format(it) }
            return VaultFile(id,name,mime,size,sha)
        } catch(e:Exception){blob(id).delete();throw e}
    }
    private fun filename(uri:Uri):String {
        var name=""
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cur->
            if(cur.moveToFirst()) name=cur.getString(0) ?: ""
        }
        return (name.ifBlank{uri.lastPathSegment?: "document"}).replace(Regex("[\\r\\n\\u0000-\\u001f/]"),"_").take(150)
    }
    fun openDecrypted(file:VaultFile):File {
        val dir=File(context.cacheDir,"preview").apply{mkdirs()}
        val destination=File(dir,file.id+"-"+file.name.substringAfterLast('.', "dat").take(8))
        FileInputStream(blob(file.id)).use{src->FileOutputStream(destination).use{decrypt(src,it)}}
        return destination
    }
    fun encryptedBlobExists(file:VaultFile)=blob(file.id).exists()
    fun cleanupPreviews() {
        listOf("preview","shared").forEach{ folder -> File(context.cacheDir,folder).listFiles()?.forEach { if(System.currentTimeMillis()-it.lastModified()>3600000)it.delete() } }
    }
    fun newCard(kind:String="Custom",profile:String="self"):VaultCard {
        val t=(FieldSuggestions.templates+customTemplates).firstOrNull{it.name==kind}
        return VaultCard(title=if(kind=="Custom") "New Card" else kind,kind=kind,profile=profile,fields=t?.fieldNames?.map{DataField(it,"")}?:emptyList())
    }
    fun exportBackup(uri:Uri,password:String) {
        require(password.length>=8){"Backup password must have at least 8 characters"}
        val salt=ByteArray(16).also(random::nextBytes)
        val iv=ByteArray(12).also(random::nextBytes)
        val key=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(),salt,210000,256)).encoded,"AES")
        val enc=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv))}
        context.contentResolver.openOutputStream(uri,"w")!!.use { stream ->
            stream.write("DVAULT1".toByteArray());stream.write(salt);stream.write(iv)
            ZipOutputStream(CipherOutputStream(stream,enc)).use{zip->
                zip.putNextEntry(ZipEntry("index.json"))
                zip.write(snapshot().toByteArray());zip.closeEntry()
                cards.flatMap{it.files}.distinctBy{it.id}.forEach{file ->
                    zip.putNextEntry(ZipEntry("blobs/"+file.id))
                    FileInputStream(blob(file.id)).use{src->decrypt(src,zip)}
                    zip.closeEntry()
                }
            }
        }
    }
    fun restoreBackup(uri:Uri,password:String) {
        val staged=File(context.cacheDir,"restore-"+uid()).apply{mkdirs()}
        try {
            val imported=mutableListOf<Pair<String,File>>()
            var root:String?=null
            context.contentResolver.openInputStream(uri)!!.use{stream->
                val header=ByteArray(7); if(stream.read(header)!=7||String(header)!="DVAULT1") throw IOException("Unsupported backup format")
                val salt=ByteArray(16);val iv=ByteArray(12);if(stream.read(salt)!=16||stream.read(iv)!=12)throw IOException("Corrupt backup")
                val key=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(PBEKeySpec(password.toCharArray(),salt,210000,256)).encoded,"AES")
                val dec=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,iv))}
                ZipInputStream(CipherInputStream(stream,dec)).use{zip->
                    while(true){
                        val entry=zip.nextEntry?:break
                        if(entry.name=="index.json"){val bytes=ByteArrayOutputStream();zip.copyTo(bytes);root=bytes.toString("UTF-8")}
                        else if(entry.name.matches(Regex("blobs/[a-f0-9-]{36}"))){
                            val id=entry.name.substringAfter('/')
                            val target=File(staged,id)
                            target.outputStream().use{zip.copyTo(it)}
                            imported.add(id to target)
                        }
                        zip.closeEntry()
                    }
                }
            }
            val json=JSONObject(root?:throw IOException("Backup index missing"))
            val importedCards=json.getJSONArray("cards")
            val ids=(0 until importedCards.length()).flatMap { i ->
                val a=importedCards.getJSONObject(i).optJSONArray("files")?:JSONArray()
                (0 until a.length()).map{j->a.getJSONObject(j).getString("id")}
            }.toSet()
            if(ids.any{id->imported.none{it.first==id}})throw IOException("Some attachments are missing")
            // A restore is a replacement, performed only after all backup entries validated.
            imported.forEach{(id,file)->
                FileOutputStream(blob(id)).use{out ->FileInputStream(file).use{src->encrypt(src,out)}}
            }
            cards.clear();profiles.clear();customTemplates.clear()
            for(i in 0 until importedCards.length())cards.add(readCard(importedCards.getJSONObject(i)))
            val p=json.optJSONArray("profiles")?:JSONArray()
            for(i in 0 until p.length()){val it=p.getJSONObject(i);profiles.add(VaultProfile(it.getString("id"),it.getString("name")))}
            val t=json.optJSONArray("templates")?:JSONArray()
            for(i in 0 until t.length()){val it=t.getJSONObject(i);customTemplates.add(CardTemplate(it.getString("id"),it.getString("name"),strings(it.optJSONArray("fields"))))}
            if(profiles.none{it.id=="self"})profiles.add(VaultProfile("self","Me"))
            persist()
        } finally {staged.deleteRecursively()}
    }
}
