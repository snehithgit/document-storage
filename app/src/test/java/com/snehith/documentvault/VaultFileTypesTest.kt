package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultFileTypesTest {
    private val jpeg=byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),0xE0.toByte(),0,0)
    private val pdf="%PDF-1.7".toByteArray()

    @Test fun whatsAppBinIsDetectedAsJpeg() {
        assertEquals(VaultTransferType("83145.jpg","image/jpeg"),
            VaultFileTypes.resolve("83145.bin","application/octet-stream",jpeg))
    }
    @Test fun mislabeledPdfIsExportedAsImage() {
        assertEquals(VaultTransferType("DOC-20261016-WA0003.jpg","image/jpeg"),
            VaultFileTypes.resolve("DOC-20261016-WA0003.pdf","application/pdf",jpeg))
    }
    @Test fun realPdfRemainsPdfWhenProviderSaysBinary() {
        assertEquals(VaultTransferType("statement.pdf","application/pdf"),
            VaultFileTypes.resolve("statement.bin","application/octet-stream",pdf))
    }
    @Test fun unfamiliarBytesStayBinaryRatherThanBeingFalselyConverted() {
        assertEquals("application/octet-stream",
            VaultFileTypes.resolve("file.bin","application/octet-stream",byteArrayOf(5,2,1)).mime)
    }
    @Test fun multipleImagesUseImageChooser() {
        assertEquals("image/*",VaultFileTypes.chooserMime(listOf(
            VaultTransferType("a.jpg","image/jpeg"),VaultTransferType("b.png","image/png"))))
    }
}
