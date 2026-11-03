package local.daybook.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import local.daybook.datetime.DayCounter
import local.daybook.model.Category
import local.daybook.model.Entry
import local.daybook.model.EntryKind

@Composable
fun EntryDetail(state: AppViewModel, selectedId: String, today: LocalDate) {
    val content by state.detail.collectAsStateWithLifecycle()
    val busy by state.busy.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()
    val categoriesState by state.categories.collectAsStateWithLifecycle()
    var deleting by rememberSaveable(selectedId) { mutableStateOf(false) }
    var archiving by rememberSaveable(selectedId) { mutableStateOf(false) }
    var sharing by rememberSaveable(selectedId) { mutableStateOf(false) }
    var includeNote by rememberSaveable(selectedId) { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    BackHandler { if (!busy) state.closeDetail() }
    // 只按当前 ID 渲染，快速切换时不允许操作上次详情残留的内容。
    val entry = (content as? ReadState.Ready<Entry?>)?.value?.takeIf { it.id == selectedId }
    val categoryName = categoriesState.valueOrNull()
        ?.firstOrNull { it.id == entry?.categoryId }?.name
    AppBackdrop {
        Scaffold(containerColor = Color.Transparent) { insets ->
            Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = state::closeDetail, enabled = !busy) { Text("返回列表") }
                        Text("记录详情", style = MaterialTheme.typography.titleLarge)
                    }
                    when {
                        content is ReadState.Loading || (entry == null && (content as? ReadState.Ready<Entry?>)?.value != null) ->
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        content is ReadState.Failed -> GlassCard(Modifier.fillMaxWidth()) {
                            Text((content as ReadState.Failed).message)
                            TextButton(onClick = state::retryReads) { Text("重试") }
                        }
                        entry == null -> GlassCard(Modifier.fillMaxWidth()) {
                            Text("这条记录已不存在", style = MaterialTheme.typography.titleLarge)
                            Text("它可能已被删除，返回列表查看其他记录。")
                        }
                        else -> {
                            GlassCard(Modifier.fillMaxWidth()) {
                                AppIcon(entry.icon?.glyph()
                                    ?: if (entry.kind == EntryKind.DATE) AppGlyph.Calendar else AppGlyph.Check,
                                    tint = entry.color?.tint() ?: MaterialTheme.colorScheme.primary)
                                Text(entry.title, style = MaterialTheme.typography.headlineMedium)
                                Text(if (entry.kind == EntryKind.TODO && entry.completed) "已完成"
                                    else DayCounter().describe(entry.date, entry.countUp, entry.includeFirstDay),
                                    style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                                Text("以今天 $today 计算", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.isArchived()) Text("已归档 · 不参与当前统计，可随时恢复",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                                if (entry.pinned) Text("已置顶 · 在列表中排在最前",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                            SectionLabel("日期与规则")
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text("${if (entry.kind == EntryKind.DATE) "原始公历日期" else "计划日期"}：${entry.date}")
                                Text("分类：${categoryName ?: "未分类"}")
                                Text("外观：${entry.icon?.label() ?: "默认图标"} · ${entry.color?.label() ?: "主题色"}")
                                if (entry.kind == EntryKind.DATE) {
                                    Text("计数方式：${if (entry.countUp) "只计正数日" else "正倒数自动"}")
                                    Text("起点当天：${if (entry.countUp && entry.includeFirstDay) "算第 1 天" else "不额外加一天"}")
                                    Text("重复：无，单次日期")
                                } else {
                                    Text("状态：${if (entry.completed) "已完成" else "未完成"}")
                                    if (!entry.completed && entry.date < today)
                                        Text("已逾期 ${ChronoUnit.DAYS.between(entry.date, today)} 天", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            SectionLabel("完整备注")
                            GlassCard(Modifier.fillMaxWidth()) {
                                SelectionContainer { Text(entry.note.ifBlank { "还没有备注，点击编辑添加。" }) }
                            }
                            if (error != null || shareError != null) GlassCard(Modifier.fillMaxWidth()) {
                                Text(error ?: shareError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            }
                            Button(onClick = { state.openEditor(entry.kind, entry) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text("编辑记录") }
                            if (entry.kind == EntryKind.TODO) FilledTonalButton(onClick = { state.complete(entry) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text(if (entry.completed) "恢复为未完成" else "标记为完成") }
                            if (!entry.isArchived()) OutlinedButton(onClick = { state.setPinned(entry, !entry.pinned) },
                                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (entry.pinned) "取消置顶" else "置顶这条记录")
                            }
                            if (entry.isArchived()) {
                                OutlinedButton(onClick = { state.restore(entry) }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()) { Text("恢复为未归档") }
                                Text("恢复后回到正常列表；到期自动归档尚未实现，恢复不会再次自动隐藏。",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                OutlinedButton(onClick = { archiving = true }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()) { Text("归档这条记录") }
                            }
                            OutlinedButton(onClick = { includeNote = false; sharing = true }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text("分享文字摘要") }
                            TextButton(onClick = { deleting = true }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text("删除记录", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (deleting && entry != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text("删除这条记录？") }, text = { Text("“${entry.title}”将从本机删除，当前版本无法恢复。") },
        confirmButton = { TextButton(onClick = { state.delete(entry.id); deleting = false }, enabled = !busy) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("保留") } })
    if (archiving && entry != null) AlertDialog(onDismissRequest = { archiving = false },
        title = { Text("归档这条记录？") },
        text = { Text("归档不删除数据，记录会移动到“已归档”列表并暂停计入统计，可以随时恢复。") },
        confirmButton = { TextButton(onClick = { state.archive(entry); archiving = false }, enabled = !busy) { Text("归档") } },
        dismissButton = { TextButton(onClick = { archiving = false }) { Text("取消") } })
    if (sharing && entry != null) AlertDialog(onDismissRequest = { sharing = false },
        title = { Text("分享哪些内容？") }, text = {
            Column {
                Text("摘要包含标题、日期和当前状态，备注默认不包含。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeNote, onCheckedChange = { includeNote = it })
                    Text("同时包含备注")
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val text = buildString {
                    appendLine(entry.title)
                    appendLine("日期：${entry.date}")
                    appendLine(if (entry.kind == EntryKind.TODO && entry.completed) "已完成"
                        else DayCounter().describe(entry.date, entry.countUp, entry.includeFirstDay))
                    if (includeNote && entry.note.isNotBlank()) append(entry.note)
                }
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, "分享记录摘要"))
                    shareError = null
                } catch (_: ActivityNotFoundException) { shareError = "未找到可接收文字的应用。" }
                catch (_: SecurityException) { shareError = "系统未允许打开分享，请重试。" }
                sharing = false
            }) { Text("打开系统分享") }
        }, dismissButton = { TextButton(onClick = { sharing = false }) { Text("取消") } })
}
