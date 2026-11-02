package local.daybook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import local.daybook.datetime.DayCounter
import local.daybook.model.EntryKind

@Composable
fun EntryEditor(draft: EntryDraft, busy: Boolean, error: String?,
                onChange: (EntryDraft) -> Unit, onClose: () -> Unit, onSave: () -> Unit) {
    var confirmExit by rememberSaveable(draft.id) { mutableStateOf(false) }
    BackHandler { if (!busy) confirmExit = true }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(Modifier.padding(insets).imePadding().fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (draft.kind == EntryKind.DATE) "记录一个日子" else "安排一件待办",
                style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(draft.title, { onChange(draft.copy(title = it.take(80))) },
                label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(draft.date, { onChange(draft.copy(date = it.take(10))) },
                label = { Text("公历日期 · YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            if (draft.kind == EntryKind.DATE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(draft.countUp, { onChange(draft.copy(countUp = it)) }, enabled = !busy)
                    Text("正数日模式", Modifier.padding(start = 12.dp))
                }
                if (draft.countUp) Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(draft.includeFirstDay, { onChange(draft.copy(includeFirstDay = it)) }, enabled = !busy)
                    Text("当天算第 1 天", Modifier.padding(start = 12.dp))
                }
                val preview = try {
                    DayCounter().describe(LocalDate.parse(draft.date), draft.countUp, draft.includeFirstDay)
                } catch (_: java.time.DateTimeException) { "输入有效日期后显示预览" }
                Text(preview, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(draft.note, { onChange(draft.copy(note = it.take(4000))) },
                label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(),
                enabled = !busy, minLines = 3, maxLines = 6)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = onSave, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (busy) "正在保存…" else "保存")
            }
            TextButton(onClick = { confirmExit = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text("放弃本次编辑？") }, text = { Text("未保存的修改将丢失。") },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("继续编辑") } })
}
