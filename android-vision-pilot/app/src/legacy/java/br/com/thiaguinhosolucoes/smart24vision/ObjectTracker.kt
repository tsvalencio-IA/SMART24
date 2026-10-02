package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Mantém um ID estável mesmo nos primeiros quadros em que o ML Kit ainda não
 * devolve trackingId. Isso é necessário para exigir evidência em mais de um
 * quadro antes de afirmar que um objeto está junto à mão.
 */
class ObjectTracker {
    private data class Track(
        val id: String,
        var box: RectF,
        var confidence: Double,
        var labels: List<String>,
        var lastSeenAt: Long
    )

    private val tracks = linkedMapOf<String, Track>()
    private var nextId = 1

    fun update(observations: List<ObjectObservation>, timestamp: Long): List<ObjectObservation> {
        tracks.entries.removeAll { timestamp - it.value.lastSeenAt > 2600L }
        val unused = tracks.values.toMutableSet()
        val output = mutableListOf<ObjectObservation>()

        observations.sortedByDescending { it.confidence }.forEach { observation ->
            val match = unused
                .map { it to matchScore(it, observation) }
                .filter { it.second >= 0.24 }
                .maxByOrNull { it.second }
                ?.first

            val track = if (match != null) {
                unused.remove(match)
                match.box = smooth(match.box, observation.box, 0.64f)
                match.confidence = observation.confidence
                if (observation.labels.isNotEmpty()) match.labels = observation.labels
                match.lastSeenAt = timestamp
                match
            } else {
                val id = "OBJECT-${nextId.toString().padStart(2, '0')}"
                nextId += 1
                Track(
                    id = id,
                    box = RectF(observation.box),
                    confidence = observation.confidence,
                    labels = observation.labels,
                    lastSeenAt = timestamp
                ).also { tracks[id] = it }
            }

            output += ObjectObservation(
                objectId = track.id,
                box = RectF(track.box),
                confidence = track.confidence,
                labels = track.labels
            )
        }
        return output
    }

    private fun matchScore(track: Track, observation: ObjectObservation): Double {
        val overlap = iou(track.box, observation.box).toDouble()
        val centerDistance = hypot(
            (track.box.centerX() - observation.box.centerX()).toDouble(),
            (track.box.centerY() - observation.box.centerY()).toDouble()
        )
        val proximity = (1.0 - centerDistance / 0.28).coerceIn(0.0, 1.0)
        val trackLabel = track.labels.firstOrNull()?.lowercase()
        val observationLabel = observation.labels.firstOrNull()?.lowercase()
        val labelAgreement = if (trackLabel != null && observationLabel != null && trackLabel == observationLabel) 1.0 else 0.0
        return overlap * 0.58 + proximity * 0.34 + labelAgreement * 0.08
    }

    private fun smooth(previous: RectF, current: RectF, alpha: Float): RectF = RectF(
        previous.left + (current.left - previous.left) * alpha,
        previous.top + (current.top - previous.top) * alpha,
        previous.right + (current.right - previous.right) * alpha,
        previous.bottom + (current.bottom - previous.bottom) * alpha
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
}
