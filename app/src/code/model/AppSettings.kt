package local.daybook.model

/** 外观与入口设置；只保存枚举名，不保存颜色值，方便以后换主题不迁移数据。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 字号系数乘到主题字号上，系统字体缩放仍会继续叠加。 */
enum class FontScale(val factor: Float) { SMALL(0.9f), DEFAULT(1f), MEDIUM(1.1f), LARGE(1.25f) }

/** 卡片密度只影响列表卡片的间距与摘要行数。 */
enum class CardDensity { COMPACT, STANDARD }

enum class NavEntry { TASKS, TODOS, ANALYSIS, SETTINGS }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.DEFAULT,
    val density: CardDensity = CardDensity.STANDARD,
    val navEntries: List<NavEntry> =
        listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.SETTINGS),
) {
    /** 设置页必须始终可达，因此设置入口不可隐藏；其余入口至少保留一个。 */
    fun normalized(): AppSettings {
        val entries = navEntries.distinct().toMutableList()
        entries.remove(NavEntry.SETTINGS)
        if (entries.isEmpty()) entries.add(NavEntry.TASKS)
        entries.add(NavEntry.SETTINGS)
        return copy(navEntries = entries)
    }

    fun isVisible(entry: NavEntry): Boolean = entry in navEntries

    /** 隐藏或显示某个入口；设置入口保持不变。 */
    fun toggleEntry(entry: NavEntry): AppSettings {
        if (entry == NavEntry.SETTINGS) return this
        val entries = navEntries.toMutableList()
        if (entry in entries) {
            entries.remove(entry)
            if (entries.none { it != NavEntry.SETTINGS }) return this
        } else {
            entries.add(entry)
        }
        return copy(navEntries = entries).normalized()
    }

    /** 在可见入口内上移（offset = -1）或下移（offset = 1）。 */
    fun moveEntry(entry: NavEntry, offset: Int): AppSettings {
        val entries = navEntries.toMutableList()
        val index = entries.indexOf(entry)
        if (index < 0) return this
        val target = (index + offset).coerceIn(0, entries.lastIndex)
        if (target == index) return this
        entries.removeAt(index)
        entries.add(target, entry)
        return copy(navEntries = entries)
    }

    companion object {
        fun decodeNav(raw: String?): List<NavEntry> = raw.orEmpty()
            .split(',')
            .mapNotNull { value -> NavEntry.entries.firstOrNull { it.name == value.trim() } }
            .ifEmpty { listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.SETTINGS) }

        fun encodeNav(entries: List<NavEntry>): String = entries.joinToString(",") { it.name }
    }
}
