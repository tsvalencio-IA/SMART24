package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.RectF

data class Zone(
    val zoneId: String,
    val storeId: String,
    val cameraId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class PersonObservation(
    val personId: String,
    val box: RectF,
    val confidence: Double,
    val source: String
) {
    val centerX: Float get() = box.centerX()
    val centerY: Float get() = box.centerY()
}

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
    val tags: List<TagObservation>,
    val capturedAt: Long
)
