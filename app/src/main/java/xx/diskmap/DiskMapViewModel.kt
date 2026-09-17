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

    private var scanJob: Job? = null
    private var scanGeneration = 0

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
    fun view(activity: Activity, node: Node) {
        if (node.isDir) return
        if (!FileViewer.open(activity, File(node.path))) notice = Notice(R.string.no_viewer)
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

    /** Deletes [nodes] one by one, or moves them to the trash; a failure does not stop the rest. */
    fun delete(nodes: List<Node>, toTrash: Boolean) {
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
        val file = File(path)
        val fresh = withContext(Dispatchers.IO) {
            if (FileOps.exists(file)) Scanner.scanChild(file) else null
        }
        val tree = root ?: return
        val currentPath = current?.path
        val existing = tree.find(path)
        when {
            existing === tree -> return
            existing != null -> existing.parent?.replaceChild(existing, fresh)
            fresh != null -> tree.find(file.parent ?: return)?.replaceChild(null, fresh)
            else -> return
        }
        // The folder on screen may have been inside the replaced subtree.
        current = currentPath?.let { tree.findNearest(it) } ?: tree
        selection = emptyList()
        treeVersion++
    }
}
