package xx.diskmap

import java.io.File
import java.io.IOException

/**
 * The app's own trash: Android has no system trash for arbitrary files.
 *
 * Each volume keeps one at its root, so moving an item in is a rename and
 * costs no copy:
 *
 *   <volume>/.DiskMapTrash/<id>/<original name>   the item itself
 *   <volume>/.DiskMapTrash/<id>.path              the absolute path it came from
 *
 * The record lives beside the slot rather than inside it, so no name the item
 * could have collides with it.
 */
object Trash {
    const val DIR_NAME = ".DiskMapTrash"
    private const val RECORD_EXT = ".path"

    class Entry(
        val id: String,
        val item: File,
        val originalPath: String,
        val deletedAt: Long,
        val size: Long,
    )

    enum class RestoreResult { OK, TARGET_EXISTS, FAILED }

    fun dirFor(volumeRoot: File) = File(volumeRoot, DIR_NAME)

    /** True for the trash folder itself and for anything inside it. */
    fun isInTrash(path: String, volumeRoot: File): Boolean {
        val trash = dirFor(volumeRoot).absolutePath
        return path == trash || path.startsWith("$trash/")
    }

    /**
     * Moves [file] into the trash of [volumeRoot]. The record is written first,
     * so a crash half-way leaves an empty slot rather than an item with no way
     * back. False when the move failed; nothing is left behind then.
     */
    fun moveToTrash(file: File, volumeRoot: File, now: Long = System.currentTimeMillis()): Boolean {
        val trash = dirFor(volumeRoot)
        val id = freeId(trash, now)
        val slot = File(trash, id)
        val record = File(trash, id + RECORD_EXT)
        if (!slot.mkdirs()) return false
        try {
            record.writeText(file.absolutePath)
        } catch (e: IOException) {
            slot.delete()
            return false
        }
        if (file.renameTo(File(slot, file.name))) return true
        record.delete()
        slot.delete()
        return false
    }

    /** What the trash holds, newest first. Slots with no record or no item are left out. */
    fun list(volumeRoot: File): List<Entry> {
        val trash = dirFor(volumeRoot)
        return trash.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { slot ->
                val item = slot.listFiles()?.singleOrNull() ?: return@mapNotNull null
                val record = File(trash, slot.name + RECORD_EXT)
                val original = try {
                    record.readText()
                } catch (e: IOException) {
                    return@mapNotNull null
                }
                Entry(slot.name, item, original, record.lastModified(), FileOps.measure(item))
            }
            .sortedByDescending { it.deletedAt }
    }

    /** Puts the item back where it came from, never over something that is there now. */
    fun restore(entry: Entry, volumeRoot: File): RestoreResult {
        val target = File(entry.originalPath)
        if (FileOps.exists(target)) return RestoreResult.TARGET_EXISTS
        target.parentFile?.mkdirs()
        if (!entry.item.renameTo(target)) return RestoreResult.FAILED
        forget(entry.id, volumeRoot)
        return RestoreResult.OK
    }

    /** Deletes one item for good. False when part of it could not be deleted. */
    fun purge(entry: Entry, volumeRoot: File): Boolean {
        if (!FileOps.deleteTree(entry.item)) return false
        forget(entry.id, volumeRoot)
        return true
    }

    /** Deletes the whole trash folder, stray files included. */
    fun empty(volumeRoot: File): Boolean = FileOps.deleteTree(dirFor(volumeRoot))

    private fun forget(id: String, volumeRoot: File) {
        val trash = dirFor(volumeRoot)
        File(trash, id).delete()
        File(trash, id + RECORD_EXT).delete()
    }

    private fun freeId(trash: File, now: Long): String {
        var id = now.toString()
        var n = 1
        while (File(trash, id).exists() || File(trash, id + RECORD_EXT).exists()) {
            id = "$now-${n++}"
        }
        return id
    }
}
