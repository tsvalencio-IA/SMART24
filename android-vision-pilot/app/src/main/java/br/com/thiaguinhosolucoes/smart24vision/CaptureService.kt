package br.com.thiaguinhosolucoes.smart24vision

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * Demonstração assistida:
 * - recebe a tela autorizada do Android (Yoosee em primeiro plano);
 * - roda visão local;
 * - mostra um controle flutuante;
 * - o OPERADOR confirma PEGOU / DEVOLVEU / SUSPEITA;
 * - publica evidências e eventos no Firebase.
 *
 * Não reconhece automaticamente SKU e não acusa furto.
 */
class CaptureService : Service() {
    companion object {
        const val ACTION_START = "SMART24_START_CAPTURE"
        const val ACTION_STOP = "SMART24_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "smart24_vision_demo"
        private const val NOTIFICATION_ID = 2401
        private const val PROCESS_INTERVAL_MS = 500L
        private const val LIVE_FRAME_INTERVAL_MS = 2500L
    }

    private data class DemoTrack(
        val personId: String,
        val objectId: String?,
        val mode: String,
        val confidence: Double,
        val startedAt: Long
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val firebase = FirebaseRestClient()
    private val vision = VisionEngine()
    private val annotator = FrameAnnotator()

    @Volatile private var latestResult: VisionResult? = null
    @Volatile private var lastFrameDataUrl: String? = null
    @Volatile private var activeTrack: DemoTrack? = null

    private var zones: List<Zone> = emptyList()
    private var lastProcessedAt = 0L
    private var lastLiveFrameAt = 0L
    private var lastHeartbeatAt = 0L

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var overlayStatus: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture("STOPPED")
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                createChannel()
                startForeground(NOTIFICATION_ID, notification("Demonstração assistida em execução"))

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

                if (!Settings.canDrawOverlays(this)) {
                    stopCapture("OVERLAY_PERMISSION_REQUIRED")
                    stopSelf()
                    return START_NOT_STICKY
                }

                showOverlay()
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

        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

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
            "SMART24VisionDemo",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        imageReader?.setOnImageAvailableListener({ reader -> handleImage(reader) }, handler)

        scope.launch {
            publishHeartbeat("WAITING_VIDEO", 0, 0, 0, "Captura autorizada; aguardando vídeo do Yoosee.")
            publishLiveStatus("WAITING_VIDEO", "Abra a câmera no Yoosee em tela cheia.")
        }

        updateOverlay("Abra a câmera no Yoosee.\nAguardando imagem…")
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
                if (isMostlyBlack(bitmap)) {
                    latestResult = null
                    updateOverlay("Sem imagem válida.\nConfira o Yoosee.")
                    if (now - lastHeartbeatAt > 5000L) {
                        publishHeartbeat("NO_IMAGE", 0, 0, 0, "A captura está preta ou sem vídeo válido.")
                        publishLiveStatus("NO_IMAGE", "Sem imagem válida.")
                        lastHeartbeatAt = now
                    }
                    return@launch
                }

                val result = vision.analyze(bitmap)
                latestResult = result

                val track = activeTrack
                val trackText = if (track == null) {
                    "Aguardando PEGOU"
                } else {
                    "Acompanhando ${track.personId}\n${track.objectId ?: "objeto não isolado"}"
                }

                updateOverlay(
                    "Pessoas ${result.persons.size} • Objetos ${result.objects.size}\n$trackText"
                )

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
                        "Vídeo do Yoosee analisado localmente."
                    )
                    lastHeartbeatAt = now
                }
            } catch (error: Throwable) {
                Log.e("SMART24", "Falha no processamento da demonstração", error)
                updateOverlay("Falha de análise:\n${error.message ?: "erro desconhecido"}")
                if (now - lastHeartbeatAt > 5000L) {
                    runCatching {
                        publishHeartbeat("DEGRADED", 0, 0, 0, error.message ?: "Falha de processamento")
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

    private fun showOverlay() {
        if (overlayRoot != null || !Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 7, 17, 31))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(0, 215, 154))
            }
        }

        val title = TextView(this).apply {
            text = "SMART24 • TESTE"
            setTextColor(Color.rgb(0, 215, 154))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val status = TextView(this).apply {
            text = "Aguardando vídeo…"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, dp(4), 0, dp(7))
        }
        overlayStatus = status

        root.addView(title)
        root.addView(status)
        root.addView(actionButton("PEGOU", Color.rgb(0, 137, 123)) { manualPicked() })
        root.addView(actionButton("DEVOLVEU", Color.rgb(38, 120, 190)) { manualReturned() })
        root.addView(actionButton("SUSPEITA", Color.rgb(198, 103, 0)) { manualSuspicion() })
        root.addView(actionButton("FINALIZAR", Color.rgb(85, 85, 96)) { manualFinish() })

        val params = WindowManager.LayoutParams(
            dp(190),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(50)
        }

        runCatching {
            windowManager?.addView(root, params)
            overlayRoot = root
        }.onFailure {
            overlayStatus = null
            overlayRoot = null
            Log.e("SMART24", "Não foi possível criar o painel flutuante", it)
        }
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            setBackgroundColor(color)
            isAllCaps = true
            setOnClickListener { action() }
        }

    private fun manualPicked() {
        scope.launch {
            val result = latestResult
            if (result == null) {
                updateOverlay("Ainda não há quadro analisado.")
                return@launch
            }

            val person = result.persons.maxByOrNull { it.confidence }
            if (person == null) {
                updateOverlay("Nenhuma pessoa detectada.\nEntre no enquadramento.")
                return@launch
            }

            val association = chooseObject(person, result.objects)
            val objectId = association.first?.objectId
            val mode = association.second
            val confidence = association.first?.confidence
                ?.times(person.confidence)
                ?.coerceIn(0.35, 0.95)
                ?: (person.confidence * 0.62).coerceIn(0.30, 0.72)

            val track = DemoTrack(
                personId = person.personId,
                objectId = objectId,
                mode = mode,
                confidence = confidence,
                startedAt = System.currentTimeMillis()
            )
            activeTrack = track

            val eventId = firebase.post("events", baseEvent(
                type = "DEMO_PICK_CONFIRMED",
                track = track,
                note = "Operador confirmou que a pessoa pegou um item. O objeto visual é apenas associação de demonstração."
            ))

            firebase.patch(
                "cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}",
                mapOf(
                    "demoTrack" to mapOf(
                        "eventId" to eventId,
                        "personId" to track.personId,
                        "objectId" to (track.objectId ?: ""),
                        "mode" to track.mode,
                        "confidence" to track.confidence,
                        "startedAt" to track.startedAt
                    ),
                    "updatedAt" to System.currentTimeMillis()
                )
            )

            updateOverlay(
                "PEGOU registrado.\n${track.personId}\n${track.objectId ?: "usando pulso/pessoa"}"
            )
        }
    }

    private fun manualReturned() {
        scope.launch {
            val track = activeTrack
            if (track == null) {
                updateOverlay("Nenhum item está em acompanhamento.")
                return@launch
            }

            firebase.post("events", baseEvent(
                type = "DEMO_RETURN_CONFIRMED",
                track = track,
                note = "Operador confirmou a devolução do item."
            ))
            activeTrack = null
            clearDemoTrack()
            updateOverlay("DEVOLUÇÃO registrada.\nAguardando novo PEGOU.")
        }
    }

    private fun manualSuspicion() {
        scope.launch {
            val track = activeTrack
            val result = latestResult
            val fallbackPerson = result?.persons?.maxByOrNull { it.confidence }

            if (track == null && fallbackPerson == null) {
                updateOverlay("Sem pessoa ou item para associar ao alerta.")
                return@launch
            }

            val effectiveTrack = track ?: DemoTrack(
                personId = fallbackPerson!!.personId,
                objectId = null,
                mode = "PERSON_ONLY",
                confidence = fallbackPerson.confidence.coerceIn(0.35, 0.85),
                startedAt = System.currentTimeMillis()
            )

            firebase.post("events", baseEvent(
                type = "DEMO_SUSPICION",
                track = effectiveTrack,
                note = "Operador marcou uma situação para revisão. Isto não é uma acusação automática."
            ))

            val occurrence = mutableMapOf<String, Any?>(
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "sessionId" to PilotSession.sessionId,
                "personId" to effectiveTrack.personId,
                "productName" to "Item demonstrativo / não identificado",
                "sku" to "",
                "status" to "pending",
                "pickedUp" to 1,
                "returned" to 0,
                "expected" to 1,
                "registered" to 0,
                "paid" to 0,
                "difference" to 1,
                "confidence" to effectiveTrack.confidence,
                "reason" to "Situação marcada manualmente para revisão durante a demonstração.",
                "source" to "ANDROID_ASSISTED_DEMO",
                "createdAt" to System.currentTimeMillis()
            )
            lastFrameDataUrl?.takeIf { it.length < 850000 }?.let {
                occurrence["snapshotDataUrl"] = it
            }
            firebase.post("occurrences", occurrence)

            updateOverlay("SUSPEITA enviada.\nVerifique Ocorrências no painel.")
        }
    }

    private fun manualFinish() {
        scope.launch {
            activeTrack?.let {
                firebase.post("events", baseEvent(
                    type = "DEMO_TRACK_FINISHED",
                    track = it,
                    note = "Acompanhamento encerrado pelo operador sem conclusão automática."
                ))
            }
            activeTrack = null
            clearDemoTrack()
            updateOverlay("Acompanhamento finalizado.\nAguardando novo PEGOU.")
        }
    }

    private fun baseEvent(type: String, track: DemoTrack, note: String): Map<String, Any?> =
        mapOf(
            "type" to type,
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "sessionId" to PilotSession.sessionId,
            "personId" to track.personId,
            "productName" to "Item demonstrativo / não identificado",
            "sku" to "",
            "quantity" to 1,
            "objectId" to (track.objectId ?: ""),
            "associationMode" to track.mode,
            "confidence" to track.confidence,
            "source" to "ANDROID_ASSISTED_DEMO",
            "note" to note,
            "createdAt" to System.currentTimeMillis()
        )

    private fun chooseObject(
        person: PersonObservation,
        objects: List<GenericObjectObservation>
    ): Pair<GenericObjectObservation?, String> {
        if (objects.isEmpty()) return null to "WRIST_OR_PERSON_ONLY"

        val wrists = listOfNotNull(person.leftWrist, person.rightWrist)
        if (wrists.isNotEmpty()) {
            val ranked = objects.map { obj ->
                val d = wrists.minOf { wrist ->
                    distance(wrist, PointF(obj.centerX, obj.centerY))
                }
                obj to d
            }.minByOrNull { it.second }

            if (ranked != null && ranked.second <= 0.30f) {
                return ranked.first to "OBJECT_NEAR_WRIST"
            }
        }

        val expanded = android.graphics.RectF(
            (person.box.left - 0.08f).coerceAtLeast(0f),
            (person.box.top - 0.06f).coerceAtLeast(0f),
            (person.box.right + 0.08f).coerceAtMost(1f),
            (person.box.bottom + 0.08f).coerceAtMost(1f)
        )

        val inside = objects
            .filter { expanded.contains(it.centerX, it.centerY) }
            .maxByOrNull { it.confidence }

        return if (inside != null) inside to "OBJECT_INSIDE_PERSON_REGION"
        else null to "WRIST_OR_PERSON_ONLY"
    }

    private fun distance(a: PointF, b: PointF): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private suspend fun clearDemoTrack() {
        firebase.patch(
            "cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}",
            mapOf(
                "demoTrack" to null,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    private suspend fun publishLiveFrame(bitmap: Bitmap, result: VisionResult) {
        val annotated = annotator.annotate(bitmap, result, zones)
        val targetWidth = 520.coerceAtMost(annotated.width)
        val targetHeight = (
            annotated.height * (targetWidth.toFloat() / annotated.width.toFloat())
        ).toInt().coerceAtLeast(1)

        val preview = if (annotated.width == targetWidth) {
            annotated
        } else {
            Bitmap.createScaledBitmap(annotated, targetWidth, targetHeight, true)
        }

        val bytes = ByteArrayOutputStream().use { stream ->
            preview.compress(Bitmap.CompressFormat.JPEG, 56, stream)
            stream.toByteArray()
        }

        val dataUrl = "data:image/jpeg;base64," +
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        lastFrameDataUrl = dataUrl

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
                "rightWrist" to person.rightWrist?.let { mapOf("x" to it.x, "y" to it.y) }
            )
        }

        val objectsPayload = result.objects.associate { obj ->
            obj.objectId to mapOf(
                "objectId" to obj.objectId,
                "trackingId" to (obj.trackingId ?: -1),
                "left" to obj.box.left,
                "top" to obj.box.top,
                "right" to obj.box.right,
                "bottom" to obj.box.bottom,
                "confidence" to obj.confidence,
                "labels" to obj.labels
            )
        }

        val track = activeTrack
        val payload = mutableMapOf<String, Any?>(
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "bridgeId" to PilotSession.bridgeId,
            "sessionId" to PilotSession.sessionId,
            "status" to "VIDEO_VISIBLE",
            "source" to "ANDROID_YOOSEE_SCREEN_DEMO",
            "frameDataUrl" to dataUrl,
            "frameWidth" to targetWidth,
            "frameHeight" to targetHeight,
            "personsDetected" to result.persons.size,
            "objectsDetected" to result.objects.size,
            "tagsDetected" to result.tags.size,
            "persons" to personsPayload,
            "objects" to objectsPayload,
            "updatedAt" to result.capturedAt
        )

        if (track != null) {
            payload["demoTrack"] = mapOf(
                "personId" to track.personId,
                "objectId" to (track.objectId ?: ""),
                "mode" to track.mode,
                "confidence" to track.confidence,
                "startedAt" to track.startedAt
            )
        }

        firebase.put(
            "cameraLive/${PilotSession.storeId}/${PilotSession.cameraId}",
            payload
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
                "source" to "ANDROID_YOOSEE_SCREEN_DEMO",
                "note" to note,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    private suspend fun publishHeartbeat(
        status: String,
        persons: Int,
        objects: Int,
        tags: Int,
        note: String
    ) {
        val timestamp = System.currentTimeMillis()

        firebase.put(
            "visionPilots/${PilotSession.pilotId}",
            mapOf(
                "pilotId" to PilotSession.pilotId,
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "bridgeId" to PilotSession.bridgeId,
                "sessionId" to PilotSession.sessionId,
                "status" to status,
                "personsDetected" to persons,
                "objectsDetected" to objects,
                "tagsDetected" to tags,
                "source" to "ANDROID_YOOSEE_SCREEN_DEMO",
                "note" to note,
                "lastSeenAt" to timestamp
            )
        )

        firebase.put(
            "cameraBridges/${PilotSession.bridgeId}",
            mapOf(
                "bridgeId" to PilotSession.bridgeId,
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "status" to status,
                "lastSeenAt" to timestamp,
                "source" to "ANDROID_YOOSEE_SCREEN_DEMO"
            )
        )
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

    private fun updateOverlay(text: String) {
        Handler(mainLooper).post {
            overlayStatus?.text = text
        }
    }

    private fun removeOverlay() {
        val view = overlayRoot ?: return
        runCatching { windowManager?.removeView(view) }
        overlayRoot = null
        overlayStatus = null
    }

    private fun stopCapture(status: String) {
        removeOverlay()

        if (PilotSession.authenticated) {
            scope.launch {
                runCatching {
                    publishHeartbeat(status, 0, 0, 0, "Demonstração encerrada.")
                    publishLiveStatus(status, "Demonstração encerrada.")
                }
            }
        }

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        virtualDisplay?.release()
        virtualDisplay = null

        val currentProjection = projection
        projection = null
        runCatching { currentProjection?.stop() }

        handlerThread?.quitSafely()
        handlerThread = null
    }

    override fun onDestroy() {
        stopCapture("STOPPED")
        vision.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun currentMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
            .getRealMetrics(metrics)

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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMART24 Demonstração",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun notification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("SMART24 • Teste assistido")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
