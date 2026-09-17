package xx.diskmap

/**
 * One file or folder of a scanned tree. A folder's [size] and [files] are the
 * sums of everything under it.
 *
 * The tree is touched only on the main thread: the scanner builds detached
 * subtrees on a worker and they are swapped in here with [replaceChild].
 */
class Node(
    /** The plain name, or the absolute path for the root of a tree. */
    val name: String,
    val isDir: Boolean,
    size: Long,
    files: Long,
) {
    var size: Long = size
        private set
    var files: Long = files
        private set
    var parent: Node? = null
        private set

    /** Largest first; every change below keeps it so. */
    var children: List<Node> = emptyList()
        private set

    val path: String
        get() = parent?.let { "${it.path}/$name" } ?: name

    val root: Node
        get() = parent?.root ?: this

    fun setChildren(list: List<Node>) {
        list.forEach { it.parent = this }
        children = list.sortedByDescending { it.size }
    }

    /** The node at [absPath], or null when it is outside this tree or was never scanned. */
    fun find(absPath: String): Node? {
        val node = findNearest(absPath) ?: return null
        return if (node.path == absPath.trimEnd('/')) node else null
    }

    /**
     * The deepest node on the way to [absPath]: the node itself when it is in the
     * tree, otherwise the closest ancestor that is. Null when the path is outside
     * this tree altogether.
     */
    fun findNearest(absPath: String): Node? {
        val base = path
        val target = absPath.trimEnd('/')
        if (target == base) return this
        if (!target.startsWith("$base/")) return null
        var node = this
        for (part in target.substring(base.length + 1).split('/')) {
            if (part.isEmpty()) continue
            node = node.children.firstOrNull { it.name == part } ?: return node
        }
        return node
    }

    /** True when this node is [other] or holds it somewhere below. */
    fun contains(other: Node): Boolean {
        var n: Node? = other
        while (n != null) {
            if (n === this) return true
            n = n.parent
        }
        return false
    }

    /**
     * Puts [new] in place of the child [old]: either may be null, for a pure add
     * or a pure removal. The size and file-count change is carried up to the
     * root, and every folder on the way is re-sorted.
     */
    fun replaceChild(old: Node?, new: Node?) {
        require(old == null || old.parent === this) { "not a child of $path" }
        val list = children.filterNot { it === old }.toMutableList()
        if (new != null) list.add(new)
        old?.parent = null
        setChildren(list)

        val sizeDelta = (new?.size ?: 0L) - (old?.size ?: 0L)
        val filesDelta = (new?.files ?: 0L) - (old?.files ?: 0L)
        var n: Node? = this
        while (n != null) {
            n.size += sizeDelta
            n.files += filesDelta
            n.parent?.let { p -> p.children = p.children.sortedByDescending { it.size } }
            n = n.parent
        }
    }
}

/**
 * [nodes] without any that lie inside another of them: deleting a folder
 * takes what is in it along, and must not be asked to delete that again.
 */
fun topmost(nodes: List<Node>): List<Node> =
    nodes.filter { n -> nodes.none { it !== n && it.contains(n) } }
