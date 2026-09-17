package xx.diskmap

import android.app.LocaleManager
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Light/dark override; SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { RINGS, TILES, LIST }

/** How many accents the palette offers; the colours themselves are in ui/Common.kt. */
const val ACCENT_COUNT = 6

/**
 * Process-wide settings, backed by SharedPreferences and exposed as [StateFlow]
 * for Compose. Call [load] once before the UI reads them.
 */
object AppSettings {
    private const val PREFS = "diskmap"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ACCENT = "accent_index"
    private const val KEY_VIEW = "view_mode"
    private const val KEY_TO_TRASH = "to_trash"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(0)
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.RINGS)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _toTrash = MutableStateFlow(true)

    /** Where the delete dialog's switch starts: the last choice made in it. */
    val toTrash: StateFlow<Boolean> = _toTrash.asStateFlow()

    fun load(context: Context) {
        val prefs = prefs(context)
        _themeMode.value = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM)
        _accentIndex.value = prefs.getInt(KEY_ACCENT, 0).coerceIn(0, ACCENT_COUNT - 1)
        _viewMode.value = enumOr(prefs.getString(KEY_VIEW, null), ViewMode.RINGS)
        _toTrash.value = prefs.getBoolean(KEY_TO_TRASH, true)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        prefs(context).edit { putString(KEY_THEME, mode.name) }
    }

    fun setAccentIndex(context: Context, index: Int) {
        val clamped = index.coerceIn(0, ACCENT_COUNT - 1)
        _accentIndex.value = clamped
        prefs(context).edit { putInt(KEY_ACCENT, clamped) }
    }

    fun setViewMode(context: Context, mode: ViewMode) {
        _viewMode.value = mode
        prefs(context).edit { putString(KEY_VIEW, mode.name) }
    }

    fun setToTrash(context: Context, on: Boolean) {
        _toTrash.value = on
        prefs(context).edit { putBoolean(KEY_TO_TRASH, on) }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ---------- Language ----------

/** One entry of the language picker; [tag] empty means "follow the device". */
data class LanguageOption(val tag: String, val label: String)

/**
 * The languages this build ships, read from res/xml/locales_config.xml, each
 * named in itself. Read from the XML directly: LocaleConfig, which would do it,
 * needs API 34.
 */
fun supportedLanguages(context: Context, systemLabel: String): List<LanguageOption> {
    val tags = mutableListOf<String>()
    context.resources.getXml(R.xml.locales_config).use { parser ->
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
            }
        }
    }
    val languages = tags.map { tag ->
        val locale = Locale.forLanguageTag(tag)
        LanguageOption(
            tag = tag,
            label = locale.getDisplayLanguage(locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
        )
    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    return listOf(LanguageOption("", systemLabel)) + languages
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** The per-app language the platform holds; empty for "follow the device". */
fun currentLanguageTag(context: Context): String =
    context.getSystemService(LocaleManager::class.java)
        .applicationLocales
        .takeUnless { it.isEmpty }
        ?.get(0)
        ?.toLanguageTag()
        .orEmpty()

/** The system persists the choice and recreates the activity. */
fun setLanguageTag(context: Context, tag: String) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}
