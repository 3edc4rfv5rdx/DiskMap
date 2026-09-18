package xx.diskmap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.diskmap.R

/** Three buttons share the bar's width; the stock side padding leaves their labels no room. */
private val BAR_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 0.dp)

/** Below the stock 40dp: the bar is under every list, and the lists want the height. */
private val BAR_BUTTON_HEIGHT = 34.dp
private val BAR_ICON_BUTTON = 32.dp

/** From this width on the bar is one row: the buttons beside the title. */
private val WIDE_BAR = 600.dp

/**
 * The bar under anything items are picked from: what is picked, and Delete,
 * Cancel and To trash. With nothing [active] it shows [hint] instead, but keeps
 * the same height, so the content above it never jumps.
 *
 * [onView], when given, puts an eye button beside the title; its place is kept
 * when it is null.
 */
@Composable
fun ActionBar(
    active: Boolean,
    title: String,
    detail: String,
    hint: String,
    canDelete: Boolean,
    canTrash: Boolean,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onTrash: () -> Unit,
    onView: (() -> Unit)? = null,
    detailIsWarning: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
            val picked = @Composable { m: Modifier ->
                Row(m, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (detailIsWarning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { onView?.invoke() },
                        enabled = active && onView != null,
                        modifier = Modifier.size(BAR_ICON_BUTTON).alpha(if (onView != null) 1f else 0f),
                    ) {
                        Icon(Icons.Outlined.Visibility, stringResource(R.string.view_file))
                    }
                }
            }
            val buttons = @Composable { m: Modifier ->
                Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val button = Modifier.weight(1f).height(BAR_BUTTON_HEIGHT)
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = active && canDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = BAR_BUTTON_PADDING,
                        modifier = button,
                    ) { Text(stringResource(R.string.delete), maxLines = 1) }
                    FilledTonalButton(
                        onClick = onCancel,
                        enabled = active,
                        contentPadding = BAR_BUTTON_PADDING,
                        modifier = button,
                    ) { Text(stringResource(R.string.cancel), maxLines = 1) }
                    Button(
                        onClick = onTrash,
                        enabled = active && canTrash,
                        contentPadding = BAR_BUTTON_PADDING,
                        modifier = button,
                    ) { Text(stringResource(R.string.to_trash), maxLines = 1) }
                }
            }
            // Laid out even when inactive, only invisible and inert then: it is
            // what gives the bar its height. Wide enough, as a phone on its
            // side, the buttons go beside the title instead of under it.
            val shown = Modifier.alpha(if (active) 1f else 0f)
            if (maxWidth >= WIDE_BAR) {
                Row(
                    shown,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    picked(Modifier.weight(1f))
                    buttons(Modifier.weight(1f))
                }
            } else {
                Column(shown) {
                    picked(Modifier)
                    buttons(Modifier.fillMaxWidth().padding(top = 2.dp))
                }
            }
            if (!active) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The one question asked before anything is deleted for good. Backing out
 * lets go of the picked items too, through [onDismiss].
 */
@Composable
fun DeleteForeverDialog(summary: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.delete_forever) + "?",
        message = summary,
        confirmText = stringResource(R.string.delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** One item by its name, several by their count. */
@Composable
fun pickedTitle(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> labeled(R.string.selected_count, names.size)
}
