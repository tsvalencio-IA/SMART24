package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Stores normalized coordinates so keyboard/viewport resizing cannot move a marking. */
class ZoneView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(65, 0, 220, 150) }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 220, 150); style = Paint.Style.STROKE; strokeWidth = 4f }
    private val savedBorder = Paint(border).apply { color = Color.YELLOW; strokeWidth = 2f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14 * resources.displayMetrics.scaledDensity }
    private val bounds = RectF()
    private val points = mutableListOf<PointF>()
    private var down: PointF? = null
    private var moved = false
    var onSelectionChanged: ((Int) -> Unit)? = null
    var savedZones: List<Zone> = emptyList()
        set(value) { field = value; invalidate() }
    var bitmap: Bitmap? = null
        set(value) { field = value; resetZone(); invalidate() }

    private fun imageBounds(): RectF {
        val bmp = bitmap ?: return RectF()
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val w = bmp.width * scale; val h = bmp.height * scale
        bounds.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        return bounds
    }
    private fun pixelRect(r: FloatArray, b: RectF) = RectF(b.left + r[0]*b.width(), b.top + r[1]*b.height(), b.left + r[2]*b.width(), b.top + r[3]*b.height())
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap?.takeUnless { it.isRecycled } ?: return
        val b = imageBounds()
        canvas.drawBitmap(bmp, null, b, bitmapPaint)
        savedZones.forEachIndexed { i, z ->
            val rect = pixelRect(floatArrayOf(z.left,z.top,z.right,z.bottom), b)
            canvas.drawRect(rect, savedBorder)
            canvas.drawText("${i+1}", rect.left+4f, (rect.top + text.textSize).coerceAtMost(b.bottom), text)
        }
        normalizedRect()?.let { r ->
            val rect = pixelRect(r, b); canvas.drawRect(rect, fill); canvas.drawRect(rect, border)
        } ?: points.forEach { canvas.drawCircle(b.left + it.x*b.width(), b.top + it.y*b.height(), 10f, border) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val b = imageBounds()
        if (bitmap == null || b.width() <= 0 || b.height() <= 0) return false
        val point = PointF(((event.x-b.left)/b.width()).coerceIn(0f,1f), ((event.y-b.top)/b.height()).coerceIn(0f,1f))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!b.contains(event.x, event.y)) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                down = PointF(event.x,event.y); moved = false
                if (points.size >= 2) points.clear()
                points += point
            }
            MotionEvent.ACTION_MOVE -> {
                val start = down ?: return true
                if (abs(event.x-start.x) + abs(event.y-start.y) > 12 * resources.displayMetrics.density) {
                    moved = true
                    if (points.size == 1) points += point else points[1] = point
                }
            }
            MotionEvent.ACTION_UP -> {
                if (moved && points.size == 2) points[1] = point
                down = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                down = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate(); onSelectionChanged?.invoke(points.size)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun resetZone() { points.clear(); invalidate(); onSelectionChanged?.invoke(0) }
    fun setNormalizedRect(rect: FloatArray?) {
        points.clear()
        if (rect != null && rect.size == 4) {
            points += PointF(rect[0].coerceIn(0f,1f), rect[1].coerceIn(0f,1f))
            points += PointF(rect[2].coerceIn(0f,1f), rect[3].coerceIn(0f,1f))
        }
        invalidate(); onSelectionChanged?.invoke(points.size)
    }
    fun normalizedRect(): FloatArray? {
        if (points.size != 2) return null
        val a=points[0]; val b=points[1]
        if (abs(a.x-b.x)<0.005f || abs(a.y-b.y)<0.005f) return null
        return floatArrayOf(minOf(a.x,b.x),minOf(a.y,b.y),maxOf(a.x,b.x),maxOf(a.y,b.y))
    }
}
