package xx.diskmap

import android.app.Activity
import android.app.Application
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class Volume(val dir: File, val label: String) {
    val total: Long get() = runCatching { StatFs(dir.path).totalBytes }.getOrDefault(0L)
    val free: Long get() = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
}

/**
 * A line for the snackbar: a string resource, and after a colon either a
 * size in bytes or a raw detail such as an exception message.
 */
class Notice(@param:StringRes val text: Int, val bytes: Long? = null, val detail: String? = null)

private const val PROGRESS_POLL_MS = 200L

class DiskMapViewModel(app: Application) : AndroidViewModel(app) {
    val volumes: List<Volume> = app.getSystemService(StorageManager::class.java).storageVolumes
        .sortedByDescending { it.isPrimary }
        .mapNotNull { v -> v.directory?.let { Volume(it, v.getDescription(app)) } }

    var volume by mutableStateOf<Volume?>(null)
        private set
    var root by mutableStateOf<Node?>(null)
        private set
    var current by mutableStateOf<Node?>(null)
        private set
    /** Selected items, in the order they were picked; compared by identity. */
    var selection by mutableStateOf<List<Node>>(emptyList())
        private set

    /** Bumped on every change inside the tree, which Compose cannot see on its own. */
    var treeVersion by mutableIntStateOf(0)
        private set

    var scanning by mutableStateOf(false)
        private set
    var scannedFiles by mutableLongStateOf(0L)
        private set
    var scannedBytes by mutableLongStateOf(0L)
        private set

    /** A delete, restore or purge is running; the tree must not change under it. */
    var busy by mutableStateOf(false)
        private set

    var notice by mutableStateOf<Notice?>(null)
        private set

    var trashOpen by mutableStateOf(false)
        private set
    var trashEntries by mutableStateOf<List<Trash.Entry>?>(null)
        private set

    // ---------- Duplicates ----------

    /** The folder the last duplicate search looked under. */
    var dupScope by mutableStateOf<String?>(null)
        private set
    var dupSearching by mutableStateOf(false)
        private set
    var dupBytesRead by mutableLongStateOf(0L)
        private set
    /** Null until a search has finished. */
    var dupGroups by mutableStateOf<List<Duplicates.Group>?>(null)
        private set
    /** Picked copies, by path: a search outlives the nodes it started from. */
    var dupPicked by mutableStateOf<Set<String>>(emptySet())
        private set

    private var dupJob: Job? = null
    private var dupGeneration = 0

    private var scanJob: Job? = null
    private var scanGeneration = 0

    /** Every launch opens the internal storage; a card is a visit, not a place to come back to. */
    fun start() {
        if (volume == null) volumes.firstOrNull()?.let { selectVolume(it) }
    }

    fun selectVolume(v: Volume) {
        if (busy) return
        volume = v
        root = null
        current = null
        selection = emptyList()
        rescan()
    }

