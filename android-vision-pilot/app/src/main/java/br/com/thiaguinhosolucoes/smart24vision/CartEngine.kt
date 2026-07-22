package br.com.thiaguinhosolucoes.smart24vision

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.hypot

class CartEngine(private val firebase: FirebaseRestClient) {
    private data class TagState(
        val serial: String,
        var productId: String,
        var productName: String,
        var sku: String,
        var lastX: Float,
        var lastY: Float,
        var lastSeenAt: Long,
        var lastZoneId: String?,
        var status: String,
        var assignedPersonId: String?
    )

    private val states = mutableMapOf<String, TagState>()
    private val mutex = Mutex()

    suspend fun process(result: VisionResult, zones: List<Zone>) = mutex.withLock {
        val currentSerials = result.tags.map { it.serial }.toSet()
        result.tags.forEach { tag ->
            val zone = zones.firstOrNull { it.contains(tag.centerX, tag.centerY) }
            val state = states.getOrPut(tag.serial) {
                TagState(tag.serial, tag.productId, tag.productName, tag.sku, tag.centerX, tag.centerY, result.capturedAt, zone?.zoneId, if (zone != null) "NA_PRATELEIRA" else "VISIVEL_FORA_DA_ZONA", null)
            }
            state.productId = tag.productId
            state.productName = tag.productName
            state.sku = tag.sku
            state.lastX = tag.centerX
            state.lastY = tag.centerY
            state.lastSeenAt = result.capturedAt

            if (state.status == "NO_CARRINHO" && zone != null) {
                publishReturn(state, zone.zoneId, result)
            } else if (state.status != "NO_CARRINHO" && zone == null) {
                val person = nearestPerson(tag.centerX, tag.centerY, result.persons)
                if (person != null) publishPickup(state, person, result, visibleOutsideZone = true)
            } else if (zone != null) {
                state.lastZoneId = zone.zoneId
                if (state.status != "NO_CARRINHO") state.status = "NA_PRATELEIRA"
                publishTagState(state, result.capturedAt)
            }
        }

        states.values.filter { it.status == "NA_PRATELEIRA" && it.serial !in currentSerials && result.capturedAt - it.lastSeenAt >= 2500L }
            .forEach { state ->
                val person = nearestPerson(state.lastX, state.lastY, result.persons)
                if (person != null) publishPickup(state, person, result, visibleOutsideZone = false)
                else publishAmbiguous(state, result)
            }
    }

    private fun nearestPerson(x: Float, y: Float, persons: List<PersonObservation>): PersonObservation? = persons
        .map { it to hypot((it.centerX - x).toDouble(), (it.centerY - y).toDouble()) }
        .filter { it.second <= 0.38 }
        .minByOrNull { it.second }
        ?.first

    private suspend fun publishPickup(state: TagState, person: PersonObservation, result: VisionResult, visibleOutsideZone: Boolean) {
        if (state.status == "NO_CARRINHO") return
        state.status = "NO_CARRINHO"
        state.assignedPersonId = person.personId
        val confidence = if (visibleOutsideZone) 0.89 else (0.68 * person.confidence).coerceAtLeast(0.42)
        firebase.post("events", mapOf(
            "type" to "PRODUCT_PICKUP",
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "sessionId" to PilotSession.sessionId,
            "personId" to person.personId,
            "tagId" to state.serial,
            "productId" to state.productId,
            "productName" to state.productName,
            "sku" to state.sku,
            "quantity" to 1,
            "confidence" to confidence,
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT",
            "createdAt" to result.capturedAt
        ))
        firebase.put("carts/${PilotSession.storeId}/${PilotSession.sessionId}/${person.personId}/${state.serial}", mapOf(
            "serial" to state.serial,
            "productId" to state.productId,
            "productName" to state.productName,
            "sku" to state.sku,
            "zoneId" to (state.lastZoneId ?: ""),
            "status" to "NO_CARRINHO",
            "confidence" to confidence,
            "updatedAt" to result.capturedAt
        ))
        publishTagState(state, result.capturedAt)
    }

    private suspend fun publishReturn(state: TagState, zoneId: String, result: VisionResult) {
        val previousPerson = state.assignedPersonId
        if (previousPerson != null) firebase.delete("carts/${PilotSession.storeId}/${PilotSession.sessionId}/$previousPerson/${state.serial}")
        state.status = "NA_PRATELEIRA"
        state.lastZoneId = zoneId
        state.assignedPersonId = null
        firebase.post("events", mapOf(
            "type" to "PRODUCT_RETURN",
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "sessionId" to PilotSession.sessionId,
            "personId" to (previousPerson ?: "UNKNOWN"),
            "tagId" to state.serial,
            "productId" to state.productId,
            "productName" to state.productName,
            "sku" to state.sku,
            "zoneId" to zoneId,
            "quantity" to 1,
            "confidence" to 0.91,
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT",
            "createdAt" to result.capturedAt
        ))
        publishTagState(state, result.capturedAt)
    }

    private suspend fun publishAmbiguous(state: TagState, result: VisionResult) {
        if (state.status == "AMBIGUOUS") return
        state.status = "AMBIGUOUS"
        firebase.post("events", mapOf(
            "type" to "AMBIGUOUS_INTERACTION",
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "sessionId" to PilotSession.sessionId,
            "personId" to "UNKNOWN",
            "tagId" to state.serial,
            "productId" to state.productId,
            "productName" to state.productName,
            "sku" to state.sku,
            "quantity" to 1,
            "confidence" to 0.30,
            "source" to "ANDROID_SCREEN_CAPTURE_PILOT",
            "createdAt" to result.capturedAt
        ))
        publishTagState(state, result.capturedAt)
    }

    private suspend fun publishTagState(state: TagState, timestamp: Long) {
        runCatching {
            firebase.patch("tagStates/${state.serial}", mapOf(
                "serial" to state.serial,
                "storeId" to PilotSession.storeId,
                "cameraId" to PilotSession.cameraId,
                "sessionId" to PilotSession.sessionId,
                "status" to state.status,
                "zoneId" to (state.lastZoneId ?: ""),
                "personId" to (state.assignedPersonId ?: ""),
                "lastSeenAt" to state.lastSeenAt,
                "updatedAt" to timestamp
            ))
        }.onFailure { Log.w("SMART24", "Falha ao publicar estado da etiqueta", it) }
    }
}
