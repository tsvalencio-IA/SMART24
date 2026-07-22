package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class VisionEngine {
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAllPotentialBarcodes().build()
    )
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .setMinFaceSize(0.06f)
            .build()
    )
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )
    private val personTracker = PersonTracker()

    suspend fun analyze(bitmap: Bitmap): VisionResult = withContext(Dispatchers.Default) {
        val capturedAt = System.currentTimeMillis()
        val fullInput = InputImage.fromBitmap(bitmap, 0)
        val candidates = mutableListOf<PersonObservation>()

        // O GAME usa MoveNet SinglePose. Aqui a mesma ideia é aplicada aos quadros
        // recebidos do Yoosee, usando o detector corporal Android para a pessoa principal.
        val pose = runCatching { Tasks.await(poseDetector.process(fullInput), 8, TimeUnit.SECONDS) }.getOrNull()
        val poseLandmarks = pose?.allPoseLandmarks.orEmpty().filter { it.inFrameLikelihood >= 0.45f }
        if (poseLandmarks.size >= 8) {
            val left = poseLandmarks.minOf { it.position.x }.coerceIn(0f, bitmap.width.toFloat())
            val top = poseLandmarks.minOf { it.position.y }.coerceIn(0f, bitmap.height.toFloat())
            val right = poseLandmarks.maxOf { it.position.x }.coerceIn(0f, bitmap.width.toFloat())
            val bottom = poseLandmarks.maxOf { it.position.y }.coerceIn(0f, bitmap.height.toFloat())
            val padX = (right - left) * 0.18f
            val padY = (bottom - top) * 0.12f
            val rect = RectF(
                ((left - padX) / bitmap.width).coerceIn(0f, 1f),
                ((top - padY) / bitmap.height).coerceIn(0f, 1f),
                ((right + padX) / bitmap.width).coerceIn(0f, 1f),
                ((bottom + padY) / bitmap.height).coerceIn(0f, 1f)
            )
            val landmarks = poseLandmarks.map {
                PointF(
                    (it.position.x / bitmap.width).coerceIn(0f, 1f),
                    (it.position.y / bitmap.height).coerceIn(0f, 1f)
                )
            }
            candidates += PersonObservation(
                personId = "POSE-PRIMARY",
                box = rect,
                confidence = poseLandmarks.map { it.inFrameLikelihood.toDouble() }.average().coerceIn(0.45, 0.98),
                source = "POSE_TRACK_GAME_DERIVED",
                landmarks = landmarks
            )
        }

        val faces = runCatching { Tasks.await(faceDetector.process(fullInput), 8, TimeUnit.SECONDS) }.getOrDefault(emptyList())
        faces.forEachIndexed { index, face ->
            val rect = normalized(face.boundingBox, bitmap.width, bitmap.height)
            if (candidates.none { iou(it.box, rect) > 0.18f }) {
                candidates += PersonObservation(
                    personId = "FACE-${face.trackingId ?: (index + 1)}",
                    box = expandFaceBox(rect),
                    confidence = 0.82,
                    source = "FACE_TRACK"
                )
            }
        }

        val objects = runCatching { Tasks.await(objectDetector.process(fullInput), 8, TimeUnit.SECONDS) }.getOrDefault(emptyList())
        objects.forEachIndexed { index, obj ->
            val rect = normalized(obj.boundingBox, bitmap.width, bitmap.height)
            val aspect = if (rect.height() > 0) rect.width() / rect.height() else 2f
            val area = rect.width() * rect.height()
            val looksHuman = rect.height() >= 0.22f && aspect in 0.16f..1.05f && area >= 0.028f
            val overlapsKnown = candidates.any { iou(it.box, rect) > 0.16f }
            if (looksHuman && !overlapsKnown) {
                candidates += PersonObservation(
                    personId = "OBJECT-${obj.trackingId ?: index + 1}",
                    box = rect,
                    confidence = 0.52,
                    source = "OBJECT_TRACK_HEURISTIC"
                )
            }
        }

        val persons = personTracker.update(candidates, capturedAt)
        val tags = detectTags(bitmap)
        VisionResult(bitmap.width, bitmap.height, persons, tags, capturedAt)
    }

    private fun expandFaceBox(face: RectF): RectF {
        val width = face.width()
        val height = face.height()
        return RectF(
            (face.left - width * 0.65f).coerceIn(0f, 1f),
            (face.top - height * 0.35f).coerceIn(0f, 1f),
            (face.right + width * 0.65f).coerceIn(0f, 1f),
            (face.bottom + height * 4.2f).coerceIn(0f, 1f)
        )
    }

    private fun detectTags(bitmap: Bitmap): List<TagObservation> {
        val regions = mutableListOf(Region(0, 0, bitmap.width, bitmap.height, bitmap))
        val halfW = bitmap.width / 2
        val halfH = bitmap.height / 2
        if (halfW > 100 && halfH > 100) {
            regions += Region(0, 0, halfW, halfH, Bitmap.createBitmap(bitmap, 0, 0, halfW, halfH))
            regions += Region(halfW, 0, bitmap.width - halfW, halfH, Bitmap.createBitmap(bitmap, halfW, 0, bitmap.width - halfW, halfH))
            regions += Region(0, halfH, halfW, bitmap.height - halfH, Bitmap.createBitmap(bitmap, 0, halfH, halfW, bitmap.height - halfH))
            regions += Region(halfW, halfH, bitmap.width - halfW, bitmap.height - halfH, Bitmap.createBitmap(bitmap, halfW, halfH, bitmap.width - halfW, bitmap.height - halfH))
        }
        val found = linkedMapOf<String, TagObservation>()
        regions.forEachIndexed { index, region ->
            try {
                val input = InputImage.fromBitmap(region.bitmap, 0)
                val barcodes = runCatching { Tasks.await(barcodeScanner.process(input), 8, TimeUnit.SECONDS) }.getOrDefault(emptyList())
                barcodes.forEach { barcode ->
                    val raw = barcode.rawValue ?: return@forEach
                    val parsed = parseSmart24(raw) ?: return@forEach
                    val box = barcode.boundingBox ?: Rect(region.width / 2, region.height / 2, region.width / 2, region.height / 2)
                    val cx = (region.offsetX + box.centerX()).toFloat() / bitmap.width.toFloat()
                    val cy = (region.offsetY + box.centerY()).toFloat() / bitmap.height.toFloat()
                    found[parsed.serial] = parsed.copy(centerX = cx.coerceIn(0f, 1f), centerY = cy.coerceIn(0f, 1f))
                }
            } finally {
                if (index > 0 && !region.bitmap.isRecycled) region.bitmap.recycle()
            }
        }
        return found.values.toList()
    }

    private fun parseSmart24(raw: String): TagObservation? {
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("system") != "SMART24") return null
            val serial = obj.optString("serial")
            if (serial.isBlank()) return null
            TagObservation(
                serial = serial,
                productId = obj.optString("productId"),
                productName = obj.optString("productName", obj.optString("sku", "Produto")),
                sku = obj.optString("sku"),
                declaredStoreId = obj.optString("storeId"),
                declaredZoneId = obj.optString("zoneId"),
                centerX = 0f,
                centerY = 0f,
                confidence = 0.93,
                rawPayload = raw
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun normalized(rect: Rect, width: Int, height: Int): RectF = RectF(
        (rect.left.toFloat() / width).coerceIn(0f, 1f),
        (rect.top.toFloat() / height).coerceIn(0f, 1f),
        (rect.right.toFloat() / width).coerceIn(0f, 1f),
        (rect.bottom.toFloat() / height).coerceIn(0f, 1f)
    )

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    fun close() {
        barcodeScanner.close()
        faceDetector.close()
        objectDetector.close()
        poseDetector.close()
    }

    private data class Region(
        val offsetX: Int,
        val offsetY: Int,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap
    )
}
