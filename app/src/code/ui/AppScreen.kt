package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import local.daybook.BuildConfig
import java.time.LocalDate
import kotlinx.coroutines.delay
import local.daybook.datetime.DayCounter
import local.daybook.model.Entry
import local.daybook.model.EntryKind

@Composable
fun AppScreen(state: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val draft by state.draft.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val busy by state.busy.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    // 前台跨午夜时刷新日数；生命周期停止时不保持轮询。
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { today = LocalDate.now(); delay(30_000) }
        }
    }
    draft?.let {
        EntryEditor(it, busy, error, state::updateDraft, state::closeEditor, state::save)
        return
    }
    val pages = listOf("任务", "待办", "统计", "设置")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(32.dp), tonalElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(6.dp)) {
                    pages.forEachIndexed { index, label ->
                        if (tab == index) {
                            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(4.dp)) { Text(label) }
                        } else {
                            TextButton(onClick = { tab = index }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(4.dp)) { Text(label) }
                        }
                    }
                }
            }
        },
    ) { insets ->
        Column(Modifier.padding(insets).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(today.toString(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(pages[tab], style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (tab < 2) Button(onClick = {
                    state.openEditor(if (tab == 0) EntryKind.DATE else EntryKind.TODO)
                }, enabled = !busy) {
                    Text(if (tab == 0) "新建日期" else "新建待办")
                }
            }
            if (error != null) GlassCard(Modifier.fillMaxWidth()) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = state::clearError) { Text("知道了") }
            }
            when (tab) {
                0, 1 -> {
                    val entries = (if (tab == 0) state.dates else state.todos).collectAsLazyPagingItems()
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (entries.loadState.refresh is LoadState.Loading) {
                            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        }
                        if (entries.loadState.refresh is LoadState.Error) {
                            item { GlassCard(Modifier.fillMaxWidth()) {
                                Text("读取失败，请重试。原有数据不会被清空。")
                                TextButton(onClick = entries::retry) { Text("重试") }
                            } }
                        } else if (entries.itemCount == 0 && entries.loadState.refresh is LoadState.NotLoading) {
                            item { GlassCard(Modifier.fillMaxWidth()) {
                                Text(if (tab == 0) "留住一个重要的日子" else "从一件小事开始",
                                    style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text("点击右上角新建，内容仅保存在设备中。")
                            } }
                        }
                        items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
                            entries[index]?.let { entry ->
                                EntryCard(entry, today, busy,
                                    onEdit = { state.openEditor(entry.kind, entry) },
                                    onDelete = { deleting = entry }, onComplete = { state.complete(entry) })
                            }
                        }
                        if (entries.loadState.append is LoadState.Error) {
                            item { TextButton(onClick = entries::retry) { Text("加载更多失败，点击重试") } }
                        }
                        if (entries.loadState.append is LoadState.Loading) {
                            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        }
                    }
                }
                2 -> GlassCard(Modifier.fillMaxWidth()) {
                    Text("统计尚在开发", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("趋势、贡献图和时间线将在数值任务与记录模型完成后接入。这里不展示模拟统计。")
                }
                3 -> GlassCard(Modifier.fillMaxWidth()) {
                    Text("本地优先", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("无账号、无会员、无追踪。当前支持跟随系统深浅色；数据仅保存在设备中，卸载会删除数据。")
                    Spacer(Modifier.height(12.dp))
                    Text("备份、应用锁、提醒及其他个性化设置仍在开发，请勿将当前开发版用作唯一数据存储。")
                    Spacer(Modifier.height(12.dp))
                    Text("开发版本 ${BuildConfig.VERSION_NAME} · GPL-3.0-only")
                }
            }
        }
    }
    deleting?.let { entry ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条记录？") },
            text = { Text("“${entry.title}”将从设备中删除，当前版本无法恢复。") },
            confirmButton = { TextButton(onClick = { state.delete(entry.id); deleting = null }, enabled = !busy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(entry: Entry, today: LocalDate, busy: Boolean,
                      onEdit: () -> Unit, onDelete: () -> Unit, onComplete: () -> Unit) {
    val description = remember(entry, today) { DayCounter().describe(entry.date, entry.countUp, entry.includeFirstDay) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(entry.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(if (entry.kind == EntryKind.TODO && entry.completed) "已完成" else description,
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(entry.date.toString(), style = MaterialTheme.typography.bodyMedium)
        if (entry.note.isNotBlank()) Text(entry.note, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry.kind == EntryKind.TODO) TextButton(onClick = onComplete, enabled = !busy) {
                Text(if (entry.completed) "设为未完成" else "完成")
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text("编辑") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("删除") }
        }
    }
}

