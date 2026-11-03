package local.daybook.ui

import androidx.compose.ui.graphics.Color
import local.daybook.model.EntryColor
import local.daybook.model.EntryIcon

/** 内容图标在界面层的语义映射，数据库只保存枚举名。 */
fun EntryIcon.glyph(): AppGlyph = when (this) {
    EntryIcon.STAR -> AppGlyph.Star
    EntryIcon.GIFT -> AppGlyph.Gift
    EntryIcon.BOOK -> AppGlyph.Book
    EntryIcon.WORK -> AppGlyph.Work
    EntryIcon.TRAVEL -> AppGlyph.Travel
    EntryIcon.HEART -> AppGlyph.Heart
    EntryIcon.FLAG -> AppGlyph.Flag
    EntryIcon.NOTE -> AppGlyph.Note
}

fun EntryIcon.label(): String = when (this) {
    EntryIcon.STAR -> "星标"
    EntryIcon.GIFT -> "纪念"
    EntryIcon.BOOK -> "阅读"
    EntryIcon.WORK -> "工作"
    EntryIcon.TRAVEL -> "旅行"
    EntryIcon.HEART -> "心愿"
    EntryIcon.FLAG -> "目标"
    EntryIcon.NOTE -> "记录"
}

/** 颜色值只在这里定义，深浅色主题下沿用同一色相并在界面上降低透明度。 */
fun EntryColor.tint(): Color = when (this) {
    EntryColor.BLUE -> Color(0xFF245EC7)
    EntryColor.TEAL -> Color(0xFF167267)
    EntryColor.GREEN -> Color(0xFF2E7D32)
    EntryColor.ORANGE -> Color(0xFFB26A00)
    EntryColor.PURPLE -> Color(0xFF6A4BC7)
    EntryColor.RED -> Color(0xFFB32635)
    EntryColor.GRAY -> Color(0xFF5F6B7A)
}

fun EntryColor.label(): String = when (this) {
    EntryColor.BLUE -> "雾蓝"
    EntryColor.TEAL -> "青绿"
    EntryColor.GREEN -> "草绿"
    EntryColor.ORANGE -> "琥珀"
    EntryColor.PURPLE -> "紫罗兰"
    EntryColor.RED -> "砖红"
    EntryColor.GRAY -> "石灰"
}
