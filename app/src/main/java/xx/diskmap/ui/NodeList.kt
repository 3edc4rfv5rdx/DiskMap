package xx.diskmap.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.diskmap.Node
import xx.diskmap.LARGEST_LIMIT
import xx.diskmap.R
import xx.diskmap.colorSlot
import xx.diskmap.largestFiles

/**
 * The children of [folder], largest first; taps as in [tapItem], and holding
 * a row or tapping its colour strip or icon selects it. Under the rings it doubles as their legend: each row wears its
 * item's colour.
 *
 * With [largest], the [LARGEST_LIMIT] largest files anywhere under [folder]
 * instead, each with the folder it sits in.
 */
@Composable
fun NodeList(
    folder: Node,
    treeVersion: Int,
    selection: List<Node>,
    onOpen: (Node) -> Unit,
    onToggle: (Node) -> Unit,
    onView: (Node) -> Unit,
    modifier: Modifier = Modifier,
    largest: Boolean = false,
) {
    val colors = chartColors()
    // Read so a change inside the tree recomposes the list.
    val kids = remember(folder, treeVersion, largest) {
        if (largest) largestFiles(folder) else folder.children
    }
    if (kids.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier) {
        // Names repeat across folders, so the flat list is keyed by path.
        itemsIndexed(kids, key = { _, n -> if (largest) n.path else n.name }) { i, node ->
            NodeRow(
                node = node,
                location = if (largest) locationIn(folder, node) else null,
                size = node.size,
                whole = folder.size,
                color = colors.fill(colorSlot(i)),
                selected = selection.holds(node),
                onClick = { tapItem(node, onOpen, onView) },
                onSelect = { onToggle(node) },
            )
        }
    }
}

/** The folder [node] sits in, relative to [folder]; null when it is [folder] itself. */
fun locationIn(folder: Node, node: Node): String? =
    node.parent?.path?.removePrefix(folder.path)?.removePrefix("/")?.takeIf { it.isNotEmpty() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodeRow(
    node: Node,
    // A second line saying where the item is, for lists that mix folders.
    location: String?,
    // Passed apart from the node: the same Node instance changes size in place,
    // and a skipped row would keep showing the old one.
    size: Long,
    whole: Long,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    // Holding the row or tapping its colour strip or icon, the same for every row.
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val share = if (whole > 0) (size.toFloat() / whole).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The colour strip and the icon are one target that selects.
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The colour the item wears in the charts.
            Box(
                Modifier
                    .width(8.dp)
                    .height(36.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            // A selected row trades its type icon for a check.
            Icon(
                imageVector = when {
                    selected -> Icons.Filled.CheckCircle
                    node.isDir -> Icons.Outlined.Folder
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(COMPACT_BUTTON)
                    .padding(9.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = when {
                        selected -> FontWeight.ExtraBold
                        node.isDir -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatSize(context, size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    // The two ends of a path say the most: where it starts and the folder itself.
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(3.dp))
                ) {
                    if (share > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(share)
                                .fillMaxHeight()
                                .background(color, RoundedCornerShape(3.dp))
                        )
                    }
                }
                Text(
                    text = formatPercent(size, whole),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).width(52.dp),
                    maxLines = 1,
                )
            }
        }
        if (node.isDir) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }
    }
}
