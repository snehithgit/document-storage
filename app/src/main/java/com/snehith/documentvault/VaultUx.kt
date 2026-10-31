package com.snehith.documentvault

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object VaultUx {
    fun importTitle(filename:String,kind:String,existing:List<String>,now:LocalDateTime):String {
        val stem=filename.substringBeforeLast('.',filename).trim()
        val camera=stem.matches(Regex("(?i)(?:img|dsc|pxl|screenshot|scan|capture|document)[_ -].*|[0-9 _()-]+"))
        val base=if(stem.isBlank()||camera) {
            if(kind!="Custom"&&kind.isNotBlank())kind else
                "Document · "+now.format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm",Locale.ENGLISH))
        } else stem
        val used=existing.map{it.lowercase(Locale.ROOT)}.toSet()
        if(base.lowercase(Locale.ROOT) !in used)return base
        var suffix=2
        while("$base ($suffix)".lowercase(Locale.ROOT) in used)suffix++
        return "$base ($suffix)"
    }
    fun initials(name:String):String {
        val words=name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}
        return (if(words.size>1)words.take(2).joinToString(""){it.take(1)} else words.firstOrNull()?.take(2))
            ?.uppercase(Locale.ROOT).orEmpty().ifBlank{"?"}
    }
    fun avatarColor(identity:String)=(identity.hashCode().toLong() and 0x7fffffffL).rem(6).toInt()
}
