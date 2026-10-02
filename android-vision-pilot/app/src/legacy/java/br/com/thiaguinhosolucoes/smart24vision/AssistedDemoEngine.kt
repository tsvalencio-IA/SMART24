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
        var handSide: String,
        var associationStatus: String,
        var associationConfidence: Double,
        var associationStableFrames: Int,
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

        val heldAssociation = result.heldObjects
            .filter { it.objectId == track.visualObjectId || it.personId == track.personId }
            .maxWithOrNull(
                compareBy<HeldObjectObservation> { it.status == "HELD_STABLE" }
                    .thenBy { it.confidence }
            )
        if (heldAssociation != null) {
            track.personId = heldAssociation.personId
            track.visualObjectId = heldAssociation.objectId
            track.handSide = heldAssociation.handSide
            track.associationStatus = heldAssociation.status
            track.associationConfidence = heldAssociation.confidence
            track.associationStableFrames = heldAssociation.stableFrames
            track.centerX = heldAssociation.objectX
            track.centerY = heldAssociation.objectY
            track.visualMode = if (heldAssociation.status == "HELD_STABLE") {
                "HAND_OBJECT_STABLE"
            } else {
                "HAND_OBJECT_CANDIDATE"
            }
            track.confidence = heldAssociation.confidence
            track.lastSeenAt = now
            if (!track.status.startsWith("ALERT")) {
                track.status = if (heldAssociation.status == "HELD_STABLE") "TRACKING_HELD" else "TRACKING_NEAR_HAND"
            }
            track.note = if (heldAssociation.status == "HELD_STABLE") {
                "Objeto associado à ${heldAssociation.handSide.lowercase()} por ${heldAssociation.stableFrames} quadros."
            } else {
                "Objeto próximo da mão; ainda acumulando evidência visual."
            }
            return@withLock
        }

        val trackedObject = track.visualObjectId?.let { id -> result.objects.firstOrNull { it.objectId == id } }
        if (trackedObject != null) {
            track.centerX = trackedObject.centerX
            track.centerY = trackedObject.centerY
            track.confidence = trackedObject.confidence
            track.associationStatus = "OBJECT_VISIBLE_HAND_NOT_CONFIRMED"
            track.associationConfidence = 0.0
            track.associationStableFrames = 0
            track.lastSeenAt = now
            track.status = if (track.status.startsWith("ALERT")) track.status else "TRACKING"
            track.note = "Objeto visual ainda acompanhado."
            return@withLock
        }

        val person = result.persons.firstOrNull { it.personId == track.personId }
            ?: result.persons.maxByOrNull { it.box.width() * it.box.height() }
        val wrist = person?.rightWrist ?: person?.leftWrist
        if (person != null && wrist != null) {
            track.personId = person.personId
            track.centerX = wrist.x
            track.centerY = wrist.y
            track.visualMode = if (track.visualObjectId == null) "WRIST_FALLBACK" else "OBJECT_LOST_WRIST_VISIBLE"
            track.confidence = (person.confidence * 0.62).coerceIn(0.28, 0.72)
            track.handSide = if (person.rightWrist != null) "RIGHT" else "LEFT"
            track.associationStatus = "WRIST_ONLY"
            track.associationConfidence = 0.0
            track.associationStableFrames = 0
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

    suspend fun markPickup(
        snapshotDataUrl: String?,
        objectCropDataUrl: String? = null,
        objectCropId: String? = null
    ): ActionResult = mutex.withLock {
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
        val association = candidate.heldObject
        val mode = when (association?.status) {
            "HELD_STABLE" -> "HAND_OBJECT_STABLE"
            "HAND_NEAR_OBJECT" -> "HAND_OBJECT_CANDIDATE"
            else -> if (objectId != null) "GENERIC_OBJECT_TRACK" else "WRIST_FALLBACK"
        }
        val confidence = when {
            association != null -> association.confidence
            candidate.objectObservation != null && candidate.person != null -> 0.66
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
            handSide = association?.handSide.orEmpty(),
            associationStatus = association?.status ?: "NOT_CONFIRMED",
            associationConfidence = association?.confidence ?: 0.0,
            associationStableFrames = association?.stableFrames ?: 0,
            centerX = center?.x,
            centerY = center?.y,
            confidence = confidence,
            startedAt = now,
            lastSeenAt = now,
            status = "TRACKING",
            note = when (association?.status) {
                "HELD_STABLE" -> "Operador confirmou a retirada; objeto já estava estável junto à mão."
                "HAND_NEAR_OBJECT" -> "Operador confirmou a retirada; objeto estava próximo da mão, ainda sem estabilidade completa."
                else -> if (objectId != null) {
                    "Operador confirmou a retirada; objeto genérico associado à pessoa sem prova suficiente de contato com a mão."
                } else {
                    "Operador confirmou a retirada; acompanhamento visual pelo pulso/pessoa."
                }
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
                "handSide" to track.handSide,
                "associationStatus" to track.associationStatus,
                "associationConfidence" to track.associationConfidence,
                "associationStableFrames" to track.associationStableFrames,
                "updatedAt" to now
            )
        )
        if (!objectCropDataUrl.isNullOrBlank()) {
            val cropMatches = objectCropId.isNullOrBlank() || objectCropId == track.visualObjectId
            val eligible = cropMatches && track.associationStatus == "HELD_STABLE"
            firebase.post(
                "visionSamples/${PilotSession.storeId}/${PilotSession.cameraId}",
                mapOf(
                    "sampleId" to "SAMPLE-$now",
                    "storeId" to PilotSession.storeId,
                    "cameraId" to PilotSession.cameraId,
                    "sessionId" to PilotSession.sessionId,
                    "personId" to track.personId,
                    "objectId" to (track.visualObjectId ?: ""),
                    "handSide" to track.handSide,
                    "productName" to track.productName,
                    "sku" to track.sku,
                    "imageDataUrl" to objectCropDataUrl,
                    "associationStatus" to track.associationStatus,
                    "associationConfidence" to track.associationConfidence,
                    "operatorConfirmed" to true,
                    "eligibleForTraining" to eligible,
                    "reviewStatus" to if (eligible) "READY_FOR_DATASET_REVIEW" else "MANUAL_REVIEW_REQUIRED",
                    "createdAt" to now
                )
            )
        }
        val evidence = when (track.associationStatus) {
            "HELD_STABLE" -> "objeto confirmado visualmente na mão"
            "HAND_NEAR_OBJECT" -> "objeto próximo da mão; revisão recomendada"
            else -> "retirada confirmada pelo operador"
        }
        ActionResult(true, "Item marcado para ${track.personId}: $evidence.")
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
            val stableHeld = result?.heldObjects?.count { it.status == "HELD_STABLE" } ?: 0
            "Pessoas ${result?.persons?.size ?: 0} • objetos ${result?.objects?.size ?: 0} • na mão $stableHeld • aguardando PEGOU"
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
        "handSide" to track.handSide,
        "associationStatus" to track.associationStatus,
        "associationConfidence" to track.associationConfidence,
        "associationStableFrames" to track.associationStableFrames,
        "operatorConfirmed" to true,
        "reason" to reason,
        "snapshotDataUrl" to snapshotDataUrl.orEmpty(),
        "source" to "ANDROID_ASSISTED_DEMO",
        "createdAt" to createdAt
    )

    private data class Candidate(
        val person: PersonObservation?,
        val objectObservation: ObjectObservation?,
        val anchor: PointF?,
        val heldObject: HeldObjectObservation?
    )

    private fun chooseCandidate(result: VisionResult): Candidate {
        val held = result.heldObjects.maxWithOrNull(
            compareBy<HeldObjectObservation> { it.status == "HELD_STABLE" }
                .thenBy { it.confidence }
        )
        if (held != null) {
            val person = result.persons.firstOrNull { it.personId == held.personId }
            val obj = result.objects.firstOrNull { it.objectId == held.objectId }
            return Candidate(person, obj, PointF(held.handX, held.handY), held)
        }

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
            return Candidate(bestPerson, bestObject, bestAnchor, null)
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
        return Candidate(person, nearestObject, anchor, null)
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
        handSide = handSide,
        associationStatus = associationStatus,
        associationConfidence = associationConfidence,
        associationStableFrames = associationStableFrames,
        centerX = centerX,
        centerY = centerY,
        confidence = confidence,
        startedAt = startedAt,
        lastSeenAt = lastSeenAt,
        note = note
    )
}
