package br.com.thiaguinhosolucoes.smart24vision

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.view.TextureView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

/** Runs on an actual Android emulator with the native LibVLC libraries loaded. */
@RunWith(AndroidJUnit4::class)
class MobileStartupTest {
    @get:Rule val activityRule = ActivityTestRule(MobileVigilanteActivity::class.java, false, false)
    @Before fun resetOnlyTestPreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("smart24_mobile", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun installsStartsAndRequiresCameraFrameBeforeCalibration() {
        activityRule.launchActivity(Intent())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            assertEquals("192.168.15.5", activity.findViewById<EditText>(R.id.mobileHostInput).text.toString())
            assertFalse(activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStartAiButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStopAiButton).isEnabled)
            assertTrue(activity.findViewById<TextView>(R.id.mobileStatus).text.contains("mesma rede"))
            assertFalse(activity.findViewById<EditText>(R.id.mobileCameraPasswordInput).isSaveEnabled)
        }
        activityRule.finishActivity()
    }

    @Test fun receivesDecodedRtspVideoFromTestServer() {
        activityRule.launchActivity(Intent())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            activity.findViewById<EditText>(R.id.mobileRtspUrlInput).setText("rtsp://10.0.2.2:8554/testcam")
            activity.findViewById<Button>(R.id.mobileConnectButton).performClick()
        }
        var confirmed = false
        val deadline = System.currentTimeMillis() + 30000L
        while (!confirmed && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                confirmed = activityRule.activity.findViewById<TextView>(R.id.mobileStatus).text.contains("VÍDEO CONFIRMADO")
            }
            if (!confirmed) Thread.sleep(200L)
        }
        assertTrue("LibVLC did not receive a decoded RTSP frame", confirmed)
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            val frame = activity.findViewById<TextureView>(R.id.mobileVideoTexture).getBitmap(160, 90)
            assertNotNull(frame)
            assertFalse(BitmapUtils.isMostlyBlack(frame!!))
            frame.recycle()
            assertTrue(activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
        }
        activityRule.finishActivity()
    }

    @Test fun resolvesAuthenticatedOnvifWithCameraClockOffsetAndReceivesVideo() {
        activityRule.launchActivity(Intent())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            activity.findViewById<EditText>(R.id.mobileHostInput).setText("10.0.2.2")
            activity.findViewById<EditText>(R.id.mobilePortInput).setText("8554")
            activity.findViewById<EditText>(R.id.mobileUserInput).setText("onvif-test")
            activity.findViewById<EditText>(R.id.mobileCameraPasswordInput).setText("onvif-test-password")
            activity.findViewById<Button>(R.id.mobileConnectButton).performClick()
        }
        waitForStatus("VÍDEO CONFIRMADO", 45000L)
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            assertTrue(activity.findViewById<TextView>(R.id.mobileStatus).text.contains("/testcam"))
            assertTrue(activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
            assertEquals("", activity.findViewById<EditText>(R.id.mobileCameraPasswordInput).text.toString())
        }
        val stats = JSONObject(URL("http://10.0.2.2:8080/stats").readText())
        assertTrue(stats.getInt("GetStreamUri") >= 1)
        assertTrue(stats.getInt("http_digest_and_wsse_accepted") >= 1)
        assertEquals(0, stats.optInt("wsse_rejected", 0))
        activityRule.finishActivity()
    }

    @Test fun cameraOwnCredentialsAreRequiredAndDoNotEnableAiWithoutVideo() {
        activityRule.launchActivity(Intent())
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            activity.findViewById<EditText>(R.id.mobileRtspUrlInput).setText("rtsp://10.0.2.2:8555/protected-video")
            activity.findViewById<Button>(R.id.mobileConnectButton).performClick()
        }
        waitForStatus("exige usuário/senha NVR/RTSP", 15000L)
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            assertFalse(activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStartAiButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStopAiButton).isEnabled)
        }
        activityRule.finishActivity()
    }

    @Test fun rtspDigestBoundToTcpSessionIsAcceptedOnAndroid() {
        val result = CameraRtspProbe().describe("rtsp://rtsp-test:rtsp-test-password@10.0.2.2:8557/protected-video")
        assertEquals(CameraRtspProbe.Status.VIDEO, result.status)
        assertEquals(200, result.code)
        val stats = JSONObject(URL("http://10.0.2.2:8080/stats").readText())
        assertTrue(stats.getInt("rtsp_session_digest_accepted") >= 1)
        // This fixture proves the DESCRIBE handshake only; it does not transmit video frames.
    }

    @Test fun nativeRetriesAndDisconnectDoNotBlockUiWhenCameraStopsResponding() {
        activityRule.launchActivity(Intent())
        val attempts = AtomicInteger()
        lateinit var nativePlayer: MobileRtspPlayer
        activityRule.runOnUiThread {
            val activity = activityRule.activity
            nativePlayer = MobileRtspPlayer(activity, activity.findViewById(R.id.mobileVideoTexture), object : MobileRtspPlayer.Listener {
                override fun onConnecting(candidateNumber: Int, total: Int) { attempts.incrementAndGet() }
                override fun onConnected(maskedUrl: String) { fail("The stalled fixture must never report video") }
                override fun onFailed(message: String) = Unit
                override fun onInterrupted(message: String) = Unit
            })
            // Deliberately exercises native playback directly, bypassing the protective probe.
            nativePlayer.connectResolved(listOf("rtsp://10.0.2.2:8556/stalled"))
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + 12500L
            while (SystemClock.elapsedRealtime() < deadline) {
                val heartbeat = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post { heartbeat.countDown() }
                assertTrue("UI blocked during native RTSP timeout/retry", heartbeat.await(2, TimeUnit.SECONDS))
                Thread.sleep(150L)
            }
            assertTrue("The native retry was not exercised", attempts.get() >= 2)
            val disconnected = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { nativePlayer.disconnect(); disconnected.countDown() }
            assertTrue("Native stop blocked the UI thread", disconnected.await(2, TimeUnit.SECONDS))
            assertTrue(JSONObject(URL("http://10.0.2.2:8080/stats").readText()).getInt("rtsp_stalled") >= 1)
        } finally {
            activityRule.runOnUiThread { nativePlayer.release() }
            activityRule.finishActivity()
        }
    }

    private fun waitForStatus(expected: String, timeout: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + timeout
        var matched = false
        var lastStatus = ""
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                lastStatus = activityRule.activity.findViewById<TextView>(R.id.mobileStatus).text.toString()
                matched = lastStatus.contains(expected)
            }
            if (!matched) Thread.sleep(200L)
        }
        assertTrue("Expected '$expected'; received '$lastStatus'", matched)
    }
}
