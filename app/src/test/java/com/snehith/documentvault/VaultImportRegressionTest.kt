package com.snehith.documentvault

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class VaultImportRegressionTest:VaultImportContract() {
    private val files=mutableMapOf<Uri,File>()
    override val rendersPdf=false
    override fun appContext():Context=RuntimeEnvironment.getApplication()
    override fun fixtureUri(file:File):Uri {
        val uri=Uri.parse("content://vault-import-fixtures/${files.size}")
        files[uri]=file
        val provider=object:ContentProvider() {
            override fun onCreate()=true
            override fun getType(uri:Uri)=VaultFileTypes.mimeFromName(files.getValue(uri).name)?:"application/octet-stream"
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?)=
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply{addRow(arrayOf(files.getValue(uri).name))}
            override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(files.getValue(uri),ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri:Uri,values:ContentValues?):Uri?=error("Read-only fixture")
            override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=error("Read-only fixture")
            override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=error("Read-only fixture")
        }
        // Without an attached authority Robolectric rejects every incoming URI.
        provider.attachInfo(appContext(),ProviderInfo().apply{authority="vault-import-fixtures"})
        ShadowContentResolver.registerProviderInternal("vault-import-fixtures",provider)
        return uri
    }
}
