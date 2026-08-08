package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Recebe somente quadros decodificados do stream RTSP da câmera.
 * Nenhum pixel da tela do Android entra neste processamento.
 */
class DirectVisionProcessor(context: Context) {
    data class Outcome(
        val result: VisionResult,
        val zones: List<Zone>,
        val assisted: AssistedTrackSnapshot?,
        val statusLine: String
    )

    private val appContext = context.applicationContext
    private val firebase = FirebaseRestClient()
    private val vision = VisionEngine()
    private val cartEngine = CartEngine(firebase)
    private val demoEngine = AssistedDemoEngine(firebase)
    private val annotator = FrameAnnotator()
    private val processMutex = Mutex()
    private var zones: List<Zone> = emptyList()
    private var lastSavedFrameAt = 0L
    private var lastLiveFrameAt = 0L
    private var lastHeartbeatAt = 0L
    private var lastZoneRefreshAt = 0L
    @Volatile private var latestSnapshotDataUrl: String? = null
    @Volatile private var latestObjectCropDataUrl: String? = null
    @Volatile private var latestObjectCropId: String? = null

    suspend fun start() {
        publishLiveStatus("CONNECTING_DIRECT", "Abrindo stream RTSP diretamente da câmera na rede local.")
        publishHeartbeat("CONNECTING_DIRECT", 0, 0, 0, "Conexão direta RTSP iniciada.")
    }

    suspend fun process(bitmap: Bitmap): Outcome = processMutex.withLock {
        require(PilotSession.authenticated) { "Sessão Firebase não autenticada." }
        require(!BitmapUtils.isMostlyBlack(bitmap)) { "A câmera enviou um quadro preto ou sem imagem útil." }
        val now = System.currentTimeMillis()

        if (now - lastZoneRefreshAt >= ZONE_REFRESH_INTERVAL_MS) {
            zones = runCatching {
                firebase.getZones(PilotSession.storeId, PilotSession.cameraId)
                    .filter {
                        it.coordinateSpace == CoordinateSpaces.DIRECT_CAMERA_FRAME_V1 ||
                            it.coordinateSpace == CoordinateSpaces.CAMERA_VIEWPORT_V1
                    }
            }.getOrDefault(zones)
            lastZoneRefreshAt = now
        }

        if (now - lastSavedFrameAt >= SAVE_FRAME_INTERVAL_MS) {
            saveLatestFrame(bitmap)
            lastSavedFrameAt = now
        }

        val result = vision.analyze(bitmap)
        updateLatestObjectCrop(bitmap, result)
        demoEngine.update(result)
        cartEngine.process(result, zones)
        val assisted = demoEngine.snapshot()

        if (now - lastLiveFrameAt >= LIVE_FRAME_INTERVAL_MS) {
            publishLiveFrame(bitmap, result, assisted)
            lastLiveFrameAt = now
        }

        if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
            publishHeartbeat(
                status = "VIDEO_DIRECT_VISIBLE",
                persons = result.persons.size,
                objects = result.objects.size,
                tags = result.tags.size,
                heldObjects = result.heldObjects.count { it.status == "HELD_STABLE" },
                note = "Quadro RTSP direto processado; sem captura da tela do celular."
            )
            lastHeartbeatAt = now
        }

        Outcome(result, zones, assisted, demoEngine.statusLine())
    }

    suspend fun markPickup(): AssistedDemoEngine.ActionResult = demoEngine.markPickup(
        snapshotDataUrl = latestSnapshotDataUrl,
        objectCropDataUrl = latestObjectCropDataUrl,
        objectCropId = latestObjectCropId
    )

    suspend fun markReturn(): AssistedDemoEngine.ActionResult = demoEngine.markReturn(latestSnapshotDataUrl)
    suspend fun markConcealment(): AssistedDemoEngine.ActionResult = demoEngine.markConcealment(latestSnapshotDataUrl)
    suspend fun sendManualAlert(): AssistedDemoEngine.ActionResult = demoEngine.sendManualAlert(latestSnapshotDataUrl)
    suspend fun finish(): AssistedDemoEngine.ActionResult = demoEngine.finish()

    suspend fun publishFailure(message: String) {
        publishLiveStatus("DIRECT_STREAM_ERROR", message)
        publishHeartbeat("DIRECT_STREAM_ERROR", 0, 0, 0, message)
    }

    suspend fun stop() {
        if (PilotSession.authenticated) {
            runCatching {
                publishLiveStatus("STOPPED", "Conexão direta encerrada pelo operador.")
                publishHeartbeat("STOPPED", 0, 0, 0, "Conexão direta encerrada pelo operador.")
            }
        }
    }

    fun close() {
        vision.close()
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
            latestObjectCropDataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            latestObjectCropId = association.objectId
        } finally {
            if (preview !== crop && !preview.isRecycled) preview.recycle()
            if (!crop.isRecycled) crop.recycle()
        }
    }

    private suspend fun publishLiveFrame(
        bitmap: Bitmap,
        result: VisionResult,
        assisted: AssistedTrackSnapshot?
    ) {
        val annotated = annotator.annotate(bitmap, result, zones, assisted)
        val targetWidth = 480.coerceAtMost(annotated.width)
        val targetHeight = (annotated.height * (targetWidth.toFloat() / annotated.width.toFloat()))
            .toInt()
            .coerceAtLeast(1)
        val preview = if (annotated.width == targetWidth) {
            annotated
        } else {
            Bitmap.createScaledBitmap(annotated, targetWidth, targetHeight, true)
        }
        try {
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
                        mapOf("side" to hand.side, "x" to hand.anchor.x, "y" to hand.anchor.y, "confidence" to hand.confidence)
                    },
                    "rightHand" to person.rightHand?.let { hand ->
                        mapOf("side" to hand.side, "x" to hand.anchor.x, "y" to hand.anchor.y, "confidence" to hand.confidence)
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
                    "status" to "VIDEO_DIRECT_VISIBLE",
                    "source" to SOURCE,
                    "analysisSpace" to CoordinateSpaces.DIRECT_CAMERA_FRAME_V1,
                    "viewportConfigured" to true,
                    "directStream" to true,
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
        } finally {
            if (preview !== annotated && !preview.isRecycled) preview.recycle()
            if (!annotated.isRecycled) annotated.recycle()
        }
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
                "source" to SOURCE,
                "analysisSpace" to CoordinateSpaces.DIRECT_CAMERA_FRAME_V1,
                "viewportConfigured" to true,
                "directStream" to true,
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
            "viewportConfigured" to true,
            "analysisSpace" to CoordinateSpaces.DIRECT_CAMERA_FRAME_V1,
            "directStream" to true,
            "source" to SOURCE,
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
                "directStream" to true,
                "lastSeenAt" to timestamp,
                "source" to SOURCE
            )
        )
    }

    private fun saveLatestFrame(bitmap: Bitmap) {
        FileOutputStream(File(appContext.filesDir, "latest_frame.jpg")).use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) {
                "Não foi possível salvar o quadro RTSP para calibração."
            }
        }
    }

    companion object {
        private const val SOURCE = "ANDROID_DIRECT_RTSP_ASSISTED_DEMO"
        private const val SAVE_FRAME_INTERVAL_MS = 2500L
        private const val LIVE_FRAME_INTERVAL_MS = 1800L
        private const val HEARTBEAT_INTERVAL_MS = 5000L
        private const val ZONE_REFRESH_INTERVAL_MS = 10000L
    }
}
