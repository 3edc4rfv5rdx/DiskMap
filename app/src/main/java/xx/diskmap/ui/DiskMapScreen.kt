package xx.diskmap.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xx.diskmap.AppSettings
import xx.diskmap.DiskMapViewModel
import xx.diskmap.Node
import xx.diskmap.R
import xx.diskmap.ViewMode
import xx.diskmap.topmost

private enum class Screen { MAP, TRASH, DUPLICATES, SETTINGS }

// Tighter than the Material default of 64dp: on a phone the chart needs every
// line of height it can get.
private val TOP_BAR_HEIGHT = 52.dp

/** The storage root in the path line; the top bar already names the storage. */
private const val ROOT_CRUMB = "~"

private fun ViewMode.labelRes(): Int = when (this) {
    ViewMode.RINGS -> R.string.view_rings
    ViewMode.TILES -> R.string.view_tiles
    ViewMode.LIST -> R.string.view_list
    ViewMode.LARGEST -> R.string.view_largest
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
    // Keyed on the tree's arrival as well: a screen restored before the scan
    // ends has nothing to search yet.
    LaunchedEffect(screen, vm.current == null) {
        if (screen == Screen.TRASH) vm.openTrash() else vm.closeTrash()
        // A search belongs to its screen: leaving drops it, arriving starts one
        // unless it is still there, as after the activity was recreated.
        if (screen != Screen.DUPLICATES) vm.closeDuplicates()
        else if (vm.dupScope == null) vm.findDuplicates()
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
                expandedHeight = TOP_BAR_HEIGHT,
                title = {
                    Text(
                        text = when (screen) {
                            Screen.MAP -> volumeLabel.ifEmpty { stringResource(R.string.app_name) }
                            Screen.TRASH -> stringResource(R.string.trash)
                            Screen.DUPLICATES -> stringResource(R.string.duplicates)
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
                        MoreButton(onClick = { menuOpen = true })
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
                                text = { Text(stringResource(R.string.duplicates)) },
                                enabled = !vm.scanning && vm.current != null,
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.DUPLICATES
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
            Screen.DUPLICATES -> DuplicatesScreen(vm, body)
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
        DeleteForeverDialog(
            summary = dotted(
                pickedTitle(items.map { it.name }),
                formatSize(context, items.sumOf { it.size }),
                filesLabel(items.sumOf { it.files }),
            ),
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
                            text = dotted(filesLabel(vm.scannedFiles), formatSize(context, vm.scannedBytes)),
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
    val viewer = fileViewer(vm)
    val onView: (Node) -> Unit = { viewer(it.path) }
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
                    modifier = m.padding(4.dp),
                )
            }
            val legend = @Composable { m: Modifier ->
                NodeList(current, version, vm.selection, vm::open, vm::toggle, onView, m)
            }
            // The rings draw into the largest circle that fits; they get most
            // of the height and the legend the rest.
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    rings(Modifier.weight(1f).fillMaxHeight())
                    legend(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    rings(Modifier.weight(1.8f).fillMaxWidth())
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

        ViewMode.LARGEST -> NodeList(
            current, version, vm.selection, vm::open, vm::toggle, onView, chart, largest = true,
        )
    }

    SelectionBar(vm, current, onDelete)
}

@Composable
private fun Breadcrumbs(current: Node, onOpen: (Node) -> Unit, onUp: () -> Unit) {
    val chain = remember(current) { generateSequence(current) { it.parent }.toList().asReversed() }
    val state = rememberLazyListState()
    LaunchedEffect(chain.size) { state.scrollToItem(chain.lastIndex) }
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUp, enabled = current.parent != null, modifier = Modifier.size(COMPACT_BUTTON)) {
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
                        .padding(horizontal = 4.dp, vertical = 4.dp),
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
            text = dotted(formatSize(context, current.size), filesLabel(current.files)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        space?.let { (free, total) ->
            Text(
                text = labeled(R.string.free, formatSize(context, free) + " / " + formatSize(context, total)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What is selected and what can be done with it; a hint on the gestures otherwise. */
@Composable
private fun SelectionBar(vm: DiskMapViewModel, current: Node, onDelete: () -> Unit) {
    val context = LocalContext.current
    // A folder and something inside it are counted once.
    val items = topmost(vm.selection)
    val total = items.sumOf { it.size }
    val canDelete = vm.canDelete(items)
    val onView = fileViewer(vm)
    // One file can be looked at.
    val viewable = items.singleOrNull()?.takeUnless { it.isDir }
    ActionBar(
        active = items.isNotEmpty(),
        title = pickedTitle(items.map { it.name }),
        detail = dotted(formatSize(context, total), formatPercent(total, current.size)),
        hint = dotted(stringResource(R.string.hint_open), stringResource(R.string.hint_select)),
        canDelete = canDelete,
        // What is already in the trash can only go for good.
        canTrash = canDelete && !vm.anyInTrash(items),
        onDelete = onDelete,
        onCancel = vm::clearSelection,
        onTrash = { vm.delete(items, toTrash = true) },
        onView = viewable?.let { file -> { onView(file.path) } },
    )
}

/** Opens a file from this screen, so the viewer stacks on top of it. */
@Composable
fun fileViewer(vm: DiskMapViewModel): (String) -> Unit {
    val activity = LocalActivity.current
    return { path -> activity?.let { vm.view(it, path) } }
}

/** Shown until all-files access is granted; [unavailable] once no screen to grant it was found. */
@Composable
fun AccessScreen(onGrant: () -> Unit, unavailable: Boolean) {
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
        if (unavailable) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.access_unavailable) + ".",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
