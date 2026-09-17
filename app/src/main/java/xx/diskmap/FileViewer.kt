package xx.diskmap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Opens files in whatever app the system picks for their type. */
object FileViewer {

    /** The type the extension names, or any type when it names none. */
    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
            ?: "*/*"

    /**
     * False when no app takes the file, or the provider refuses it. [activity]
     * must be the screen itself: the viewer then opens in this app's task and
     * Back returns here. Started from the application context it needed a task
     * of its own, and the system brought forward whatever task was last.
     */
    fun open(activity: Activity, file: File): Boolean {
        val context: Context = activity
        val uri = try {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
