package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Seeds fictional documents for a manual UI audit of the isolated storagecheck app.
 * Skipped unless run with `-e auditSeed true`; the records are left in place on purpose. */
@RunWith(AndroidJUnit4::class)
class AuditSeedDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get()=VaultStorage.get(context)

    private fun blob(write:(File)->Unit,name:String,mime:String,text:String=""):VaultFile {
        val id=UUID.randomUUID().toString()
        val target=File(context.filesDir,"blobs/$id.dat").apply{parentFile!!.mkdirs()}
        write(target)
        return VaultFile(id,name,mime,target.length(),VaultBackupChecks.sha256(target.inputStream()),text,
            if(text.isBlank())"pending" else "On-device OCR")
    }
    private fun image(lines:List<String>,name:String,w:Int=1400,h:Int=900)=blob({out->
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(236,240,245))}
        val canvas=Canvas(bitmap);val paint=Paint().apply{color=Color.rgb(20,30,60);textSize=58f;isAntiAlias=true}
        lines.forEachIndexed{i,l->canvas.drawText(l,60f,120f+i*90f,paint)}
        out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    },name,"image/png",lines.joinToString("\n"))
    private fun pdf(pages:List<List<String>>,name:String,height:Int=842)=blob({out->
        val doc=PdfDocument()
        pages.forEachIndexed{index,lines->
            val page=doc.startPage(PdfDocument.PageInfo.Builder(595,height,index+1).create())
            val paint=Paint().apply{textSize=16f}
            lines.forEachIndexed{i,l->page.canvas.drawText(l,40f,60f+i*26f,paint)}
            doc.finishPage(page)
        }
        out.outputStream().use{doc.writeTo(it)};doc.close()
    },name,"application/pdf")

    @Test fun seedFictionalVault() {
        Assume.assumeTrue("Run with -e auditSeed true",
            InstrumentationRegistry.getArguments().getString("auditSeed")=="true")
        check(context.packageName=="com.snehith.documentvault.storagecheck")
        val today=LocalDate.now()
        val asha=VaultProfile("audit-asha","Asha Fictional");val ravi=VaultProfile("audit-ravi","Ravi Fictional")
        repo.saveProfile(asha);repo.saveProfile(ravi)
        fun f(n:String,v:String,c:Float=0f,s:String="manual")=DataField(n,v,source=s,confidence=c)
        repo.saveCard(VaultCard(title="Passport - Asha Fictional",kind="Passport",category="Identity",profile=asha.id,
            expiry=today.plusYears(4).toString(),
            fields=listOf(f("Full Name","ASHA FICTIONAL",.99f,"extracted"),f("Passport Number","Z0000001",.99f,"extracted"),
                f("Date of Birth","1990-01-01",.99f,"extracted"),f("Nationality","FIC",.99f,"extracted")),
            files=listOf(image(listOf("FICTIONAL REPUBLIC","PASSPORT","ASHA FICTIONAL","Z0000001"),"passport-front.png"))))
        repo.saveCard(VaultCard(title="Driving Licence",kind="Driving Licence",category="Identity",profile="self",
            expiry=today.plusDays(12).toString(),
            fields=listOf(f("Licence Number","FIC-DL-2020",.72f,"extracted"),f("Full Name","SAMPLE PERSON",.7f,"extracted")),
            files=listOf(pdf(listOf(listOf("FICTIONAL DRIVING LICENCE","Licence FIC-DL-2020"),listOf("Page 2","Vehicle classes: LMV")),
                "Driving licence.pdf"))))
        repo.saveCard(VaultCard(title="Seafarer CDC",kind="Seafarer CDC",category="Work",profile=ravi.id,
            expiry=today.minusDays(20).toString(),
            fields=listOf(f("CDC Number","FIC123456",.7f,"extracted"),f("Full Name","RAVI FICTIONAL")),
            files=listOf(image(listOf("CONTINUOUS DISCHARGE CERTIFICATE","RAVI FICTIONAL","FIC123456"),"cdc.png"),
                image(listOf("CDC - PAGE 2","Sea service record"),"cdc-page-2.png"))))
        repo.saveCard(VaultCard(title="Health insurance receipt",kind="Custom",category="Finance",profile="self",
            fields=listOf(f("Policy","FIC-HEALTH-77"),f("Premium","12,345.00")),
            files=listOf(pdf(listOf((1..60).map{"Fictional line item $it ........ 100.00"}),"Insurance receipt.pdf",3000))))
        repo.saveCard(VaultCard(title="Medical fitness certificate",kind="Medical",category="Health",profile=ravi.id,
            expiry=today.plusMonths(8).toString(),fields=listOf(f("Issued by","Fictional Clinic"))))
        repo.saveCard(VaultCard(title="Rental agreement with a deliberately very long title to test wrapping on small screens",
            category="Home",profile="self",notes="Fictional notes. Nothing here is real.",
            files=listOf(blob({it.writeText("Fictional rental agreement text.\nTenant: Sample Person")},"agreement.txt","text/plain"))))
    }
}
