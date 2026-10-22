package com.lalit.jigsaw

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

enum class Difficulty(val cols: Int, val rows: Int, val label: String) {
    EASY(3, 3, "Easy"),
    MEDIUM(4, 4, "Medium"),
    HARD(5, 5, "Hard"),
    EXPERT(6, 6, "Expert");

    val count: Int get() = cols * rows
    val shape: String get() = "$cols × $rows"

    companion object {
        fun of(index: Int): Difficulty = values()[index.coerceIn(0, values().size - 1)]
        fun ofName(name: String?): Difficulty = values().firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * A single piece. Positions are kept in "unit board space": the board is a
 * 1x1 square, a piece's cell origin sits at (col/cols, row/rows) when solved.
 * Using unit space means a rotation or resize never invalidates the game state.
 */
class Piece(val col: Int, val row: Int, val cols: Int, val rows: Int) {
    var x = 0f
    var y = 0f
    var placed = false
    var z = 0

    /** Outline in pixels, relative to the cell's top-left corner. */
    val path = Path()

    /** Destination rect for the whole picture, in the same local space. */
    val dst = RectF()

    /** Bounding box of [path] in unit space, relative to the cell origin. */
    val touch = RectF()

    /** Pixel-space outline as a region, for accurate touch picking. */
    val hit = Region()

    val correctX: Float get() = col.toFloat() / cols
    val correctY: Float get() = row.toFloat() / rows
}

/**
 * Builds interlocking outlines. Every internal boundary is one shared polyline:
 * the two pieces that meet there walk it in opposite directions, so their edges
 * are exact complements.
 */
class BoardGeometry(val cols: Int, val rows: Int, val size: Float, seed: Long) {

    val cellW = size / cols
    val cellH = size / rows
    val tabUnit = min(cellW, cellH)
    val pieces = ArrayList<Piece>(cols * rows)

    private val hb = Array(rows + 1) { IntArray(cols) }
    private val vb = Array(rows) { IntArray(cols + 1) }

    init {
        val rnd = Random(seed)
        for (r in 1 until rows) for (c in 0 until cols) hb[r][c] = if (rnd.nextBoolean()) 1 else -1
        for (r in 0 until rows) for (c in 1 until cols) vb[r][c] = if (rnd.nextBoolean()) 1 else -1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                pieces.add(buildPiece(r, c))
            }
        }
    }

    private fun buildPiece(r: Int, c: Int): Piece {
        val piece = Piece(c, r, cols, rows)
        val ax = c * cellW
        val ay = r * cellH
        val bx = ax + cellW
        val by = ay + cellH
        val ring = ArrayList<Float>(640)

        fun edge(x0: Float, y0: Float, x1: Float, y1: Float, sign: Int) {
            val pts = boundary(x0, y0, x1, y1, sign, tabUnit)
            var i = 0
            while (i < pts.size) {
                val x = pts[i]
                val y = pts[i + 1]
                if (ring.isEmpty() || abs(ring[ring.size - 2] - x) > 0.02f || abs(ring[ring.size - 1] - y) > 0.02f) {
                    ring.add(x)
                    ring.add(y)
                }
                i += 2
            }
        }

        edge(ax, ay, bx, ay, if (r == 0) 0 else -hb[r][c])                     // top
        edge(bx, ay, bx, by, if (c == cols - 1) 0 else -vb[r][c + 1])          // right
        edge(bx, by, ax, by, if (r == rows - 1) 0 else hb[r + 1][c])           // bottom
        edge(ax, by, ax, ay, if (c == 0) 0 else vb[r][c])                      // left

        var i = 0
        while (i < ring.size) {
            val x = ring[i] - ax
            val y = ring[i + 1] - ay
            if (i == 0) piece.path.moveTo(x, y) else piece.path.lineTo(x, y)
            i += 2
        }
        piece.path.close()

        val bounds = RectF()
        piece.path.computeBounds(bounds, true)
        val clipPad = size.toInt() * 2
        piece.hit.setPath(piece.path, Region(-clipPad, -clipPad, clipPad, clipPad))
        piece.touch.set(bounds.left / size, bounds.top / size, bounds.right / size, bounds.bottom / size)
        piece.dst.set(-ax, -ay, size - ax, size - ay)
        return piece
    }

