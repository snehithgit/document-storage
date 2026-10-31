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
}

data class OcrBatchOutcome(val filename:String,val success:Boolean,val error:String="")
object OcrBatchSummary {
    fun message(outcomes:List<OcrBatchOutcome>):String {
        if(outcomes.isEmpty())return "No files read"
        val successful=outcomes.count{it.success}
        val failures=outcomes.filterNot{it.success}
        if(failures.isEmpty())return "Reading complete: $successful of ${outcomes.size} files"
        return "$successful of ${outcomes.size} read; failed: "+
            failures.joinToString(", "){it.filename}
    }
}
