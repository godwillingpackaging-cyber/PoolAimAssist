package com.example.poolaimassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Same local coordinate space as OverlayView; no raw screen-offset drift. */
class CalibrationOverlay(context: Context, private val onComplete: (List<Pt>) -> Unit) : View(context) {
    private val labels = listOf(
        "1/6: TOP LEFT pocket", "2/6: TOP MIDDLE pocket", "3/6: TOP RIGHT pocket",
        "4/6: BOTTOM RIGHT pocket", "5/6: BOTTOM MIDDLE pocket", "6/6: BOTTOM LEFT pocket"
    )
    private val tapped = mutableListOf<Pt>()
    private val dim = Paint().apply { color = Color.argb(45, 0, 0, 0) }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 5f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 33f }
    private val background = Paint().apply { color = Color.argb(220, 0, 0, 0) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        tapped.forEach { canvas.drawCircle(it.x, it.y, 17f, marker) }
        val label = if (tapped.size < labels.size) labels[tapped.size] else "Saved!"
        val labelWidth = textPaint.measureText(label)
        canvas.drawRoundRect(16f, height - 83f, labelWidth + 48f, height - 12f, 16f, 16f, background)
        canvas.drawText(label, 32f, height - 34f, textPaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && tapped.size < 6) {
            // Both calibration and guide overlays are fullscreen window-local views.
            tapped.add(Pt(event.x, event.y))
            invalidate()
            if (tapped.size == 6) postDelayed({ onComplete(tapped.toList()) }, 250)
        }
        return true
    }
}