    /** Polyline from A to B, optionally carrying a tab. sign 0 = plain edge. */
    private fun boundary(ax: Float, ay: Float, bx: Float, by: Float, sign: Int, u: Float): FloatArray {
        if (sign == 0) return floatArrayOf(ax, ay, bx, by)
        val dx = bx - ax
        val dy = by - ay
        val len = hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val nx = -uy
        val ny = ux
        val tab = tabProfile(u)
        val half = HALF[HALF.size - 2] * u
        val start = len / 2f - half
        val out = ArrayList<Float>(tab.size + 6)
        out.add(ax + ux * start)
        out.add(ay + uy * start)
        var i = 0
        while (i < tab.size) {
            val tx = tab[i]
            val ty = tab[i + 1]
            val d = start + tx + half
            out.add(ax + ux * d + nx * sign * ty)
            out.add(ay + uy * d + ny * sign * ty)
            i += 2
        }
        out.add(bx)
        out.add(by)
        return out.toFloatArray()
    }

    companion object {
        /**
         * Half of the tab outline, in units of [u]: distance along the edge from
         * the tab centre, and protrusion out of the edge. Mirrored to build the
         * full tab. The narrow neck and round head are what make it read as a
         * real jigsaw piece.
         */
        private val HALF = floatArrayOf(
            0.000f, 0.255f,
            0.075f, 0.245f,
            0.125f, 0.195f,
            0.128f, 0.135f,
            0.085f, 0.095f,
            0.115f, 0.045f,
            0.165f, 0.000f
        )

        private fun tabProfile(u: Float): FloatArray {
            val pts = ArrayList<Float>(28)
            for (i in 6 downTo 1) {
                pts.add(-HALF[i * 2] * u)
                pts.add(HALF[i * 2 + 1] * u)
            }
            pts.add(HALF[0] * u)
            pts.add(HALF[1] * u)
            for (i in 1..6) {
                pts.add(HALF[i * 2] * u)
                pts.add(HALF[i * 2 + 1] * u)
            }
            return catmull(pts.toFloatArray(), 4)
        }

        private fun catmull(pts: FloatArray, perSeg: Int): FloatArray {
            val n = pts.size / 2
            val out = ArrayList<Float>((n - 1) * perSeg * 2 + 2)
            for (i in 0 until n - 1) {
                val i0 = (i - 1).coerceIn(0, n - 1)
                val i1 = i
                val i2 = i + 1
                val i3 = (i + 2).coerceIn(0, n - 1)
                val p0x = pts[2 * i0]; val p0y = pts[2 * i0 + 1]
                val p1x = pts[2 * i1]; val p1y = pts[2 * i1 + 1]
                val p2x = pts[2 * i2]; val p2y = pts[2 * i2 + 1]
                val p3x = pts[2 * i3]; val p3y = pts[2 * i3 + 1]
                for (s in 0 until perSeg) {
                    val t = s.toFloat() / perSeg
                    val t2 = t * t
                    val t3 = t2 * t
                    out.add(0.5f * (2 * p1x + (-p0x + p2x) * t +
                        (2 * p0x - 5 * p1x + 4 * p2x - p3x) * t2 +
                        (-p0x + 3 * p1x - 3 * p2x + p3x) * t3))
                    out.add(0.5f * (2 * p1y + (-p0y + p2y) * t +
                        (2 * p0y - 5 * p1y + 4 * p2y - p3y) * t2 +
                        (-p0y + 3 * p1y - 3 * p2y + p3y) * t3))
                }
            }
            out.add(pts[2 * (n - 1)])
            out.add(pts[2 * (n - 1) + 1])
            return out.toFloatArray()
        }
    }
}
