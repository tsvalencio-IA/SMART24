package br.com.thiaguinhosolucoes.smart24vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

class FrameAnnotator {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD }

    fun annotate(source: Bitmap, result: VisionResult, zones: List<Zone>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width.toFloat()
        val h = output.height.toFloat()
        val scale = max(1f, w / 1080f)
        stroke.strokeWidth = 4f * scale
        text.textSize = 24f * scale

        zones.forEach { zone ->
            stroke.color = Color.rgb(255, 193, 7)
            val rect = RectF(zone.left * w, zone.top * h, zone.right * w, zone.bottom * h)
            canvas.drawRect(rect, stroke)
            drawLabel(canvas, zone.zoneId, rect.left, rect.top, Color.rgb(255, 193, 7), scale)
        }

        result.persons.forEachIndexed { index, person ->
            val personColor = PERSON_COLORS[index % PERSON_COLORS.size]
            stroke.color = personColor
            val rect = RectF(person.box.left * w, person.box.top * h, person.box.right * w, person.box.bottom * h)
            canvas.drawRect(rect, stroke)
            drawLabel(canvas, person.personId, rect.left, (rect.top - 30f * scale).coerceAtLeast(0f), personColor, scale)

            if (person.trail.size >= 2) {
                val path = Path()
                person.trail.forEachIndexed { pointIndex, point ->
                    val x = point.x * w
                    val y = point.y * h
                    if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                stroke.color = personColor
                stroke.strokeWidth = 3f * scale
                canvas.drawPath(path, stroke)
                stroke.strokeWidth = 4f * scale
            }

            fill.color = personColor
            person.landmarks.forEach { landmark -> canvas.drawCircle(landmark.x * w, landmark.y * h, 4f * scale, fill) }
        }

        result.tags.forEach { tag ->
            val x = tag.centerX * w
            val y = tag.centerY * h
            stroke.color = Color.rgb(0, 230, 118)
            canvas.drawCircle(x, y, 22f * scale, stroke)
            val shortSerial = tag.serial.takeLast(10)
            drawLabel(canvas, shortSerial, x + 26f * scale, y - 16f * scale, Color.rgb(0, 230, 118), scale)
        }

        fill.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, 50f * scale, fill)
        text.color = Color.WHITE
        canvas.drawText("SMART24 • pessoas ${result.persons.size} • etiquetas ${result.tags.size}", 14f * scale, 34f * scale, text)
        return output
    }

    private fun drawLabel(canvas: Canvas, value: String, x: Float, y: Float, color: Int, scale: Float) {
        val padding = 6f * scale
        val width = text.measureText(value) + padding * 2
        val height = text.textSize + padding * 2
        fill.color = Color.argb(205, 0, 0, 0)
        canvas.drawRoundRect(x, y, x + width, y + height, 8f * scale, 8f * scale, fill)
        text.color = color
        canvas.drawText(value, x + padding, y + text.textSize + padding / 2, text)
    }

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
