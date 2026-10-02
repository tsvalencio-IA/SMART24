package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MobileOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val personPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }

    var zones: List<Zone> = emptyList()
        set(value) { field = value; invalidate() }
    var result: VisionResult? = null
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zones.forEach { z ->
            val l = z.left * width
            val t = z.top * height
            val r = z.right * width
            val b = z.bottom * height
            canvas.drawRect(l, t, r, b, zonePaint)
            canvas.drawText(z.sku.ifBlank { z.zoneId }, l + 4, (t - 8).coerceAtLeast(28f), textPaint)
        }

        result?.persons?.forEach { p ->
            canvas.drawRect(
                p.box.left * width, p.box.top * height,
                p.box.right * width, p.box.bottom * height, personPaint
            )
            p.leftWrist?.let { canvas.drawCircle(it.x * width, it.y * height, 10f, personPaint) }
            p.rightWrist?.let { canvas.drawCircle(it.x * width, it.y * height, 10f, personPaint) }
        }
    }
}
