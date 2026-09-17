package xx.diskmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class ChartLayoutTest {

    private fun leaf(name: String, size: Long) = Node(name, isDir = false, size = size, files = 1)

    private fun dir(name: String, vararg kids: Node) =
        Node(name, isDir = true, size = kids.sumOf { it.size }, files = kids.sumOf { it.files })
            .apply { setChildren(kids.toList()) }

    private fun area(r: FloatArray) = (r[2] - r[0]) * (r[3] - r[1])

    @Test
    fun squarifyFillsTheRectangleInProportion() {
        val values = listOf(6.0, 6.0, 4.0, 3.0, 2.0, 2.0, 1.0)
        val rects = squarify(values, 600f, 400f)
        val total = values.sum()

        assertEquals(values.size, rects.size)
        rects.forEachIndexed { i, r ->
            assertEquals(values[i] / total * 240_000, area(r).toDouble(), 1.0)
            assertTrue(r[0] >= -0.01f && r[1] >= -0.01f && r[2] <= 600.01f && r[3] <= 400.01f)
        }
        assertEquals(240_000.0, rects.sumOf { area(it).toDouble() }, 1.0)
    }

    @Test
    fun squarifyKeepsCellsReasonablySquare() {
        val rects = squarify(List(16) { 1.0 }, 400f, 400f)
        val worst = rects.maxOf { r ->
            val w = r[2] - r[0]
            val h = r[3] - r[1]
            max(w / h, h / w)
        }
        assertTrue("worst aspect $worst", worst < 2f)
    }

    @Test
    fun squarifyGivesZerosEmptyCells() {
        val rects = squarify(listOf(5.0, 0.0, 0.0), 100f, 50f)
        assertEquals(5000f, area(rects[0]), 0.5f)
        assertEquals(0f, area(rects[1]))
        assertEquals(0f, area(rects[2]))
        assertTrue(squarify(listOf(0.0), 100f, 100f).all { area(it) == 0f })
        assertTrue(squarify(listOf(1.0), 0f, 100f).all { area(it) == 0f })
    }

    @Test
    fun treemapFoldsTheTailIntoOneCell() {
        val kids = (0 until TREEMAP_MAX_CELLS + 5).map { leaf("f$it", 100L - it) }
        val cells = treemapCells(dir("root", *kids.toTypedArray()), 800f, 600f)

        assertEquals(TREEMAP_MAX_CELLS + 1, cells.size)
        val other = cells.last()
        assertNull(other.node)
        assertEquals(-1, other.slot)
        assertEquals((TREEMAP_MAX_CELLS until TREEMAP_MAX_CELLS + 5).sumOf { 100L - it }, other.size)
        assertEquals(0, cells[0].slot)
        assertEquals(-1, cells[COLOR_SLOTS].slot)
    }

    @Test
    fun treemapSkipsEmptyChildren() {
        val cells = treemapCells(dir("root", leaf("a", 5), leaf("zero", 0)), 100f, 100f)
        assertEquals(listOf("a"), cells.map { it.node?.name })
    }

    @Test
    fun sunburstAnglesFollowSizes() {
        val deep = dir("d2", leaf("x", 10))
        val root = dir("root", dir("big", deep, leaf("y", 20)), leaf("small", 10))
        val arcs = sunburstArcs(root)

        val big = arcs.first { it.node.name == "big" }
        assertEquals(1, big.depth)
        assertEquals(0f, big.start)
        assertEquals(270f, big.sweep, 0.01f)
        val small = arcs.first { it.node.name == "small" }
        assertEquals(270f, small.start, 0.01f)
        assertEquals(90f, small.sweep, 0.01f)
        assertEquals(1, small.slot)

        // Children start where their parent starts and inherit its colour.
        val y = arcs.first { it.node.name == "y" }
        assertEquals(2, y.depth)
        assertEquals(0f, y.start, 0.01f)
        assertEquals(0, y.slot)
        val d2 = arcs.first { it.node.name == "d2" }
        assertEquals(2, d2.depth)
        assertEquals(180f, d2.start, 0.01f)
        // Two rings: what lies deeper is not drawn.
        assertTrue(arcs.none { it.node.name == "x" })
    }

    @Test
    fun sunburstStopsAtItsDepthAndDropsSlivers() {
        var node = leaf("leaf", 1)
        repeat(SUNBURST_DEPTH + 2) { node = dir("d$it", node) }
        val root = dir("root", node, leaf("huge", 10_000_000))
        val arcs = sunburstArcs(root)

        assertTrue(arcs.all { it.depth <= SUNBURST_DEPTH })
        assertEquals(listOf("huge"), arcs.map { it.node.name })
        assertTrue(sunburstArcs(dir("empty")).isEmpty())
    }

    @Test
    fun largestFilesSearchesTheWholeSubtree() {
        val root = dir(
            "root",
            leaf("a", 5),
            dir("d", leaf("b", 50), dir("e", leaf("c", 30), leaf("tiny", 1))),
            leaf("f", 40),
        )
        assertEquals(listOf("b", "f", "c"), largestFiles(root, 3).map { it.name })
        assertEquals(listOf("b", "f", "c", "a", "tiny"), largestFiles(root, 10).map { it.name })
        assertTrue(largestFiles(root, 0).isEmpty())
        assertTrue(largestFiles(dir("empty")).isEmpty())
        // A file on its own is its own largest file.
        assertEquals(listOf("a"), largestFiles(leaf("a", 5)).map { it.name })
    }

    @Test
    fun sunburstHitFindsTheArc() {
        val root = dir("root", leaf("a", 3), leaf("b", 1))
        val arcs = sunburstArcs(root)
        assertSame(arcs[0].node, sunburstHit(arcs, 1, 10f)?.node)
        assertEquals("b", sunburstHit(arcs, 1, 300f)?.node?.name)
        assertNull(sunburstHit(arcs, 2, 10f))
        assertTrue(abs(arcs.sumOf { it.sweep.toDouble() } - 360.0) < 0.01)
    }
}
