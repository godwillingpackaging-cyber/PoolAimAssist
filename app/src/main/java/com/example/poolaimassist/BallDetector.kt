package com.example.poolaimassist

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight colour + circular-shape detector for 8 Ball Pool blue-felt tables.
 * Replaces the earlier connected-component detector, which merged a cue ball
 * with the game's white aim line and consequently returned Cue:NO.
 *
 * Not a trained vision model: table skins, ball effects and overlays can cause
 * misdetections. All coordinates are in the input bitmap's pixel space.
 */
class BallDetector(private val gridStep: Int = 3) {
    private data class Candidate(val x: Float, val y: Float, val score: Float, val whiteScore: Float)

    fun detectBalls(bitmap: Bitmap, pockets: List<Pt> = emptyList()): List<Ball> {
        val w = bitmap.width
        val h = bitmap.height
        if (pockets.size != 6 || w < 100 || h < 100) return emptyList()
        val minX = pockets.minOf { it.x }
        val maxX = pockets.maxOf { it.x }
        val minY = pockets.minOf { it.y }
        val maxY = pockets.maxOf { it.y }
        val tableWidth = maxX - minX
        val tableHeight = maxY - minY
        if (tableWidth < 160f || tableHeight < 80f) return emptyList()

        // In the standard 8 Ball Pool perspective, a ball diameter is about
        // 2.7% of the distance between the corner pockets.
        val radius = (tableWidth * 0.0136f).coerceIn(6f, 25f)
        val inset = radius * 1.85f
        val left = (minX + inset).roundToInt().coerceIn(0, w - 1)
        val right = (maxX - inset).roundToInt().coerceIn(0, w - 1)
        val top = (minY + inset).roundToInt().coerceIn(0, h - 1)
        val bottom = (maxY - inset).roundToInt().coerceIn(0, h - 1)
        if (left >= right || top >= bottom) return emptyList()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        fun pixel(x: Int, y: Int): Int = pixels[y * w + x]
        fun red(c: Int) = c ushr 16 and 255
        fun green(c: Int) = c ushr 8 and 255
        fun blue(c: Int) = c and 255

        // Sample felt at several regular interior positions. Median is robust
        // to the occasional object ball occupying a sample position.
        val grDiffs = ArrayList<Int>(35)
        val bgDiffs = ArrayList<Int>(35)
        for (gy in 1..5) for (gx in 1..7) {
            val px = (minX + tableWidth * gx / 8f).roundToInt().coerceIn(0, w - 1)
            val py = (minY + tableHeight * gy / 6f).roundToInt().coerceIn(0, h - 1)
            val c = pixel(px, py)
            grDiffs.add(green(c) - red(c))
            bgDiffs.add(blue(c) - green(c))
        }
        val feltGR = grDiffs.sorted()[grDiffs.size / 2]
        val feltBG = bgDiffs.sorted()[bgDiffs.size / 2]
        fun isFelt(c: Int): Boolean {
            val gr = green(c) - red(c)
            val bg = blue(c) - green(c)
            return abs(gr - feltGR) < 48 && abs(bg - feltBG) < 45 &&
                (feltGR < 15 || gr > 20) && (feltBG < 6 || bg > 4)
        }
        fun isWhite(c: Int): Boolean {
            val r = red(c); val g = green(c); val b = blue(c)
            val low = min(r, min(g, b))
            val high = max(r, max(g, b))
            return low > 175 && high - low < 65
        }

        // Pre-compute sampling offsets, so scoring each candidate is inexpensive.
        val inside = ArrayList<Pair<Int, Int>>(24)
        val outer = ArrayList<Pair<Int, Int>>(12)
        for (index in 0 until 12) {
            val angle = index * Math.PI / 6.0
            for (factor in listOf(0.42f, 0.73f)) {
                inside.add(Pair((kotlin.math.cos(angle) * radius * factor).roundToInt(),
                    (kotlin.math.sin(angle) * radius * factor).roundToInt()))
            }
            outer.add(Pair((kotlin.math.cos(angle) * radius * 1.54f).roundToInt(),
                (kotlin.math.sin(angle) * radius * 1.54f).roundToInt()))
        }

        val candidates = ArrayList<Candidate>()
        val step = max(2, gridStep)
        for (y in top..bottom step step) for (x in left..right step step) {
            // This fast gate skips most of the uniform blue table.
            if (isFelt(pixel(x, y))) continue
            var insideNonFelt = 0
            var whiteCount = 0
            var outerFelt = 0
            for ((dx, dy) in inside) {
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val c = pixel(xx, yy)
                if (!isFelt(c)) insideNonFelt++
                if (isWhite(c)) whiteCount++
            }
            // A line can intersect the ball; require a circular foreground
            // region surrounded mostly by blue felt, not just a white stripe.
            if (insideNonFelt < 17) continue
            for ((dx, dy) in outer) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && isFelt(pixel(xx, yy))) outerFelt++
            }
            if (outerFelt < 10) continue
            val insideFraction = insideNonFelt / inside.size.toFloat()
            val outsideFraction = outerFelt / outer.size.toFloat()
            candidates.add(Candidate(x.toFloat(), y.toFloat(),
                insideFraction * 0.6f + outsideFraction * 0.4f,
                whiteCount / inside.size.toFloat()))
        }
        if (candidates.isEmpty()) return emptyList()

        // Keep only one maximum per physical ball (but don't merge nearby balls).
        val retained = ArrayList<Candidate>()
        val separation = radius * 1.55f
        val distanceLimit = separation * separation
        for (candidate in candidates.sortedByDescending { it.score }) {
            if (retained.any {
                val dx = candidate.x - it.x
                val dy = candidate.y - it.y
                dx * dx + dy * dy < distanceLimit
            }) continue
            retained.add(candidate)
            if (retained.size >= 25) break
        }
        if (retained.isEmpty()) return emptyList()

        // The cue is mostly neutral-white across *both* halves. Numbered and
        // striped balls may have bright patches but generally score lower.
        val mostWhite = retained.indices.maxByOrNull { retained[it].whiteScore } ?: -1
        val cueIndex = if (mostWhite >= 0 && retained[mostWhite].whiteScore > 0.48f) mostWhite else -1
        return retained.mapIndexed { index, candidate ->
            Ball(Pt(candidate.x, candidate.y), radius, index == cueIndex)
        }
    }
}
