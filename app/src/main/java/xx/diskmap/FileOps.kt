package xx.diskmap

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/** File operations that must not follow symbolic links. */
object FileOps {

    fun attributes(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (e: Exception) {
        null
    }

    /** True for anything at [file], a dangling link included. */
    fun exists(file: File): Boolean = attributes(file) != null

    /** Bytes under [file], counted the way [Scanner] counts them. */
    fun measure(file: File): Long {
        val attrs = attributes(file) ?: return 0L
        if (!attrs.isDirectory) return if (attrs.isRegularFile) attrs.size() else 0L
        return file.listFiles().orEmpty().sumOf { measure(it) }
    }

    /**
     * Deletes [file] and everything under it. A link is removed as a link: the
     * folder it points at is left alone, which File.deleteRecursively does not
     * promise. Goes on past a failure so as much as possible is freed; false
     * when anything is left.
     */
    fun deleteTree(file: File): Boolean {
        val attrs = attributes(file) ?: return true
        var ok = true
        if (attrs.isDirectory) {
            file.listFiles().orEmpty().forEach { if (!deleteTree(it)) ok = false }
        }
        return file.delete() && ok
    }
}
