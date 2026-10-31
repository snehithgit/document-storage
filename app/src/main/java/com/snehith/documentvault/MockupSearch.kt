package com.snehith.documentvault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Pill used for filters and suggestions, as in the mockup's .chip. */
@Composable fun MockupChip(label:String,selected:Boolean,onClick:()->Unit){
    val p=mockupPalette
    Surface(onClick=onClick,shape=CircleShape,
        color=if(selected)p.ink else p.panelRaised,
        border=if(selected)null else BorderStroke(1.dp,p.line)){
        Text(label,Modifier.padding(horizontal=14.dp,vertical=9.dp),
            color=if(selected)p.panel else p.inkMuted,fontSize=13.sp,
            fontWeight=FontWeight.SemiBold,maxLines=1)
    }
}

/** Text with the matched part of [query] picked out, like the mockup's <mark>. */
@Composable fun HighlightedText(text:String,query:String,color:Color,
    modifier:Modifier=Modifier,fontSize:androidx.compose.ui.unit.TextUnit=13.sp,maxLines:Int=2){
    val p=mockupPalette
    val annotated=remember(text,query,p){
        buildAnnotatedString{
            var cursor=0
            SearchLogic.matchRanges(text,query).forEach{range->
                append(text.substring(cursor,range.first))
                withStyle(SpanStyle(background=p.accentSoft,color=p.accent,
                    fontWeight=FontWeight.SemiBold)){append(text.substring(range.first,range.last+1))}
                cursor=range.last+1
            }
            append(text.substring(cursor))
        }
    }
    Text(annotated,modifier,color=color,fontSize=fontSize,maxLines=maxLines,
        overflow=TextOverflow.Ellipsis)
}

@Composable fun VaultNavSearch(cards:List<VaultCard>,repo:VaultRepository,
    onOpen:(VaultCard)->Unit,profiles:List<VaultProfile> = emptyList(),
    selectedPerson:String="all",onPerson:(String)->Unit={},hidePreviews:Boolean=true){
    val p=mockupPalette
    var query by rememberSaveable{mutableStateOf("")}
    var mode by rememberSaveable{mutableStateOf("All")}
    var results by remember(cards){mutableStateOf(SearchLogic.search(cards,"","All"))}
    var searching by remember{mutableStateOf(false)}
    var scope by remember(cards,selectedPerson){mutableStateOf(cards)}
    LaunchedEffect(query,mode,cards,selectedPerson){
        searching=true
        delay(250)
        val snapshot=VaultPersonFilter.filter(cards,selectedPerson)
        scope=snapshot
        results=withContext(Dispatchers.Default){SearchLogic.search(snapshot,query,mode)}
        searching=false
    }
    val q=query.trim()
    Column(Modifier.fillMaxSize().background(p.panel).padding(horizontal=18.dp)){
        Spacer(Modifier.height(22.dp))
        Eyebrow(profiles.firstOrNull{it.id==selectedPerson}?.name?:"My vault")
        Spacer(Modifier.height(10.dp))
        Text("Search",color=p.ink,fontSize=32.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val fieldShape=RoundedCornerShape(18.dp)
        TextField(query,{query=it},
            modifier=Modifier.fillMaxWidth().border(1.dp,p.line,fieldShape),
            singleLine=true,shape=fieldShape,
            leadingIcon={Icon(Icons.Outlined.Search,null,tint=p.inkFaint)},
            trailingIcon={
                if(query.isNotEmpty())IconButton(onClick={query=""}){
                    Icon(Icons.Outlined.Close,"Clear search",tint=p.inkMuted)
                }
            },
            placeholder={Text("Search names, numbers, text on pages",color=p.inkFaint,fontSize=14.sp)},
            colors=TextFieldDefaults.colors(
                focusedContainerColor=p.panelRaised,unfocusedContainerColor=p.panelRaised,
                focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,
                disabledIndicatorColor=Color.Transparent,
                focusedTextColor=p.ink,unfocusedTextColor=p.ink,cursorColor=p.accent))
        if(profiles.size>1)Row(Modifier.horizontalScroll(rememberScrollState()).padding(top=12.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            MockupChip("Everyone",selectedPerson=="all"){onPerson("all")}
            profiles.forEach{profile->
                MockupChip(profile.name,selectedPerson==profile.id){onPerson(profile.id)}
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top=12.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SearchLogic.modes.forEach{choice->MockupChip(choice,mode==choice){mode=choice}}
        }
        if(searching)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp),
            color=p.accent,trackColor=p.accentSoft)
        if(q.isEmpty()){
            val ideas=SearchLogic.suggestions(scope)
            if(ideas.isNotEmpty()){
                Spacer(Modifier.height(14.dp))
                MockupSectionHeader("Try searching","Titles · fields · text")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    ideas.forEach{idea->MockupChip(idea,false){query=idea}}
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MockupSectionHeader(
            if(q.isEmpty())VaultCounts.format(results.size,"document") else VaultCounts.format(results.size,"result"),
            if(q.isEmpty())null else "Best matches first")
        Spacer(Modifier.height(10.dp))
        if(q.isNotEmpty()&&results.isEmpty()&&!searching){
            Column(Modifier.fillMaxWidth().padding(vertical=36.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.Search,null,tint=p.inkFaint,modifier=Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                Text("No cards match “$q”",color=p.ink,fontSize=15.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Search looks through titles, saved fields and text read from your pages.",
                    color=p.inkMuted,fontSize=13.sp)
            }
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),
            contentPadding=PaddingValues(bottom=24.dp)){
            items(results,key={it.card.id}){result->
                val shape=RoundedCornerShape(18.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(p.panelRaised)
                    .border(1.dp,p.line,shape).clickable{onOpen(result.card)}.padding(12.dp),
                    horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    VaultPreviewPrivacy(hidePreviews&&result.card.category.equals("Identity",true),Modifier.size(62.dp,80.dp),showLabel=false){
                        VaultThumbnail(result.card.files.firstOrNull(),repo,Modifier.size(62.dp,80.dp))
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(VaultNames.title(result.card.title),color=p.ink,fontSize=14.5.sp,
                            fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(result.card.category+" · "+
                            (profiles.firstOrNull{it.id==result.card.profile}?.name?:"Me"),
                            color=p.inkFaint,fontSize=12.sp,maxLines=1)
                        result.hits.forEach{hit->
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
                                verticalAlignment=Alignment.Top){
                                when(hit.tag){
                                    SearchLogic.TEXT->MockupTag("Text",p.warning,p.warningSoft)
                                    SearchLogic.FIELD->MockupTag("Field",p.accent,p.accentSoft)
                                    else->MockupTag("Title",p.inkMuted,p.line.copy(alpha=.5f))
                                }
                                HighlightedText(hit.text,q,p.inkMuted,Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
