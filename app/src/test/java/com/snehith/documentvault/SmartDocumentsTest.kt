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
    @Test fun fallbackOcrUsesNamedDatesAndNoVerifyPlaceholders() {
        val fields=FieldSuggestions.parse("Date of Birth: 05/02/1990\nExpiry Date: 21-07-2030")
        assertEquals("1990-02-05",fields.first{it.name=="Date of Birth"}.value)
        assertEquals("2030-07-21",fields.first{it.name=="Expiry Date"}.value)
        assertFalse(fields.any{it.name=="Date"||it.name.contains("(verify)")})
    }

    @Test fun flexibleDatesHandleCaseAndSingleDigitDays(){
        assertEquals("2030-04-12",SmartDocuments.normalizeDate("12 APR 2030"))
        assertEquals("1990-02-05",SmartDocuments.normalizeDate("5/2/1990"))
        assertEquals("2030-04-12",SmartDocuments.normalizeDate("12-APR-2030"))
    }
    @Test fun noisyMrzCharacterSubstitutionsAreRestrictedToDates(){
        val noisy=sample2.substring(0,13)+"74O8I2"+sample2.substring(19)
        val extracted=SmartDocuments.parse(sample1+"\n"+noisy)
        assertEquals("1974-08-12",
            extracted?.fields?.firstOrNull{it.name=="Date of Birth"}?.value)
    }
    @Test fun truncatedMrzLinesCanStillExtractVerifiedCoreFields(){
        val extracted=SmartDocuments.parse(sample1.dropLast(2)+"\n"+sample2.dropLast(2))
        assertEquals("Passport",extracted?.kind)
        assertEquals("L898902C3",extracted?.fields?.firstOrNull{
            it.name=="Passport Number"}?.value)
    }
    @Test fun aadhaarNameCanBeImmediatelyAboveDobGender(){
        val prefix="23452345234"
        val number=(0..9).map{prefix+it}.single(SmartDocuments::validAadhaar)
        val detected=SmartDocuments.parse(
            "GOVERNMENT OF INDIA\nAADHAAR\nKavita Sharma\nDOB: 5/2/1990 Female\n"+number)
        assertEquals("Kavita Sharma",detected?.fullName)
    }

    @Test fun panNameHeaderSelectsApplicantInsteadOfFather(){
        val found=SmartDocuments.parse("INCOME TAX DEPARTMENT\nGOVT OF INDIA\nनाम / Name\nASHA RAO\n"+
            "Father's Name\nMOHAN RAO\n01/02/1990\nDate of Birth\n"+
            "Permanent Account Number Card\nABCDE1234F")
        assertEquals("PAN",found?.kind)
        assertEquals("ASHA RAO",found?.fields?.firstOrNull{it.name=="Full Name"}?.value)
    }
    @Test fun fatherMustNeverBeGuessedAsHolder(){
        val found=SmartDocuments.parse("INCOME TAX DEPARTMENT\nFather's Name\nMOHAN RAO\n"+
            "01/02/1990\nDate of Birth\nABCDE1234F")
        assertEquals("PAN",found?.kind)
        assertTrue(found?.fields?.none{it.name=="Full Name"&&it.value=="MOHAN RAO"}==true)
    }
}
