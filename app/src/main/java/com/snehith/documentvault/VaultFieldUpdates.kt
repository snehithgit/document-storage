package com.snehith.documentvault

/** Merge a UI editor's change set onto the latest persisted card fields. */
object VaultFieldUpdates {
    fun merge(original:List<DataField>,edited:List<DataField>,
        latest:List<DataField>):List<DataField>{
        val originalById=original.associateBy{it.id}
        val editedById=edited.associateBy{it.id}
        val deleted=originalById.keys-editedById.keys
        val changed=edited.filter { item->
            originalById[item.id]!=item
        }.associateBy{it.id}
        val updated=latest.filterNot{it.id in deleted}
            .map{changed[it.id]?:it}.toMutableList()
        val currentIds=updated.map{it.id}.toSet()
        edited.filter{it.id !in currentIds}.forEach{updated.add(it)}
        // The editor may have rearranged fields: honour that order, and keep fields that
        // exist only in the latest copy (e.g. added by OCR meanwhile) after them.
        val reordered=original.map{it.id}!=edited.map{it.id}
        if(!reordered)return updated
        val byId=updated.associateBy{it.id}
        val ordered=edited.mapNotNull{byId[it.id]}
        val orderedIds=ordered.map{it.id}.toSet()
        return ordered+updated.filter{it.id !in orderedIds}
    }
    private fun key(name:String)=name.lowercase().filter{it.isLetterOrDigit()}
        .replace("number","no")
    /** A value picked from a page fills the field of that name (keeping its ID) rather
     * than adding a near-duplicate such as "CDC No." beside an empty "CDC Number". */
    fun pick(fields:List<DataField>,name:String,value:String,page:Int):List<DataField> {
        val index=fields.indexOfFirst{key(it.name)==key(name)}
        val picked=DataField(name,value,page,"selected")
        if(index<0)return fields+picked
        return fields.mapIndexed{i,f->if(i==index)picked.copy(id=f.id,name=f.name) else f}
    }
    /** Field names to offer when saving a picked value: empty fields first. */
    fun nameSuggestions(card:VaultCard):List<String> {
        val template=FieldSuggestions.templates.firstOrNull{it.name.equals(card.kind,true)}?.fieldNames.orEmpty()
        return (card.fields.filter{it.value.isBlank()}.map{it.name}+card.fields.map{it.name}+template)
            .filter{it.isNotBlank()}.distinctBy{key(it)}
    }
    /** Dates, long numbers and ID-like codes are the values people usually pick. */
    fun likelyValue(line:String):Boolean {
        val text=line.trim()
        return Regex("\\b(?:\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2})\\b").containsMatchIn(text) ||
            Regex("\\d{5,}").containsMatchIn(text.replace(" ","")) ||
            Regex("\\b[A-Z]{1,4}\\d{4,}\\b").containsMatchIn(text)
    }
    /** A changed value is the user's: it leaves review and later OCR runs keep it. */
    fun edited(existing:DataField?,name:String,value:String):DataField {
        val changed=existing==null||existing.value!=value
        return DataField(name,value,existing?.page?:-1,
            if(changed)"manual" else existing!!.source,
            existing?.id?:java.util.UUID.randomUUID().toString(),
            if(changed)0f else existing!!.confidence)
    }
}

data class OcrBatchOutcome(val filename:String,val success:Boolean,val error:String="",val filled:Int=0)
object OcrBatchSummary {
    fun message(outcomes:List<OcrBatchOutcome>):String {
        if(outcomes.isEmpty())return "No files read"
        val successful=outcomes.count{it.success}
        val failures=outcomes.filterNot{it.success}
        val filled=outcomes.sumOf{it.filled}
        if(failures.isEmpty())return if(filled==0)
            "Text was read, but no details were recognised. Use “Select from page” to pick them."
            else "Reading complete: "+VaultCounts.format(filled,"detail")+" filled. Please check them."
        return "$successful of ${outcomes.size} read; failed: "+
            failures.joinToString(", "){it.filename}
    }
}
