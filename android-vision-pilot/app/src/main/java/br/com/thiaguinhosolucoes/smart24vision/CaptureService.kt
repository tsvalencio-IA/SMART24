package br.com.thiaguinhosolucoes.smart24vision

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
        private const val ALERT_CHANNEL_ID = "smart24_demo_alerts"
        private const val NOTIFICATION_ID = 2401
        private const val PROCESS_INTERVAL_MS = 350L
        private const val LIVE_FRAME_INTERVAL_MS = 1800L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val firebase = FirebaseRestClient()
    private val vision = VisionEngine()
    private val cartEngine = CartEngine(firebase)
    private val demoEngine = AssistedDemoEngine(firebase)
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
    private var overlayView: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var windowManager: WindowManager? = null
    @Volatile private var latestSnapshotDataUrl: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture("STOPPED")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                createChannels()
                startForeground(NOTIFICATION_ID, notification("Analisando vídeo da câmera Yoosee"))
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
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
        stopping.set(false)
        clearLatestFrame()
        val metrics = currentMetrics()
        handlerThread = HandlerThread("Smart24Capture").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data).also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture("STOPPED")
                    stopSelf()
                }
            }, handler)
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "SMART24Vision",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
        imageReader?.setOnImageAvailableListener({ reader -> handleImage(reader) }, handler)
        showAssistedOverlay()
        scope.launch {
            publishHeartbeat("WAITING_VIDEO", 0, 0, 0, "Captura autorizada; aguardando o vídeo ao vivo do Yoosee.")
            publishLiveStatus("WAITING_VIDEO", "Abra a câmera no Yoosee e deixe o vídeo ao vivo visível.")
        }
    }

    private fun handleImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val now = System.currentTimeMillis()
        if (busy.get() || now - lastProcessedAt < PROCESS_INTERVAL_MS) {
            image.close()
            return
        }
        busy.set(true)
        lastProcessedAt = now
        val bitmap = runCatching { BitmapUtils.fromRgbaImage(image) }.getOrNull()
        image.close()
        if (bitmap == null) {
            busy.set(false)
            return
        }
        scope.launch {
            try {
                if (now - lastZoneRefreshAt > 10000L) {
                    zones = runCatching { firebase.getZones(PilotSession.storeId, PilotSession.cameraId) }.getOrDefault(zones)
                    lastZoneRefreshAt = now
                }
                if (BitmapUtils.isMostlyBlack(bitmap)) {
                    if (now - lastHeartbeatAt > 5000L) {
                        publishHeartbeat("NO_IMAGE", 0, 0, 0, "A captura está preta ou sem vídeo; o Yoosee pode estar fora da tela ou bloqueando captura.")
                        publishLiveStatus("NO_IMAGE", "Sem imagem válida. Confirme o vídeo ao vivo no Yoosee.")
                        lastHeartbeatAt = now
                    }
                    updateOverlayStatus("SEM IMAGEM: abra o vídeo ao vivo no Yoosee. Se continuar preto, o Yoosee está bloqueando a captura.")
                    return@launch
                }

                // A calibração recebe somente um quadro que passou pela
                // validação. Nunca substituímos a última imagem por uma tela
                // preta ou por uma superfície protegida do Yoosee.
                if (now - lastSavedFrameAt > 2500L) {
                    saveLatestFrame(bitmap)
                    lastSavedFrameAt = now
                }

                val result = vision.analyze(bitmap)
                demoEngine.update(result)
                cartEngine.process(result, zones)
                updateOverlayStatus(demoEngine.statusLine())

                if (now - lastLiveFrameAt >= LIVE_FRAME_INTERVAL_MS) {
                    publishLiveFrame(bitmap, result)
                    lastLiveFrameAt = now
                }

                if (now - lastHeartbeatAt > 5000L) {
                    publishHeartbeat(
                        "VIDEO_VISIBLE",
                        result.persons.size,
                        result.objects.size,
                        result.tags.size,
                        "Imagem real do Yoosee processada; demonstração assistida disponível."
                    )
                    lastHeartbeatAt = now
                }
            } catch (error: Throwable) {
                Log.e("SMART24", "Erro no processamento", error)
                if (now - lastHeartbeatAt > 5000L) {
                    runCatching {
                        publishHeartbeat("DEGRADED", 0, 0, 0, error.message ?: "Falha de processamento")
                        publishLiveStatus("DEGRADED", error.message ?: "Falha de processamento")
                    }
                    updateOverlayStatus("Falha: ${error.message ?: "processamento"}")
                    lastHeartbeatAt = now
                }
            } finally {
                bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private suspend fun publishLiveFrame(bitmap: Bitmap, result: VisionResult) {
        val assisted = demoEngine.snapshot()
        val annotated = annotator.annotate(bitmap, result, zones, assisted)
        val targetWidth = 480.coerceAtMost(annotated.width)
        val targetHeight = (annotated.height * (targetWidth.toFloat() / annotated.width.toFloat())).toInt().coerceAtLeast(1)
        val preview = if (annotated.width == targetWidth) {
            annotated
        } else {
            Bitmap.createScaledBitmap(annotated, targetWidth, targetHeight, true)
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            preview.compress(Bitmap.CompressFormat.JPEG, 58, stream)
            stream.toByteArray()
        }
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        latestSnapshotDataUrl = dataUrl

        val personsPayload = result.persons.associate { person ->
            person.personId to mapOf(
                "personId" to person.personId,
                "left" to person.box.left,
                "top" to person.box.top,
                "right" to person.box.right,
                "bottom" to person.box.bottom,
                "confidence" to person.confidence,
                "source" to person.source,
                "leftWrist" to person.leftWrist?.let { mapOf("x" to it.x, "y" to it.y) },
                "rightWrist" to person.rightWrist?.let { mapOf("x" to it.x, "y" to it.y) },
                "trail" to person.trail.mapIndexed { index, point ->
                    index.toString() to mapOf("x" to point.x, "y" to point.y)
                }.toMap()
            )
        }
        val objectsPayload = result.objects.associate { obj ->
            obj.objectId to mapOf(
                "objectId" to obj.objectId,
                "left" to obj.box.left,
                "top" to obj.box.top,
                "right" to obj.box.right,
                "bottom" to obj.box.bottom,
                "x" to obj.centerX,
                "y" to obj.centerY,
                "confidence" to obj.confidence,
                "labels" to obj.labels
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
        val assistedPayload = assisted?.let {
            mapOf(
                "trackId" to it.trackId,
                "status" to it.status,
                "productName" to it.productName,
                "sku" to it.sku,
                "zoneId" to it.zoneId,
                "personId" to it.personId,
                "visualObjectId" to (it.visualObjectId ?: ""),
                "visualMode" to it.visualMode,
                "x" to it.centerX,
                "y" to it.centerY,
                "confidence" to it.confidence,
                "startedAt" to it.startedAt,
                "lastSeenAt" to it.lastSeenAt,
                "note" to it.note
            )
        }

        firebase.put(
            "cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}",
            mapOf(
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "bridgeId" to PilotSession.bridgeId,
                "sessionId" to PilotSession.sessionId,
                "status" to "VIDEO_VISIBLE",
                "source" to "ANDROID_SCREEN_CAPTURE_ASSISTED_DEMO",
                "frameDataUrl" to dataUrl,
                "frameWidth" to targetWidth,
                "frameHeight" to targetHeight,
                "personsDetected" to result.persons.size,
                "objectsDetected" to result.objects.size,
                "tagsDetected" to result.tags.size,
                "persons" to personsPayload,
                "objects" to objectsPayload,
                "tags" to tagsPayload,
                "assistedDemo" to assistedPayload,
                "updatedAt" to result.capturedAt
            )
        )
        if (preview !== annotated) preview.recycle()
        annotated.recycle()
    }

    private suspend fun publishLiveStatus(status: String, note: String) {
        firebase.patch(
            "cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}",
            mapOf(
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "bridgeId" to PilotSession.bridgeId,
                "sessionId" to PilotSession.sessionId,
                "status" to status,
                "source" to "ANDROID_SCREEN_CAPTURE_ASSISTED_DEMO",
                "note" to note,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    private suspend fun publishHeartbeat(status: String, persons: Int, objects: Int, tags: Int, note: String) {
        val timestamp = System.currentTimeMillis()
        val payload = mapOf(
            "pilotId" to PilotSession.pilotId,
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "bridgeId" to PilotSession.bridgeId,
            "sessionId" to PilotSession.sessionId,
            "status" to status,
            "personsDetected" to persons,
            "objectsDetected" to objects,
            "tagsDetected" to tags,
            "source" to "ANDROID_SCREEN_CAPTURE_ASSISTED_DEMO",
            "note" to note,
            "lastSeenAt" to timestamp
        )
        firebase.put("visionPilots/${PilotSession.pilotId}", payload)
        firebase.put(
            "cameraBridges/${PilotSession.bridgeId}",
            mapOf(
                "bridgeId" to PilotSession.bridgeId,
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "status" to status,
                "lastSeenAt" to timestamp,
                "source" to "ANDROID_SCREEN_CAPTURE_ASSISTED_DEMO"
            )
        )
    }

    private fun showAssistedOverlay() {
        if (!Settings.canDrawOverlays(this) || overlayView != null) return
        Handler(Looper.getMainLooper()).post {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.argb(230, 7, 17, 31))
                    setStroke(dp(1), Color.rgb(0, 215, 154))
                }
            }
            val title = TextView(this).apply {
                text = "SMART24 • DEMO ASSISTIDA"
                setTextColor(Color.rgb(0, 215, 154))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val product = TextView(this).apply {
                text = "${PilotSession.demoProductName} • ${PilotSession.demoSku}"
                setTextColor(Color.WHITE)
                textSize = 11f
            }
            val status = TextView(this).apply {
                text = "Aguardando vídeo…"
                setTextColor(Color.rgb(220, 231, 243))
                textSize = 11f
                maxLines = 3
                setPadding(0, dp(4), 0, dp(6))
            }
            overlayStatus = status
            panel.addView(title)
            panel.addView(product)
            panel.addView(status)

            val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(actionButton("PEGOU", Color.rgb(0, 121, 107)) {
                runDemoAction { demoEngine.markPickup(latestSnapshotDataUrl) }
            })
            row1.addView(actionButton("DEVOLVEU", Color.rgb(30, 136, 229)) {
                runDemoAction { demoEngine.markReturn(latestSnapshotDataUrl) }
            })
            panel.addView(row1)

            val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row2.addView(actionButton("ESCONDEU", Color.rgb(239, 108, 0)) {
                runDemoAction(alert = true) { demoEngine.markConcealment(latestSnapshotDataUrl) }
            })
            row2.addView(actionButton("ALERTA", Color.rgb(198, 40, 40)) {
                runDemoAction(alert = true) { demoEngine.sendManualAlert(latestSnapshotDataUrl) }
            })
            panel.addView(row2)

            val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row3.addView(actionButton("FINALIZAR", Color.rgb(69, 90, 100)) {
                runDemoAction { demoEngine.finish() }
            })
            row3.addView(actionButton("OCULTAR PAINEL", Color.rgb(55, 71, 79)) {
                removeAssistedOverlay()
            })
            panel.addView(row3)

            val params = WindowManager.LayoutParams(
                dp(300),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(8)
                y = dp(72)
            }
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            runCatching { windowManager?.addView(panel, params) }
                .onSuccess { overlayView = panel }
                .onFailure { Log.e("SMART24", "Não foi possível abrir controle flutuante", it) }
        }
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(8), dp(2), dp(8), dp(2))
            val params = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            layoutParams = params
            setOnClickListener { action() }
        }

    private fun runDemoAction(
        alert: Boolean = false,
        action: suspend () -> AssistedDemoEngine.ActionResult
    ) {
        updateOverlayStatus("Registrando ação…")
        scope.launch {
            val result = runCatching { action() }
                .getOrElse { AssistedDemoEngine.ActionResult(false, it.message ?: "Falha ao registrar ação.") }
            updateOverlayStatus(result.message)
            if (result.ok && alert) {
                showLocalAlert(result.message)
            }
        }
    }

    private fun updateOverlayStatus(text: String) {
        Handler(Looper.getMainLooper()).post { overlayStatus?.text = text }
    }

    private fun removeAssistedOverlay() {
        Handler(Looper.getMainLooper()).post {
            overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
            overlayView = null
            overlayStatus = null
        }
    }

    private fun showLocalAlert(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("SMART24 — ocorrência demonstrativa")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    private fun saveLatestFrame(bitmap: Bitmap) {
        FileOutputStream(File(filesDir, "latest_frame.jpg")).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)
        }
    }

    private fun clearLatestFrame() {
        runCatching { File(filesDir, "latest_frame.jpg").delete() }
        lastSavedFrameAt = 0L
    }

    private fun stopCapture(status: String) {
        if (!stopping.compareAndSet(false, true)) return
        removeAssistedOverlay()
        scope.launch {
            if (PilotSession.authenticated) {
                runCatching {
                    publishHeartbeat(status, 0, 0, 0, "Captura encerrada")
                    publishLiveStatus(status, "Captura encerrada")
                }
            }
        }
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        val activeProjection = projection
        projection = null
        activeProjection?.stop()
        handlerThread?.quitSafely()
        handlerThread = null
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

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SMART24 Vision", NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Alertas SMART24", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setContentTitle("SMART24 Vision Pilot")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
