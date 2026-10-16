package com.snehith.documentvault
import org.junit.Assert.*
import org.junit.Test
class VaultImportGroupingTest {
 @Test fun groupedPagesReuseFirstCreatedCard(){
  assertNull(VaultImportGrouping.nextTarget(null,null,true))
  assertEquals("card1",VaultImportGrouping.nextTarget(null,"card1",true))
  assertNull(VaultImportGrouping.nextTarget(null,"card1",false))
  assertEquals("existing",VaultImportGrouping.nextTarget("existing","card1",false))
 }
}
