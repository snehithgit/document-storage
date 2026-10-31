package com.snehith.documentvault

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

data class SmartDocument(
    val kind:String,
    val fullName:String?,
    val expiry:String?,
    val fields:List<DataField>,
    val confidence:Float
){
    val title:String get()=if(fullName.isNullOrBlank())kind else "$kind - $fullName"
}

/** Offline, deterministic extraction: no API calls, model downloads or secrets. */
object SmartDocuments {
    private val mrzWeights=intArrayOf(7,3,1)
    private fun mrzValue(c:Char):Int=when(c){
        in '0'..'9'->c-'0'
        in 'A'..'Z'->c-'A'+10
        '<'->0
        else->-1
    }
    fun mrzCheck(value:String,check:Char):Boolean {
        if(check !in '0'..'9')return false
        var total=0
        for((index,ch) in value.withIndex()){
            val code=mrzValue(ch)
            if(code<0)return false
            total+=code*mrzWeights[index%3]
        }
        return total%10==check-'0'
    }

    private val verhoeffD=arrayOf(
        intArrayOf(0,1,2,3,4,5,6,7,8,9),
        intArrayOf(1,2,3,4,0,6,7,8,9,5),
        intArrayOf(2,3,4,0,1,7,8,9,5,6),
        intArrayOf(3,4,0,1,2,8,9,5,6,7),
        intArrayOf(4,0,1,2,3,9,5,6,7,8),
        intArrayOf(5,9,8,7,6,0,4,3,2,1),
        intArrayOf(6,5,9,8,7,1,0,4,3,2),
        intArrayOf(7,6,5,9,8,2,1,0,4,3),
        intArrayOf(8,7,6,5,9,3,2,1,0,4),
        intArrayOf(9,8,7,6,5,4,3,2,1,0))
    private val verhoeffP=arrayOf(
        intArrayOf(0,1,2,3,4,5,6,7,8,9),
        intArrayOf(1,5,7,6,2,8,3,0,9,4),
        intArrayOf(5,8,0,3,7,9,6,1,4,2),
        intArrayOf(8,9,1,6,0,4,3,5,2,7),
        intArrayOf(9,4,5,3,1,2,6,8,7,0),
        intArrayOf(4,2,8,6,5,7,3,9,0,1),
        intArrayOf(2,7,9,3,8,0,6,4,1,5),
        intArrayOf(7,0,4,6,9,1,3,2,5,8))
    fun validAadhaar(candidate:String):Boolean {
        val digits=candidate.filter{it.isDigit()}
        if(digits.length!=12||digits.any{it !in '0'..'9'}||digits.first() in "01")return false
        var c=0
        digits.reversed().forEachIndexed{i,char->
            c=verhoeffD[c][verhoeffP[i%8][char-'0']]
        }
        return c==0
    }