    fun rescan() {
        val v = volume ?: return
        if (busy) return
        scanJob?.cancel()
        val generation = ++scanGeneration
        val keepPath = current?.path
        scanning = true
        scannedFiles = 0L
        scannedBytes = 0L
        scanJob = viewModelScope.launch {
            val progress = ScanProgress()
            val ticker = launch {
                while (true) {
                    scannedFiles = progress.files.get()
                    scannedBytes = progress.bytes.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                val tree = withContext(Dispatchers.IO) {
                    Trash.migrate(v.dir)
                    Scanner.scanTree(v.dir, progress) { ensureActive() }
                }
                root = tree
                current = keepPath?.let { tree.findNearest(it) } ?: tree
                selection = emptyList()
                treeVersion++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = Notice(R.string.scan_failed, detail = e.message)
            } finally {
                ticker.cancel()
                if (generation == scanGeneration) scanning = false
            }
        }
    }

    fun open(node: Node) {
        if (!node.isDir) return
        current = node
        selection = emptyList()
    }

    /** One level up; false at the root, so the back press can leave the app. */
    fun up(): Boolean {
        val parent = current?.parent ?: return false
        selection = emptyList()
        current = parent
        return true
    }

    /** Adds [node] to the selection, or takes it out if it is there. */
    fun toggle(node: Node) {
        selection = if (selection.any { it === node }) selection.filterNot { it === node } else selection + node
    }

    fun clearSelection() {
        selection = emptyList()
    }

    /** Hands a file to the app that shows its type; says so when there is none. */
    fun view(activity: Activity, path: String) {
        if (!FileViewer.open(activity, File(path))) notice = Notice(R.string.no_viewer)
    }

    fun noticeShown() {
        notice = null
    }

    fun isInTrash(node: Node): Boolean {
        val v = volume ?: return false
        return Trash.isInTrash(node.path, v.dir)
    }

    /** Deleting the root itself is never offered: it is the whole volume. */
    fun canDelete(nodes: List<Node>): Boolean =
        nodes.isNotEmpty() && nodes.all { it.parent != null } && !busy && !scanning

    /** True when the trash cannot take [nodes]: some are in it already. */
    fun anyInTrash(nodes: List<Node>): Boolean = nodes.any { isInTrash(it) }

    /**
     * Deletes [nodes] one by one, or moves them to the trash; a failure does not
     * stop the rest. [onDone] runs on the main thread once the tree is updated.
     */
    fun delete(nodes: List<Node>, toTrash: Boolean, onDone: () -> Unit = {}) {
        val v = volume ?: return
        val targets = topmost(nodes)
        if (!canDelete(targets)) return
        val useTrash = toTrash && !anyInTrash(targets)
        val paths = targets.map { it.path }
        val before = targets.sumOf { it.size }
        runOperation {
            var failed = 0
            for (path in paths) {
                val ok = withContext(Dispatchers.IO) {
                    if (useTrash) Trash.moveToTrash(File(path), v.dir) else FileOps.deleteTree(File(path))
                }
                if (!ok) failed++
                syncPath(path)
            }
            if (useTrash) syncPath(Trash.dirFor(v.dir).path)
            val left = paths.sumOf { root?.find(it)?.size ?: 0L }
            notice = when {
                failed > 0 && left == before -> Notice(R.string.delete_failed)
                failed > 0 -> Notice(R.string.delete_partly, bytes = before - left)
                useTrash -> Notice(R.string.moved_to_trash)
                else -> Notice(R.string.freed, bytes = before)
            }
            onDone()
        }
    }

    // ---------- Trash ----------

    fun openTrash() {
        trashOpen = true
        reloadTrash()
    }

    fun closeTrash() {
        trashOpen = false
        trashEntries = null
    }

    fun restore(entry: Trash.Entry) {
        val v = volume ?: return
        runOperation {
            val result = withContext(Dispatchers.IO) { Trash.restore(entry, v.dir) }
            if (result == Trash.RestoreResult.OK) {
                syncPath(entry.originalPath)
                syncPath(Trash.dirFor(v.dir).path)
            }
            notice = Notice(
                when (result) {
                    Trash.RestoreResult.OK -> R.string.restored
                    Trash.RestoreResult.TARGET_EXISTS -> R.string.restore_exists
                    Trash.RestoreResult.FAILED -> R.string.restore_failed
                }
            )
            reloadTrashNow()
        }
    }

    fun purge(entry: Trash.Entry) {
        val v = volume ?: return
        runOperation {
            val ok = withContext(Dispatchers.IO) { Trash.purge(entry, v.dir) }
            syncPath(Trash.dirFor(v.dir).path)
            notice = if (ok) Notice(R.string.freed, bytes = entry.size) else Notice(R.string.delete_failed)
            reloadTrashNow()
        }
    }

    fun emptyTrash() {
        val v = volume ?: return
        val total = trashEntries.orEmpty().sumOf { it.size }
        runOperation {
            val ok = withContext(Dispatchers.IO) { Trash.empty(v.dir) }
            syncPath(Trash.dirFor(v.dir).path)
            notice = if (ok) Notice(R.string.freed, bytes = total) else Notice(R.string.delete_failed)
            reloadTrashNow()
        }
    }

    private fun reloadTrash() {
        viewModelScope.launch { reloadTrashNow() }
    }

    private suspend fun reloadTrashNow() {
        val v = volume ?: return
        trashEntries = withContext(Dispatchers.IO) { Trash.list(v.dir) }
    }

    /** Looks for duplicates under the folder on screen, the trash left out. */
    fun findDuplicates() {
        val folder = current ?: return
        val v = volume ?: return
        dupJob?.cancel()
        val generation = ++dupGeneration
        dupScope = folder.path
        dupGroups = null
        dupPicked = emptySet()
        dupBytesRead = 0L
        dupSearching = true
        // Walked here, on the main thread, where the tree is safe to read.
        val candidates = Duplicates.candidates(folder, skip = { Trash.isInTrash(it.path, v.dir) })
        dupJob = viewModelScope.launch {
            val progress = Duplicates.Progress()
            val ticker = launch {
                while (true) {
                    dupBytesRead = progress.bytes.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                dupGroups = withContext(Dispatchers.IO) {
                    Duplicates.confirm(candidates, progress) { ensureActive() }
                }
            } finally {
                ticker.cancel()
                // A search started meanwhile owns the flag now.
                if (generation == dupGeneration) dupSearching = false
            }
        }
    }

    fun closeDuplicates() {
        dupJob?.cancel()
        dupGeneration++
        dupSearching = false
        dupGroups = null
        dupPicked = emptySet()
        dupScope = null
    }

    fun toggleDuplicate(path: String) {
        dupPicked = if (path in dupPicked) dupPicked - path else dupPicked + path
    }

    fun clearDuplicates() {
        dupPicked = emptySet()
    }

    /** Picks every copy but the likely original in each group. */
    fun pickAllButOne() {
        dupPicked = dupGroups.orEmpty().flatMap { g ->
            val keep = Duplicates.keeper(g)
            g.copies.filter { it !== keep }.map { it.path }
        }.toSet()
    }

    /** True when some group would lose every copy: allowed, but the bar warns of it. */
    fun wouldWipeAGroup(): Boolean =
        dupGroups.orEmpty().any { g -> g.copies.all { it.path in dupPicked } }

    fun deleteDuplicates(toTrash: Boolean) {
        val tree = root ?: return
        val nodes = dupPicked.mapNotNull { tree.find(it) }
        delete(nodes, toTrash) {
            // Whatever is gone leaves its group; a group of one is no longer one.
            dupGroups = dupGroups?.mapNotNull { g ->
                val left = g.copies.filter { FileOps.exists(File(it.path)) }
                if (left.size > 1) Duplicates.Group(g.size, left) else null
            }
            dupPicked = emptySet()
        }
    }

    // ---------- Tree upkeep ----------

    /**
     * Runs one file operation at a time. A scan in progress is left to finish
     * first: its tree would not know about the change.
     */
    private fun runOperation(block: suspend () -> Unit) {
        if (busy || scanning) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    /**
     * Brings the node at [path] in line with the disk: rescans it, drops it when
     * it is gone, or adds it under its parent when it is new. Only that subtree
     * is walked, and the sizes above it are adjusted by the difference.
     */
    private suspend fun syncPath(path: String) {
        val tree = root ?: return
        val existing = tree.find(path)
        if (existing === tree) return
        // What to walk: the node itself, or, for a path the tree has never seen,
        // the topmost folder on the way that is new as well — the trash can
        // bring Documents/DiskMap into being along with itself.
        val target = if (existing != null) {
            path
        } else {
            val anchor = tree.findNearest(path) ?: return
            anchor.path + "/" + path.removePrefix(anchor.path + "/").substringBefore('/')
        }
        val file = File(target)
        val fresh = withContext(Dispatchers.IO) {
            if (FileOps.exists(file)) Scanner.scanChild(file) else null
        }
        // A rescan that replaced the tree meanwhile already has the change.
        if (root !== tree) return
        val currentPath = current?.path
        val old = tree.find(target)
        when {
            old != null -> old.parent?.replaceChild(old, fresh)
            fresh != null -> tree.find(file.parent ?: return)?.replaceChild(null, fresh)
            else -> return
        }
        // The folder on screen may have been inside the replaced subtree.
        current = currentPath?.let { tree.findNearest(it) } ?: tree
        selection = emptyList()
        treeVersion++
    }
}
