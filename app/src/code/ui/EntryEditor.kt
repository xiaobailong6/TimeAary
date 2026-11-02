package local.daybook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import local.daybook.datetime.DayCounter
import local.daybook.datetime.CalendarDates
import local.daybook.model.EntryKind

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditor(draft: EntryDraft, busy: Boolean, error: String?,
                onChange: (EntryDraft) -> Unit, onClose: () -> Unit, onSave: () -> Unit) {
    var confirmExit by rememberSaveable(draft.id) { mutableStateOf(false) }
    var choosingDate by rememberSaveable(draft.id) { mutableStateOf(false) }
    val parsed = remember(draft.date) { CalendarDates.parseSupported(draft.date) }
    BackHandler { if (!busy) confirmExit = true }
    AppBackdrop {
        Scaffold(containerColor = Color.Transparent,
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 2.dp) {
                    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onSave, enabled = !busy && draft.title.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
                            Text(if (busy) "正在保存…" else "保存到我的记录")
                        }
                    }
                }
            }) { insets ->
            Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 700.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { confirmExit = true }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "返回，取消本次编辑" }) {
                            AppIcon(AppGlyph.Back)
                        }
                        Text(if (draft.kind == EntryKind.DATE) "记录一个日子" else "安排一件待办",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    }
                    Text("把重要的事，认真记下来。", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionLabel("基本信息")
                    GlassCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(draft.title, { onChange(draft.copy(title = it.take(80))) },
                            label = { Text("标题") }, placeholder = { Text(if (draft.kind == EntryKind.DATE) "例如：出发的日子" else "例如：读完一章书") },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                            shape = RoundedCornerShape(16.dp), supportingText = { Text("${draft.title.length} / 80") })
                        OutlinedTextField(draft.date, { onChange(draft.copy(date = it.take(10))) },
                            label = { Text(if (draft.kind == EntryKind.DATE) "公历日期" else "计划日期") },
                            modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                            isError = parsed == null, shape = RoundedCornerShape(16.dp),
                            supportingText = { Text("YYYY-MM-DD，支持 1900～2200 年") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            trailingIcon = {
                                IconButton(onClick = { choosingDate = true }, enabled = !busy,
                                    modifier = Modifier.semantics { contentDescription = "打开日历选择日期" }) {
                                    AppIcon(AppGlyph.Calendar)
                                }
                            })
                    }
                    if (draft.kind == EntryKind.DATE) {
                        SectionLabel("如何计算日数")
                        GlassCard(Modifier.fillMaxWidth()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !draft.countUp,
                                    onClick = { onChange(draft.copy(countUp = false)) }, label = { Text("正倒数自动") }, enabled = !busy)
                                FilterChip(selected = draft.countUp,
                                    onClick = { onChange(draft.copy(countUp = true)) }, label = { Text("只计正数日") }, enabled = !busy)
                            }
                            Text(if (draft.countUp) "从起点记录已过天数；未来起点显示尚未开始。"
                                else "未来显示还有多少天，过去显示已经过多少天。",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (draft.countUp) {
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("当天算第 1 天", style = MaterialTheme.typography.titleMedium)
                                        Text("仅改变开始后的日数展示", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(draft.includeFirstDay, { onChange(draft.copy(includeFirstDay = it)) }, enabled = !busy,
                                        modifier = Modifier.semantics { contentDescription = "当天算第 1 天" })
                                }
                            }
                        }
                    }
                    SectionLabel("备注")
                    GlassCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(draft.note, { onChange(draft.copy(note = it.take(4000))) },
                            label = { Text("想记住的话（可选）") }, modifier = Modifier.fillMaxWidth(),
                            enabled = !busy, minLines = 3, maxLines = 6, shape = RoundedCornerShape(16.dp))
                    }
                    SectionLabel("保存预览")
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(draft.title.ifBlank { "你的记录" }, style = MaterialTheme.typography.titleMedium)
                        val preview = parsed?.let { DayCounter().describe(it, draft.countUp, draft.includeFirstDay) }
                            ?: "选择有效日期后显示预览"
                        Text(preview, style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("日期与备注仅保存在本机", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (choosingDate) {
        // Material 日期选择器以 UTC 零点表示日历日，不能按设备偏移再次转换。
        val picker = rememberDatePickerState(initialSelectedDateMillis =
            CalendarDates.toPickerMillis(parsed ?: LocalDate.now().coerceIn(LocalDate.of(1900, 1, 1), LocalDate.of(2200, 12, 31))),
            yearRange = 1900..2200)
        DatePickerDialog(onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        onChange(draft.copy(date = CalendarDates.fromPickerMillis(it).toString()))
                    }
                    choosingDate = false
                }, enabled = picker.selectedDateMillis != null && !busy) { Text("使用这个日期") }
            }, dismissButton = { TextButton(onClick = { choosingDate = false }) { Text("取消") } }) {
            DatePicker(state = picker, showModeToggle = true)
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text("放弃本次编辑？") }, text = { Text("未保存的修改将丢失。") },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("继续编辑") } })
}