    /** Shared strict OCR date parser: flexible day/month width, case-insensitive month. */
    fun normalizeDate(raw:String):String? {
        val clean=raw.trim().replace(',', ' ').replace(Regex("\\s+")," ")
        val patterns=listOf("uuuu-M-d","d/M/uuuu","d-M-uuuu","d.M.uuuu",
            "d MMM uuuu","d-MMM-uuuu","MMM d uuuu")
        for(pattern in patterns){
            val parser=java.time.format.DateTimeFormatterBuilder()
                .parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
            try{return LocalDate.parse(clean,parser).toString()}
            catch(_:DateTimeParseException){}
        }
        return null
    }
    private fun normalizedMrzLine(raw:String):String =
        raw.uppercase(Locale.ROOT).replace(" ","").replace('«','<')
            .replace(Regex("K{2,}")){ match->"<".repeat(match.value.length) }
            .padEnd(44,'<').take(44)
    private fun digitMrz(value:String):String =
        value.map{when(it){'O'->'0';'I'->'1';'S'->'5';'B'->'8';else->it}}.joinToString("")
    private fun mrzDate(raw:String,birth:Boolean):String? {
        if(raw.length!=6||raw.any{!it.isDigit()})return null
        val yy=raw.substring(0,2).toInt()
        val current=LocalDate.now().year
        val year=if(birth){
            val twenty=2000+yy
            if(twenty<=current)twenty else 1900+yy
        }else{
            val twenty=2000+yy
            if(twenty>current+40)twenty-100 else twenty
        }
        return try{LocalDate.of(year,raw.substring(2,4).toInt(),raw.substring(4,6).toInt()).toString()}
        catch(_:Exception){null}
    }
    private fun labelValue(text:String,vararg labels:String):String? {
        for(label in labels) {
            val rx=Regex("(?im)^\\s*(?:$label)\\s*[:\\-]?\\s*([^\\r\\n]{2,65})")
            val value=rx.find(text)?.groupValues?.getOrNull(1)?.trim()
            if(!value.isNullOrBlank())return value
        }
        return null
    }
    /** Extract only the applicant's name, never a father/mother/spouse OCR line. */
    private fun nameValue(text:String):String? {
        val relatives=Regex("(?i)(?:father|mother|spouse|husband|wife|guardian|parent|nominee)")
        val labels=Regex("(?i)^(?:नाम\\s*[/|]\\s*)?(?:full\\s*name|name\\s*of\\s*applicant|applicant\\s*name|name\\s*of\\s*(?:holder|bearer)|name)\\s*[:\\-]?\\s*(.*)$")
        val lines=text.lines().map{it.trim()}.filter{it.isNotBlank()}
        fun usable(raw:String):String? {
            val value=raw.replace(Regex("[^\\p{L}\\s.'-]"),"").trim()
            return value.takeIf{it.length in 3..55 && it.split(Regex("\\s+")).size in 1..5 &&
                !relatives.containsMatchIn(it) &&
                !Regex("(?i)(?:government|department|income tax|india|aadhaar|birth|number|gender)").containsMatchIn(it)}
        }
        for(i in lines.indices){
            val line=lines[i]
            if(relatives.containsMatchIn(line))continue
            val match=labels.matchEntire(line)?:continue
            val inline=usable(match.groupValues[1])
            if(inline!=null)return inline
            val after=lines.getOrNull(i+1)?.let(::usable)
            if(after!=null)return after
        }
        return null
    }

    private fun dateValue(text:String,vararg labels:String):String?{
        val dates=Regex("\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[ -][A-Za-z]{3}[ -]\\d{4}")
        val value=labelValue(text,*labels)?:return null
        return dates.find(value)?.value?.let{normalizeDate(it)}
    }
    private fun field(name:String,value:String?,confidence:Float):DataField?=
        value?.takeIf{it.isNotBlank()}?.let{DataField(name,it,source="extracted",confidence=confidence)}

