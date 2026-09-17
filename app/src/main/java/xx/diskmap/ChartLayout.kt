package xx.diskmap

import kotlin.math.max
import kotlin.math.min

/** How many of a folder's largest children get a colour of their own; the rest are "other". */
const val COLOR_SLOTS = 8

/** Colour slot of the child at [index] among its siblings, or -1 for "other". */
fun colorSlot(index: Int): Int = if (index < COLOR_SLOTS) index else -1

// ---------- Treemap ----------

class TreemapCell(
    /** Null for the cell that stands for everything too small to draw. */
    val node: Node?,
    val slot: Int,
    val size: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/** The most cells a treemap draws before folding the rest into one. */
const val TREEMAP_MAX_CELLS = 40

/**
 * The children of [folder] as squarified rectangles filling [width] x [height].
 * Children past [TREEMAP_MAX_CELLS] fold into one cell with a null node.
 */
fun treemapCells(folder: Node, width: Float, height: Float): List<TreemapCell> {
    val kids = folder.children.filter { it.size > 0 }
    val shown = kids.take(TREEMAP_MAX_CELLS)
    val rest = kids.drop(TREEMAP_MAX_CELLS).sumOf { it.size }
    val items = ArrayList<Triple<Node?, Int, Long>>(shown.size + 1)
    shown.forEachIndexed { i, n -> items.add(Triple(n, colorSlot(i), n.size)) }
    if (rest > 0) items.add(Triple(null, -1, rest))
    val rects = squarify(items.map { it.third.toDouble() }, width, height)
    return items.zip(rects) { (node, slot, size), r ->
        TreemapCell(node, slot, size, r[0], r[1], r[2], r[3])
    }
}

/**
 * Squarified treemap (Bruls, Huizing, van Wijk). [values] must be sorted
 * largest first; the result is one [left, top, right, bottom] per value, in
 * the same order. Zero or negative values get an empty rectangle.
 */
fun squarify(values: List<Double>, width: Float, height: Float): List<FloatArray> {
    val total = values.sumOf { max(it, 0.0) }
    if (total <= 0.0 || width <= 0f || height <= 0f) return values.map { FloatArray(4) }
    val scale = width.toDouble() * height / total
    val areas = values.map { max(it, 0.0) * scale }
    val out = ArrayList<FloatArray>(values.size)

    var x = 0.0
    var y = 0.0
    var w = width.toDouble()
    var h = height.toDouble()
    var start = 0
    while (start < areas.size) {
        if (areas[start] <= 0.0) {
            out.add(floatArrayOf(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat()))
            start++
            continue
        }
        val side = min(w, h)
        var end = start + 1
        var rowSum = areas[start]
        while (end < areas.size && areas[end] > 0.0 &&
            worst(areas, start, end + 1, rowSum + areas[end], side) <= worst(areas, start, end, rowSum, side)
        ) {
            rowSum += areas[end]
            end++
        }
        // The last row takes whatever is left, so rounding never leaves a gap.
        val isLast = end >= areas.size || areas.subList(end, areas.size).all { it <= 0.0 }
        if (w >= h) {
            val thick = if (isLast) w else rowSum / h
            var cy = y
            for (i in start until end) {
                val len = if (i == end - 1) y + h - cy else areas[i] / thick
                out.add(floatArrayOf(x.toFloat(), cy.toFloat(), (x + thick).toFloat(), (cy + len).toFloat()))
                cy += len
            }
            x += thick
            w -= thick
        } else {
            val thick = if (isLast) h else rowSum / w
            var cx = x
            for (i in start until end) {
                val len = if (i == end - 1) x + w - cx else areas[i] / thick
                out.add(floatArrayOf(cx.toFloat(), y.toFloat(), (cx + len).toFloat(), (y + thick).toFloat()))
                cx += len
            }
            y += thick
            h -= thick
        }
        start = end
    }
    return out
}

/** The worst aspect ratio in areas[from until to] laid along [side]. */
private fun worst(areas: List<Double>, from: Int, to: Int, sum: Double, side: Double): Double {
    var hi = 0.0
    var lo = Double.MAX_VALUE
    for (i in from until to) {
        hi = max(hi, areas[i])
        lo = min(lo, areas[i])
    }
    val s2 = side * side
    val sum2 = sum * sum
    return max(s2 * hi / sum2, sum2 / (s2 * lo))
}

// ---------- Sunburst ----------

class SunburstArc(
    val node: Node,
    /** 1 for the ring next to the centre. */
    val depth: Int,
    /** Degrees clockwise from twelve o'clock. */
    val start: Float,
    val sweep: Float,
    /** The slot of the centre's child this arc lies under, -1 for "other". */
    val slot: Int,
)

/** Rings drawn around the centre. */
const val SUNBURST_DEPTH = 2

/** Arcs narrower than this are not drawn: they could not be seen or tapped. */
const val SUNBURST_MIN_SWEEP = 0.75f

/** The rings around [center], [SUNBURST_DEPTH] deep. */
fun sunburstArcs(center: Node): List<SunburstArc> {
    val out = ArrayList<SunburstArc>()
    if (center.size <= 0) return out
    val degPerByte = 360.0 / center.size
    fun add(node: Node, depth: Int, start: Double, slot: Int) {
        var angle = start
        node.children.forEachIndexed { i, child ->
            val sweep = child.size * degPerByte
            val childSlot = if (depth == 1) colorSlot(i) else slot
            if (sweep >= SUNBURST_MIN_SWEEP) {
                out.add(SunburstArc(child, depth, angle.toFloat(), sweep.toFloat(), childSlot))
                if (depth < SUNBURST_DEPTH && child.isDir) add(child, depth + 1, angle, childSlot)
            }
            angle += sweep
        }
    }
    add(center, 1, 0.0, -1)
    return out
}

/** The arc in ring [depth] at [angle] degrees, if any. */
fun sunburstHit(arcs: List<SunburstArc>, depth: Int, angle: Float): SunburstArc? =
    arcs.firstOrNull { it.depth == depth && angle >= it.start && angle < it.start + it.sweep }
