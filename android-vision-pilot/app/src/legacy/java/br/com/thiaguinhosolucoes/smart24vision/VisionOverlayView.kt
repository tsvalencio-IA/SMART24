package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Desenha as evidências da visão sobre o vídeo RTSP, sem alterar o stream. */
class VisionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = dp(12f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    @Volatile private var result: VisionResult? = null
    @Volatile private var zones: List<Zone> = emptyList()
    @Volatile private var assisted: AssistedTrackSnapshot? = null

    fun update(
        result: VisionResult,
        zones: List<Zone>,
        assisted: AssistedTrackSnapshot?
    ) {
        this.result = result
        this.zones = zones
        this.assisted = assisted
        postInvalidateOnAnimation()
    }

    fun clear() {
        result = null
        zones = emptyList()
        assisted = null
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val vision = result ?: return
        if (width <= 0 || height <= 0 || vision.width <= 0 || vision.height <= 0) return
        val frame = fittedFrame(vision.width, vision.height)

        zones.forEach { zone ->
            line.color = Color.rgb(255, 193, 7)
            line.strokeWidth = dp(2.5f)
            val rect = mapRect(zone.left, zone.top, zone.right, zone.bottom, frame)
            canvas.drawRect(rect, line)
            drawLabel(canvas, zone.zoneId, rect.left, rect.top, line.color)
        }

        vision.persons.forEachIndexed { index, person ->
            val color = PERSON_COLORS[index % PERSON_COLORS.size]
            line.color = color
            line.strokeWidth = dp(3f)
            val rect = mapRect(person.box.left, person.box.top, person.box.right, person.box.bottom, frame)
            canvas.drawRect(rect, line)
            drawLabel(canvas, person.personId, rect.left, (rect.top - dp(22f)).coerceAtLeast(frame.top), color)
            person.leftWrist?.let { point ->
                canvas.drawCircle(mapX(point.x, frame), mapY(point.y, frame), dp(8f), line)
            }
            person.rightWrist?.let { point ->
                canvas.drawCircle(mapX(point.x, frame), mapY(point.y, frame), dp(8f), line)
            }
        }

        vision.objects.forEach { item ->
            val active = assisted?.visualObjectId == item.objectId
            line.color = if (active) Color.rgb(255, 64, 129) else Color.rgb(0, 210, 220)
            line.strokeWidth = if (active) dp(4f) else dp(2f)
            val rect = mapRect(item.box.left, item.box.top, item.box.right, item.box.bottom, frame)
            canvas.drawRect(rect, line)
        }

        vision.heldObjects.forEach { held ->
            val stable = held.status == "HELD_STABLE"
            val color = if (stable) Color.rgb(255, 64, 129) else Color.rgb(255, 179, 0)
            line.color = color
            line.strokeWidth = if (stable) dp(4f) else dp(2.5f)
            val handX = mapX(held.handX, frame)
            val handY = mapY(held.handY, frame)
            val objectX = mapX(held.objectX, frame)
            val objectY = mapY(held.objectY, frame)
            canvas.drawLine(handX, handY, objectX, objectY, line)
            canvas.drawCircle(handX, handY, if (stable) dp(13f) else dp(9f), line)
            if (stable) {
                drawLabel(canvas, "OBJETO NA MÃO", objectX, (objectY - dp(24f)).coerceAtLeast(frame.top), color)
            }
        }

        assisted?.let { track ->
            val x = track.centerX
            val y = track.centerY
            if (x != null && y != null) {
                line.color = Color.rgb(255, 64, 129)
                line.strokeWidth = dp(5f)
                canvas.drawCircle(mapX(x, frame), mapY(y, frame), dp(20f), line)
            }
        }

        fill.color = Color.argb(190, 0, 0, 0)
        canvas.drawRect(frame.left, frame.top, frame.right, frame.top + dp(30f), fill)
        text.color = Color.WHITE
        val heldCount = vision.heldObjects.count { it.status == "HELD_STABLE" }
        canvas.drawText(
            "SMART24 • pessoas ${vision.persons.size} • objetos ${vision.objects.size} • na mão $heldCount",
            frame.left + dp(8f),
            frame.top + dp(20f),
            text
        )
    }

    private fun fittedFrame(frameWidth: Int, frameHeight: Int): RectF {
        val scale = min(width / frameWidth.toFloat(), height / frameHeight.toFloat())
        val contentWidth = frameWidth * scale
        val contentHeight = frameHeight * scale
        val left = (width - contentWidth) / 2f
        val top = (height - contentHeight) / 2f
        return RectF(left, top, left + contentWidth, top + contentHeight)
    }

    private fun mapRect(left: Float, top: Float, right: Float, bottom: Float, frame: RectF) = RectF(
        mapX(left, frame),
        mapY(top, frame),
        mapX(right, frame),
        mapY(bottom, frame)
    )

    private fun mapX(value: Float, frame: RectF): Float = frame.left + value.coerceIn(0f, 1f) * frame.width()
    private fun mapY(value: Float, frame: RectF): Float = frame.top + value.coerceIn(0f, 1f) * frame.height()

    private fun drawLabel(canvas: Canvas, value: String, x: Float, y: Float, color: Int) {
        val padding = dp(4f)
        val labelWidth = text.measureText(value) + padding * 2
        val labelHeight = text.textSize + padding * 2
        fill.color = Color.argb(205, 0, 0, 0)
        canvas.drawRoundRect(x, y, x + labelWidth, y + labelHeight, dp(5f), dp(5f), fill)
        text.color = color
        canvas.drawText(value, x + padding, y + text.textSize + padding / 2f, text)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private val PERSON_COLORS = intArrayOf(
            Color.rgb(41, 182, 246),
            Color.rgb(255, 112, 67),
            Color.rgb(171, 71, 188),
            Color.rgb(38, 166, 154),
            Color.rgb(255, 238, 88)
        )
    }
}
