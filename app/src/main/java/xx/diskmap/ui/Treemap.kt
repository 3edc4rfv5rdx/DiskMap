package xx.diskmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xx.diskmap.Node
import xx.diskmap.R
import xx.diskmap.treemapCells

/** The children of [folder] as rectangles sized by bytes; taps as in [tapItem], hold to select. */
@Composable
fun Treemap(
    folder: Node,
    treeVersion: Int,
    selection: List<Node>,
    onOpen: (Node) -> Unit,
    onToggle: (Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = chartColors()
    val outline = MaterialTheme.colorScheme.onSurface
    val otherLabel = stringResource(R.string.other)
    val measurer = rememberTextMeasurer()
    val selecting = selection.isNotEmpty()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val cells = remember(folder, treeVersion, canvasSize) {
        treemapCells(folder, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(cells, selecting) {
                fun nodeAt(p: Offset): Node? = cells.firstOrNull { it.contains(p.x, p.y) }?.node
                detectTapGestures(
                    onTap = { p ->
                        val node = nodeAt(p) ?: return@detectTapGestures
                        tapItem(node, selecting, onOpen, onToggle)
                    },
                    onLongPress = { p -> nodeAt(p)?.let(onToggle) },
                )
            }
    ) {
        val gap = 2.dp.toPx()
        val pad = 6.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        val framePadX = 4.dp.toPx()
        val hasSelection = cells.any { c -> c.node != null && selection.holds(c.node) }

        for (cell in cells) {
            val w = cell.right - cell.left - gap
            val h = cell.bottom - cell.top - gap
            if (w <= 0f || h <= 0f) continue
            val topLeft = Offset(cell.left + gap / 2, cell.top + gap / 2)
            val fill = colors.fill(cell.slot)
            val picked = cell.node != null && selection.holds(cell.node)
            val alpha = if (!hasSelection || picked) 1f else DIMMED_ALPHA
            drawRoundRect(
                color = fill,
                topLeft = topLeft,
                size = Size(w, h),
                cornerRadius = radius,
                alpha = alpha,
            )
            if (picked) {
                val stroke = SELECTION_OUTLINE.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = topLeft + Offset(stroke / 2, stroke / 2),
                    size = Size(w - stroke, h - stroke),
                    cornerRadius = radius,
                    style = Stroke(width = stroke),
                )
            }

            // Direct labels, where the cell is big enough to hold one.
            val textWidth = (w - pad * 2).toInt()
            if (textWidth < 24.dp.toPx() || h < 22.dp.toPx()) continue
            // Judged on the tile as drawn: a dimmed one is mostly the surface.
            val ink = inkOn(fill.copy(alpha = alpha).compositeOver(colors.surface))
            val name = cell.node?.name ?: otherLabel
            val title = measurer.measure(
                text = name,
                style = TextStyle(
                    color = ink,
                    fontSize = 14.sp,
                    fontWeight = when {
                        picked -> FontWeight.ExtraBold
                        cell.node?.isDir == true -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                ),
                constraints = Constraints(maxWidth = textWidth),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A folder's size is framed, a file's is not; the frame takes its
            // inset out of the text's width.
            val folder = cell.node?.isDir == true
            val frameInset = if (folder) framePadX else 0f
            val sizeText = measurer.measure(
                text = formatSize(context, cell.size),
                style = TextStyle(color = ink, fontSize = 12.sp),
                constraints = Constraints(maxWidth = (textWidth - frameInset * 2).toInt().coerceAtLeast(0)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            clipRect(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h) {
                drawText(title, topLeft = topLeft + Offset(pad, pad / 2))
                if (h > title.size.height + sizeText.size.height + pad) {
                    val sizeAt = topLeft + Offset(pad + frameInset, pad / 2 + title.size.height)
                    drawText(sizeText, topLeft = sizeAt)
                    if (folder) {
                        val frameHeight = sizeText.size.height.toFloat()
                        drawRoundRect(
                            color = ink,
                            topLeft = sizeAt - Offset(frameInset, 0f),
                            size = Size(sizeText.size.width + frameInset * 2, frameHeight),
                            cornerRadius = CornerRadius(frameHeight / 2),
                            style = Stroke(width = FOLDER_FRAME.toPx()),
                        )
                    }
                }
            }
        }
    }
}
