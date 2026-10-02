package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot

/**
 * Associa objetos visuais a mãos anônimas usando geometria e persistência.
 *
 * Um único quadro só produz HAND_NEAR_OBJECT. A afirmação HELD_STABLE exige a
 * mesma associação em quadros consecutivos e confiança geométrica suficiente.
 * Isso reduz falsos positivos causados por objetos da prateleira ao fundo.
 */
class HandObjectAssociator {
    private data class State(
        var hits: Int,
        var smoothedScore: Double,
        var lastSeenAt: Long
    )

    private data class Candidate(
        val person: PersonObservation,
        val hand: HandObservation,
        val objectObservation: ObjectObservation,
        val score: Double,
        val distance: Double,
        val evidence: List<String>
    )

    private val states = mutableMapOf<String, State>()

    fun update(
        persons: List<PersonObservation>,
        objects: List<ObjectObservation>,
        timestamp: Long
    ): List<HeldObjectObservation> {
        states.entries.removeAll { timestamp - it.value.lastSeenAt > 2600L }
        if (persons.isEmpty() || objects.isEmpty()) return emptyList()

        val candidates = buildCandidates(persons, objects)
        val usedHands = mutableSetOf<String>()
        val usedObjects = mutableSetOf<String>()
        val selected = mutableListOf<Candidate>()

        candidates.sortedByDescending { it.score }.forEach { candidate ->
            val handKey = "${candidate.person.personId}:${candidate.hand.side}"
            if (handKey in usedHands || candidate.objectObservation.objectId in usedObjects) return@forEach
            usedHands += handKey
            usedObjects += candidate.objectObservation.objectId
            selected += candidate
        }

        return selected.map { candidate ->
            val stateKey = stateKey(candidate)
            val previous = states[stateKey]
            val consecutive = previous != null && timestamp - previous.lastSeenAt <= 1800L
            val state = if (previous == null || !consecutive) {
                State(1, candidate.score, timestamp)
            } else {
                previous.apply {
                    hits = (hits + 1).coerceAtMost(99)
                    smoothedScore = smoothedScore * 0.58 + candidate.score * 0.42
                    lastSeenAt = timestamp
                }
            }
            states[stateKey] = state

            val stable = state.hits >= 2 && state.smoothedScore >= 0.54
            val evidence = candidate.evidence.toMutableList().apply {
                if (state.hits >= 2) add("MULTI_FRAME_${state.hits}")
            }
            HeldObjectObservation(
                associationId = "HOLD-${candidate.person.personId}-${candidate.hand.side}-${candidate.objectObservation.objectId}",
                personId = candidate.person.personId,
                objectId = candidate.objectObservation.objectId,
                handSide = candidate.hand.side,
                status = if (stable) "HELD_STABLE" else "HAND_NEAR_OBJECT",
                confidence = state.smoothedScore.coerceIn(0.0, 0.98),
                stableFrames = state.hits,
                handX = candidate.hand.anchor.x,
                handY = candidate.hand.anchor.y,
                objectX = candidate.objectObservation.centerX,
                objectY = candidate.objectObservation.centerY,
                distanceToObject = candidate.distance,
                evidence = evidence
            )
        }.sortedWith(compareByDescending<HeldObjectObservation> { it.status == "HELD_STABLE" }.thenByDescending { it.confidence })
    }

    private fun buildCandidates(
        persons: List<PersonObservation>,
        objects: List<ObjectObservation>
    ): List<Candidate> {
        val output = mutableListOf<Candidate>()
        persons.forEach { person ->
            val hands = buildList<HandObservation> {
                person.leftHand?.let { add(it) }
                person.rightHand?.let { add(it) }
                if (isEmpty()) {
                    person.leftWrist?.let {
                        add(HandObservation("LEFT", it, it, emptyList(), person.confidence * 0.55))
                    }
                    person.rightWrist?.let {
                        add(HandObservation("RIGHT", it, it, emptyList(), person.confidence * 0.55))
                    }
                }
            }

            hands.forEach { hand ->
                objects.forEach { obj ->
                    score(person, hand, obj)?.let(output::add)
                }
            }
        }
        return output
    }

    private fun score(
        person: PersonObservation,
        hand: HandObservation,
        obj: ObjectObservation
    ): Candidate? {
        val objectArea = obj.box.width() * obj.box.height()
        val personArea = person.box.width() * person.box.height()
        if (objectArea !in 0.00025f..0.16f) return null
        if (personArea > 0f && objectArea > personArea * 0.62f && obj.box.contains(person.centerX, person.centerY)) return null

        val maxDistance = (person.box.height() * 0.16f + person.box.width() * 0.08f)
            .coerceIn(0.055f, 0.18f)
        val edgeDistance = distanceToRect(hand.anchor, obj.box)
        if (edgeDistance > maxDistance) return null

        val expanded = RectF(
            (obj.box.left - 0.012f).coerceAtLeast(0f),
            (obj.box.top - 0.012f).coerceAtLeast(0f),
            (obj.box.right + 0.012f).coerceAtMost(1f),
            (obj.box.bottom + 0.012f).coerceAtMost(1f)
        )
        val anchorInside = expanded.contains(hand.anchor.x, hand.anchor.y)
        val fingertipHits = hand.fingertips.count { expanded.contains(it.x, it.y) }
        val fingertipRatio = if (hand.fingertips.isEmpty()) 0.0 else fingertipHits.toDouble() / hand.fingertips.size
        val distanceScore = (1.0 - edgeDistance / maxDistance).coerceIn(0.0, 1.0)
        val contactScore = maxOf(if (anchorInside) 1.0 else 0.0, fingertipRatio)
        val detectorScore = obj.confidence.coerceIn(0.25, 0.95)
        val sizeScore = when {
            objectArea < 0.0008f -> 0.45
            objectArea <= 0.08f -> 1.0
            else -> 0.58
        }
        var score = distanceScore * 0.48 + contactScore * 0.24 + detectorScore * 0.16 + sizeScore * 0.12
        if (hand.fingertips.isEmpty()) score *= 0.84
        if (score < 0.40) return null

        val evidence = buildList {
            add("HAND_DISTANCE")
            if (anchorInside) add("HAND_ANCHOR_INSIDE_OBJECT")
            if (fingertipHits > 0) add("FINGERTIP_OVERLAP_$fingertipHits")
            if (obj.labels.isNotEmpty()) add("GENERIC_LABEL_${obj.labels.first()}")
        }
        return Candidate(person, hand, obj, score, edgeDistance.toDouble(), evidence)
    }

    private fun stateKey(candidate: Candidate): String =
        "${candidate.person.personId}|${candidate.hand.side}|${candidate.objectObservation.objectId}"

    private fun distanceToRect(point: PointF, rect: RectF): Float {
        val dx = when {
            point.x < rect.left -> rect.left - point.x
            point.x > rect.right -> point.x - rect.right
            else -> 0f
        }
        val dy = when {
            point.y < rect.top -> rect.top - point.y
            point.y > rect.bottom -> point.y - rect.bottom
            else -> 0f
        }
        return hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }
}
