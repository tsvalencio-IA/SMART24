package br.com.thiaguinhosolucoes.smart24vision

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.view.TextureView
import android.view.View
import android.app.Instrumentation
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
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
            assertEquals("administrator", activity.findViewById<EditText>(R.id.mobileUserInput).text.toString())
            assertEquals("554", activity.findViewById<EditText>(R.id.mobilePortInput).text.toString())
            assertEquals("OFICINA", activity.findViewById<EditText>(R.id.mobileStoreInput).text.toString())
            assertEquals("SALA", activity.findViewById<EditText>(R.id.mobileCameraInput).text.toString())
            assertFalse(activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStartAiButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.mobileStopAiButton).isEnabled)
            assertTrue(activity.findViewById<TextView>(R.id.mobileStatus).text.contains("câmera Sala da oficina cadastrada"))
            assertTrue(activity.findViewById<TextView>(R.id.mobileStatus).text.contains("senha NVR/RTSP"))
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

    @Test fun liveVideoCalibrationSaveEditAndReturnSurviveBackground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        MobileZoneStore.clear(context, "OFICINA", "SALA")
        activityRule.launchActivity(Intent())
        activityRule.runOnUiThread {
            activityRule.activity.findViewById<EditText>(R.id.mobileRtspUrlInput).setText("rtsp://10.0.2.2:8554/testcam")
            activityRule.activity.findViewById<Button>(R.id.mobileConnectButton).performClick()
        }
        waitForStatus("VÍDEO CONFIRMADO",30000L)
        for (cycle in 0..1) {
            val monitor = instrumentation.addMonitor(MobileCalibrationActivity::class.java.name,null,false)
            activityRule.runOnUiThread { activityRule.activity.findViewById<Button>(R.id.mobileCalibrateButton).performClick() }
            val calibration = instrumentation.waitForMonitorWithTimeout(monitor,10000L) as? MobileCalibrationActivity
            assertNotNull("Calibration did not open after a real decoded frame",calibration)
            instrumentation.removeMonitor(monitor)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val c = calibration!!
                val view = c.findViewById<ZoneView>(R.id.mobileZoneView)
                assertNotNull(view.bitmap)
                view.setNormalizedRect(floatArrayOf(.12f,.15f,.4f,.6f))
                c.findViewById<EditText>(R.id.mobileProductNameInput).setText("Produto ${cycle+1}")
                c.findViewById<EditText>(R.id.mobileLocationInput).setText("Armário 1 • prateleira 2")
                c.findViewById<Button>(R.id.mobileSaveZoneButton).performClick()
                // Repeated save must edit the same area, never silently duplicate it.
                c.findViewById<Button>(R.id.mobileSaveZoneButton).performClick()
                val zones = MobileZoneStore.load(context,"OFICINA","SALA")
                assertEquals(cycle+1,zones.size)
                assertTrue(zones.last().sku.startsWith("LOCAL-"))
                assertEquals("Armário 1 • prateleira 2",zones.last().locationName)
                assertTrue(c.findViewById<TextView>(R.id.mobileCalibrationStatus).text.contains("salvo"))
            }
            saveScreenshot("calibration-$cycle")
            instrumentation.runOnMainSync { calibration!!.findViewById<Button>(R.id.mobileFinishZonesButton).performClick() }
            waitForStatus("VÍDEO CONFIRMADO",30000L)
            activityRule.runOnUiThread {
                assertTrue(activityRule.activity.findViewById<Button>(R.id.mobileCalibrateButton).isEnabled)
                assertTrue(activityRule.activity.findViewById<TextView>(R.id.mobileGuide).text.contains("${cycle+1} áreas"))
            }
        }
        activityRule.runOnUiThread {
            activityRule.activity.findViewById<Button>(R.id.mobileStartAiButton).performClick()
            assertTrue(activityRule.activity.findViewById<Button>(R.id.mobileStopAiButton).isEnabled)
        }
        Thread.sleep(1500)
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        Thread.sleep(1200)
        val intent = Intent(context,MobileVigilanteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
        waitForStatus("VÍDEO CONFIRMADO",30000L)
        saveScreenshot("video-returned")
        // Opening the event site is optional and has a clear explanation before navigation.
        activityRule.runOnUiThread { activityRule.activity.findViewById<Button>(R.id.mobilePanelButton).performClick() }
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
        activityRule.runOnUiThread {
            assertTrue(activityRule.activity.findViewById<TextView>(R.id.mobileStatus).text.contains("VÍDEO CONFIRMADO"))
        }
        activityRule.finishActivity()
    }
    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frame = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null),"proof").apply { mkdirs() }
        FileOutputStream(File(dir,"$name.png")).use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
        frame.recycle()
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
