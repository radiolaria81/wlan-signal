package de.radiolaria.wlansignal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

/**
 * Verlauf der letzten [capacity] Messungen (eine pro Sekunde).
 * Lücken (kein WLAN) werden als NaN gespeichert und nicht verbunden.
 */
class SignalGraph(ctx: Context) : View(ctx) {

    var capacity = 120
    private val values = ArrayDeque<Float>()

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ctx.getColor(R.color.primary)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = ctx.getColor(R.color.divider)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = ctx.getColor(R.color.text_sub)
    }
    private val path = Path()

    private val top = -30f
    private val bottom = -100f
    private val gridLines = intArrayOf(-50, -67, -80)

    fun add(rssi: Int?) {
        values.addLast(rssi?.toFloat() ?: Float.NaN)
        while (values.size > capacity) values.removeFirst()
        invalidate()
    }

    fun clear() {
        values.clear()
        invalidate()
    }

    private fun y(v: Float, h: Float): Float = ((top - v.coerceIn(bottom, top)) / (top - bottom)) * h

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (g in gridLines) {
            val gy = y(g.toFloat(), h)
            canvas.drawLine(0f, gy, w, gy, gridPaint)
            canvas.drawText("$g", 2f * density, gy - 3f * density, labelPaint)
        }

        if (values.isEmpty()) return
        val step = w / (capacity - 1).coerceAtLeast(1)
        // Neueste Messung rechts am Rand
        val offset = capacity - values.size
        path.reset()
        var penDown = false
        values.forEachIndexed { i, v ->
            if (v.isNaN()) {
                penDown = false
            } else {
                val px = (offset + i) * step
                val py = y(v, h)
                if (penDown) path.lineTo(px, py) else path.moveTo(px, py)
                penDown = true
            }
        }
        canvas.drawPath(path, linePaint)
    }
}
