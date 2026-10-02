package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs

/** These events are hypotheses from shelf appearance; they do not confirm a sale or theft. */
data class MobileItemEvent(val type: String, val personId: String, val hand: String, val zone: Zone, val confidence: Double, val evidence: String, val createdAt: Long)

class MobileItemEngine(private val pickupThreshold: Double = 0.12, private val returnThreshold: Double = 0.08) {
    private data class HandState(val zone: Zone, val enteredAt: Long)
    private data class Pending(val person: PersonObservation, val hand: String, val state: HandState, val exitedAt: Long)
    private val hands = mutableMapOf<String, HandState>()
    private val pending = mutableMapOf<String, Pending>()
    private val active = mutableSetOf<String>()
    private val baselines = mutableMapOf<String, IntArray>()
    private val lastClear = mutableMapOf<String, IntArray>()
    private val cooldown = mutableMapOf<String, Long>()

    fun update(bitmap: Bitmap, result: VisionResult, zones: List<Zone>): List<MobileItemEvent> {
        val now = result.capturedAt
        val events = mutableListOf<MobileItemEvent>()
        val persons = result.persons.associateBy { it.personId }
        // Lost tracking cannot be interpreted as a hand leaving a product zone.
        hands.keys.removeAll { it.substringBefore(':') !in persons }
        pending.keys.removeAll { it.substringBefore(':') !in persons }
        zones.filter { zone -> !occupied(zone, result) }.forEach { zone ->
            if (zone.zoneId !in baselines) {
                val fp = fingerprint(bitmap, zone)
                baselines[zone.zoneId] = fp
                lastClear[zone.zoneId] = fp
            }
        }
        result.persons.forEach { person ->
            processHand(person, "LEFT", person.leftWrist, zones, now)
            processHand(person, "RIGHT", person.rightWrist, zones, now)
        }
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (key, value) = iterator.next()
            val zone = value.state.zone
            if (occupied(zone, result)) {
                if (now - value.exitedAt > 8000) iterator.remove()
                continue
            }
            if (now - value.exitedAt < 700) continue
            val baseline = baselines[zone.zoneId]
            val previous = lastClear[zone.zoneId]
            val current = fingerprint(bitmap, zone)
            val ownerKey = "${value.person.personId}:${zone.zoneId}"
            val change = if (previous != null) difference(previous, current) else 0.0
            val originalChange = if (baseline != null) difference(baseline, current) else 1.0
            val event = when {
                ownerKey in active && originalChange <= returnThreshold && change >= returnThreshold -> {
                    active.remove(ownerKey)
                    MobileItemEvent("ITEM_RETURNED_PROBABLE", value.person.personId, value.hand, zone, 0.72, "A mão voltou à zona e a imagem se aproximou do estado inicial. Requer revisão.", now)
                }
                baseline != null && change >= pickupThreshold && (cooldown[ownerKey] ?: 0L) + 2000 < now -> {
                    active += ownerKey
                    MobileItemEvent("ITEM_PICKED_PROBABLE", value.person.personId, value.hand, zone, (0.60 + minOf(0.25, change)), "Pulso saiu da zona e houve mudança visual de ${(change * 100).toInt()}%. Requer revisão.", now)
                }
                else -> MobileItemEvent("SHELF_INTERACTION", value.person.personId, value.hand, zone, 0.50, "Interação na zona; a imagem não permite concluir retirada ou devolução.", now)
            }
            events += event
            cooldown[ownerKey] = now
            lastClear[zone.zoneId] = current
            iterator.remove()
        }
        zones.filter { zone -> !occupied(zone, result) && pending.values.none { it.state.zone.zoneId == zone.zoneId } }.forEach { zone ->
            lastClear[zone.zoneId] = fingerprint(bitmap, zone)
        }
        return events
    }

    private fun processHand(person: PersonObservation, hand: String, wrist: PointF?, zones: List<Zone>, now: Long) {
        val key = "${person.personId}:$hand"
        if (wrist == null) { hands.remove(key); return }
        val zoneNow = zones.firstOrNull { it.contains(wrist.x, wrist.y) && it.sku.isNotBlank() }
        val before = hands[key]
        if (before != null && before.zone.zoneId != zoneNow?.zoneId) {
            if (now - before.enteredAt in 150..10000) pending[key] = Pending(person, hand, before, now)
            hands.remove(key)
        }
        if (zoneNow != null && key !in hands) hands[key] = HandState(zoneNow, now)
    }

    private fun occupied(zone: Zone, result: VisionResult): Boolean = result.persons.any { person ->
        listOfNotNull(person.leftWrist, person.rightWrist).any { zone.contains(it.x, it.y) } ||
            (person.box.left < zone.right && person.box.right > zone.left && person.box.top < zone.bottom && person.box.bottom > zone.top)
    }

    private fun difference(baseline: IntArray, current: IntArray): Double {
        if (baseline.size != current.size || baseline.isEmpty()) return 0.0
        val brightnessShift = current.average() - baseline.average()
        return baseline.indices.sumOf { abs(current[it] - baseline[it] - brightnessShift) } / baseline.size / 255.0
    }

    private fun fingerprint(bitmap: Bitmap, zone: Zone): IntArray {
        val x1 = (zone.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y1 = (zone.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val x2 = (zone.right * bitmap.width).toInt().coerceIn(x1 + 1, bitmap.width)
        val y2 = (zone.bottom * bitmap.height).toInt().coerceIn(y1 + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, x1, y1, x2 - x1, y2 - y1)
        val small = Bitmap.createScaledBitmap(crop, 24, 24, true)
        try {
            val pixels = IntArray(576)
            small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
            return IntArray(pixels.size) { i ->
                val c = pixels[i]
                (((c shr 16) and 255) * 30 + ((c shr 8) and 255) * 59 + (c and 255) * 11) / 100
            }
        } finally {
            if (small !== bitmap && small !== crop) small.recycle()
            if (crop !== bitmap) crop.recycle()
        }
    }

    fun reset() { hands.clear(); pending.clear(); active.clear(); baselines.clear(); lastClear.clear(); cooldown.clear() }
}
