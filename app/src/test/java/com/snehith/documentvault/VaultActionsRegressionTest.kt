package com.snehith.documentvault

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class VaultActionsRegressionTest:VaultActionsContract(){
    override fun appContext():Context=RuntimeEnvironment.getApplication()
}
