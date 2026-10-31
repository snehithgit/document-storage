package com.snehith.documentvault

import java.util.Locale
import java.util.UUID

data class DocumentChange(val cards:List<VaultCard>,val entries:List<ProfileEntry>,val destinationIds:List<String>)

/** Plans are pure; the repository commits documents and their profile links together. */
object VaultDocumentActions {
    fun screenKey(route:String,cardId:String,session:String="",fileId:String="",personId:String=""):String = when(route) {
        "edit" -> "edit:$session"
        "detail","fulldetails","selectfields","attachments","tools" -> "$route:$cardId"
        "merge-documents","split-document" -> "$route:$cardId:$session"
        "person" -> "$route:$personId"
        "viewer" -> "$route:$cardId:$fileId:$session"
        "crop" -> "$route:$session"
        else -> route
    }
    fun uniqueTitle(base:String,existing:Collection<String>):String {
        val used=existing.map{it.lowercase(Locale.ROOT)}.toSet()
        if(base.lowercase(Locale.ROOT) !in used)return base
        var n=2
        while("$base ($n)".lowercase(Locale.ROOT) in used)n++
        return "$base ($n)"
    }
    fun merge(cards:List<VaultCard>,entries:List<ProfileEntry>,targetId:String,sources:Set<String>,move:Boolean,
              newId:()->String={UUID.randomUUID().toString()},ownerNames:Map<String,String> = emptyMap()):DocumentChange {
        require(sources.isNotEmpty()&&targetId !in sources){"Choose other documents to merge"}
        val target=cards.firstOrNull{it.id==targetId}?:error("Destination document no longer exists")
        require(sources.all{id->cards.any{it.id==id}}){"A selected document no longer exists"}
        val selected=cards.filter{it.id in sources}
        val files=target.files.toMutableList()
        val fields=target.fields.toMutableList()
        val links=mutableMapOf<Pair<String,String>,String>()
        selected.forEach { source ->
            source.files.forEach { file ->
                val duplicate=files.firstOrNull{it.id==file.id}
                require(duplicate==null||duplicate==file){"A shared attachment has conflicting details; resolve them before merging"}
                if(duplicate==null)files.add(file)
            }
            source.fields.forEach { field ->
                val duplicate=fields.firstOrNull{it.id==field.id}
                val added=if(duplicate==null||duplicate==field)field else field.copy(id=newId())
                if(duplicate==null||duplicate!=field)fields.add(added)
                links[source.id to field.id]=added.id
            }
        }
        val notes=(listOf(target.notes)+selected.map { source ->
            buildString {
                append("Merged from: ").append(source.title)
                append("\nType: ").append(source.kind).append("; category: ").append(source.category)
                append("; person: ").append(ownerNames[source.profile]?:source.profile)
                if(source.expiry.isNotBlank())append("\nExpiry: ").append(source.expiry)
                if(source.previousId.isNotBlank())append("\nPrevious document ID: ").append(source.previousId)
                if(source.notes.isNotBlank())append("\n").append(source.notes)
            }
        }).filter{it.isNotBlank()}.joinToString("\n\n")
        val tags=(listOf(target)+selected).flatMap{it.tags.split(',')}.map{it.trim()}.filter{it.isNotBlank()}.distinct().joinToString(", ")
        // Contracting a history chain through a moved source must not create a cycle:
        // destination -> intermediate -> source would otherwise become destination -> intermediate -> destination.
        val byId=cards.associateBy{it.id};val visited=mutableSetOf(targetId)
        var history=target.previousId;var loopsAfterMove=false
        while(move&&history.isNotBlank()&&visited.add(history)){
            if(history in sources){loopsAfterMove=true;break}
            history=byId[history]?.previousId.orEmpty()
        }
        val mergedNotes=if(loopsAfterMove)listOf(notes,"Previous document before merge: "+target.previousId)
            .filter{it.isNotBlank()}.joinToString("\n\n") else notes
        val merged=target.copy(files=files,fields=fields,notes=mergedNotes,tags=tags,
            previousId=if(loopsAfterMove)"" else target.previousId)
        val next=cards.filterNot{move&&it.id in sources}.map { card ->
            if(card.id==targetId)merged else if(move&&card.previousId in sources)card.copy(previousId=targetId) else card
        }
        val nextEntries=if(!move)entries else entries.map { entry ->
            val mapped=links[entry.cardId to entry.fieldId]
            if(entry.cardId in sources&&entry.linked&&mapped!=null)entry.copy(cardId=targetId,
                fieldId=mapped) else entry
        }
        return DocumentChange(next,nextEntries,listOf(targetId))
    }
    fun split(cards:List<VaultCard>,entries:List<ProfileEntry>,source:VaultCard,parts:List<VaultFile>,
              remainder:List<VaultFile>,move:Boolean,newId:()->String={UUID.randomUUID().toString()},
              now:Long=System.currentTimeMillis(),extraNotes:String=""):DocumentChange {
        require(parts.isNotEmpty()){ "Choose at least one file or page" }
        val detailSize=source.notes.length.toLong()+source.fields.sumOf{it.name.length.toLong()+it.value.length}
        require(detailSize*parts.size+extraNotes.length<=10_000_000L){"Split fewer items at once; copied details would be too large"}
        val names=cards.map{it.title}.toMutableList()
        val children=parts.mapIndexed { index,file ->
            val title=uniqueTitle(source.title+" — "+file.name.substringBeforeLast('.',file.name),names)
            names.add(title)
            source.copy(id=newId(),title=title,created=now,previousId="",files=listOf(file),
                fields=source.fields.map{it.copy(page=-1)},
                notes=listOf(source.notes,"Split from: "+source.title,if(index==0)extraNotes else "")
                    .filter{it.isNotBlank()}.joinToString("\n\n"))
        }
        val removeSource=move&&remainder.isEmpty()
        val retained=cards.filterNot{removeSource&&it.id==source.id}.map { card ->
            when {
                move&&card.id==source.id -> card.copy(files=remainder,fields=card.fields.map{it.copy(page=-1)})
                removeSource&&card.previousId==source.id -> card.copy(previousId=children.first().id)
                else -> card
            }
        }
        val nextEntries=if(!removeSource)entries else entries.map { entry ->
            if(entry.cardId==source.id)entry.copy(cardId=children.first().id) else entry
        }
        return DocumentChange(children+retained,nextEntries,children.map{it.id})
    }
}
