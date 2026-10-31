package com.snehith.documentvault

/** Applies one OCR result to the latest saved card without losing saved text or
 * anything the user entered. Pure, so it is JVM-testable apart from WorkManager. */
object OcrMerge {
    /** Null when nothing was recognized: an empty re-run must never erase saved text. */
    fun apply(latest:VaultCard,fileId:String,result:ExtractedDocument):VaultCard? {
        if(result.text.isBlank())return null
        val current=latest.files.firstOrNull{it.id==fileId}
            ?:throw java.io.IOException("Attachment was removed")
        val smart=result.document
        val revised=current.copy(extractedText=result.text,ocrStatus=result.method)
        val titleIsGeneric=latest.title.isBlank() ||
            latest.title.startsWith("Scan ",true) ||
            latest.title.startsWith("Scanned document",true) ||
            latest.title.equals(current.name.substringBeforeLast('.'),true)
        val identity=smart?.kind in setOf("Passport","Aadhaar","PAN","Driving Licence")
        return latest.copy(
            files=latest.files.map{if(it.id==fileId)revised else it},
            fields=mergeFields(latest.fields,result.fields),
            category=if(identity && (latest.category=="Personal"||
                latest.category.isBlank()))"Identity" else latest.category,
            kind=smart?.kind?.takeIf{latest.kind=="Custom"||
                latest.kind=="Recovered"}?:latest.kind,
            title=if(titleIsGeneric&&smart!=null&&smart.confidence>=.7f&&
                !smart.fullName.isNullOrBlank())smart.title else latest.title,
            expiry=if(latest.expiry.isBlank())smart?.expiry?:"" else latest.expiry
        )
    }
    /** Manual and selected values are never replaced. A blank field is filled in place,
     * keeping its ID for linked profile entries; an earlier extraction is replaced only
     * by a more confident reading. */
    fun mergeFields(existing:List<DataField>,extracted:List<DataField>):List<DataField> {
        val fields=existing.toMutableList()
        extracted.filter{it.name.isNotBlank()&&it.value.isNotBlank()}.forEach{f->
            val index=fields.indexOfFirst{it.name.equals(f.name,true)}
            if(index<0){fields.add(f);return@forEach}
            val old=fields[index]
            val improves=old.source=="extracted"&&f.confidence>old.confidence
            if(old.value.isBlank()||improves)
                fields[index]=f.copy(id=old.id,name=old.name,page=if(f.page>=0)f.page else old.page)
        }
        return fields
    }
}
