package xx.diskmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class CoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, bytes: Int): File =
        File(tmp.root, path).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(bytes))
        }

    // ---------- Scanner and Node ----------

    @Test
    fun scanSumsSizesAndSortsLargestFirst() {
        file("a/x", 10)
        file("a/y", 30)
        file("b/z", 5)
        file("top", 1)
        val root = Scanner.scanTree(tmp.root)

        assertEquals(46L, root.size)
        assertEquals(4L, root.files)
        assertEquals(listOf("a", "b", "top"), root.children.map { it.name })
        assertEquals(listOf("y", "x"), root.children[0].children.map { it.name })
        assertEquals(tmp.root.absolutePath + "/a/y", root.children[0].children[0].path)
    }

    @Test
    fun scanDoesNotFollowSymlinks() {
        file("real/big", 100)
        Files.createSymbolicLink(File(tmp.root, "link").toPath(), File(tmp.root, "real").toPath())
        val root = Scanner.scanTree(tmp.root)

        assertEquals(100L, root.size)
        val link = root.find(tmp.root.absolutePath + "/link")!!
        assertFalse(link.isDir)
        assertEquals(0L, link.size)
    }

    @Test
    fun findAndFindNearest() {
        file("a/b/c", 1)
        val root = Scanner.scanTree(tmp.root)
        val base = tmp.root.absolutePath

        assertSame(root, root.find(base))
        assertSame(root, root.find("$base/"))
        assertEquals("c", root.find("$base/a/b/c")?.name)
        assertNull(root.find("$base/a/missing"))
        assertEquals("a", root.findNearest("$base/a/missing/deeper")?.name)
        assertNull(root.find(base + "x/a"))
        assertNull(root.findNearest("/elsewhere"))
    }

    @Test
    fun replaceChildCarriesSizeUpAndResorts() {
        file("a/x", 10)
        file("b/y", 20)
        val root = Scanner.scanTree(tmp.root)
        val a = root.children.first { it.name == "a" }
        assertEquals("b", root.children[0].name)

        file("a/more", 50)
        val fresh = Scanner.scanChild(File(tmp.root, "a"))
        root.replaceChild(a, fresh)

        assertEquals(80L, root.size)
        assertEquals(3L, root.files)
        assertEquals("a", root.children[0].name)
        assertSame(root, fresh.parent)
        assertNull(a.parent)

        val x = fresh.children.first { it.name == "x" }
        fresh.replaceChild(x, null)
        assertEquals(50L, fresh.size)
        assertEquals(70L, root.size)
        assertEquals(2L, root.files)
    }

    @Test
    fun containsIsTheAncestorTest() {
        file("a/b/c", 1)
        val root = Scanner.scanTree(tmp.root)
        val a = root.children[0]
        val c = a.children[0].children[0]
        assertTrue(a.contains(c))
        assertTrue(c.contains(c))
        assertFalse(c.contains(a))
    }

    @Test
    fun topmostDropsWhatIsInsideAnotherPick() {
        file("a/b/c", 1)
        file("d", 1)
        val root = Scanner.scanTree(tmp.root)
        val a = root.children.first { it.name == "a" }
        val b = a.children[0]
        val c = b.children[0]
        val d = root.children.first { it.name == "d" }

        assertEquals(listOf(a, d), topmost(listOf(c, a, d, b)).sortedBy { it.name })
        assertEquals(listOf(c), topmost(listOf(c)))
        assertTrue(topmost(emptyList()).isEmpty())
    }

    @Test
    fun cancelCheckStopsTheScan() {
        file("a/b", 1)
        var calls = 0
        val thrown = runCatching {
            Scanner.scanTree(tmp.root) { if (++calls > 1) throw IllegalStateException("stop") }
        }.exceptionOrNull()
        assertNotNull(thrown)
    }

    // ---------- FileOps ----------

    @Test
    fun deleteTreeRemovesALinkButNotItsTarget() {
        file("keep/data", 7)
        val victim = File(tmp.root, "victim").apply { mkdirs() }
        Files.createSymbolicLink(File(victim, "link").toPath(), File(tmp.root, "keep").toPath())

        assertTrue(FileOps.deleteTree(victim))
        assertFalse(victim.exists())
        assertTrue(File(tmp.root, "keep/data").exists())
    }

    // ---------- Trash ----------

    @Test
    fun trashRoundTrip() {
        val volume = tmp.root
        val item = file("docs/report", 12)

        assertTrue(Trash.moveToTrash(item, volume, now = 1000))
        assertFalse(item.exists())
        val entries = Trash.list(volume)
        assertEquals(1, entries.size)
        assertEquals(item.absolutePath, entries[0].originalPath)
        assertEquals(12L, entries[0].size)
        assertTrue(Trash.isInTrash(entries[0].item.absolutePath, volume))
        assertFalse(Trash.isInTrash(item.absolutePath, volume))

        assertEquals(Trash.RestoreResult.OK, Trash.restore(entries[0], volume))
        assertTrue(item.exists())
        assertTrue(Trash.list(volume).isEmpty())
        assertEquals(emptyList<String>(), Trash.dirFor(volume).list()!!.toList())
    }

    @Test
    fun sameNameTwiceGetsTwoSlots() {
        val volume = tmp.root
        file("x", 1)
        assertTrue(Trash.moveToTrash(File(volume, "x"), volume, now = 5))
        file("x", 2)
        assertTrue(Trash.moveToTrash(File(volume, "x"), volume, now = 5))
        assertEquals(setOf(1L, 2L), Trash.list(volume).map { it.size }.toSet())
    }

    @Test
    fun restoreNeverOverwrites() {
        val volume = tmp.root
        val item = file("x", 1)
        Trash.moveToTrash(item, volume)
        file("x", 9)
        val entry = Trash.list(volume).single()

        assertEquals(Trash.RestoreResult.TARGET_EXISTS, Trash.restore(entry, volume))
        assertEquals(9L, item.length())
        assertEquals(1, Trash.list(volume).size)
    }

    @Test
    fun restoreRecreatesMissingParents() {
        val volume = tmp.root
        val item = file("deep/er/x", 3)
        Trash.moveToTrash(item, volume)
        File(volume, "deep").deleteRecursively()

        assertEquals(Trash.RestoreResult.OK, Trash.restore(Trash.list(volume).single(), volume))
        assertEquals(3L, item.length())
    }

    @Test
    fun purgeAndEmpty() {
        val volume = tmp.root
        Trash.moveToTrash(file("a", 1), volume)
        Trash.moveToTrash(file("b", 1), volume)
        val entries = Trash.list(volume)

        assertTrue(Trash.purge(entries[0], volume))
        assertEquals(1, Trash.list(volume).size)
        assertTrue(Trash.empty(volume))
        assertFalse(Trash.dirFor(volume).exists())
        assertTrue(Trash.list(volume).isEmpty())
    }

    @Test
    fun failedMoveLeavesNothingBehind() {
        val volume = tmp.root
        assertFalse(Trash.moveToTrash(File(volume, "missing"), volume))
        assertEquals(emptyList<String>(), Trash.dirFor(volume).list()!!.toList())
    }
}
