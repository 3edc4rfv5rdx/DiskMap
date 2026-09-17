package xx.diskmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xx.diskmap.Node
import xx.diskmap.SUNBURST_DEPTH
import xx.diskmap.SunburstArc
import xx.diskmap.sunburstArcs
import xx.diskmap.sunburstHit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The centre's share of the radius; the rings split the rest evenly. */
private const val HUB_FRACTION = 0.26f

/** Arcs narrower than this get no size label; measuring them would be wasted. */
private const val LABEL_MIN_SWEEP = 4f

/** The shortest arc, at its middle, that still carries a label's dot clearly. */
private val LABEL_MIN_ARC = 14.dp

private class RingGeometry(size: Size) {
    val center = Offset(size.width / 2, size.height / 2)
    val outer = min(size.width, size.height) / 2
    val hub = outer * HUB_FRACTION
    val ring = (outer - hub) / SUNBURST_DEPTH

    /** 0 for the hub, 1..SUNBURST_DEPTH for a ring, -1 outside. */
    fun depthAt(p: Offset): Int {
        val r = hypot(p.x - center.x, p.y - center.y)
        return when {
            r < hub -> 0
            r >= outer -> -1
            else -> 1 + ((r - hub) / ring).toInt().coerceAtMost(SUNBURST_DEPTH - 1)
        }
    }

    /** Degrees clockwise from twelve o'clock. */
    fun angleAt(p: Offset): Float {
        val deg = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat() + 90f
        return (deg + 360f) % 360f
    }
}

/**
 * Rings around [folder]: taps as in [tapItem], hold to select, tap the hub
 * to go up.
 */
@Composable
fun Sunburst(
    folder: Node,
    treeVersion: Int,
    selection: List<Node>,
    onOpen: (Node) -> Unit,
    onToggle: (Node) -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = chartColors()
    val hubColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurface
    val plateColor = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val arcs = remember(folder, treeVersion) { sunburstArcs(folder) }
    val selecting = selection.isNotEmpty()

    Canvas(
        modifier.pointerInput(arcs, selecting) {
            fun arcAt(p: Offset): Pair<Int, SunburstArc?> {
                val g = RingGeometry(Size(size.width.toFloat(), size.height.toFloat()))
                val depth = g.depthAt(p)
                return depth to if (depth > 0) sunburstHit(arcs, depth, g.angleAt(p)) else null
            }
            detectTapGestures(
                onTap = { p ->
                    val (depth, arc) = arcAt(p)
                    when {
                        depth == 0 -> onUp()
                        arc == null -> Unit
                        else -> tapItem(arc.node, selecting, onOpen, onToggle)
                    }
                },
                onLongPress = { p -> arcAt(p).second?.let { onToggle(it.node) } },
            )
        }
    ) {
        val g = RingGeometry(size)
        // A 2px surface gap between neighbours, as an angle at each ring's middle.
        val gapPx = 2.dp.toPx()
        // The part of the selection drawn here; everything outside it is faded.
        val focus = selection.filter { s -> arcs.any { it.node === s } }
        fun alphaOf(node: Node) = if (focus.isEmpty() || focus.any { it.contains(node) }) 1f else DIMMED_ALPHA

        for (arc in arcs) {
            val mid = g.hub + g.ring * (arc.depth - 0.5f)
            val gapDeg = (gapPx / mid * 180f / PI).toFloat()
            val sweep = arc.sweep - gapDeg
            if (sweep <= 0f) continue
            val color = colors.fill(arc.slot, arc.depth)
            val width = g.ring - gapPx
            drawArc(
                color = color,
                startAngle = arc.start - 90f + gapDeg / 2,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(g.center.x - mid, g.center.y - mid),
                size = Size(mid * 2, mid * 2),
                style = Stroke(width = width),
                alpha = alphaOf(arc.node),
            )
            if (selection.holds(arc.node)) {
                val outerR = mid + width / 2
                drawArc(
                    color = outline,
                    startAngle = arc.start - 90f + gapDeg / 2,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(g.center.x - outerR, g.center.y - outerR),
                    size = Size(outerR * 2, outerR * 2),
                    style = Stroke(width = SELECTION_OUTLINE.toPx()),
                )
            }
        }

        drawCircle(hubColor, radius = g.hub - gapPx, center = g.center)
        val box = (g.hub * 1.4f).toInt().coerceAtLeast(1)
        val label = measurer.measure(
            text = formatSize(context, folder.size),
            style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            constraints = Constraints(maxWidth = box),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        drawText(
            label,
            topLeft = Offset(g.center.x - label.size.width / 2, g.center.y - label.size.height / 2),
        )

        // Size labels: a dot on the middle of the arc says which arc the number
        // belongs to, so the number itself may run over its neighbours. It sits
        // on a plate of the window colour to stay readable there. Larger arcs
        // are labelled first, and a label that would cover another is dropped.
        val dotR = 3.5.dp.toPx()
        val dotGap = 3.dp.toPx()
        val platePad = 3.dp.toPx()
        // The hub is taken from the start: no label covers the centre.
        val placed = arrayListOf(Rect(g.center, g.hub))
        for (arc in arcs.sortedByDescending { it.sweep * (it.depth + 1) }) {
            if (arc.sweep < LABEL_MIN_SWEEP) continue
            val mid = g.hub + g.ring * (arc.depth - 0.5f)
            val arcLength = (mid * Math.toRadians(arc.sweep.toDouble())).toFloat()
            if (arcLength < LABEL_MIN_ARC.toPx()) continue
            val theta = Math.toRadians((arc.start + arc.sweep / 2).toDouble())
            val cx = g.center.x + mid * sin(theta).toFloat()
            val cy = g.center.y - mid * cos(theta).toFloat()
            val sizeLabel = measurer.measure(
                text = formatSize(context, arc.node.size),
                style = TextStyle(color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            // Beside the dot on the right, or on the left where the right
            // would leave the canvas.
            val tw = sizeLabel.size.width.toFloat()
            val th = sizeLabel.size.height.toFloat()
            val rightSide = cx + dotR + dotGap + tw + platePad <= size.width
            val textLeft = if (rightSide) cx + dotR + dotGap else cx - dotR - dotGap - tw
            val plate = Rect(textLeft - platePad, cy - th / 2, textLeft + tw + platePad, cy + th / 2)
            // The dot is kept clear as well, or a later plate could hide it.
            val dot = Rect(Offset(cx, cy), dotR)
            if (placed.any { it.overlaps(plate) || it.overlaps(dot) }) continue
            placed.add(plate)
            placed.add(dot)

            val alpha = alphaOf(arc.node)
            // Dark in the light theme, light in the dark one, like the number.
            drawCircle(labelColor, radius = dotR, center = Offset(cx, cy), alpha = alpha)
            drawRoundRect(
                color = plateColor,
                topLeft = plate.topLeft,
                size = plate.size,
                cornerRadius = CornerRadius(th / 2),
                alpha = 0.8f * alpha,
            )
            drawText(sizeLabel, topLeft = Offset(textLeft, cy - th / 2), alpha = alpha)
        }
    }
}
