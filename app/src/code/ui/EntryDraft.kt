package local.daybook.ui

import java.time.LocalDate
import java.util.UUID
import local.daybook.model.Entry
import local.daybook.model.EntryColor
import local.daybook.model.EntryIcon
import local.daybook.model.EntryKind
import local.daybook.model.parseEntryColor
import local.daybook.model.parseEntryIcon

/** 草稿只保存必要字段；新建时固定 UUID，失败重试不会生成不同记录。 */
data class EntryDraft(
    val id: String,
    val kind: EntryKind,
    val title: String = "",
    val note: String = "",
    val date: String = LocalDate.now().toString(),
    val countUp: Boolean = false,
    val includeFirstDay: Boolean = false,
    val categoryId: String? = null,
    val icon: EntryIcon? = null,
    val color: EntryColor? = null,
) {
    fun encode(): ArrayList<String> = arrayListOf(
        id, kind.name, title, note, date, countUp.toString(), includeFirstDay.toString(),
        categoryId.orEmpty(), icon?.name.orEmpty(), color?.name.orEmpty(),
    )

    companion object {
        fun create(kind: EntryKind) = EntryDraft(UUID.randomUUID().toString(), kind)
        fun from(entry: Entry) = EntryDraft(entry.id, entry.kind, entry.title, entry.note,
            entry.date.toString(), entry.countUp, entry.includeFirstDay, entry.categoryId,
            entry.icon, entry.color)

        // 旧版本保存的七项 / 八项草稿仍可恢复，缺少的分类按未分类、外观按类型默认。
        fun decode(values: List<String>?): EntryDraft? {
            if (values == null || values.size !in 7..10) return null
            return runCatching {
                UUID.fromString(values[0])
                EntryDraft(values[0], EntryKind.valueOf(values[1]), values[2], values[3], values[4],
                    values[5].toBooleanStrict(), values[6].toBooleanStrict(),
                    values.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() },
                    parseEntryIcon(values.getOrNull(8)), parseEntryColor(values.getOrNull(9)))
            }.getOrNull()
        }
    }
}
