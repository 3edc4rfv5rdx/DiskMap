package xx.diskmap.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xx.diskmap.ACCENT_COUNT
import xx.diskmap.Node
import xx.diskmap.Notice
import xx.diskmap.R
import xx.diskmap.ThemeMode
import java.text.NumberFormat
import java.util.Locale

// ---------- Theme ----------

// The window is a distinct tone from the containers, so dialogs and cards stand
// out against the screen behind them. The same values as in Steps.
val WindowLight = Color(0xFFF1F2F4)
val WindowDark = Color(0xFF121212)
private val TonalButtonDark = Color(0xFF5A5A5A)

private val LightColors = lightColorScheme(
    background = WindowLight,
    surface = WindowLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val DarkColors = darkColorScheme(
    background = WindowDark,
    surface = WindowDark,
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF1F1F1F),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF303030),
    // Tonal buttons must read as buttons against the dark window.
    secondaryContainer = TonalButtonDark,
    onSecondaryContainer = Color.White,
)

/**
 * Accent choices, the same six as in Steps: mid-tones that hold contrast on
 * both windows and carry white text as a button fill.
 */
val AccentPalette = listOf(
    Color(0xFF00897B), // teal
    Color(0xFF1E88E5), // blue
    Color(0xFF5C6BC0), // indigo
    Color(0xFF8E24AA), // purple
    Color(0xFFEF6C00), // orange
    Color(0xFFE53935), // red
).also { check(it.size == ACCENT_COUNT) }

fun accentAt(index: Int): Color = AccentPalette[index.coerceIn(AccentPalette.indices)]

/** Free of Compose, so the activity can ask it before the first frame. */
fun isDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun DiskMapTheme(themeMode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode, isSystemInDarkTheme())
    val scheme = (if (dark) DarkColors else LightColors).copy(
        primary = accentAt(accentIndex),
        onPrimary = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Chart colours ----------

// Eight categorical hues in a fixed order, validated for colour-blind
// separation on the light and the dark surface respectively. They name the
// largest children of a folder and are independent of the accent.
private val SERIES_LIGHT = listOf(
    0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100,
    0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948,
).map { Color(it) }
private val SERIES_DARK = listOf(
    0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500,
    0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767,
).map { Color(it) }

class ChartColors(
    private val series: List<Color>,
    val other: Color,
    val surface: Color,
) {
    /** The fill for [slot] (-1 is "other"), faded toward the surface ring by ring. */
    fun fill(slot: Int, depth: Int = 1): Color {
        val base = series.getOrNull(slot) ?: other
        return lerp(base, surface, ((depth - 1) * 0.28f).coerceIn(0f, 0.7f))
    }
}

/**
 * Black or white, whichever reads better on [fill]. By the WCAG ratio the two
 * tie near 0.18 luminance, but that puts black on the mid blues, where white
 * reads better to the eye; the switch is set higher, so black goes only on
 * the plainly bright colours.
 */
fun inkOn(fill: Color): Color = if (fill.luminance() > INK_CROSSOVER) Color.Black else Color.White

private const val INK_CROSSOVER = 0.25f

@Composable
fun chartColors(): ChartColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return ChartColors(
        series = if (dark) SERIES_DARK else SERIES_LIGHT,
        other = scheme.outline,
        surface = scheme.surface,
    )
}

// ---------- Text ----------

/** A size the way the system writes it, in the user's units. */
fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

/** [part] as a share of [whole], one decimal. */
fun formatPercent(part: Long, whole: Long): String =
    if (whole <= 0) "0%" else String.format(Locale.getDefault(), "%.1f%%", part * 100.0 / whole)

/** "Label: value", the one way a label and its value are joined. */
fun labelValue(label: String, value: Any): String = "$label: $value"

@Composable
fun labeled(@StringRes label: Int, value: Any): String = labelValue(stringResource(label), value)

/** Parts of one line with the one separator between them; empty parts are left out. */
fun dotted(vararg parts: String?): String = parts.filterNot { it.isNullOrEmpty() }.joinToString("  ·  ")

fun noticeText(context: Context, notice: Notice): String {
    val head = context.getString(notice.text)
    val tail = notice.bytes?.let { formatSize(context, it) } ?: notice.detail
    return if (tail.isNullOrBlank()) head else labelValue(head, tail)
}

// ---------- Selection ----------

/** How faint the other marks of a chart go while something in it is selected. */
const val DIMMED_ALPHA = 0.25f

/** The outline a chart draws around a selected mark. */
val SELECTION_OUTLINE = 4.dp

fun List<Node>.holds(node: Node): Boolean = any { it === node }

/**
 * What a plain tap on an item does, the same in every view: while anything is
 * selected it adds or removes the item, otherwise a folder opens and a file is
 * selected.
 */
fun tapItem(node: Node, selecting: Boolean, onOpen: (Node) -> Unit, onToggle: (Node) -> Unit) {
    if (node.isDir && !selecting) onOpen(node) else onToggle(node)
}

/** "Files: N", the one way a file count is written. */
@Composable
fun filesLabel(count: Long): String = labeled(R.string.files, formatCount(count))

/** The frame around a folder's size on the rings and the tiles, which tells it from a file. */
val FOLDER_FRAME = 1.dp

/** An icon button smaller than Material's 48dp, for rows that must stay low. */
val COMPACT_BUTTON = 40.dp

/** The ⋮ button that opens an [AppMenu]. */
@Composable
fun MoreButton(onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
    }
}

// ---------- Dialog pieces ----------

private val DIALOG_BUTTON_PADDING = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
fun DialogConfirmButton(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = DIALOG_BUTTON_PADDING,
        colors = if (danger) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = DIALOG_BUTTON_PADDING) {
        Text(text, maxLines = 1)
    }
}

/**
 * Every drop-down menu in the app: a raised card with an outline, so it
 * stands off the screen behind it in both themes.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = scheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, scheme.outline),
        content = content,
    )
}

/** Yes/no for something that cannot be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { DialogConfirmButton(confirmText, danger = true, onClick = onConfirm) },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** Single choice from a labelled list; scrolls, since the language list can grow. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onPick(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** One accent colour, ringed when it is the one in force. */
@Composable
fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
