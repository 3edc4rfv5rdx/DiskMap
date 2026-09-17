package xx.diskmap.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xx.diskmap.AppSettings
import xx.diskmap.DiskMapViewModel
import xx.diskmap.Node
import xx.diskmap.R
import xx.diskmap.ViewMode
import xx.diskmap.topmost

private enum class Screen { MAP, TRASH, SETTINGS }

/** The storage root in the path line; the top bar already names the storage. */
private const val ROOT_CRUMB = "~"

private fun ViewMode.labelRes(): Int = when (this) {
    ViewMode.RINGS -> R.string.view_rings
    ViewMode.TILES -> R.string.view_tiles
    ViewMode.LIST -> R.string.view_list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskMapScreen(onAbout: () -> Unit) {
    val vm: DiskMapViewModel = viewModel()
    val context = LocalContext.current
    val viewMode by AppSettings.viewMode.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
    var menuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    // What the delete-for-good question is about, while it is up.
    var deleteTargets by remember { mutableStateOf<List<Node>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.start() }
    // The trash list lives in the view model; a recreated activity comes back
    // to this screen with it still open, or reloads it.
    LaunchedEffect(screen) {
        if (screen == Screen.TRASH) vm.openTrash() else vm.closeTrash()
    }
    LaunchedEffect(vm.notice) {
        val notice = vm.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(noticeText(context, notice))
        vm.noticeShown()
    }

    val current = vm.current
    val volumeLabel = vm.volume?.label.orEmpty()

    BackHandler(enabled = screen != Screen.MAP || vm.selection.isNotEmpty() || current?.parent != null) {
        when {
            screen != Screen.MAP -> screen = Screen.MAP
            vm.selection.isNotEmpty() -> vm.clearSelection()
            else -> vm.up()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (screen) {
                            Screen.MAP -> volumeLabel.ifEmpty { stringResource(R.string.app_name) }
                            Screen.TRASH -> stringResource(R.string.trash)
                            Screen.SETTINGS -> stringResource(R.string.settings)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (screen != Screen.MAP) {
                        IconButton(onClick = { screen = Screen.MAP }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (screen == Screen.MAP) {
                        Box {
                            IconButton(onClick = { viewMenuOpen = true }) {
                                Icon(Icons.Outlined.BarChart, stringResource(R.string.chart_type))
                            }
                            AppMenu(expanded = viewMenuOpen, onDismissRequest = { viewMenuOpen = false }) {
                                ViewMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes())) },
                                        trailingIcon = {
                                            if (mode == viewMode) Icon(Icons.Filled.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            viewMenuOpen = false
                                            AppSettings.setViewMode(context, mode)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (screen == Screen.MAP) {
                        IconButton(onClick = { screen = Screen.TRASH }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.trash))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                        }
                        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rescan)) },
                                enabled = !vm.busy && vm.volume != null,
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.MAP
                                    vm.rescan()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.SETTINGS
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                onClick = {
                                    menuOpen = false
                                    onAbout()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val body = Modifier.padding(padding).fillMaxSize()
        when (screen) {
            Screen.SETTINGS -> SettingsScreen(
                volumes = vm.volumes,
                volume = vm.volume,
                // Switching mid-operation would pull the tree out from under it.
                onVolume = { if (it !== vm.volume) vm.selectVolume(it) },
                volumeLocked = vm.busy,
                modifier = body,
            )
            Screen.TRASH -> TrashScreen(
                entries = vm.trashEntries,
                busy = vm.busy || vm.scanning,
                onRestore = vm::restore,
                onPurge = vm::purge,
                onEmpty = vm::emptyTrash,
                modifier = body,
            )
            Screen.MAP -> MapBody(
                vm = vm,
                viewMode = viewMode,
                onDelete = { deleteTargets = vm.selection },
                modifier = body,
            )
        }
    }

    // Only deleting for good asks first: the trash can always be undone.
    deleteTargets?.let { targets ->
        val items = topmost(targets)
        ConfirmDialog(
            title = stringResource(R.string.delete_forever) + "?",
            message = selectionTitle(items) + "  ·  " + formatSize(context, items.sumOf { it.size }) +
                "  ·  " + stringResource(R.string.files) + ": " + formatCount(items.sumOf { it.files }),
            confirmText = stringResource(R.string.delete),
            // Backing out of the delete lets go of the items too.
            onDismiss = {
                deleteTargets = null
                vm.clearSelection()
            },
            onConfirm = {
                deleteTargets = null
                vm.delete(targets, toTrash = false)
            },
        )
    }
}

@Composable
private fun MapBody(
    vm: DiskMapViewModel,
    viewMode: ViewMode,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val current = vm.current

    Column(modifier) {
        if (vm.scanning || vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (current == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (vm.scanning) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.scanning))
                        Text(
                            text = stringResource(R.string.files) + ": " + formatCount(vm.scannedFiles) +
                                "  ·  " + formatSize(context, vm.scannedBytes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (vm.volumes.isEmpty()) {
                    Text(stringResource(R.string.no_storage))
                }
            }
        } else {
            MapContent(vm, current, viewMode, onDelete)
        }
    }
}

@Composable
private fun ColumnScope.MapContent(
    vm: DiskMapViewModel,
    current: Node,
    viewMode: ViewMode,
    onDelete: () -> Unit,
) {
    val version = vm.treeVersion
    val onView = fileViewer(vm)
    Breadcrumbs(current, onOpen = vm::open, onUp = { vm.up() })
    Summary(vm, current)

    val chart = Modifier.weight(1f).fillMaxWidth()
    when (viewMode) {
        ViewMode.RINGS -> BoxWithConstraints(chart) {
            val rings = @Composable { m: Modifier ->
                Sunburst(
                    folder = current,
                    treeVersion = version,
                    selection = vm.selection,
                    onOpen = vm::open,
                    onToggle = vm::toggle,
                    onUp = { vm.up() },
                    modifier = m.padding(12.dp),
                )
            }
            val legend = @Composable { m: Modifier ->
                NodeList(current, version, vm.selection, vm::open, vm::toggle, onView, m)
            }
            // The rings draw into the largest circle that fits, so they
            // only need a share of the space; the legend takes the rest.
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    rings(Modifier.weight(1f).fillMaxHeight())
                    legend(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    rings(Modifier.weight(1.3f).fillMaxWidth())
                    legend(Modifier.weight(1f))
                }
            }
        }

        ViewMode.TILES -> Treemap(
            folder = current,
            treeVersion = version,
            selection = vm.selection,
            onOpen = vm::open,
            onToggle = vm::toggle,
            modifier = chart.padding(8.dp),
        )

        ViewMode.LIST -> NodeList(current, version, vm.selection, vm::open, vm::toggle, onView, chart)
    }

    SelectionBar(vm, current, onDelete)
}

@Composable
private fun Breadcrumbs(current: Node, onOpen: (Node) -> Unit, onUp: () -> Unit) {
    val chain = remember(current) { generateSequence(current) { it.parent }.toList().asReversed() }
    val state = rememberLazyListState()
    LaunchedEffect(chain.size) { state.scrollToItem(chain.lastIndex) }
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUp, enabled = current.parent != null) {
            Icon(Icons.Filled.ArrowUpward, stringResource(R.string.up))
        }
        LazyRow(
            state = state,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(chain) { i, node ->
                if (i > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val last = i == chain.lastIndex
                Text(
                    text = if (node.parent == null) ROOT_CRUMB else node.name,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(enabled = !last) { onOpen(node) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Summary(vm: DiskMapViewModel, current: Node) {
    val context = LocalContext.current
    val volume = vm.volume
    // Read so the free space is asked again after a delete.
    val version = vm.treeVersion
    val space = remember(volume, version) { volume?.let { it.free to it.total } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatSize(context, current.size) + "  ·  " +
                stringResource(R.string.files) + ": " + formatCount(current.files),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        space?.let { (free, total) ->
            Text(
                text = stringResource(R.string.free) + ": " + formatSize(context, free) + " / " +
                    formatSize(context, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What is selected and what can be done with it; a hint on the gestures otherwise.
 * The bar is always as tall as with a selection, so the chart above it does not
 * change size when something is selected or let go.
 */
@Composable
private fun SelectionBar(vm: DiskMapViewModel, current: Node, onDelete: () -> Unit) {
    val selecting = vm.selection.isNotEmpty()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Laid out even with nothing selected, only invisible and inert then:
            // it is what gives the bar its height.
            Column(Modifier.alpha(if (selecting) 1f else 0f)) {
                SelectedItems(vm, current, onDelete)
            }
            if (!selecting) {
                Text(
                    text = stringResource(R.string.hint_open) + "  ·  " + stringResource(R.string.hint_select),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                )
            }
        }
    }
}

/** Three buttons share the bar's width; the stock side padding leaves their labels no room. */
private val BAR_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

/** Opens a file from this screen, so the viewer stacks on top of it. */
@Composable
private fun fileViewer(vm: DiskMapViewModel): (Node) -> Unit {
    val activity = LocalActivity.current
    return { node -> activity?.let { vm.view(it, node) } }
}

/** One item by its name, several by their count. */
@Composable
private fun selectionTitle(items: List<Node>): String = when (items.size) {
    0 -> ""
    1 -> items[0].name
    else -> stringResource(R.string.selected_count) + ": " + items.size
}

/**
 * What is selected, its total size, and three actions: delete for good, let
 * go, move to the trash. With nothing selected, the same shape doing nothing.
 */
@Composable
private fun SelectedItems(vm: DiskMapViewModel, current: Node, onDelete: () -> Unit) {
    val context = LocalContext.current
    // A folder and something inside it are counted once.
    val items = topmost(vm.selection)
    val total = items.sumOf { it.size }
    val enabled = vm.canDelete(items)
    val onView = fileViewer(vm)
    // One file can be looked at; the button keeps its place otherwise, so
    // the bar does not change height.
    val viewable = items.singleOrNull()?.takeUnless { it.isDir }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = selectionTitle(items),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (items.isEmpty()) "" else formatSize(context, total) + "  ·  " + formatPercent(total, current.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { viewable?.let(onView) },
            enabled = viewable != null,
            modifier = Modifier.alpha(if (viewable != null) 1f else 0f),
        ) {
            Icon(Icons.Outlined.Visibility, stringResource(R.string.view_file))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onDelete,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = BAR_BUTTON_PADDING,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.delete), maxLines = 1) }
        FilledTonalButton(
            onClick = vm::clearSelection,
            enabled = items.isNotEmpty(),
            contentPadding = BAR_BUTTON_PADDING,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.cancel), maxLines = 1) }
        // What is already in the trash can only go for good.
        Button(
            onClick = { vm.delete(items, toTrash = true) },
            enabled = enabled && !vm.anyInTrash(items),
            contentPadding = BAR_BUTTON_PADDING,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.to_trash), maxLines = 1) }
    }
}

/** Shown until all-files access is granted. */
@Composable
fun AccessScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.access_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.access_text) + ".",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.access_grant)) }
    }
}
