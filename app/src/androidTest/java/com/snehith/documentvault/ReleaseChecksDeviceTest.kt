package com.snehith.documentvault

import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReleaseChecksDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    /** Workflow 23: the vault stays offline. */
    @Test fun appRequestsNoNetworkPermission() {
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
        val requested=info.requestedPermissions.orEmpty().toSet()
        // WorkManager merges ACCESS_NETWORK_STATE; it cannot send data without INTERNET.
        assertFalse(requested.toString(),"android.permission.INTERNET" in requested)
    }
    @Test fun androidCloudBackupIsDisabled() {
        val app=context.packageManager.getApplicationInfo(context.packageName,0)
        assertEquals(0,app.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
    /** Workflow 22: shared files carry a real filename, extension and MIME type. */
    @Test fun sharingUsesReadableNameAndType() {
        check(context.packageName=="com.snehith.documentvault.uxcheck")
        val id=UUID.randomUUID().toString()
        val blob=File(context.filesDir,"blobs/$id.dat").apply{parentFile!!.mkdirs()
            writeBytes("%PDF-1.4\n% fictional\n".toByteArray())}
        try {
            val file=VaultFile(id,"Fictional Licence","application/pdf",blob.length(),"")
            val shared=VaultShareFiles.prepare(context,VaultStorage.get(context),listOf(file)).single()
            assertEquals("application/pdf",context.contentResolver.getType(shared.uri))
            context.contentResolver.query(shared.uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)!!.use{
                assertTrue(it.moveToFirst())
                assertEquals("Fictional Licence.pdf",it.getString(0))
            }
            assertFalse(shared.uri.toString().contains(id))
        } finally { blob.delete() }
    }
}
