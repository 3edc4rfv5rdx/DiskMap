package xx.diskmap

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Finds files with the same content, reading as little as it can:
 *
 *  1. files are grouped by size, which costs no reading at all;
 *  2. within a group, by a hash of their first and last [EDGE] bytes;
 *  3. only what still matches is hashed whole.
 *
 * Names and dates play no part in finding copies — a copy is often renamed and
 * its date is whatever the app that made it chose — only in [keeper].
 */
object Duplicates {
    /** Smaller files are not worth the reading: their copies free next to nothing. */
    const val MIN_SIZE = 1L shl 20

    /** How much of each end the quick hash reads. */
    private const val EDGE = 64 * 1024

    private const val BLOCK = 1 shl 20

    class Copy(val path: String, val size: Long, val modified: Long) {
        val name: String get() = path.substringAfterLast('/')
    }

    class Group(val size: Long, val copies: List<Copy>) {
        /** What deleting all copies but one would free. */
        val wasted: Long get() = size * (copies.size - 1)
    }

    /** Bytes read so far, for the screen to poll. */
    class Progress {
        val bytes = AtomicLong()
    }

    /**
     * The files under [folder] of at least [minSize] bytes, grouped by size, with
     * lone sizes dropped. Reads only the tree, so it is quick enough for the
     * main thread; [skip] leaves out whole subtrees, the trash for one.
     */
    fun candidates(folder: Node, skip: (Node) -> Boolean, minSize: Long = MIN_SIZE): List<List<Copy>> {
        val bySize = HashMap<Long, MutableList<String>>()
        val stack = ArrayDeque<Node>()
        stack.addLast(folder)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (skip(node)) continue
            if (node.isDir) {
                node.children.forEach { stack.addLast(it) }
            } else if (node.size >= minSize) {
                bySize.getOrPut(node.size) { mutableListOf() }.add(node.path)
            }
        }
        return bySize.filterValues { it.size > 1 }.map { (size, paths) ->
            paths.map { Copy(it, size, 0L) }
        }
    }

    /**
     * Narrows [candidates] down to real duplicates, largest waste first. A file
     * that cannot be read drops out of its group. Runs off the main thread;
     * [checkCancel] is called between files.
     */
    fun confirm(
        candidates: List<List<Copy>>,
        progress: Progress? = null,
        checkCancel: () -> Unit = {},
    ): List<Group> {
        val quick = candidates.flatMap { group ->
            split(group) { edgeHash(File(it.path), it.size, progress).also { checkCancel() } }
        }
        val exact = quick.flatMap { group ->
            // Files no longer than both edges were read whole already.
            if (group[0].size <= 2L * EDGE) listOf(group)
            else split(group) { fullHash(File(it.path), progress, checkCancel) }
        }
        return exact
            .map { group ->
                Group(group[0].size, group.map { Copy(it.path, it.size, File(it.path).lastModified()) })
            }
            .sortedByDescending { it.wasted }
    }

    /** [group] cut by [key]; unreadable files (null key) and lone files dropped. */
    private fun split(group: List<Copy>, key: (Copy) -> String?): List<List<Copy>> =
        group.mapNotNull { copy -> key(copy)?.let { it to copy } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.size > 1 }

    private fun edgeHash(file: File, size: Long, progress: Progress?): String? = try {
        RandomAccessFile(file, "r").use { raf ->
            val digest = MessageDigest.getInstance("SHA-1")
            val buf = ByteArray(EDGE)
            // readFully: a file shorter than the tree says has changed since the
            // scan, and the EOFException drops it from the group.
            val head = minOf(EDGE.toLong(), size).toInt()
            raf.readFully(buf, 0, head)
            digest.update(buf, 0, head)
            var read = head.toLong()
            if (size > EDGE) {
                val tailStart = maxOf(EDGE.toLong(), size - EDGE)
                val tail = (size - tailStart).toInt()
                raf.seek(tailStart)
                raf.readFully(buf, 0, tail)
                digest.update(buf, 0, tail)
                read += tail
            }
            progress?.bytes?.addAndGet(read)
            digest.digest().toHex()
        }
    } catch (e: IOException) {
        null
    }

    private fun fullHash(file: File, progress: Progress?, checkCancel: () -> Unit): String? = try {
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-1")
            val buf = ByteArray(BLOCK)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                progress?.bytes?.addAndGet(n.toLong())
                checkCancel()
            }
            digest.digest().toHex()
        }
    } catch (e: IOException) {
        null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ---------- Which copy stays ----------

    // " (1)" at the end, "copy"/"копия"/"копія" at the end with an optional
    // number, or "Copy of"/"Копия" in front — in the name without its extension.
    private val COPY_MARK = Regex(
        """\(\d+\)\s*$|(^|[\s_-])(copy|копия|копія)([\s_-]*\d+)?\s*$|^(copy of|копия|копія)\s""",
        RegexOption.IGNORE_CASE,
    )

    /** True when the name says it is a copy of something. */
    fun looksLikeCopy(name: String): Boolean = COPY_MARK.containsMatchIn(name.substringBeforeLast('.'))

    /**
     * The copy most likely to be the original: a name without a copy mark
     * first, then the oldest, then the shortest path.
     */
    fun keeper(group: Group): Copy = group.copies.minWith(
        compareBy<Copy>({ looksLikeCopy(it.name) }, { it.modified }, { it.path.length }),
    )
}
