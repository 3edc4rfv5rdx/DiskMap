package xx.diskmap.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.diskmap.Node
import xx.diskmap.R
import xx.diskmap.colorSlot

/**
 * The children of [folder], largest first; taps as in [tapItem], hold to
 * select. Under the rings it doubles as their legend:
 * each row wears its item's colour.
 */
@Composable
fun NodeList(
    folder: Node,
    treeVersion: Int,
    selection: List<Node>,
    onOpen: (Node) -> Unit,
    onToggle: (Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    // Read so a change inside the tree recomposes the list.
    val kids = remember(folder, treeVersion) { folder.children }
    if (kids.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier) {
        itemsIndexed(kids, key = { _, n -> n.name }) { i, node ->
            NodeRow(
                node = node,
                size = node.size,
                whole = folder.size,
                color = colors.fill(colorSlot(i)),
                selected = selection.holds(node),
                onClick = { tapItem(node, selection.isNotEmpty(), onOpen, onToggle) },
                onLongClick = { onToggle(node) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodeRow(
    node: Node,
    // Passed apart from the node: the same Node instance changes size in place,
    // and a skipped row would keep showing the old one.
    size: Long,
    whole: Long,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val share = if (whole > 0) (size.toFloat() / whole).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The colour the item wears in the charts.
        Box(
            Modifier
                .width(8.dp)
                .height(36.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Icon(
            imageVector = if (node.isDir) Icons.Outlined.Folder else Icons.Outlined.Description,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp).size(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (node.isDir) FontWeight.SemiBold else FontWeight.Normal,
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
