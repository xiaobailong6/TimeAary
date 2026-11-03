package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import local.daybook.model.EntryKind

private data class CreateTypeItem(
    val title: String,
    val glyph: AppGlyph,
    val note: String,
    val kind: EntryKind?,
)

/** 已实现类型放在前面；未实现类型保留 2 列位置并标注“未实现”，点击无效也不误导。 */
private val CreateTypes = listOf(
    CreateTypeItem("日期记录", AppGlyph.Calendar, "正数 / 倒数日", EntryKind.DATE),
    CreateTypeItem("待办", AppGlyph.Check, "完成与反选", EntryKind.TODO),
    CreateTypeItem("数值进度", AppGlyph.Chart, "未实现", null),
    CreateTypeItem("计数", AppGlyph.Note, "未实现", null),
    CreateTypeItem("目标 / 习惯", AppGlyph.Flag, "未实现", null),
    CreateTypeItem("专注", AppGlyph.Star, "未实现", null),
)

/**
 * 新建类型面板：2 列入口，底部玻璃面板。
 * 只有已实现类型可点击，其余入口保留位置但不提供假表单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTypePanel(visible: Boolean, onDismiss: () -> Unit, onCreate: (EntryKind) -> Unit) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("新建", style = MaterialTheme.typography.headlineMedium)
            Text("先选类型，表单再按类型分组填写。未实现的类型会在正式版补上。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            CreateTypes.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        CreateTypeCell(glyph = item.glyph, title = item.title, note = item.note,
                            supported = item.kind != null, modifier = Modifier.weight(1f)) {
                            item.kind?.let(onCreate)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}
