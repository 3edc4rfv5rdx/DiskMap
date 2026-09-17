package xx.diskmap

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Counters a running scan bumps, for the screen to poll. */
class ScanProgress {
    val files = AtomicLong()
    val bytes = AtomicLong()
}

/**
 * Walks a folder into a [Node] tree.
 *
 * Symbolic links are counted as zero-byte files and never followed: a link
 * cannot loop the walk, and whatever it points at is counted once, where it
 * really lives. A folder that cannot be listed (Android/data on Android 11+)
 * comes out empty.
 */
object Scanner {

    /** A whole tree, its root named by the absolute path so [Node.path] works. */
    fun scanTree(dir: File, progress: ScanProgress? = null, checkCancel: () -> Unit = {}): Node =
        walk(dir, dir.absolutePath, progress, checkCancel)

    /** A subtree to hang under an existing node, named by the plain file name. */
    fun scanChild(file: File, checkCancel: () -> Unit = {}): Node =
        walk(file, file.name, null, checkCancel)

    private fun walk(file: File, name: String, progress: ScanProgress?, checkCancel: () -> Unit): Node {
        val attrs = FileOps.attributes(file)
        if (attrs == null || !attrs.isDirectory) {
            val size = if (attrs?.isRegularFile == true) attrs.size() else 0L
            progress?.files?.incrementAndGet()
            progress?.bytes?.addAndGet(size)
            return Node(name, isDir = false, size = size, files = 1)
        }
        checkCancel()
        val kids = file.listFiles().orEmpty().map { walk(it, it.name, progress, checkCancel) }
        return Node(name, isDir = true, size = kids.sumOf { it.size }, files = kids.sumOf { it.files })
            .apply { setChildren(kids) }
    }
}