    fun parse(text:String):SmartDocument? {
        val compact=text.uppercase(Locale.ROOT)
        val lines=compact.lines().map{it.trim().replace(" ","")}.filter{it.isNotBlank()}
        val passportIndex=lines.indexOfFirst{it.startsWith("P<")&&it.length in 42..46}
        if(passportIndex>=0 && passportIndex+1<lines.size){
            val one=normalizedMrzLine(lines[passportIndex])
            val two=normalizedMrzLine(lines[passportIndex+1])
            if(one.length==44&&two.length==44){
                val number=two.substring(0,9).trimEnd('<')
                val numberOK=mrzCheck(two.substring(0,9),digitMrz(two[9].toString())[0])
                val birthDigits=digitMrz(two.substring(13,19))
                val expiryDigits=digitMrz(two.substring(21,27))
                val dobOK=mrzCheck(birthDigits,digitMrz(two[19].toString())[0])
                val expiryOK=mrzCheck(expiryDigits,digitMrz(two[27].toString())[0])
                val compositeOK=mrzCheck(
                    two.substring(0,9)+digitMrz(two[9].toString())+
                        birthDigits+digitMrz(two[19].toString())+
                        expiryDigits+digitMrz(two[27].toString())+
                        two.substring(28,43),
                    digitMrz(two[43].toString())[0])
                val fullName=one.substring(5).split("<<").take(2).joinToString(" "){
                    it.replace("<"," ").trim()
                }.replace(Regex("\\s+")," ").trim().takeIf{it.isNotBlank()}
                val dob=mrzDate(birthDigits,true)
                val expiry=mrzDate(expiryDigits,false)
                val confidence=if(numberOK&&dobOK&&expiryOK&&compositeOK).99f
                    else if(numberOK&&dobOK&&expiryOK).89f else .65f
                val fields=listOfNotNull(
                    field("Full Name",fullName,confidence),
                    field("Passport Number",if(numberOK)number else null,confidence),
                    field("Nationality",two.substring(10,13).takeUnless{it.contains('<')},confidence),
                    field("Date of Birth",if(dobOK)dob else null,confidence),
                    field("Sex",two[20].toString().takeUnless{it=="<"},confidence),
                    field("Expiry Date",if(expiryOK)expiry else null,confidence))
                return SmartDocument("Passport",fullName,if(expiryOK)expiry else null,fields,confidence)
            }
        }
        val detectedName=nameValue(text)
        val inferredName=if(detectedName==null) {
            val originals=text.lines().map{it.trim()}
            val relatives=Regex("(?i)(?:father|mother|spouse|husband|wife|guardian|parent)")
            val near=originals.indexOfFirst{
                Regex("(?i)(?:DOB|date of birth|birth|male|female)").containsMatchIn(it)
            }
            if(near>0)originals.subList((near-3).coerceAtLeast(0),near)
                .indices.reversed().firstNotNullOfOrNull{index->
                    val absolute=(near-3).coerceAtLeast(0)+index
                    val candidate=originals[absolute]
                    val before=originals.getOrNull(absolute-1).orEmpty()
                    candidate.takeIf{
                        it.length in 4..55&&!it.any(Char::isDigit)&&
                        it.split(Regex("\\s+")).size in 2..5 &&
                        !relatives.containsMatchIn(it) && !relatives.containsMatchIn(before) &&
                        !Regex("(?i)(?:government|india|uidai|aadhaar|address|enrolment)").containsMatchIn(it)
                    }
                } else null
        }else null
        val name=detectedName?:inferredName
        val birth=dateValue(text,"date\\s*of\\s*birth","dob","birth\\s*date")
        val expiry=dateValue(text,"expiry\\s*date","date\\s*of\\s*expiry","valid\\s*until","valid\\s*upto")
        if(compact.contains("AADHAAR")||compact.contains("UIDAI")||compact.contains("UNIQUE IDENTIFICATION")){
            val candidates=Regex("(?<!\\d)(?:[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4})(?!\\d)")
                .findAll(text).map{it.value.filter(Char::isDigit)}.filter(::validAadhaar).toList()
            val number=candidates.firstOrNull()?:return null
            return SmartDocument("Aadhaar",name,null,listOfNotNull(
                field("Full Name",name,.85f),field("Aadhaar Number",number,.98f),
                field("Date of Birth",birth,.85f)),.96f)
        }
        if(compact.contains("PAN")||compact.contains("INCOME TAX")||compact.contains("PERMANENT ACCOUNT")){
            val number=Regex("(?<![A-Z0-9])[A-Z]{5}\\d{4}[A-Z](?![A-Z0-9])")
                .find(compact)?.value
            if(number!=null)return SmartDocument("PAN",name,null,listOfNotNull(
                field("Full Name",name,.8f),field("PAN Number",number,.99f),
                field("Date of Birth",birth,.8f)),.94f)
        }
        if(compact.contains("DRIVING LICENCE")||compact.contains("DRIVING LICENSE")||
            compact.contains("DRIVER'S LICENCE")){
            val number=labelValue(text,"(?:dl|licence|license)\\s*(?:no|number|id)\\.?")
                ?.split(Regex("\\s+"))?.firstOrNull()?.takeIf{it.length in 7..25}
                ?:Regex("(?<![A-Z0-9])[A-Z]{2}[ -]?\\d{2}[ -]?\\d{8,13}(?![A-Z0-9])")
                    .find(compact)?.value
            return SmartDocument("Driving Licence",name,expiry,listOfNotNull(
                field("Full Name",name,.7f),field("Licence Number",number,.75f),
                field("Date of Birth",birth,.7f),field("Expiry Date",expiry,.7f)),.72f)
        }
        if(compact.contains("CONTINUOUS DISCHARGE")||compact.contains("SEAFARER CDC")||
            Regex("\\bCDC\\b").containsMatchIn(compact)){
            val number=labelValue(text,"(?:cdc|certificate)\\s*(?:no|number)\\.?")
                ?.split(Regex("\\s+"))?.firstOrNull()?.takeIf{it.length in 5..24}
            return SmartDocument("Seafarer CDC",name,expiry,listOfNotNull(
                field("Full Name",name,.7f),field("CDC Number",number,.72f),
                field("Date of Birth",birth,.7f),field("Expiry Date",expiry,.7f)),.7f)
        }
        return null
    }
}
