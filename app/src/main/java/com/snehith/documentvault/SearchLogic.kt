package com.snehith.documentvault

/** One line of evidence for why a card matched: where it was found and what it said. */
data class SearchHit(val tag:String,val text:String)
data class SearchResult(val card:VaultCard,val hits:List<SearchHit>,val score:Int)

object SearchLogic{
    const val TITLE="Title"
    const val FIELD="Field"
    const val TEXT="OCR"
    val modes=listOf("All","Documents","Information","Scanned Text")

    /**
     * Cards matching [query] with the reasons they matched, best matches first.
     * A blank query lists every card without reasons. Sensitive field values are
     * masked in the reason text, exactly like everywhere else in the app.
     */
    fun search(cards:List<VaultCard>,query:String,mode:String):List<SearchResult>{
        val q=query.trim()
        if(q.isEmpty())return cards.map{SearchResult(it,emptyList(),0)}
        val withTitle=mode=="All"||mode=="Documents"
        val withFields=mode=="All"||mode=="Information"
        val withText=mode=="All"||mode=="Scanned Text"
        return cards.mapNotNull{card->
            val hits=mutableListOf<SearchHit>()
            if(withTitle){
                val document=card.title+" "+card.category+" "+card.kind+" "+
                    card.files.joinToString(" "){it.name}
                if(document.contains(q,true))
                    hits+=SearchHit(TITLE,card.title+if(card.category.isBlank())"" else " · "+card.category)
            }
            if(withFields){
                card.fields.filter{(it.name+" "+it.value).contains(q,true)}.forEach{f->
                    val shown=if(ProfileLogic.isSensitive(f.name))ProfileLogic.mask(f.value) else f.value
                    hits+=SearchHit(FIELD,f.name+": "+shown)
                }
            }
            if(withText){
                card.files.firstNotNullOfOrNull{file->
                    snippet(file.extractedText,q)
                }?.let{hits+=SearchHit(TEXT,it)}
            }
            if(hits.isEmpty())null else {
                val score=(if(hits.any{it.tag==TITLE})3 else 0)+
                    (if(hits.any{it.tag==FIELD})2 else 0)+
                    (if(hits.any{it.tag==TEXT})1 else 0)
                SearchResult(card,hits.take(3),score)
            }
        }.sortedByDescending{it.score}
    }

    private fun snippet(text:String,query:String):String?{
        val pos=text.indexOf(query,ignoreCase=true)
        if(pos<0)return null
        val from=(pos-24).coerceAtLeast(0)
        val to=(pos+query.length+28).coerceAtMost(text.length)
        val body=text.substring(from,to).replace(Regex("\\s+")," ").trim()
        return (if(from>0)"…" else "")+body+(if(to<text.length)"…" else "")
    }

    /** Non-overlapping, case-insensitive match ranges for highlighting. */
    fun matchRanges(text:String,query:String):List<IntRange>{
        val q=query.trim()
        if(q.isEmpty())return emptyList()
        val out=mutableListOf<IntRange>()
        var from=0
        while(from<text.length){
            val at=text.indexOf(q,from,ignoreCase=true)
            if(at<0)break
            out+=at until at+q.length
            from=at+q.length
        }
        return out
    }

    /** Short search ideas drawn from the person's own cards, never from sample data. */
    fun suggestions(cards:List<VaultCard>,limit:Int=6):List<String>{
        val names=cards.map{it.title.trim()}.filter{it.isNotEmpty()}
        val categories=cards.map{it.category.trim()}.filter{it.isNotEmpty()}
        return (names+categories).distinctBy{it.lowercase()}.take(limit)
    }
}
