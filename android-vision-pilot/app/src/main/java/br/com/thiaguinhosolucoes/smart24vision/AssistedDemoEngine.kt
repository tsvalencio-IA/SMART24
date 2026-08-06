package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.PointF
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.hypot

/**
 * Controlador da demonstração assistida.
 *
 * O operador confirma os momentos "pegou", "devolveu" e "possível ocultação".
 * A visão tenta associar a confirmação à pessoa, ao pulso e ao objeto genérico
 * mais próximos. Portanto, este módulo NÃO afirma reconhecimento automático do SKU.
 */
class AssistedDemoEngine(private val firebase: FirebaseRestClient) {
    data class ActionResult(val ok: Boolean, val message: String)

    private data class ActiveTrack(
        val trackId: String,
        val serial: String,
        val productName: String,
        val sku: String,
        val zoneId: String,
        var personId: String,
        var visualObjectId: String?,
        var visualMode: String,
        var centerX: Float?,
        var centerY: Float?,
        var confidence: Double,
        val startedAt: Long,
        var lastSeenAt: Long,
        var status: String,
        var note: String
    )

    private val mutex = Mutex()
    private var latestResult: VisionResult? = null
    private var active: ActiveTrack? = null

    suspend fun update(result: VisionResult) = mutex.withLock {
        latestResult = result
        val track = active ?: return@withLock
        val now = result.capturedAt

        val trackedObject = track.visualObjectId?.let { id -> result.objects.firstOrNull { it.objectId == id } }
        if (trackedObject != null) {
            track.centerX = trackedObject.centerX
            track.centerY = trackedObject.centerY
            track.confidence = trackedObject.confidence
            track.lastSeenAt = now
            track.status = if (track.status.startsWith("ALERT")) track.status else "TRACKING"
            track.note = "Objeto visual ainda acompanhado."
            return@withLock
        }

        val person = result.persons.firstOrNull { it.personId == track.personId }
            ?: result.persons.maxByOrNull { it.box.width() * it.box.height() }
        val wrist = person?.rightWrist ?: person?.leftWrist
        if (wrist != null) {
            track.personId = person.personId
            track.centerX = wrist.x
            track.centerY = wrist.y
            track.visualMode = if (track.visualObjectId == null) "WRIST_FALLBACK" else "OBJECT_LOST_WRIST_VISIBLE"
            track.confidence = (person.confidence * 0.62).coerceIn(0.28, 0.72)
            track.lastSeenAt = now
            if (!track.status.startsWith("ALERT")) track.status = "WRIST_TRACKING"
            track.note = if (track.visualObjectId == null) {
                "Sem objeto genérico estável; acompanhamento demonstrativo pelo pulso."
            } else {
                "Objeto genérico saiu da visão; pulso da pessoa ainda visível."
            }
        } else if (now - track.lastSeenAt > 1500L && !track.status.startsWith("ALERT")) {
            track.status = "VISUAL_LOST"
            track.note = "A visão perdeu o objeto e o pulso. Revisão humana necessária."
            track.confidence = 0.20
        }
    }

