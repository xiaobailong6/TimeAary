package local.daybook.model

/** 内容图标与颜色只存枚举名，颜色值由界面主题决定，方便深色模式自动适配。 */
enum class EntryIcon { STAR, GIFT, BOOK, WORK, TRAVEL, HEART, FLAG, NOTE }

enum class EntryColor { BLUE, TEAL, GREEN, ORANGE, PURPLE, RED, GRAY }

/** 未知或缺失的枚举名按“未选择”处理，不因历史数据中断读取。 */
fun parseEntryIcon(raw: String?): EntryIcon? =
    raw?.let { value -> EntryIcon.entries.firstOrNull { it.name == value } }

fun parseEntryColor(raw: String?): EntryColor? =
    raw?.let { value -> EntryColor.entries.firstOrNull { it.name == value } }
