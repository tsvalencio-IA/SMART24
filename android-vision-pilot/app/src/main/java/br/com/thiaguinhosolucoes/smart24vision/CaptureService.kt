package br.com.thiaguinhosolucoes.smart24vision

import android.app.Activity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "SMART24_START_CAPTURE"
        const val ACTION_STOP = "SMART24_STOP_CAPTURE"
        const val ACTION_DEMO_PICKUP = "SMART24_DEMO_PICKUP"
        const val ACTION_DEMO_RETURN = "SMART24_DEMO_RETURN"
        const val ACTION_DEMO_CONCEAL = "SMART24_DEMO_CONCEAL"
        const val ACTION_DEMO_ALERT = "SMART24_DEMO_ALERT"
        const val ACTION_DEMO_FINISH = "SMART24_DEMO_FINISH"
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
    private var cameraViewport: CameraViewport? = null
    private var lastProcessedAt = 0L
    @Volatile private var lastImageReceivedAt = 0L
    private var lastSavedFrameAt = 0L
    private var lastScreenSavedFrameAt = 0L
    private var lastLiveFrameAt = 0L
    private var lastHeartbeatAt = 0L
    private var lastZoneRefreshAt = 0L
    private var lastViewportRefreshAt = 0L
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var overlayView: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var windowManager: WindowManager? = null
    @Volatile private var latestSnapshotDataUrl: String? = null
    @Volatile private var latestObjectCropDataUrl: String? = null
    @Volatile private var latestObjectCropId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture(
                    CaptureStatusStore.STATE_STOPPED,
                    "Análise parada. Os dados já enviados continuam no Firebase."
                )
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DEMO_PICKUP,
            ACTION_DEMO_RETURN,
            ACTION_DEMO_CONCEAL,
            ACTION_DEMO_ALERT,
            ACTION_DEMO_FINISH -> {
                handleManualControl(intent.action.orEmpty())
                return START_NOT_STICKY
            }
            ACTION_START -> {
                createChannels()
                startForeground(NOTIFICATION_ID, notification("Analisando vídeo da câmera Yoosee"))
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode != Activity.RESULT_OK || data == null) {
                    stopCapture(
                        CaptureStatusStore.STATE_ERROR,
                        "O Android não devolveu uma autorização válida de captura. Autorize ‘Tela inteira’ e tente novamente."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!PilotSession.authenticated) {
                    stopCapture(
                        CaptureStatusStore.STATE_ERROR,
                        "A sessão do Firebase expirou. Entre novamente e depois autorize a captura da tela."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                runCatching { startProjection(resultCode, data) }
                    .onFailure { error ->
                        Log.e("SMART24", "Falha ao iniciar a captura de tela", error)
                        stopCapture(
                            CaptureStatusStore.STATE_ERROR,
                            "Falha ao iniciar a captura: ${error.message ?: "erro do Android"}. Autorize ‘Tela inteira’ e tente novamente."
                        )
                        stopSelf()
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (projection != null) return
        stopping.set(false)
        CaptureStatusStore.reset(this, "Autorização recebida. Preparando a captura da tela…")
        clearLatestFrame()
        lastProcessedAt = 0L
        lastImageReceivedAt = 0L
        lastLiveFrameAt = 0L
        lastHeartbeatAt = 0L
        lastZoneRefreshAt = 0L
        lastViewportRefreshAt = 0L
        cameraViewport = CameraViewportStore.load(this, PilotSession.storeId, PilotSession.cameraId)
        latestSnapshotDataUrl = null
        latestObjectCropDataUrl = null
        latestObjectCropId = null
        val metrics = currentMetrics()
        handlerThread = HandlerThread("Smart24Capture").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data).also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture(
                        CaptureStatusStore.STATE_STOPPED,
                        "A autorização de captura foi encerrada pelo Android."
                    )
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
        if (PilotSession.overlayControlsEnabled) showAssistedOverlay()
        CaptureStatusStore.update(
            this,
            CaptureStatusStore.STATE_WAITING_VIDEO,
            "Captura de tela ativa. Abra o vídeo ao vivo no Yoosee; o SMART24 está aguardando o primeiro quadro."
        )
        scope.launch {
            publishHeartbeat("WAITING_VIDEO", 0, 0, 0, "Captura autorizada; aguardando o vídeo ao vivo do Yoosee.")
            publishLiveStatus("WAITING_VIDEO", "Abra a câmera no Yoosee e deixe o vídeo ao vivo visível.")
        }
        scope.launch {
            delay(8000L)
            if (projection != null && !stopping.get() && lastImageReceivedAt == 0L) {
                CaptureStatusStore.update(
                    this@CaptureService,
                    CaptureStatusStore.STATE_WAITING_VIDEO,
                    "Nenhum quadro chegou em 8 segundos. Pare a análise, autorize novamente e escolha ‘Tela inteira’ na janela do Android."
                )
            }
        }
    }

    private fun handleImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val now = System.currentTimeMillis()
        lastImageReceivedAt = now
        if (busy.get() || now - lastProcessedAt < PROCESS_INTERVAL_MS) {
            image.close()
            return
        }
        busy.set(true)
        lastProcessedAt = now
        val bitmap = runCatching { BitmapUtils.fromRgbaImage(image) }.getOrNull()
        image.close()
        if (bitmap == null) {
            CaptureStatusStore.update(
                this,
                CaptureStatusStore.STATE_ERROR,
                "O Android enviou um quadro que o SMART24 não conseguiu converter. Pare a análise e autorize novamente."
            )
            busy.set(false)
            return
        }
        scope.launch {
            var cameraBitmap: Bitmap? = null
            try {
                if (BitmapUtils.isMostlyBlack(bitmap)) {
                    if (now - lastHeartbeatAt > 5000L) {
                        CaptureStatusStore.update(
                            this@CaptureService,
                            CaptureStatusStore.STATE_NO_IMAGE,
                            "A captura chegou preta. A gravação em nuvem do Yoosee não interfere: autorize ‘Tela inteira’. Se continuar preto, o Yoosee está protegendo o vídeo nesse aparelho."
                        )
                        runCatching {
                            publishHeartbeat("NO_IMAGE", 0, 0, 0, "A captura está preta ou sem vídeo; o Yoosee pode estar fora da tela ou bloqueando captura.")
                            publishLiveStatus("NO_IMAGE", "Sem imagem válida. Confirme o vídeo ao vivo no Yoosee.")
                        }.onFailure { Log.e("SMART24", "Falha ao publicar estado sem imagem", it) }
                        lastHeartbeatAt = now
                    }
                    updateOverlayStatus("SEM IMAGEM: abra o vídeo ao vivo no Yoosee. Se continuar preto, o Yoosee está bloqueando a captura.")
                    return@launch
                }

                // Guardamos a tela inteira apenas para o operador delimitar o
                // vídeo. Ela nunca é enviada ao detector de pessoas/objetos.
                if (now - lastScreenSavedFrameAt > 2500L) {
                    saveLatestScreenFrame(bitmap)
                    lastScreenSavedFrameAt = now
                }

                if (now - lastViewportRefreshAt > 5000L) {
                    val remoteViewport = runCatching {
                        firebase.getCameraViewport(PilotSession.storeId, PilotSession.cameraId)
                    }.getOrNull()
                    if (remoteViewport != null) {
                        cameraViewport = remoteViewport
                        CameraViewportStore.save(this@CaptureService, remoteViewport)
                    } else if (cameraViewport == null) {
                        cameraViewport = CameraViewportStore.load(
                            this@CaptureService,
                            PilotSession.storeId,
                            PilotSession.cameraId
                        )
                    }
                    lastViewportRefreshAt = now
                }

                val viewport = cameraViewport
                if (viewport == null || !viewport.valid) {
                    if (now - lastHeartbeatAt > 5000L) {
                        CaptureStatusStore.update(
                            this@CaptureService,
                            CaptureStatusStore.STATE_WAITING_VIEWPORT,
                            "Tela recebida. Volte ao SMART24 e use ‘3. Delimitar somente o vídeo da câmera’ para excluir menus e miniaturas da análise.",
                            validFrame = true
                        )
                        runCatching {
                            publishHeartbeat(
                                "WAITING_VIEWPORT",
                                0,
                                0,
                                0,
                                "Tela real recebida; aguardando delimitação da área do vídeo."
                            )
                            publishLiveStatus(
                                "WAITING_VIEWPORT",
                                "Delimite no APK somente a imagem ao vivo da câmera antes de iniciar a visão."
                            )
                        }
                        lastHeartbeatAt = now
                    }
                    updateOverlayStatus("DEFINA A ÁREA DO VÍDEO: volte ao SMART24 e conclua o passo 3.")
                    return@launch
                }

                val analysisFrame = BitmapUtils.cropViewport(bitmap, viewport)
                cameraBitmap = analysisFrame
                if (BitmapUtils.isMostlyBlack(analysisFrame)) {
                    if (now - lastHeartbeatAt > 5000L) {
                        CaptureStatusStore.update(
                            this@CaptureService,
                            CaptureStatusStore.STATE_DEGRADED,
                            "A área delimitada está preta ou não contém o vídeo. Ajuste novamente os limites no passo 3."
                        )
                        publishLiveStatus("DEGRADED", "Área delimitada sem imagem útil; recalibre o vídeo.")
                        lastHeartbeatAt = now
                    }
                    updateOverlayStatus("RECORTE SEM VÍDEO: ajuste novamente a área da câmera.")
                    return@launch
                }

                if (now - lastZoneRefreshAt > 10000L) {
                    zones = runCatching {
                        firebase.getZones(PilotSession.storeId, PilotSession.cameraId)
                            .filter { it.coordinateSpace == CoordinateSpaces.CAMERA_VIEWPORT_V1 }
                    }.getOrDefault(zones)
                    lastZoneRefreshAt = now
                }

                // latest_frame.jpg contém somente pixels da câmera e serve à
                // calibração da prateleira. A interface do celular não entra.
                if (now - lastSavedFrameAt > 2500L) {
                    saveLatestFrame(analysisFrame)
                    lastSavedFrameAt = now
                    CaptureStatusStore.update(
                        this@CaptureService,
                        CaptureStatusStore.STATE_VIDEO_VISIBLE,
                        "Vídeo da câmera isolado. Pessoas, mãos e objetos agora são analisados somente dentro do recorte; a calibração da prateleira foi liberada.",
                        validFrame = true
                    )
                }

                val result = vision.analyze(analysisFrame)
                updateLatestObjectCrop(analysisFrame, result)
                demoEngine.update(result)
                cartEngine.process(result, zones)
                updateOverlayStatus(demoEngine.statusLine())

                if (now - lastLiveFrameAt >= LIVE_FRAME_INTERVAL_MS) {
                    publishLiveFrame(analysisFrame, result)
                    lastLiveFrameAt = now
                }

                if (now - lastHeartbeatAt > 5000L) {
                    publishHeartbeat(
                        "VIDEO_VISIBLE",
                        result.persons.size,
                        result.objects.size,
                        result.tags.size,
                        "Vídeo isolado processado; ${result.heldObjects.count { it.status == "HELD_STABLE" }} objeto(s) estável(is) na mão.",
                        heldObjects = result.heldObjects.count { it.status == "HELD_STABLE" }
                    )
                    lastHeartbeatAt = now
                }
            } catch (error: Throwable) {
                Log.e("SMART24", "Erro no processamento", error)
                if (now - lastHeartbeatAt > 5000L) {
                    val calibrationNote = if (CaptureStatusStore.snapshot(this@CaptureService).hasValidFrame) {
                        "A calibração continua disponível."
                    } else {
                        "A calibração ainda não foi liberada."
                    }
                    CaptureStatusStore.update(
                        this@CaptureService,
                        CaptureStatusStore.STATE_DEGRADED,
                        "Uma etapa da análise falhou: ${error.message ?: "erro de processamento"}. $calibrationNote"
                    )
                    runCatching {
                        publishHeartbeat("DEGRADED", 0, 0, 0, error.message ?: "Falha de processamento")
                        publishLiveStatus("DEGRADED", error.message ?: "Falha de processamento")
                    }
                    updateOverlayStatus("Falha: ${error.message ?: "processamento"}")
                    lastHeartbeatAt = now
                }
            } finally {
                cameraBitmap?.let { frame ->
                    if (frame !== bitmap && !frame.isRecycled) frame.recycle()
                }
                bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private fun updateLatestObjectCrop(bitmap: Bitmap, result: VisionResult) {
        val association = result.heldObjects.maxWithOrNull(
            compareBy<HeldObjectObservation> { it.status == "HELD_STABLE" }
                .thenBy { it.confidence }
        )
        val obj = association?.let { held -> result.objects.firstOrNull { it.objectId == held.objectId } }
        if (association == null || obj == null) {
            latestObjectCropDataUrl = null
            latestObjectCropId = null
            return
        }

        val padding = (maxOf(obj.box.width(), obj.box.height()) * 0.28f).coerceIn(0.012f, 0.06f)
        val crop = runCatching { BitmapUtils.cropNormalized(bitmap, obj.box, padding) }.getOrNull()
        if (crop == null) {
            latestObjectCropDataUrl = null
            latestObjectCropId = null
            return
        }
        var preview: Bitmap = crop
        try {
            val maxSide = maxOf(crop.width, crop.height)
            if (maxSide > 320) {
                val scale = 320f / maxSide.toFloat()
                preview = Bitmap.createScaledBitmap(
                    crop,
                    (crop.width * scale).toInt().coerceAtLeast(1),
                    (crop.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
            val bytes = ByteArrayOutputStream().use { stream ->
                preview.compress(Bitmap.CompressFormat.JPEG, 82, stream)
                stream.toByteArray()
            }
            latestObjectCropDataUrl = "data:image/jpeg;base64," +
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            latestObjectCropId = association.objectId
        } finally {
            if (preview !== crop && !preview.isRecycled) preview.recycle()
            if (!crop.isRecycled) crop.recycle()
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
                "leftHand" to person.leftHand?.let { hand ->
                    mapOf(
                        "side" to hand.side,
                        "x" to hand.anchor.x,
                        "y" to hand.anchor.y,
                        "confidence" to hand.confidence
                    )
                },
                "rightHand" to person.rightHand?.let { hand ->
                    mapOf(
                        "side" to hand.side,
                        "x" to hand.anchor.x,
                        "y" to hand.anchor.y,
                        "confidence" to hand.confidence
                    )
                },
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
        val heldObjectsPayload = result.heldObjects.associate { held ->
            held.associationId to mapOf(
                "associationId" to held.associationId,
                "personId" to held.personId,
                "objectId" to held.objectId,
                "handSide" to held.handSide,
                "status" to held.status,
                "confidence" to held.confidence,
                "stableFrames" to held.stableFrames,
                "handX" to held.handX,
                "handY" to held.handY,
                "objectX" to held.objectX,
                "objectY" to held.objectY,
                "distanceToObject" to held.distanceToObject,
                "evidence" to held.evidence
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
                "handSide" to it.handSide,
                "associationStatus" to it.associationStatus,
                "associationConfidence" to it.associationConfidence,
                "associationStableFrames" to it.associationStableFrames,
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
                "analysisSpace" to CoordinateSpaces.CAMERA_VIEWPORT_V1,
                "viewportConfigured" to true,
                "frameDataUrl" to dataUrl,
                "frameWidth" to targetWidth,
                "frameHeight" to targetHeight,
                "personsDetected" to result.persons.size,
                "objectsDetected" to result.objects.size,
                "heldObjectsDetected" to result.heldObjects.count { it.status == "HELD_STABLE" },
                "handObjectCandidates" to result.heldObjects.size,
                "tagsDetected" to result.tags.size,
                "persons" to personsPayload,
                "objects" to objectsPayload,
                "heldObjects" to heldObjectsPayload,
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
                "viewportConfigured" to (cameraViewport?.valid == true),
                "analysisSpace" to if (cameraViewport?.valid == true) {
                    CoordinateSpaces.CAMERA_VIEWPORT_V1
                } else {
                    CoordinateSpaces.FULL_SCREEN_LEGACY
                },
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
        note: String,
        heldObjects: Int = 0
    ) {
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
            "heldObjectsDetected" to heldObjects,
            "tagsDetected" to tags,
            "viewportConfigured" to (cameraViewport?.valid == true),
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
                runDemoAction {
                    demoEngine.markPickup(
                        snapshotDataUrl = latestSnapshotDataUrl,
                        objectCropDataUrl = latestObjectCropDataUrl,
                        objectCropId = latestObjectCropId
                    )
                }
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
            val current = CaptureStatusStore.snapshot(this@CaptureService)
            val state = if (current.state in setOf(
                    CaptureStatusStore.STATE_VIDEO_VISIBLE,
                    CaptureStatusStore.STATE_DEGRADED
                )
            ) {
                current.state
            } else {
                CaptureStatusStore.STATE_VIDEO_VISIBLE
            }
            CaptureStatusStore.update(
                this@CaptureService,
                state,
                result.message,
                validFrame = current.hasValidFrame
            )
            if (result.ok && alert) {
                showLocalAlert(result.message)
            }
        }
    }

    private fun handleManualControl(action: String) {
        if (projection == null || stopping.get()) {
            CaptureStatusStore.update(
                this,
                CaptureStatusStore.STATE_ERROR,
                "A análise não está ativa. Inicie a captura e aguarde o vídeo recortado antes de usar os controles."
            )
            return
        }
        when (action) {
            ACTION_DEMO_PICKUP -> runDemoAction {
                demoEngine.markPickup(
                    snapshotDataUrl = latestSnapshotDataUrl,
                    objectCropDataUrl = latestObjectCropDataUrl,
                    objectCropId = latestObjectCropId
                )
            }
            ACTION_DEMO_RETURN -> runDemoAction { demoEngine.markReturn(latestSnapshotDataUrl) }
            ACTION_DEMO_CONCEAL -> runDemoAction(alert = true) {
                demoEngine.markConcealment(latestSnapshotDataUrl)
            }
            ACTION_DEMO_ALERT -> runDemoAction(alert = true) {
                demoEngine.sendManualAlert(latestSnapshotDataUrl)
            }
            ACTION_DEMO_FINISH -> runDemoAction { demoEngine.finish() }
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
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) {
                "Não foi possível salvar o quadro para calibração"
            }
        }
    }

    private fun saveLatestScreenFrame(bitmap: Bitmap) {
        FileOutputStream(File(filesDir, "latest_screen_frame.jpg")).use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it)) {
                "Não foi possível salvar a tela para delimitar o vídeo"
            }
        }
    }

    private fun clearLatestFrame() {
        runCatching { File(filesDir, "latest_frame.jpg").delete() }
        runCatching { File(filesDir, "latest_screen_frame.jpg").delete() }
        lastSavedFrameAt = 0L
        lastScreenSavedFrameAt = 0L
    }

    private fun stopCapture(status: String, message: String = "Captura encerrada") {
        if (!stopping.compareAndSet(false, true)) return
        CaptureStatusStore.update(this, status, message)
        removeAssistedOverlay()
        scope.launch {
            if (PilotSession.authenticated) {
                runCatching {
                    publishHeartbeat(status, 0, 0, 0, message)
                    publishLiveStatus(status, message)
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
        stopCapture(CaptureStatusStore.STATE_STOPPED, "Serviço de captura encerrado.")
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
