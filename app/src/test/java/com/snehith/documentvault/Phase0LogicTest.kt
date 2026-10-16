package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class Phase0LogicTest {
    @Test fun everyFieldGetsStableIndependentId() {
        val a=DataField("Passport Number","A12345")
        val b=DataField("Passport Number","A12345")
        assertNotEquals(a.id,b.id)
        assertEquals(a.id,a.copy(value="A12346").id)
    }
    @Test fun indexFreeSelectionRemainsCorrectAfterReorderOrDeletion() {
        val first=DataField("Name","A")
        val second=DataField("Number","B")
        val chosen=setOf(second.id)
        val reordered=listOf(second,first)
        assertEquals(listOf("B"),reordered.filter{it.id in chosen}.map{it.value})
        val remaining=reordered.filterNot{it.id==first.id}
        assertEquals(listOf("B"),remaining.filter{it.id in chosen}.map{it.value})
    }
    @Test fun searchableTextSplitsPerPageAndNeverDuplicatesWholeOCR() {
        val raw="--- PAGE 1 ---\nAlpha\n--- PAGE 2 ---\nBeta"
        assertEquals(listOf("Alpha","Beta"),PdfPageTexts.parse(raw,2))
        assertTrue(PdfPageTexts.parse("Unmarked combined text",2).isEmpty())
        assertEquals(listOf("Single"),PdfPageTexts.parse("Single",1))
    }
    @Test fun pdfGeometryUsesA4PointsRatherThanImagePixels() {
        val x=PdfPageGeometry.fitA4(4000,3000)
        assertEquals(595f,x[0])
        assertEquals(842f,x[1])
        assertTrue(x[4]<595f&&x[5]<842f)
        assertTrue(x[4]>0&&x[5]>0)
    }
    @Test fun imageSamplingHonorsTargetSize() {
        assertEquals(2,ImageSampling.sample(4000,3000,2000))
        assertEquals(4,ImageSampling.sample(4000,3000,1200))
        assertEquals(1,ImageSampling.sample(640,480,2000))
    }
    @Test fun namesStayHumanReadableAndKeepExtensions() {
        val uuid="scan-da5a9729-a108-43cf-b03e-8c4c77afcc25.jpg"
        assertEquals("Scanned document",VaultNames.title(uuid))
        assertEquals("Scanned document.jpg",VaultNames.label(uuid))
        assertEquals("Aadhaar front",VaultNames.title("Aadhaar front.png"))
    }
    @Test fun pluralsUseCorrectCount() {
        assertEquals("1 file",VaultCounts.format(1,"file"))
        assertEquals("0 files",VaultCounts.format(0,"file"))
        assertEquals("2 documents",VaultCounts.format(2,"document"))
    }
    @Test fun expiryDatesMustBeValidIso() {
        assertTrue(VaultExpiry.isIsoDate("2028-02-29"))
        assertFalse(VaultExpiry.isIsoDate("2027-02-29"))
        assertFalse(VaultExpiry.isIsoDate("29/02/2028"))
    }
    @Test fun attachmentReorderPreservesEveryFileId() {
        val a=VaultFile("a","a.jpg","image/jpeg",1,"sha-a")
        val b=VaultFile("b","b.pdf","application/pdf",2,"sha-b")
        assertEquals(listOf("b","a"),VaultOrder.reorder(listOf(a,b),listOf("b","a")).map{it.id})
        assertThrows(IllegalArgumentException::class.java){
            VaultOrder.reorder(listOf(a,b),listOf("a","a"))
        }
        assertThrows(IllegalArgumentException::class.java){
            VaultOrder.reorder(listOf(a,b),listOf("a"))
        }
    }
}
