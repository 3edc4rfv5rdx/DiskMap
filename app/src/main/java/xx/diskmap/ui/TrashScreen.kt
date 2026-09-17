package xx.diskmap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.diskmap.R
import xx.diskmap.Trash
import java.text.DateFormat
import java.util.Date

/** What the trash holds, with restore and delete-for-good per item, and one button to empty it. */
@Composable
fun TrashScreen(
    entries: List<Trash.Entry>?,
    busy: Boolean,
    onRestore: (Trash.Entry) -> Unit,
    onPurge: (Trash.Entry) -> Unit,
    onEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // By id: an entry object does not survive a reload or the activity being recreated.
    var purgeId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }

    if (entries == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.trash_empty_state), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatSize(context, entries.sumOf { it.size }),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            DialogConfirmButton(stringResource(R.string.empty_trash), danger = true, enabled = !busy) {
                confirmEmpty = true
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatSize(context, entry.size), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = entry.originalPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = dateFormat.format(Date(entry.deletedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EntryMenu(
                        enabled = !busy,
                        onRestore = { onRestore(entry) },
                        onPurge = { purgeId = entry.id },
                    )
                }
                HorizontalDivider()
            }
        }
    }

    entries.firstOrNull { it.id == purgeId }?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.delete_forever),
            message = entry.item.name + " (" + formatSize(context, entry.size) + ")",
            confirmText = stringResource(R.string.delete),
            onDismiss = { purgeId = null },
            onConfirm = {
                purgeId = null
                onPurge(entry)
            },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.empty_trash),
            message = stringResource(R.string.empty_trash_confirm) + ".",
            confirmText = stringResource(R.string.delete),
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                confirmEmpty = false
                onEmpty()
            },
        )
    }
}

/** The actions on one trash entry, behind its ⋮ button. */
@Composable
private fun EntryMenu(enabled: Boolean, onRestore: () -> Unit, onPurge: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
        }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.restore)) },
                onClick = {
                    open = false
                    onRestore()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onPurge()
                },
            )
        }
    }
}
