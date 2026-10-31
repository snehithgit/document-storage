package com.snehith.documentvault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultStorageDeviceTest:VaultStorageContract() {
    override fun appContext():Context = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName=="com.snehith.documentvault.storagecheck") {
            "Storage device tests require -PvaultDebugSuffix=.storagecheck"
        }
    }
}
