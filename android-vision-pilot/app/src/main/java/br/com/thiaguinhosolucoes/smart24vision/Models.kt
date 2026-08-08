package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.PointF
import android.graphics.RectF

data class Zone(
    val zoneId: String,
    val storeId: String,
    val cameraId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val coordinateSpace: String = CoordinateSpaces.FULL_SCREEN_LEGACY
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

object CoordinateSpaces {
    const val FULL_SCREEN_LEGACY = "FULL_SCREEN_LEGACY"
    const val CAMERA_VIEWPORT_V1 = "CAMERA_VIEWPORT_V1"
}

data class CameraViewport(
    val storeId: String,
    val cameraId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val updatedAt: Long = 0L
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val valid: Boolean
        get() = left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
            width >= 0.12f && height >= 0.08f
}

data class HandObservation(
    val side: String,
    val wrist: PointF,
    val anchor: PointF,
    val fingertips: List<PointF>,
    val confidence: Double
)

data class PersonObservation(
    val personId: String,
    val box: RectF,
    val confidence: Double,
    val source: String,
    val trail: List<PointF> = emptyList(),
    val landmarks: List<PointF> = emptyList(),
    val leftWrist: PointF? = null,
    val rightWrist: PointF? = null,
    val leftHand: HandObservation? = null,
    val rightHand: HandObservation? = null
) {
    val centerX: Float get() = box.centerX()
    val centerY: Float get() = box.centerY()
}

data class ObjectObservation(
    val objectId: String,
    val box: RectF,
    val confidence: Double,
    val labels: List<String> = emptyList()
) {
    val centerX: Float get() = box.centerX()
    val centerY: Float get() = box.centerY()
}

data class HeldObjectObservation(
    val associationId: String,
    val personId: String,
    val objectId: String,
    val handSide: String,
    val status: String,
    val confidence: Double,
    val stableFrames: Int,
    val handX: Float,
    val handY: Float,
    val objectX: Float,
    val objectY: Float,
    val distanceToObject: Double,
    val evidence: List<String> = emptyList()
)

data class TagObservation(
    val serial: String,
    val productId: String,
    val productName: String,
    val sku: String,
    val declaredStoreId: String,
    val declaredZoneId: String,
    val centerX: Float,
    val centerY: Float,
    val confidence: Double,
    val rawPayload: String
)

data class VisionResult(
    val width: Int,
    val height: Int,
    val persons: List<PersonObservation>,
    val objects: List<ObjectObservation>,
    val heldObjects: List<HeldObjectObservation>,
    val tags: List<TagObservation>,
    val capturedAt: Long
)

data class AssistedTrackSnapshot(
    val trackId: String,
    val status: String,
    val productName: String,
    val sku: String,
    val zoneId: String,
    val personId: String,
    val visualObjectId: String?,
    val visualMode: String,
    val handSide: String,
    val associationStatus: String,
    val associationConfidence: Double,
    val associationStableFrames: Int,
    val centerX: Float?,
    val centerY: Float?,
    val confidence: Double,
    val startedAt: Long,
    val lastSeenAt: Long,
    val note: String
)
