package xx.diskmap.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

private enum class Screen { MAP, TRASH, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskMapScreen(onAbout: () -> Unit) {
    val vm: DiskMapViewModel = viewModel()
    val context = LocalContext.current
    val viewMode by AppSettings.viewMode.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
    var menuOpen by remember { mutableStateOf(false) }
    var volumeMenuOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Node?>(null) }
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

    BackHandler(enabled = screen != Screen.MAP || vm.selected != null || current?.parent != null) {
        when {
            screen != Screen.MAP -> screen = Screen.MAP
            vm.selected != null -> vm.select(null)
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
                    if (screen == Screen.MAP && vm.volumes.size > 1) {
                        Box {
                            IconButton(onClick = { volumeMenuOpen = true }) {
                                Icon(Icons.Outlined.SdStorage, stringResource(R.string.storage))
                            }
                            DropdownMenu(expanded = volumeMenuOpen, onDismissRequest = { volumeMenuOpen = false }) {
                                vm.volumes.forEach { v ->
                                    DropdownMenuItem(
                                        text = { Text(v.label) },
                                        enabled = !vm.busy,
                                        onClick = {
                                            volumeMenuOpen = false
                                            vm.selectVolume(v)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                                text = { Text(stringResource(R.string.trash)) },
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.TRASH
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
            Screen.SETTINGS -> SettingsScreen(body)
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
                volumeLabel = volumeLabel,
                onDelete = { deleteTarget = it },
                modifier = body,
            )
        }
    }

    deleteTarget?.let { target ->
        DeleteDialog(
            node = target,
            inTrash = vm.isInTrash(target),
            onDismiss = { deleteTarget = null },
            onConfirm = { toTrash ->
                deleteTarget = null
                vm.delete(target, toTrash)
            },
        )
    }
}

@Composable
private fun MapBody(
    vm: DiskMapViewModel,
    viewMode: ViewMode,
    volumeLabel: String,
    onDelete: (Node) -> Unit,
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
            MapContent(vm, current, viewMode, volumeLabel, onDelete)
        }
    }
}

@Composable
private fun ColumnScope.MapContent(
    vm: DiskMapViewModel,
    current: Node,
    viewMode: ViewMode,
    volumeLabel: String,
    onDelete: (Node) -> Unit,
) {
    val context = LocalContext.current
    val version = vm.treeVersion
    Breadcrumbs(current, volumeLabel, onOpen = vm::open)
    Summary(vm, current)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val modes = ViewMode.entries
        modes.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = mode == viewMode,
                onClick = { AppSettings.setViewMode(context, mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ViewMode.RINGS -> R.string.view_rings
                            ViewMode.TILES -> R.string.view_tiles
                            ViewMode.LIST -> R.string.view_list
                        }
                    )
                )
            }
        }
    }

    val chart = Modifier.weight(1f).fillMaxWidth()
    when (viewMode) {
        ViewMode.RINGS -> BoxWithConstraints(chart) {
            val rings = @Composable { m: Modifier ->
                Sunburst(
                    folder = current,
                    treeVersion = version,
                    selected = vm.selected,
                    onOpen = vm::open,
                    onSelect = vm::select,
                    onUp = { vm.up() },
                    modifier = m.padding(12.dp),
                )
            }
            val legend = @Composable { m: Modifier ->
                NodeList(current, version, vm.selected, vm::open, vm::select, m)
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
            selected = vm.selected,
            onOpen = vm::open,
            onSelect = vm::select,
            modifier = chart.padding(8.dp),
        )

        ViewMode.LIST -> NodeList(current, version, vm.selected, vm::open, vm::select, chart)
    }

    SelectionBar(vm, current, onDelete)
}

@Composable
private fun Breadcrumbs(current: Node, volumeLabel: String, onOpen: (Node) -> Unit) {
    val chain = remember(current) { generateSequence(current) { it.parent }.toList().asReversed() }
    val state = rememberLazyListState()
    LaunchedEffect(chain.size) { state.scrollToItem(chain.lastIndex) }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                text = displayName(node, volumeLabel),
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

/** What is selected and what can be done with it; a hint on the gestures otherwise. */
@Composable
private fun SelectionBar(vm: DiskMapViewModel, current: Node, onDelete: (Node) -> Unit) {
    val context = LocalContext.current
    val node = vm.selected
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (node == null) {
                Text(
                    text = stringResource(R.string.hint_open) + "  ·  " + stringResource(R.string.hint_select),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                SelectedItem(vm, node, current, onDelete)
            }
        }
    }
}

@Composable
private fun SelectedItem(vm: DiskMapViewModel, node: Node, current: Node, onDelete: (Node) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = node.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSize(context, node.size) + "  ·  " + formatPercent(node.size, current.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { vm.select(null) }) {
            Icon(Icons.Filled.Close, stringResource(R.string.close))
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        if (node.isDir) {
            OutlinedButton(onClick = { vm.open(node) }) { Text(stringResource(R.string.open)) }
        }
        Button(
            onClick = { onDelete(node) },
            enabled = vm.canDelete(node),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text(stringResource(R.string.delete)) }
    }
}

@Composable
private fun DeleteDialog(
    node: Node,
    inTrash: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (toTrash: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val remembered by AppSettings.toTrash.collectAsState()
    var toTrash by remember { mutableStateOf(remembered && !inTrash) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete) + "?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(node.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = formatSize(context, node.size) +
                        if (node.isDir) "  ·  " + stringResource(R.string.files) + ": " + formatCount(node.files) else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.to_trash), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = toTrash, onCheckedChange = { toTrash = it }, enabled = !inTrash)
                }
                if (inTrash) {
                    Text(
                        stringResource(R.string.trash_permanent_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!toTrash) {
                    Text(
                        stringResource(R.string.delete_permanent_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.delete), danger = true) { onConfirm(toTrash) }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
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
