package com.fynx.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
        device.waitForIdle(10_000)
        instrumentation.startActivitySync(launchIntent)
        val reachedFynx = waitForForegroundPackage(device, context.packageName, 45_000)

        assertTrue("FYNX must become the foreground package after its launcher intent; current=" + device.currentPackageName, reachedFynx)
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
        val reachedFynx = waitForForegroundPackage(device, context.packageName, 45_000)

        assertTrue("FYNX must become the foreground package after a profile deep link; current=" + device.currentPackageName, reachedFynx)
        assertEquals(context.packageName, device.currentPackageName)
    }

    private fun waitForForegroundPackage(device: UiDevice, packageName: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.currentPackageName == packageName) return true
            SystemClock.sleep(250)
        }
        return device.currentPackageName == packageName
    }
}
