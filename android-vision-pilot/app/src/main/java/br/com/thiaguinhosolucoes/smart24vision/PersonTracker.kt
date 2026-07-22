package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Estabiliza IDs e trajetórias usando a mesma ideia do GAME: ler posições em
 * sequência, suavizar e transformar o movimento da câmera em coordenadas.
 * Não faz identificação civil nem reconhecimento facial de identidade.
 */
class PersonTracker {
    private data class Track(
        val id: String,
        var box: RectF,
        var confidence: Double,
        var source: String,
        var lastSeenAt: Long,
        val trail: ArrayDeque<PointF> = ArrayDeque(),
        var landmarks: List<PointF> = emptyList()
    )

    private val tracks = linkedMapOf<String, Track>()
    private var nextId = 1

    fun update(observations: List<PersonObservation>, timestamp: Long): List<PersonObservation> {
        tracks.entries.removeAll { timestamp - it.value.lastSeenAt > 4500L }
        val unused = tracks.values.toMutableSet()
        val output = mutableListOf<PersonObservation>()

        observations.sortedByDescending { it.confidence }.forEach { observation ->
            val match = unused
                .map { it to matchScore(it.box, observation.box) }
                .filter { it.second >= 0.20 }
                .maxByOrNull { it.second }
                ?.first

            val track = if (match != null) {
                unused.remove(match)
                match.box = smooth(match.box, observation.box, 0.58f)
                match.confidence = observation.confidence
                match.source = observation.source
                match.lastSeenAt = timestamp
                match.landmarks = observation.landmarks
                match
            } else {
                val id = "PERSON-${nextId.toString().padStart(2, '0')}"
                nextId += 1
                Track(id, RectF(observation.box), observation.confidence, observation.source, timestamp, landmarks = observation.landmarks)
                    .also { tracks[id] = it }
            }

            val center = PointF(track.box.centerX(), track.box.centerY())
            if (track.trail.isEmpty() || distance(track.trail.last(), center) > 0.008f) {
                track.trail.addLast(center)
                while (track.trail.size > 20) track.trail.removeFirst()
            }

            output += PersonObservation(
                personId = track.id,
                box = RectF(track.box),
                confidence = track.confidence,
                source = track.source,
                trail = track.trail.toList(),
                landmarks = track.landmarks
            )
        }
        return output
    }

    private fun matchScore(a: RectF, b: RectF): Double {
        val overlap = iou(a, b).toDouble()
        val dist = hypot((a.centerX() - b.centerX()).toDouble(), (a.centerY() - b.centerY()).toDouble())
        val proximity = (1.0 - dist / 0.55).coerceIn(0.0, 1.0)
        return overlap * 0.65 + proximity * 0.35
    }

    private fun smooth(previous: RectF, current: RectF, alpha: Float): RectF = RectF(
        previous.left + (current.left - previous.left) * alpha,
        previous.top + (current.top - previous.top) * alpha,
        previous.right + (current.right - previous.right) * alpha,
        previous.bottom + (current.bottom - previous.bottom) * alpha
    )

    private fun distance(a: PointF, b: PointF): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
