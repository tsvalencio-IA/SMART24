package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Motor visual deliberadamente simples para a demonstração.
 *
 * Verdade operacional:
 * - detecta uma pose corporal principal;
 * - usa faces como apoio para manter pessoas;
 * - rastreia objetos genéricos salientes pelo ML Kit;
 * - QR SMART24 continua opcional, mas NÃO é necessário para o botão PEGOU.
 *
 * Ele não afirma reconhecer SKU automaticamente.
 */
class VisionEngine {
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
            .enableClassification()
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
        val input = InputImage.fromBitmap(bitmap, 0)
        val candidates = mutableListOf<PersonObservation>()

        val pose = runCatching {
            Tasks.await(poseDetector.process(input), 6, TimeUnit.SECONDS)
        }.getOrNull()

        val poseLandmarks = pose?.allPoseLandmarks.orEmpty()
            .filter { it.inFrameLikelihood >= 0.45f }

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

            val leftWrist = pose?.getPoseLandmark(PoseLandmark.LEFT_WRIST)
                ?.takeIf { it.inFrameLikelihood >= 0.35f }
                ?.let { normalizedPoint(it.position, bitmap.width, bitmap.height) }

            val rightWrist = pose?.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
                ?.takeIf { it.inFrameLikelihood >= 0.35f }
                ?.let { normalizedPoint(it.position, bitmap.width, bitmap.height) }

            candidates += PersonObservation(
                personId = "POSE-PRIMARY",
                box = rect,
                confidence = poseLandmarks.map { it.inFrameLikelihood.toDouble() }
                    .average().coerceIn(0.45, 0.98),
                source = "POSE_PRIMARY",
                landmarks = poseLandmarks.map {
                    normalizedPoint(it.position, bitmap.width, bitmap.height)
                },
                leftWrist = leftWrist,
                rightWrist = rightWrist
            )
        }

        val faces = runCatching {
            Tasks.await(faceDetector.process(input), 6, TimeUnit.SECONDS)
        }.getOrDefault(emptyList())

        faces.forEachIndexed { index, face ->
            val faceRect = normalized(face.boundingBox, bitmap.width, bitmap.height)
            if (candidates.none { iou(it.box, faceRect) > 0.18f }) {
                candidates += PersonObservation(
                    personId = "FACE-${face.trackingId ?: (index + 1)}",
                    box = expandFaceBox(faceRect),
                    confidence = 0.80,
                    source = "FACE_TRACK"
                )
            }
        }

        val rawObjects = runCatching {
            Tasks.await(objectDetector.process(input), 6, TimeUnit.SECONDS)
        }.getOrDefault(emptyList())

        // Primeiro aproveitamos objetos com formato humano para reforçar a lista de pessoas.
        rawObjects.forEachIndexed { index, obj ->
            val rect = normalized(obj.boundingBox, bitmap.width, bitmap.height)
            val aspect = if (rect.height() > 0) rect.width() / rect.height() else 2f
            val area = rect.width() * rect.height()
            val looksHuman = rect.height() >= 0.24f && aspect in 0.16f..1.05f && area >= 0.03f
            val overlapsKnown = candidates.any { iou(it.box, rect) > 0.16f }
            if (looksHuman && !overlapsKnown) {
                candidates += PersonObservation(
                    personId = "OBJECT-PERSON-${obj.trackingId ?: index + 1}",
                    box = rect,
                    confidence = 0.52,
                    source = "OBJECT_PERSON_HEURISTIC"
                )
            }
        }

        val persons = personTracker.update(candidates, capturedAt)

        // Objetos genéricos: não chamamos isso de "produto reconhecido".
        // Excluímos caixas grandes que parecem ser a própria pessoa.
        val genericObjects = rawObjects.mapIndexedNotNull { index, obj ->
            val rect = normalized(obj.boundingBox, bitmap.width, bitmap.height)
            val area = rect.width() * rect.height()
            val personOverlap = persons.maxOfOrNull { iou(it.box, rect) } ?: 0f
            val tooLarge = area > 0.42f
            val probablyPerson = personOverlap > 0.58f && rect.height() > 0.28f
            if (tooLarge || probablyPerson || area < 0.0008f) return@mapIndexedNotNull null

            val labels = obj.labels.mapNotNull { label ->
                label.text?.trim()?.takeIf { it.isNotBlank() }
            }
            val confidence = obj.labels.maxOfOrNull { it.confidence.toDouble() } ?: 0.55

            GenericObjectObservation(
                objectId = "OBJ-${obj.trackingId ?: index + 1}",
                trackingId = obj.trackingId,
                box = rect,
                confidence = confidence.coerceIn(0.35, 0.95),
                labels = labels
            )
        }

        val tags = emptyList<TagObservation>()

        VisionResult(
            width = bitmap.width,
            height = bitmap.height,
            persons = persons,
            objects = genericObjects,
            tags = tags,
            capturedAt = capturedAt
        )
    }

    private fun normalizedPoint(point: PointF, width: Int, height: Int) = PointF(
        (point.x / width).coerceIn(0f, 1f),
        (point.y / height).coerceIn(0f, 1f)
    )

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
        faceDetector.close()
        objectDetector.close()
        poseDetector.close()
    }
}
