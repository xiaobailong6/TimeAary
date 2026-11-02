package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import local.daybook.model.*

fun EntryFilter.label(): String = when (this) {
    EntryFilter.ALL -> "全部"
    EntryFilter.TODAY -> "当天"
    EntryFilter.FUTURE -> "未来"
    EntryFilter.PAST -> "过去"
    EntryFilter.OPEN -> "未完成"
    EntryFilter.DONE -> "已完成"
    EntryFilter.OVERDUE -> "逾期未完成"
}
fun EntrySort.label(): String = when (this) {
    EntrySort.DATE_ASC -> "日期从早到晚"
    EntrySort.DATE_DESC -> "日期从晚到早"
    EntrySort.TITLE -> "标题排序"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryControls(kind: EntryKind, options: EntryOptions, onChange: (EntryOptions) -> Unit) {
    var sorting by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = options.search, onValueChange = { onChange(options.copy(search = it.take(80))) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("搜索标题或备注") },
            trailingIcon = { if (options.search.isNotEmpty()) TextButton(onClick = { onChange(options.copy(search = "")) }) { Text("清除") } })
        val filters = if (kind == EntryKind.DATE)
            listOf(EntryFilter.ALL, EntryFilter.TODAY, EntryFilter.FUTURE, EntryFilter.PAST)
        else listOf(EntryFilter.ALL, EntryFilter.OPEN, EntryFilter.DONE, EntryFilter.TODAY, EntryFilter.OVERDUE)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { filter ->
                FilterChip(selected = options.filter == filter,
                    onClick = { onChange(options.copy(filter = filter)) }, label = { Text(filter.label()) })
            }
        }
        Row {
            Box {
                TextButton(onClick = { sorting = true }) { Text(options.sort.label()) }
                DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                    EntrySort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(sort.label()) },
                            onClick = { onChange(options.copy(sort = sort)); sorting = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (options != EntryOptions()) TextButton(onClick = { onChange(EntryOptions()) }) { Text("重置条件") }
        }
    }
}
