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
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import local.daybook.model.*

@Composable
fun AppScreen(state: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val draft by state.draft.collectAsStateWithLifecycle()
    val detailId by state.detailId.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val busy by state.busy.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()
    val today by state.today.collectAsStateWithLifecycle()
    val reference by state.reference.collectAsStateWithLifecycle()
    val dateOptions by state.dateOptions.collectAsStateWithLifecycle()
    val todoOptions by state.todoOptions.collectAsStateWithLifecycle()
    val dateList = rememberLazyListState()
    val todoList = rememberLazyListState()
    val analysisList = rememberLazyListState()
    val settingsList = rememberLazyListState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refreshToday() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { state.refreshToday(); delay(30_000) }
        }
    }
    draft?.let {
        EntryEditor(it, busy, error, state::updateDraft, state::closeEditor, state::save)
        return
    }
    detailId?.let { EntryDetail(state, it, today); return }
    val titles = listOf("把日子，放在心上", "一件一件，从容完成", "看见记录的分布", "按你的节奏")
    val pages = listOf("任务", "待办", "统计", "设置")
    val kind = if (tab == 1) EntryKind.TODO else EntryKind.DATE
    val options = if (tab == 1) todoOptions else dateOptions
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
                    // 分类型隔离 Paging UI 状态，切页不把旧类型条目当作新类型操作。
                    key(kind) {
                        val entries = (if (tab == 1) state.todos else state.dates).collectAsLazyPagingItems()
                        val listState = when (tab) { 0 -> dateList; 1 -> todoList; 2 -> analysisList; else -> settingsList }
                        Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), state = listState,
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                item(key = "header-$tab") {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(today.format(DateTimeFormatter.ofPattern("M 月 d 日  EEEE", java.util.Locale.CHINA)),
                                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(pages[tab], style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                                            if (tab < 2) FilledTonalButton(onClick = { state.openEditor(kind) }, enabled = !busy) {
                                                AppIcon(AppGlyph.Plus, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                                Spacer(Modifier.width(6.dp)); Text("新建")
                                            }
                                        }
                                        Text(titles[tab], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (error != null) item(key = "error") {
                                    GlassCard(Modifier.fillMaxWidth()) {
                                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = state::clearError) { Text("关闭提示") }
                                    }
                                }
                                if (tab < 2) {
                                    item(key = "week") { WeekCalendar(reference, today, state::selectReference) }
                                    item(key = "controls") { EntryControls(kind, options) { state.updateOptions(kind, it) } }
                                    item(key = "section") {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            SectionLabel(if (tab == 0) "我的日期" else "我的待办")
                                            Text("${options.filter.label()} · 筛选和日数以 $reference 为准",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (tab == 1) Text("未完成优先，组内按${options.sort.label()}。历史参考日不改变实际完成状态。",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (entries.loadState.refresh is LoadState.Loading) item(key = "loading") {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                    if (entries.loadState.refresh is LoadState.Error) item(key = "read-error") {
                                        ReadErrorCard("暂时无法读取记录，原有内容不会被清空。", entries::retry)
                                    } else if (entries.itemCount == 0 && entries.loadState.refresh is LoadState.NotLoading) {
                                        item(key = "empty") {
                                            val constrained = options.search.isNotBlank() || options.filter != EntryFilter.ALL
                                            GlassCard(Modifier.fillMaxWidth()) {
                                                AppIcon(if (tab == 0) AppGlyph.Calendar else AppGlyph.Check, Modifier.size(40.dp))
                                                Text(if (constrained) "没有匹配的记录" else if (tab == 0) "总有一天，值得记住" else "今天，想完成什么？",
                                                    style = MaterialTheme.typography.titleLarge)
                                                Text(if (constrained) "试试更短的关键词，或清除筛选。搜索覆盖标题和备注。"
                                                    else "点击新建添加记录，所有内容仅保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                TextButton(onClick = {
                                                    if (constrained) state.updateOptions(kind, EntryOptions()) else state.openEditor(kind)
                                                }, enabled = !busy) { Text(if (constrained) "清除条件" else "添加第一条记录") }
                                            }
                                        }
                                    }
                                    items(entries.itemCount, key = entries.itemKey { "entry-${it.id}" }) { index ->
                                        entries[index]?.let { entry ->
                                            EntryCard(entry, reference, busy,
                                                onEdit = { state.openEditor(entry.kind, entry) }, onDelete = { deleting = entry },
                                                onComplete = { state.complete(entry) }, onOpen = { state.openDetail(entry.id) })
                                        }
                                    }
                                    if (entries.loadState.append is LoadState.Error) item(key = "append-error") {
                                        TextButton(onClick = entries::retry) { Text("加载更多失败，点击重试") }
                                    }
                                    if (entries.loadState.append is LoadState.Loading) item(key = "append-loading") {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                } else if (tab == 2) item { AnalysisContent(state) }
                                else item { SettingsOverview() }
                            }
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
