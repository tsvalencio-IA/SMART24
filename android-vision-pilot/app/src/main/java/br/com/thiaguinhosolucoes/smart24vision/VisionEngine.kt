package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Motor visual Local Edge sem bibliotecas nativas de ML.
 *
 * Android 16 do aparelho real registrou crash nativo em libmlkitcommonpipeline.so.
 * O monitor RTSP, Firebase e Central não podem cair por causa do motor de visão.
 *
 * Este motor usa movimento entre quadros para manter o vigilante funcionando sem
 * carregar ML Kit nativo. As inferências continuam heurísticas e exigem revisão humana.
 */
class VisionEngine {
    private val personTracker = PersonTracker()
    private var previousGray: IntArray? = null

    suspend fun analyze(bitmap: Bitmap): VisionResult = withContext(Dispatchers.Default) {
        val capturedAt = System.currentTimeMillis()
        val sampleWidth = 96
        val ratio = bitmap.height.toDouble() / bitmap.width.toDouble()
        val sampleHeight = (sampleWidth * ratio).toInt().coerceIn(54, 128)
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)

        try {
            val pixels = IntArray(sampleWidth * sampleHeight)
            scaled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            val gray = IntArray(pixels.size) { index ->
                val color = pixels[index]
                (((color shr 16) and 255) * 30 + ((color shr 8) and 255) * 59 + (color and 255) * 11) / 100
            }

            val before = previousGray
            previousGray = gray
            if (before == null || before.size != gray.size) {
                return@withContext VisionResult(bitmap.width, bitmap.height, emptyList(), emptyList(), emptyList(), capturedAt)
            }

            val moving = BooleanArray(gray.size)
            var movingCount = 0
            for (i in gray.indices) {
                if (abs(gray[i] - before[i]) >= 28) {
                    moving[i] = true
                    movingCount++
                }
            }

            if (movingCount < max(24, gray.size / 250)) {
                val persons = personTracker.update(emptyList(), capturedAt)
                return@withContext VisionResult(bitmap.width, bitmap.height, persons, emptyList(), emptyList(), capturedAt)
            }

            val components = components(moving, sampleWidth, sampleHeight)
            val personCandidates = components
                .mapNotNull { component ->
                    val rect = normalized(component, sampleWidth, sampleHeight)
                    val area = rect.width() * rect.height()
                    val aspect = if (rect.height() > 0f) rect.width() / rect.height() else 9f
                    val looksHuman = area >= 0.025f && rect.height() >= 0.18f && aspect in 0.16f..1.55f
                    if (!looksHuman) return@mapNotNull null

                    val expanded = RectF(
                        (rect.left - rect.width() * 0.08f).coerceIn(0f, 1f),
                        (rect.top - rect.height() * 0.05f).coerceIn(0f, 1f),
                        (rect.right + rect.width() * 0.08f).coerceIn(0f, 1f),
                        (rect.bottom + rect.height() * 0.05f).coerceIn(0f, 1f)
                    )
                    val wristY = (expanded.top + expanded.height() * 0.62f).coerceIn(0f, 1f)
                    PersonObservation(
                        personId = "MOTION-${component.size}",
                        box = expanded,
                        confidence = (0.50 + min(0.35, area.toDouble() * 1.8)).coerceIn(0.50, 0.85),
                        source = "MOTION_VISION_SAFE",
                        leftWrist = PointF((expanded.left + expanded.width() * 0.28f).coerceIn(0f, 1f), wristY),
                        rightWrist = PointF((expanded.left + expanded.width() * 0.72f).coerceIn(0f, 1f), wristY)
                    )
                }
                .sortedByDescending { it.box.width() * it.box.height() }
                .take(3)

            val persons = personTracker.update(personCandidates, capturedAt)

            val objects = components
                .mapIndexedNotNull { index, component ->
                    val rect = normalized(component, sampleWidth, sampleHeight)
                    val area = rect.width() * rect.height()
                    val personOverlap = persons.maxOfOrNull { iou(it.box, rect) } ?: 0f
                    if (area !in 0.0025f..0.20f || personOverlap > 0.55f) return@mapIndexedNotNull null
                    GenericObjectObservation(
                        objectId = "MOTION-OBJ-${index + 1}",
                        trackingId = null,
                        box = rect,
                        confidence = (0.45 + min(0.30, component.size.toDouble() / gray.size * 8.0)).coerceIn(0.45, 0.75),
                        labels = listOf("movimento")
                    )
                }
                .take(8)

            VisionResult(
                width = bitmap.width,
                height = bitmap.height,
                persons = persons,
                objects = objects,
                tags = emptyList(),
                capturedAt = capturedAt
            )
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private data class Component(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val size: Int
    )

    private fun components(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val result = mutableListOf<Component>()
        val minPixels = max(10, mask.size / 700)

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var left = width
            var top = height
            var right = 0
            var bottom = 0
            var size = 0

            while (head < tail) {
                val current = queue[head++]
                val x = current % width
                val y = current / width
                size++
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)

                fun add(nx: Int, ny: Int) {
                    if (nx !in 0 until width || ny !in 0 until height) return
                    val next = ny * width + nx
                    if (!mask[next] || visited[next]) return
                    visited[next] = true
                    queue[tail++] = next
                }

                add(x - 1, y)
                add(x + 1, y)
                add(x, y - 1)
                add(x, y + 1)
            }

            if (size >= minPixels) result += Component(left, top, right, bottom, size)
        }

        return result.sortedByDescending { it.size }.take(16)
    }

    private fun normalized(component: Component, width: Int, height: Int) = RectF(
        component.left.toFloat() / width,
        component.top.toFloat() / height,
        (component.right + 1).toFloat() / width,
        (component.bottom + 1).toFloat() / height
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
        previousGray = null
    }
}
