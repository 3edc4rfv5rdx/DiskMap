package xx.diskmap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xx.diskmap.DiskMapViewModel
import xx.diskmap.R
import xx.diskmap.colorSlot
import java.text.DateFormat
import java.util.Date

/**
 * Groups of identical files under the folder the search started from. A tap
 * picks a copy, its icon opens it; the bar deletes or trashes what is picked,
 * but never every copy of a group.
 */
@Composable
fun DuplicatesScreen(vm: DiskMapViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = chartColors()
    val viewer = fileViewer(vm)
    val groups = vm.dupGroups
    val picked = vm.dupPicked
    var confirm by remember { mutableStateOf(false) }
    // Read so a delete, which changes the tree, redraws the rows.
    val version = vm.treeVersion
    val scope = remember(vm.dupScope, version) { vm.dupScope?.let { vm.root?.find(it) } }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }

    Column(modifier) {
        when {
            groups == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (vm.dupSearching) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.dup_searching))
                        Text(
                            text = labeled(R.string.dup_read, formatSize(context, vm.dupBytesRead)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            groups.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.dup_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dotted(
                            labeled(R.string.dup_groups, groups.size),
                            labeled(R.string.dup_freeable, formatSize(context, groups.sumOf { it.wasted })),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = vm::pickAllButOne) {
                        Text(stringResource(R.string.keep_one), maxLines = 1)
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    groups.forEachIndexed { index, group ->
                        item(key = "group:" + group.copies[0].path) {
                            Text(
                                text = formatSize(context, group.size) + " × " + group.copies.size,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(group.copies, key = { it.path }) { copy ->
                            // A copy deleted from elsewhere has no node; its row goes.
                            val node = vm.root?.find(copy.path) ?: return@items
                            val where = scope?.let { locationIn(it, node) }
                            NodeRow(
                                node = node,
                                location = dotted(where, dateFormat.format(Date(copy.modified))),
                                size = copy.size,
                                whole = scope?.size ?: 0L,
                                color = colors.fill(colorSlot(index)),
                                selected = copy.path in picked,
                                onClick = { viewer(copy.path) },
                                onSelect = { vm.toggleDuplicate(copy.path) },
                            )
                        }
                    }
                }
            }
        }

        val pickedCopies = groups.orEmpty().flatMap { it.copies }.filter { it.path in picked }
        val wipes = vm.wouldWipeAGroup()
        val canDelete = pickedCopies.isNotEmpty() && !wipes && !vm.busy && !vm.scanning
        ActionBar(
            active = pickedCopies.isNotEmpty(),
            title = pickedTitle(pickedCopies.map { it.name }),
            detail = if (wipes) {
                stringResource(R.string.dup_keep_warning)
            } else {
                formatSize(context, pickedCopies.sumOf { it.size })
            },
            detailIsWarning = wipes,
            hint = stringResource(R.string.dup_hint),
            canDelete = canDelete,
            canTrash = canDelete,
            onDelete = { confirm = true },
            onCancel = vm::clearDuplicates,
            onTrash = { vm.deleteDuplicates(toTrash = true) },
            onView = pickedCopies.singleOrNull()?.let { copy -> { viewer(copy.path) } },
        )

        if (confirm) {
            DeleteForeverDialog(
                summary = dotted(
                    pickedTitle(pickedCopies.map { it.name }),
                    formatSize(context, pickedCopies.sumOf { it.size }),
                ),
                onDismiss = {
                    confirm = false
                    vm.clearDuplicates()
                },
                onConfirm = {
                    confirm = false
                    vm.deleteDuplicates(toTrash = false)
                },
            )
        }
    }
}
