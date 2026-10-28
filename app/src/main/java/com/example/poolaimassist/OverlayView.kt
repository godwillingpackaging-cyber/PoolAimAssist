package com.example.poolaimassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Fullscreen non-touchable guide layer. Drawing coordinates are view-local pixels. */
class OverlayView(context: Context) : View(context) {
    @Volatile private var cueBall: Ball? = null
    @Volatile private var guides: List<ShotGuide> = emptyList()
    @Volatile private var pockets: List<Pt> = emptyList()
    @Volatile private var status = "Starting capture..."

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; strokeWidth = 5f; style = Paint.Style.STROKE }
    private val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120,255,255,255); strokeWidth = 2f; style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; strokeWidth = 3f; style = Paint.Style.STROKE }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE }
    private val statText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
    private val statBg = Paint().apply { color = Color.argb(200,10,16,22) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pockets.forEach { canvas.drawCircle(it.x, it.y, 17f, ring) }
        val tx = (width - 530f).coerceAtLeast(16f)
        canvas.drawRoundRect(tx - 8f, 12f, width.toFloat() - 12f, 50f, 10f, 10f, statBg)
        canvas.drawText(status, tx, 39f, statText)
        val cue = cueBall ?: return
        if (guides.isEmpty()) return
        guides.take(8).forEach { g ->
            canvas.drawLine(g.objectBall.center.x, g.objectBall.center.y, g.pocket.x, g.pocket.y, secondary)
        }
        val best = guides.first()
        canvas.drawLine(cue.center.x, cue.center.y, best.ghostBallCenter.x, best.ghostBallCenter.y, line)
        canvas.drawLine(best.objectBall.center.x, best.objectBall.center.y, best.pocket.x, best.pocket.y, line)
        canvas.drawCircle(best.ghostBallCenter.x, best.ghostBallCenter.y, best.objectBall.radiusPx, ghost)
    }

    fun update(cue: Ball?, shots: List<ShotGuide>, pocketPositions: List<Pt>, diagnostic: String) {
        cueBall = cue
        guides = shots
        pockets = pocketPositions
        status = diagnostic
        postInvalidate()
    }
}
