package com.example.poolaimassist

import kotlin.math.acos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.PI

/**
 * Classic "ghost ball" aiming geometry:
 * to sink object ball B into pocket P, the cue ball must arrive at a point
 * (the "ghost ball" position) that is exactly one ball-diameter away from B,
 * along the line from P through B extended backwards.
 *
 * The cue ball is then aimed from its current position through that ghost-ball point.
 * The shot is only physically sane if the cut angle between
 * (cue -> ghost) and (ghost -> pocket, i.e. same direction as object ball travel)
 * is less than ~90 degrees (you cannot "pull" a ball, only push it).
 */
object TrajectoryCalculator {

    private const val MAX_CUT_ANGLE_DEG = 80f // beyond this the shot is not realistically makeable

    fun computeShotGuides(cueBall: Ball, objectBalls: List<Ball>, pockets: List<Pt>): List<ShotGuide> {
        if (pockets.isEmpty()) return emptyList()
        val ballDiameter = cueBall.radiusPx * 2f

        val guides = mutableListOf<ShotGuide>()
        for (obj in objectBalls) {
            for (pocket in pockets) {
                val travelDir = (pocket - obj.center).normalized()
                if (travelDir.length() < 0.01f) continue

                // Ghost ball sits one diameter behind the object ball, opposite the pocket direction.
                val ghost = obj.center - (travelDir * ballDiameter)

                val cueToGhost = (ghost - cueBall.center).normalized()
                val cutAngle = angleBetweenDeg(cueToGhost, travelDir)

                if (cutAngle <= MAX_CUT_ANGLE_DEG) {
                    guides.add(ShotGuide(obj, pocket, ghost, cutAngle))
                }
            }
        }
        // Easiest shots (smallest cut angle) first.
        return guides.sortedBy { it.cutAngleDeg }
    }

    private fun angleBetweenDeg(a: Pt, b: Pt): Float {
        val dot = (a.x * b.x + a.y * b.y).toDouble()
        val clamped = min(1.0, max(-1.0, dot))
        return (acos(clamped) * 180.0 / PI).toFloat()
    }
}
