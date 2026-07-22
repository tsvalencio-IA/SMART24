package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ZoneView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 220, 150)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 220, 150)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    var bitmap: Bitmap? = null
        set(value) { field = value; resetZone(); invalidate() }
    private val points = mutableListOf<PointF>()
    private var drawLeft = 0f
    private var drawTop = 0f
    private var drawWidth = 0f
    private var drawHeight = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        drawWidth = bmp.width * scale
        drawHeight = bmp.height * scale
        drawLeft = (width - drawWidth) / 2f
        drawTop = (height - drawHeight) / 2f
        canvas.drawBitmap(bmp, null, android.graphics.RectF(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight), bitmapPaint)
        if (points.size == 2) {
            val p1 = points[0]
            val p2 = points[1]
            val rect = android.graphics.RectF(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x), maxOf(p1.y, p2.y))
            canvas.drawRect(rect, zonePaint)
            canvas.drawRect(rect, borderPaint)
        } else {
            points.forEach { canvas.drawCircle(it.x, it.y, 12f, borderPaint) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN || bitmap == null) return true
        if (event.x !in drawLeft..(drawLeft + drawWidth) || event.y !in drawTop..(drawTop + drawHeight)) return true
        if (points.size >= 2) points.clear()
        points += PointF(event.x, event.y)
        invalidate()
        return true
    }

    fun resetZone() { points.clear(); invalidate() }

    fun normalizedRect(): FloatArray? {
        if (points.size != 2 || drawWidth <= 0f || drawHeight <= 0f) return null
        val x1 = ((points[0].x - drawLeft) / drawWidth).coerceIn(0f, 1f)
        val y1 = ((points[0].y - drawTop) / drawHeight).coerceIn(0f, 1f)
        val x2 = ((points[1].x - drawLeft) / drawWidth).coerceIn(0f, 1f)
        val y2 = ((points[1].y - drawTop) / drawHeight).coerceIn(0f, 1f)
        return floatArrayOf(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
    }
}
