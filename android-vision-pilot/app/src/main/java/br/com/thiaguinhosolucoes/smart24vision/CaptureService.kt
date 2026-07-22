package br.com.thiaguinhosolucoes.smart24vision

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "SMART24_START_CAPTURE"
        const val ACTION_STOP = "SMART24_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "smart24_vision"
        private const val NOTIFICATION_ID = 2401
        private const val LIVE_FRAME_INTERVAL_MS = 2500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val firebase = FirebaseRestClient()
    private val vision = VisionEngine()
    private val cartEngine = CartEngine(firebase)
    private val annotator = FrameAnnotator()
    private var zones: List<Zone> = emptyList()
    private var lastProcessedAt = 0L
    private var lastSavedFrameAt = 0L
    private var lastLiveFrameAt = 0L
    private var lastHeartbeatAt = 0L
    private var lastZoneRefreshAt = 0L
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopCapture("STOPPED"); stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                createChannel()
                startForeground(NOTIFICATION_ID, notification("Analisando vídeo da câmera Yoosee"))
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                if (resultCode < 0 || data == null || !PilotSession.authenticated) {
                    stopCapture("ERROR")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startProjection(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (projection != null) return
        val metrics = currentMetrics()
        handlerThread = HandlerThread("Smart24Capture").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data).also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCapture("STOPPED"); stopSelf() }
            }, handler)
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "SMART24Vision", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, handler
        )
        imageReader?.setOnImageAvailableListener({ reader -> handleImage(reader) }, handler)
        scope.launch {
            publishHeartbeat("WAITING_VIDEO", 0, 0, "Captura autorizada; aguardando o vídeo ao vivo do Yoosee.")
            publishLiveStatus("WAITING_VIDEO", "Abra a câmera no Yoosee e deixe o vídeo ao vivo visível.")
        }
    }

    private fun handleImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val now = System.currentTimeMillis()
        if (busy.get() || now - lastProcessedAt < 700L) { image.close(); return }
        busy.set(true)
        lastProcessedAt = now
        val bitmap = runCatching { BitmapUtils.fromRgbaImage(image) }.getOrNull()
        image.close()
        if (bitmap == null) { busy.set(false); return }
        scope.launch {
            try {
                if (now - lastSavedFrameAt > 4000L) { saveLatestFrame(bitmap); lastSavedFrameAt = now }
                if (now - lastZoneRefreshAt > 10000L) {
                    zones = runCatching { firebase.getZones(PilotSession.storeId, PilotSession.cameraId) }.getOrDefault(zones)
                    lastZoneRefreshAt = now
                }
                if (isMostlyBlack(bitmap)) {
                    if (now - lastHeartbeatAt > 5000L) {
                        publishHeartbeat("NO_IMAGE", 0, 0, "A captura está preta ou sem vídeo; o Yoosee pode estar fora da tela ou bloqueando captura.")
                        publishLiveStatus("NO_IMAGE", "Sem imagem válida. Confirme o vídeo ao vivo no Yoosee.")
                        lastHeartbeatAt = now
                    }
                    return@launch
                }

                val result = vision.analyze(bitmap)
                cartEngine.process(result, zones)

                if (now - lastLiveFrameAt >= LIVE_FRAME_INTERVAL_MS) {
                    publishLiveFrame(bitmap, result)
                    lastLiveFrameAt = now
                }

                if (now - lastHeartbeatAt > 5000L) {
                    publishHeartbeat("VIDEO_VISIBLE", result.persons.size, result.tags.size, "Imagem real do Yoosee processada e publicada no painel SMART24.")
                    lastHeartbeatAt = now
                }
            } catch (error: Throwable) {
                Log.e("SMART24", "Erro no processamento", error)
                if (now - lastHeartbeatAt > 5000L) {
                    runCatching {
                        publishHeartbeat("DEGRADED", 0, 0, error.message ?: "Falha de processamento")
                        publishLiveStatus("DEGRADED", error.message ?: "Falha de processamento")
                    }
                    lastHeartbeatAt = now
                }
            } finally {
                bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private suspend fun publishLiveFrame(bitmap: Bitmap, result: VisionResult) {
        val annotated = annotator.annotate(bitmap, result, zones)
        val targetWidth = 480.coerceAtMost(annotated.width)
        val targetHeight = (annotated.height * (targetWidth.toFloat() / annotated.width.toFloat())).toInt().coerceAtLeast(1)
        val preview = if (annotated.width == targetWidth) annotated else Bitmap.createScaledBitmap(annotated, targetWidth, targetHeight, true)
        val bytes = ByteArrayOutputStream().use { stream ->
            preview.compress(Bitmap.CompressFormat.JPEG, 58, stream)
            stream.toByteArray()
        }
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val personsPayload = result.persons.associate { person ->
            person.personId to mapOf(
                "personId" to person.personId,
                "left" to person.box.left,
                "top" to person.box.top,
                "right" to person.box.right,
                "bottom" to person.box.bottom,
                "confidence" to person.confidence,
                "source" to person.source,
                "trail" to person.trail.mapIndexed { index, point -> index.toString() to mapOf("x" to point.x, "y" to point.y) }.toMap()
            )
        }
        val tagsPayload = result.tags.associate { tag ->
            tag.serial to mapOf(
                "serial" to tag.serial,
                "productId" to tag.productId,
                "productName" to tag.productName,
                "sku" to tag.sku,
                "x" to tag.centerX,
                "y" to tag.centerY,
                "confidence" to tag.confidence
            )
        }
        firebase.put("cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}", mapOf(
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "bridgeId" to PilotSession.bridgeId,
            "sessionId" to PilotSession.sessionId,
            "status" to "VIDEO_VISIBLE",
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT",
            "frameDataUrl" to dataUrl,
            "frameWidth" to targetWidth,
            "frameHeight" to targetHeight,
            "personsDetected" to result.persons.size,
            "tagsDetected" to result.tags.size,
            "persons" to personsPayload,
            "tags" to tagsPayload,
            "updatedAt" to result.capturedAt
        ))
        if (preview !== annotated) preview.recycle()
        annotated.recycle()
    }

    private suspend fun publishLiveStatus(status: String, note: String) {
        firebase.patch("cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}", mapOf(
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "bridgeId" to PilotSession.bridgeId,
            "sessionId" to PilotSession.sessionId,
            "status" to status,
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT",
            "note" to note,
            "updatedAt" to System.currentTimeMillis()
        ))
    }

    private suspend fun publishHeartbeat(status: String, persons: Int, tags: Int, note: String) {
        val timestamp = System.currentTimeMillis()
        val payload = mapOf(
            "pilotId" to PilotSession.pilotId,
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "bridgeId" to PilotSession.bridgeId,
            "sessionId" to PilotSession.sessionId,
            "status" to status,
            "personsDetected" to persons,
            "tagsDetected" to tags,
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT",
            "note" to note,
            "lastSeenAt" to timestamp
        )
        firebase.put("visionPilots/${PilotSession.pilotId}", payload)
        firebase.put("cameraBridges/${PilotSession.bridgeId}", mapOf(
            "bridgeId" to PilotSession.bridgeId,
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "status" to status,
            "lastSeenAt" to timestamp,
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT"
        ))
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var dark = 0
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                if ((r + g + b) / 3 < 12) dark++
                total++
                x += stepX
            }
            y += stepY
        }
        return total > 0 && dark.toDouble() / total.toDouble() > 0.96
    }

    private fun saveLatestFrame(bitmap: Bitmap) {
        FileOutputStream(File(filesDir, "latest_frame.jpg")).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    }

    private fun stopCapture(status: String) {
        scope.launch {
            if (PilotSession.authenticated) runCatching {
                publishHeartbeat(status, 0, 0, "Captura encerrada")
                publishLiveStatus(status, "Captura encerrada")
            }
        }
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close(); imageReader = null
        virtualDisplay?.release(); virtualDisplay = null
        projection?.stop(); projection = null
        handlerThread?.quitSafely(); handlerThread = null
        vision.close()
    }

    override fun onDestroy() {
        stopCapture("STOPPED")
        scope.cancel()
        super.onDestroy()
    }

    private fun currentMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val maxWidth = 1080
        if (metrics.widthPixels > maxWidth) {
            val scale = maxWidth.toFloat() / metrics.widthPixels
            metrics.widthPixels = maxWidth
            metrics.heightPixels = (metrics.heightPixels * scale).toInt()
        }
        return metrics
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "SMART24 Vision", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setContentTitle("SMART24 Vision Pilot")
        .setContentText(text)
        .setOngoing(true)
        .build()
}