    suspend fun markPickup(snapshotDataUrl: String?): ActionResult = mutex.withLock {
        val existing = active
        if (existing != null && existing.status !in setOf("RETURNED", "FINISHED")) {
            return@withLock ActionResult(false, "Já existe um item em acompanhamento. Devolva ou finalize antes.")
        }
        val result = latestResult
            ?: return@withLock ActionResult(false, "Ainda não existe quadro analisado. Deixe o vídeo visível por alguns segundos.")

        val candidate = chooseCandidate(result)
        val now = System.currentTimeMillis()
        val trackId = "DEMO-$now"
        val serial = trackId
        val productName = PilotSession.demoProductName.ifBlank { "Item de demonstração" }
        val sku = PilotSession.demoSku.ifBlank { "DEMO-001" }
        val zoneId = PilotSession.demoZoneId.ifBlank { "PRATELEIRA-DEMO" }
        val personId = candidate.person?.personId ?: "PERSON-NAO-CONFIRMADA"
        val objectId = candidate.objectObservation?.objectId
        val center = candidate.objectObservation?.let { PointF(it.centerX, it.centerY) }
            ?: candidate.anchor
        val mode = if (objectId != null) "GENERIC_OBJECT_TRACK" else "WRIST_FALLBACK"
        val confidence = when {
            candidate.objectObservation != null && candidate.person != null -> 0.78
            candidate.person != null -> 0.55
            else -> 0.32
        }

        val track = ActiveTrack(
            trackId = trackId,
            serial = serial,
            productName = productName,
            sku = sku,
            zoneId = zoneId,
            personId = personId,
            visualObjectId = objectId,
            visualMode = mode,
            centerX = center?.x,
            centerY = center?.y,
            confidence = confidence,
            startedAt = now,
            lastSeenAt = now,
            status = "TRACKING",
            note = if (objectId != null) {
                "Operador confirmou a retirada; objeto genérico associado à pessoa."
            } else {
                "Operador confirmou a retirada; acompanhamento visual pelo pulso/pessoa."
            }
        )
        active = track

        firebase.post("events", eventPayload(
            type = "DEMO_TRACK_STARTED",
            track = track,
            createdAt = now,
            snapshotDataUrl = snapshotDataUrl,
            reason = "Retirada confirmada manualmente durante demonstração assistida."
        ))
        firebase.put(
            "carts/${PilotSession.storeId}/${PilotSession.sessionId}/${track.personId}/${track.serial}",
            mapOf(
                "serial" to track.serial,
                "productName" to track.productName,
                "sku" to track.sku,
                "zoneId" to track.zoneId,
                "status" to "DEMO_TRACKING",
                "confidence" to track.confidence,
                "operatorConfirmed" to true,
                "visualMode" to track.visualMode,
                "updatedAt" to now
            )
        )
        ActionResult(true, "Item marcado. Pessoa: ${track.personId}. Modo: ${track.visualMode}.")
    }

    suspend fun markReturn(snapshotDataUrl: String?): ActionResult = mutex.withLock {
        val track = active ?: return@withLock ActionResult(false, "Nenhum item está em acompanhamento.")
        val now = System.currentTimeMillis()
        track.status = "RETURNED"
        track.note = "Devolução confirmada manualmente pelo operador."
        firebase.post("events", eventPayload(
            type = "DEMO_ITEM_RETURNED",
            track = track,
            createdAt = now,
            snapshotDataUrl = snapshotDataUrl,
            reason = track.note
        ))
        firebase.delete("carts/${PilotSession.storeId}/${PilotSession.sessionId}/${track.personId}/${track.serial}")
        active = null
        ActionResult(true, "Devolução registrada e item removido do carrinho demonstrativo.")
    }

    suspend fun markConcealment(snapshotDataUrl: String?): ActionResult = createAlert(
        type = "DEMO_POSSIBLE_CONCEALMENT",
        reason = "Operador marcou possível ocultação do item durante a demonstração.",
        snapshotDataUrl = snapshotDataUrl
    )

    suspend fun sendManualAlert(snapshotDataUrl: String?): ActionResult = createAlert(
        type = "DEMO_ALERT_SENT",
        reason = "Alerta manual enviado pelo operador da demonstração.",
        snapshotDataUrl = snapshotDataUrl
    )

    private suspend fun createAlert(type: String, reason: String, snapshotDataUrl: String?): ActionResult = mutex.withLock {
        val track = active ?: return@withLock ActionResult(false, "Marque primeiro que a pessoa pegou um item.")
        val now = System.currentTimeMillis()
        track.status = "ALERT_PENDING_REVIEW"
        track.note = reason

        firebase.post("events", eventPayload(
            type = type,
            track = track,
            createdAt = now,
            snapshotDataUrl = snapshotDataUrl,
            reason = reason
        ))
        firebase.post("occurrences", mapOf(
            "type" to type,
            "status" to "pending",
            "storeId" to PilotSession.storeId,
            "cameraId" to PilotSession.cameraId,
            "sessionId" to PilotSession.sessionId,
            "personId" to track.personId,
            "trackId" to track.trackId,
            "productName" to track.productName,
            "sku" to track.sku,
            "pickedUp" to 1,
            "returned" to 0,
            "expected" to 1,
            "registered" to 0,
            "paid" to 0,
            "difference" to 1,
            "confidence" to track.confidence,
            "reason" to reason,
            "operatorConfirmed" to true,
            "visualMode" to track.visualMode,
            "snapshotDataUrl" to snapshotDataUrl.orEmpty(),
            "createdAt" to now
        ))
        ActionResult(true, "Alerta enviado ao Firebase e à fila de ocorrências.")
    }

