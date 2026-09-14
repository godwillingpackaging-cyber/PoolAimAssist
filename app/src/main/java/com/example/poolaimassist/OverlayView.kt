package com.example.poolaimassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Transparent full-screen view drawn via WindowManager (TYPE_APPLICATION_OVERLAY) that
 * renders the current shot guides on top of whatever app is in front (the pool game).
 */
class OverlayView(context: Context) : View(context) {

    @Volatile
    var cueBall: Ball? = null
    @Volatile
    var guides: List<ShotGuide> = emptyList()
    @Volatile
    var pockets: List<Pt> = emptyList()

    private val bestLinePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val altLinePaint = Paint().apply {
        color = Color.argb(140, 255, 255, 255)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val ghostBallPaint = Paint().apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val pocketPaint = Paint().apply {
        color = Color.argb(150, 0, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (p in pockets) {
            canvas.drawCircle(p.x, p.y, 22f, pocketPaint)
        }

        val cue = cueBall ?: return
        if (guides.isEmpty()) return

        // Faint lines for every feasible shot.
        for (g in guides) {
            canvas.drawLine(g.objectBall.center.x, g.objectBall.center.y, g.pocket.x, g.pocket.y, altLinePaint)
        }

        // Highlight the easiest shot.
        val best = guides.first()
        canvas.drawLine(cue.center.x, cue.center.y, best.ghostBallCenter.x, best.ghostBallCenter.y, bestLinePaint)
        canvas.drawLine(best.objectBall.center.x, best.objectBall.center.y, best.pocket.x, best.pocket.y, bestLinePaint)
        canvas.drawCircle(best.ghostBallCenter.x, best.ghostBallCenter.y, best.objectBall.radiusPx, ghostBallPaint)
    }

    fun update(cue: Ball?, newGuides: List<ShotGuide>) {
        cueBall = cue
        guides = newGuides
        postInvalidate()
    }

    fun setPockets(newPockets: List<Pt>) {
        pockets = newPockets
        postInvalidate()
    }
}
