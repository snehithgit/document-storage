package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class SmartDocumentsTest {
    private val sample1="P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
    private val sample2="L898902C36UTO7408122F1204159ZE184226B<<<<<10"

    @Test fun validTd3PassportMrzParsesNameNumberDatesAndIcaoDigits() {
        assertEquals(44,sample1.length)
        assertEquals(44,sample2.length)
        val found=SmartDocuments.parse(sample1+"\n"+sample2)
        assertNotNull(found)
        assertEquals("Passport",found!!.kind)
        assertEquals("L898902C3",found.fields.first{it.name=="Passport Number"}.value)
        assertEquals("1974-08-12",found.fields.first{it.name=="Date of Birth"}.value)
        assertEquals("2012-04-15",found.expiry)
        assertTrue(found.confidence>=.89f)
        assertEquals("Passport - ERIKSSON ANNA MARIA",found.title)
    }
    @Test fun corruptMrzDigitsAreRejected() {
        assertTrue(SmartDocuments.mrzCheck("L898902C3",'6'))
        assertFalse(SmartDocuments.mrzCheck("L898902C3",'7'))
        val damaged=sample2.substring(0,9)+"7"+sample2.substring(10)
        val found=SmartDocuments.parse(sample1+"\n"+damaged)!!
        assertFalse(found.fields.any{it.name=="Passport Number"})
    }
    @Test fun validateVerhoeffAadhaarNotJustTwelveDigits() {
        val prefix="23452345234"
        val accepted=(0..9).map{prefix+it}.single(SmartDocuments::validAadhaar)
        assertTrue(SmartDocuments.validAadhaar(accepted.chunked(4).joinToString(" ")))
        val altered=accepted.dropLast(1)+if(accepted.last()=='9')'8' else '9'
        assertFalse(SmartDocuments.validAadhaar(altered))
        val found=SmartDocuments.parse("GOVERNMENT OF INDIA\nAADHAAR\nFull Name: Mira Sharma\n"+accepted)
        assertEquals("Aadhaar",found?.kind)
        assertEquals(accepted,found?.fields?.first{it.name=="Aadhaar Number"}?.value)
    }
    @Test fun panDetectionAndDateNormalization() {
        val found=SmartDocuments.parse("INCOME TAX DEPARTMENT\nPAN: ABCDE1234F\nName: Ravi Kumar\nDOB: 05/02/1990")
        assertEquals("PAN",found?.kind)
        assertEquals("ABCDE1234F",found?.fields?.first{it.name=="PAN Number"}?.value)
        assertEquals("1990-02-05",found?.fields?.first{it.name=="Date of Birth"}?.value)
        assertEquals("2029-11-03",SmartDocuments.normalizeDate("03/11/2029"))
        assertNull(SmartDocuments.normalizeDate("31/02/2027"))
    }
    @Test fun drivingLicenseAndCdcHaveRealFieldLabels() {
        val dl=SmartDocuments.parse("DRIVING LICENCE\nFull Name: Prakash Kumar\nDL No: MH1220121234567\nExpiry Date: 21-07-2030")
        assertEquals("Driving Licence",dl?.kind)
        assertEquals("2030-07-21",dl?.expiry)
        assertTrue(dl?.fields?.all{it.name!="Date"}==true)
        val cdc=SmartDocuments.parse("CONTINUOUS DISCHARGE CERTIFICATE\nName: Anita Rao\nCDC No: MUM123456\nExpiry Date: 2029-12-01")
        assertEquals("Seafarer CDC",cdc?.kind)
        assertEquals("MUM123456",cdc?.fields?.first{it.name=="CDC Number"}?.value)
    }
}