    suspend fun finish(): ActionResult = mutex.withLock {
        val track = active ?: return@withLock ActionResult(false, "Nenhum item está em acompanhamento.")
        val now = System.currentTimeMillis()
        firebase.post("events", eventPayload(
            type = "DEMO_TRACK_FINISHED",
            track = track,
            createdAt = now,
            snapshotDataUrl = null,
            reason = "Acompanhamento encerrado manualmente sem conclusão automática."
        ))
        track.status = "FINISHED"
        active = null
        ActionResult(true, "Acompanhamento encerrado.")
    }

    suspend fun snapshot(): AssistedTrackSnapshot? = mutex.withLock {
        active?.toSnapshot()
    }

    suspend fun statusLine(): String = mutex.withLock {
        val result = latestResult
        val track = active
        if (track == null) {
            "Pessoas ${result?.persons?.size ?: 0} • objetos ${result?.objects?.size ?: 0} • aguardando PEGOU"
        } else {
            "${track.productName} • ${track.personId} • ${track.status}"
        }
    }

    private fun eventPayload(
        type: String,
        track: ActiveTrack,
        createdAt: Long,
        snapshotDataUrl: String?,
        reason: String
    ): Map<String, Any?> = mapOf(
        "type" to type,
        "storeId" to PilotSession.storeId,
        "cameraId" to PilotSession.cameraId,
        "sessionId" to PilotSession.sessionId,
        "personId" to track.personId,
        "trackId" to track.trackId,
        "tagId" to track.serial,
        "productName" to track.productName,
        "sku" to track.sku,
        "zoneId" to track.zoneId,
        "quantity" to 1,
        "confidence" to track.confidence,
        "visualObjectId" to (track.visualObjectId ?: ""),
        "visualMode" to track.visualMode,
        "operatorConfirmed" to true,
        "reason" to reason,
        "snapshotDataUrl" to snapshotDataUrl.orEmpty(),
        "source" to "ANDROID_ASSISTED_DEMO",
        "createdAt" to createdAt
    )

    private data class Candidate(
        val person: PersonObservation?,
        val objectObservation: ObjectObservation?,
        val anchor: PointF?
    )

    private fun chooseCandidate(result: VisionResult): Candidate {
        var bestPerson: PersonObservation? = null
        var bestObject: ObjectObservation? = null
        var bestAnchor: PointF? = null
        var bestDistance = Double.MAX_VALUE

        result.persons.forEach { person ->
            val anchors = listOfNotNull(person.leftWrist, person.rightWrist)
                .ifEmpty { listOf(PointF(person.centerX, person.centerY)) }
            result.objects.forEach { obj ->
                anchors.forEach { anchor ->
                    val distance = hypot(
                        (anchor.x - obj.centerX).toDouble(),
                        (anchor.y - obj.centerY).toDouble()
                    )
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestPerson = person
                        bestObject = obj
                        bestAnchor = anchor
                    }
                }
            }
        }

        if (bestPerson != null && bestObject != null && bestDistance <= 0.42) {
            return Candidate(bestPerson, bestObject, bestAnchor)
        }

        val person = result.persons.maxByOrNull { it.box.width() * it.box.height() }
        val anchor = person?.rightWrist ?: person?.leftWrist
            ?: person?.let { PointF(it.centerX, it.centerY) }
        val nearestObject = if (anchor == null) {
            result.objects.maxByOrNull { it.box.width() * it.box.height() }
        } else {
            result.objects.minByOrNull {
                hypot((anchor.x - it.centerX).toDouble(), (anchor.y - it.centerY).toDouble())
            }
        }
        return Candidate(person, nearestObject, anchor)
    }

    private fun ActiveTrack.toSnapshot() = AssistedTrackSnapshot(
        trackId = trackId,
        status = status,
        productName = productName,
        sku = sku,
        zoneId = zoneId,
        personId = personId,
        visualObjectId = visualObjectId,
        visualMode = visualMode,
        centerX = centerX,
        centerY = centerY,
        confidence = confidence,
        startedAt = startedAt,
        lastSeenAt = lastSeenAt,
        note = note
    )
}
