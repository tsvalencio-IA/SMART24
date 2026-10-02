package br.com.thiaguinhosolucoes.smart24vision

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an actual Android emulator with the native LibVLC libraries loaded. */
@RunWith(AndroidJUnit4::class)
class MobileStartupTest {
    @get:Rule val activityRule = ActivityTestRule(MobileVigilanteActivity::class.java, false, false)
    @Test fun installsStartsAndRequiresCameraFrameBeforeCalibration() {
        activityRule.launchActivity(Intent())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            assertEquals("192.168.15.5", activity.findViewById<EditText>(R.id.mobileHostInput).text.toString())
            assertFalse(activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStartAiButton).isEnabled)
            assertTrue(activity.findViewById<TextView>(R.id.mobileStatus).text.contains("mesma rede"))
            assertFalse(activity.findViewById<EditText>(R.id.mobileCameraPasswordInput).isSaveEnabled)
        }
        activityRule.finishActivity()
    }
}
