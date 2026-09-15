package com.fynx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FynxSmokeTest {
    @Test
    fun appLaunchesAndOwnsTheForegroundPackage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("FYNX must expose a launch intent", launchIntent)

        launchIntent!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        device.waitForIdle(10_000)

        assertEquals("com.fynx.app", device.currentPackageName)
        assertTrue("FYNX must expose a non-empty application label", context.applicationInfo.loadLabel(context.packageManager).toString().isNotBlank())
    }
}
