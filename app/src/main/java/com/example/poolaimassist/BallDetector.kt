package com.example.poolaimassist

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Table-aware detector for the blue-felt layout used in 8 Ball Pool.
 * Works only inside the six calibrated pockets and suppresses long thin cue guides.
 * This is a best-effort image classifier, not an ML model.
 */
class BallDetector(private val gridStep: Int = 3) {
    fun detectBalls(bitmap: Bitmap, pockets: List<Pt> = emptyList()): List<Ball> {
        val w = bitmap.width
        val h = bitmap.height
        if (pockets.size != 6 || w < 100 || h < 100) return emptyList()
        val minPocketX = pockets.minOf { it.x }
        val maxPocketX = pockets.maxOf { it.x }
        val minPocketY = pockets.minOf { it.y }
        val maxPocketY = pockets.maxOf { it.y }
        val tableW = maxPocketX - minPocketX
        val tableH = maxPocketY - minPocketY
        if (tableW < 160f || tableH < 80f) return emptyList()

        // Exclude cushion, pocket markers and top/bottom HUD.
        val inset = max(10f, min(tableW, tableH) * 0.045f)
        val x0 = (minPocketX + inset).toInt().coerceIn(0, w - 1)
        val x1 = (maxPocketX - inset).toInt().coerceIn(1, w)
        val y0 = (minPocketY + inset).toInt().coerceIn(0, h - 1)
        val y1 = (maxPocketY - inset).toInt().coerceIn(1, h)
        val cols = (x1 - x0) / gridStep
        val rows = (y1 - y0) / gridStep
        if (cols < 10 || rows < 10) return emptyList()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        fun pixel(x: Int, y: Int): Int = pixels[y * w + x]

        // Get a robust felt colour from spaced positions away from the rails.
        val rs = mutableListOf<Int>()
        val gs = mutableListOf<Int>()
        val bs = mutableListOf<Int>()
        for (gy in 1..5) for (gx in 1..7) {
            val sx = x0 + ((x1 - x0) * gx / 8)
            val sy = y0 + ((y1 - y0) * gy / 6)
            val c = pixel(sx, sy)
            rs.add(c shr 16 and 255)
            gs.add(c shr 8 and 255)
            bs.add(c and 255)
        }
        val feltR = rs.sorted()[rs.size / 2]
        val feltG = gs.sorted()[gs.size / 2]
        val feltB = bs.sorted()[bs.size / 2]
        val feltGR = feltG - feltR
        val feltBG = feltB - feltG

        fun isFelt(c: Int): Boolean {
            val r = c shr 16 and 255
            val g = c shr 8 and 255
            val b = c and 255
            val gr = g - r
            val bg = b - g
            // Relative channel differences survive the table's light/dark gradient.
            return abs(gr - feltGR) < 48 && abs(bg - feltBG) < 45 &&
                (if (feltGR >= 15) gr >= 20 else true) &&
                (if (feltBG >= 6) bg >= 4 else if (feltBG <= -6) bg <= -4 else true)
        }

        val mask = BooleanArray(cols * rows)
        for (y in 0 until rows) for (x in 0 until cols) {
            mask[y * cols + x] = !isFelt(pixel(x0 + x * gridStep, y0 + y * gridStep))
        }
        // Opening: 3x3 erosion then dilation suppresses narrow white aiming lines.
        val eroded = BooleanArray(mask.size)
        for (y in 1 until rows - 1) for (x in 1 until cols - 1) {
            var all = true
            for (dy in -1..1) for (dx in -1..1) {
                if (!mask[(y + dy) * cols + x + dx]) all = false
            }
            eroded[y * cols + x] = all
        }
        val opened = BooleanArray(mask.size)
        for (y in 1 until rows - 1) for (x in 1 until cols - 1) {
            var any = false
            for (dy in -1..1) for (dx in -1..1) {
                if (eroded[(y + dy) * cols + x + dx]) any = true
            }
            opened[y * cols + x] = any
        }

        val visited = BooleanArray(opened.size)
        val objects = mutableListOf<Pair<Ball, Float>>()
        val minDiameter = max(10f, tableW * 0.010f)
        val maxDiameter = max(26f, tableW * 0.060f)
        val stack = IntArray(opened.size)
        for (root in opened.indices) {
            if (!opened[root] || visited[root]) continue
            var count = 0
            var top = 0
            stack[top++] = root
            visited[root] = true
            var minX = cols; var maxX = 0; var minY = rows; var maxY = 0
            while (top > 0) {
                val current = stack[--top]
                val cx = current % cols
                val cy = current / cols
                count++
                minX = min(minX, cx); maxX = max(maxX, cx)
                minY = min(minY, cy); maxY = max(maxY, cy)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = cx + dx; val ny = cy + dy
                    if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue
                    val next = ny * cols + nx
                    if (opened[next] && !visited[next]) {
                        visited[next] = true
                        stack[top++] = next
                    }
                }
            }
            val pw = (maxX - minX + 1) * gridStep.toFloat()
            val ph = (maxY - minY + 1) * gridStep.toFloat()
            val diameter = max(pw, ph)
            val aspect = pw / max(1f, ph)
            if (count < 7 || diameter !in minDiameter..maxDiameter || aspect !in 0.60f..1.65f) continue
            val bx = x0 + (minX + maxX + 1) * gridStep / 2f
            val by = y0 + (minY + maxY + 1) * gridStep / 2f
            val radius = diameter * 0.50f

            // White cue ball = large neutral-white area and almost no saturated colour.
            var white = 0
            var saturated = 0
            var total = 0
            val inner = max(4, (radius * 0.75f).toInt())
            val cx = bx.toInt(); val cy = by.toInt()
            for (dy in -inner..inner step 2) for (dx in -inner..inner step 2) {
                if (dx * dx + dy * dy > inner * inner) continue
                val xx = cx + dx; val yy = cy + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val c = pixel(xx, yy)
                val r = c shr 16 and 255
                val g = c shr 8 and 255
                val b = c and 255
                val high = max(r, max(g, b))
                val low = min(r, min(g, b))
                if (low > 175 && high - low < 55) white++
                if (high - low > 90) saturated++
                total++
            }
            if (total == 0) continue
            val whiteness = white.toFloat() / total
            val strongColor = saturated.toFloat() / total
            val cueScore = whiteness - 0.8f * strongColor
            objects.add(Pair(Ball(Pt(bx, by), radius, false), cueScore))
        }
        if (objects.isEmpty()) return emptyList()
        // Most game balls share a radius. Reject tiny prohibition icons and merged blobs.
        val radii = objects.map { it.first.radiusPx }.sorted()
        val medianRadius = radii[radii.size / 2]
        val consistent = objects.filter {
            it.first.radiusPx >= medianRadius * 0.85f && it.first.radiusPx <= medianRadius * 1.4f
        }
        if (consistent.isEmpty()) return emptyList()
        val best = consistent.indices.maxByOrNull { consistent[it].second } ?: return emptyList()
        val cueIndex = if (consistent[best].second >= 0.38f) best else -1
        return consistent.mapIndexed { index, pair ->
            pair.first.copy(isCueBall = index == cueIndex)
        }
    }
}
