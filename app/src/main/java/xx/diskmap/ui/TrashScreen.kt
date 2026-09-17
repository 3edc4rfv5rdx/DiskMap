package xx.diskmap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = { purgeId = entry.id }, enabled = !busy) {
                            Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
                        }
                        FilledTonalButton(onClick = { onRestore(entry) }, enabled = !busy) {
                            Text(stringResource(R.string.restore))
                        }
                    }
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
