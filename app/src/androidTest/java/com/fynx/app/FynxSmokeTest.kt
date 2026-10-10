package com.fynx.app

import android.content.Intent
import android.net.Uri
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
        val firstLaunchReachedFynx = run {
            context.startActivity(launchIntent)
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 45_000)
        }
        // Emulator startup can occasionally race the launcher transition. Retry once only
        // when FYNX never becomes visible; still fail if the app does not own the foreground.
        val reachedFynx = firstLaunchReachedFynx || run {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(launchIntent)
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 15_000)
        }
        assertTrue("FYNX must become visible after launch", reachedFynx)
        device.waitForIdle(5_000)
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
        val reachedFynx = device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 45_000)
        device.waitForIdle(10_000)

        assertTrue("FYNX must reach the foreground after a profile deep link", reachedFynx)
        assertEquals(context.packageName, device.currentPackageName)
    }
}
