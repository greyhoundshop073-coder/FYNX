package com.fynx.app

import android.content.Intent
import android.net.Uri
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

        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        device.pressHome()
        instrumentation.startActivitySync(launchIntent)
        device.waitForIdle(10_000)

        assertEquals(context.packageName, device.currentPackageName)
        assertTrue("FYNX must expose a non-empty application label", context.applicationInfo.loadLabel(context.packageManager).toString().isNotBlank())
    }

    @Test
    fun appDeepLinkLaunchesIntoFynx() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("fynx://profile/alice")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        device.pressHome()
        instrumentation.startActivitySync(intent)
        device.waitForIdle(10_000)

        assertEquals(context.packageName, device.currentPackageName)
    }
}
