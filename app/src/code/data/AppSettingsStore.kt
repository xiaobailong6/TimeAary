package local.daybook.data

import android.content.Context
import local.daybook.model.AppSettings
import local.daybook.model.CardDensity
import local.daybook.model.FontScale
import local.daybook.model.ThemeMode

/** 外观设置只有几项短字符串，选择 SharedPreferences 而不是再建一张数据库表。 */
interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("ui-settings", Context.MODE_PRIVATE)

    override fun load(): AppSettings = AppSettings(
        themeMode = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        fontScale = enumOr(prefs.getString(KEY_FONT, null), FontScale.DEFAULT),
        density = enumOr(prefs.getString(KEY_DENSITY, null), CardDensity.STANDARD),
        navEntries = AppSettings.decodeNav(prefs.getString(KEY_NAV, null)),
    ).normalized()

    override fun save(settings: AppSettings) {
        val normalized = settings.normalized()
        prefs.edit()
            .putString(KEY_THEME, normalized.themeMode.name)
            .putString(KEY_FONT, normalized.fontScale.name)
            .putString(KEY_DENSITY, normalized.density.name)
            .putString(KEY_NAV, AppSettings.encodeNav(normalized.navEntries))
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    private companion object {
        const val KEY_THEME = "themeMode"
        const val KEY_FONT = "fontScale"
        const val KEY_DENSITY = "density"
        const val KEY_NAV = "navEntries"
    }
}

/** 测试与渲染预览使用内存实现，不写真实偏好文件。 */
class MemorySettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private var current = initial.normalized()

    override fun load(): AppSettings = current

    override fun save(settings: AppSettings) { current = settings.normalized() }
}
