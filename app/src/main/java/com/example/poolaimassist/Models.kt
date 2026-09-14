package com.example.poolaimassist

/** Simple 2D point in screen-pixel space (same coordinate space as the capture and the overlay). */
data class Pt(val x: Float, val y: Float) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun times(s: Float) = Pt(x * s, y * s)
    fun length(): Float = kotlin.math.sqrt(x * x + y * y)
    fun normalized(): Pt {
        val l = length()
        return if (l < 0.0001f) Pt(0f, 0f) else Pt(x / l, y / l)
    }
}

/** A detected ball on the table. */
data class Ball(
    val center: Pt,
    val radiusPx: Float,
    val isCueBall: Boolean
)

/** One suggested shot: cue ball -> ghost-ball aim point -> object ball -> pocket. */
data class ShotGuide(
    val objectBall: Ball,
    val pocket: Pt,
    val ghostBallCenter: Pt,
    val cutAngleDeg: Float
)
