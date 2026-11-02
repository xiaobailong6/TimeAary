package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import local.daybook.model.Entry
import local.daybook.model.EntryKind

@Composable
fun AppScreen(state: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var referenceDay by rememberSaveable { mutableStateOf<String?>(null) }
    val draft by state.draft.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val busy by state.busy.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    val reference = referenceDay?.let(LocalDate::parse) ?: today
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        // 前台跨日刷新；未手动选择日期时自动跟随今天。
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { today = LocalDate.now(); delay(30_000) }
        }
    }
    draft?.let {
        EntryEditor(it, busy, error, state::updateDraft, state::closeEditor, state::save)
        return
    }
    val titles = listOf("把日子，放在心上", "一件一件，从容完成", "看见时间的积累", "按你的节奏")
    val pages = listOf("任务", "待办", "统计", "设置")
    val dateList = rememberLazyListState()
    val todoList = rememberLazyListState()
    val otherList = rememberLazyListState()
    AppBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Box(Modifier.statusBarsPadding().padding(start = 20.dp, top = 28.dp)) {
                    GlassNavigation(tab, vertical = true, onSelect = { tab = it })
                }
                Scaffold(modifier = Modifier.weight(1f), containerColor = Color.Transparent,
                    bottomBar = {
                        if (!wide) Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.widthIn(max = 480.dp)) { GlassNavigation(tab, onSelect = { tab = it }) }
                        }
                    }) { insets ->
                    val entries = (if (tab == 1) state.todos else state.dates).collectAsLazyPagingItems()
                    Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(),
                            state = when (tab) { 0 -> dateList; 1 -> todoList; else -> otherList },
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item(key = "header-$tab") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(today.format(DateTimeFormatter.ofPattern("M 月 d 日  EEEE", java.util.Locale.CHINA)),
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(pages[tab], style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                                        if (tab < 2) FilledTonalButton(onClick = {
                                            state.openEditor(if (tab == 0) EntryKind.DATE else EntryKind.TODO)
                                        }, enabled = !busy, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                            AppIcon(AppGlyph.Plus, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            Spacer(Modifier.width(6.dp)); Text("新建")
                                        }
                                    }
                                    Text(titles[tab], style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (error != null) item(key = "error") {
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = state::clearError) { Text("关闭提示") }
                                }
                            }
                            if (tab < 2) {
                                item(key = "week") {
                                    WeekCalendar(reference, today) { referenceDay = if (it == today) null else it.toString() }
                                }
                                item(key = "section") {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SectionLabel(if (tab == 0) "我的日期" else "我的待办")
                                        Text(if (reference == today) "全部记录 · 日数以今天为准"
                                            else "全部记录 · 日数以 $reference 为准",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (entries.loadState.refresh is LoadState.Loading) item(key = "loading") {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                if (entries.loadState.refresh is LoadState.Error) item(key = "read-error") {
                                    GlassCard(Modifier.fillMaxWidth()) {
                                        Text("暂时无法读取记录", style = MaterialTheme.typography.titleMedium)
                                        Text("原有内容不会被清空，请重试。")
                                        TextButton(onClick = entries::retry) { Text("重新加载") }
                                    }
                                } else if (entries.itemCount == 0 && entries.loadState.refresh is LoadState.NotLoading) {
                                    item(key = "empty") {
                                        GlassCard(Modifier.fillMaxWidth()) {
                                            AppIcon(if (tab == 0) AppGlyph.Calendar else AppGlyph.Check, Modifier.size(40.dp))
                                            Text(if (tab == 0) "总有一天，值得记住" else "今天，想完成什么？",
                                                style = MaterialTheme.typography.titleLarge)
                                            Text(if (tab == 0) "生日、相遇、出发的日子。记下日期，让时间替你计数。"
                                                else "把眼前的一件小事写下来，完成后轻轻勾选。",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            TextButton(onClick = { state.openEditor(if (tab == 0) EntryKind.DATE else EntryKind.TODO) },
                                                enabled = !busy) { Text(if (tab == 0) "记录第一个日子" else "添加第一件待办") }
                                        }
                                    }
                                }
                                items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
                                    entries[index]?.let { entry ->
                                        EntryCard(entry, reference, busy,
                                            onEdit = { state.openEditor(entry.kind, entry) },
                                            onDelete = { deleting = entry }, onComplete = { state.complete(entry) })
                                    }
                                }
                                if (entries.loadState.append is LoadState.Error) item(key = "append-error") {
                                    TextButton(onClick = entries::retry) { Text("加载更多失败，点击重试") }
                                }
                                if (entries.loadState.append is LoadState.Loading) item(key = "append-loading") {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            } else if (tab == 2) item { AnalysisIntro() }
                            else item { SettingsOverview() }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { entry ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条记录？") },
            text = { Text("“${entry.title}”将从设备中删除，当前版本无法恢复。") },
            confirmButton = { TextButton(onClick = { state.delete(entry.id); deleting = null }, enabled = !busy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("保留") } })
    }
}
