package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import local.daybook.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppScreen(state: AppViewModel) {
    val draft by state.draft.collectAsStateWithLifecycle()
    val detailId by state.detailId.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var batchDeleting by remember { mutableStateOf(false) }
    var managingCategories by remember { mutableStateOf(false) }
    var creatingType by remember { mutableStateOf(false) }
    val busy by state.busy.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()
    val today by state.today.collectAsStateWithLifecycle()
    val reference by state.reference.collectAsStateWithLifecycle()
    val dateOptions by state.dateOptions.collectAsStateWithLifecycle()
    val todoOptions by state.todoOptions.collectAsStateWithLifecycle()
    val categoriesState by state.categories.collectAsStateWithLifecycle()
    val categories = categoriesState.valueOrNull() ?: emptyList()
    val settings by state.settings.collectAsStateWithLifecycle()
    val selection by state.selection.collectAsStateWithLifecycle()
    val messages by state.messages.collectAsStateWithLifecycle()
    var currentName by rememberSaveable { mutableStateOf(NavEntry.TASKS.name) }
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
        EntryEditor(it, categories, busy, error, state::updateDraft, state::closeEditor, state::save)
        return
    }
    detailId?.let { EntryDetail(state, it, today); return }

    // 入口顺序与显隐来自设置；当前入口被隐藏时回退到第一个可见入口。
    val navEntries = settings.navEntries
    val chosen = NavEntry.entries.firstOrNull { it.name == currentName } ?: NavEntry.TASKS
    val current = if (chosen in navEntries) chosen else navEntries.first()
    val listEntries = current == NavEntry.TASKS || current == NavEntry.TODOS
    val kind = if (current == NavEntry.TODOS) EntryKind.TODO else EntryKind.DATE
    val options = if (current == NavEntry.TODOS) todoOptions else dateOptions
    val archivedView = listEntries && options.filter == EntryFilter.ARCHIVED
    val selectionMode = selection.isNotEmpty()
    val titles = mapOf(
        NavEntry.TASKS to "把日子，放在心上",
        NavEntry.TODOS to "一件一件，从容完成",
        NavEntry.ANALYSIS to "看见记录的分布",
        NavEntry.SETTINGS to "按你的节奏",
    )

    AppBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Column(Modifier.statusBarsPadding().padding(start = 20.dp, top = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlassNavigation(navEntries, current, vertical = true) { entry ->
                        currentName = entry.name
                        state.clearSelection()
                    }
                    if (listEntries) CategorySidebar(categories, options.categoryId, archivedView,
                        onSelect = { id -> state.updateOptions(kind, options.copy(categoryId = id)) },
                        onManage = { managingCategories = true })
                }
                Scaffold(modifier = Modifier.weight(1f), containerColor = Color.Transparent,
                    bottomBar = {
                        if (!wide) Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.widthIn(max = 480.dp)) {
                                GlassNavigation(navEntries, current) { entry ->
                                    currentName = entry.name
                                    state.clearSelection()
                                }
                            }
                        }
                    }) { insets ->
                    key(current.name) {
                        val paging: LazyPagingItems<Entry>? = if (listEntries) {
                            (if (current == NavEntry.TODOS) state.todos else state.dates).collectAsLazyPagingItems()
                        } else null
                        val listState = when (current) {
                            NavEntry.TASKS -> dateList
                            NavEntry.TODOS -> todoList
                            NavEntry.ANALYSIS -> analysisList
                            NavEntry.SETTINGS -> settingsList
                        }
                        Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), state = listState,
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (selectionMode) {
                                    item(key = "selection") {
                                        SelectionBar(selection.size, busy, categories,
                                            onSelectAll = {
                                                if (paging != null) state.selectAll(
                                                    (0 until paging.itemCount).mapNotNull { index -> paging[index]?.id })
                                            },
                                            onClear = state::clearSelection,
                                            onMove = { categoryId -> state.batchAssignCategory(categoryId) },
                                            onDelete = { batchDeleting = true })
                                    }
                                } else {
                                    item(key = "header") {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(today.format(DateTimeFormatter.ofPattern("M 月 d 日  EEEE", java.util.Locale.CHINA)),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(current.label(), style = MaterialTheme.typography.headlineLarge,
                                                    modifier = Modifier.weight(1f))
                                                if (listEntries) FilledTonalButton(onClick = { creatingType = true },
                                                    enabled = !busy && !archivedView) {
                                                    AppIcon(AppGlyph.Plus, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                                    Spacer(Modifier.width(6.dp)); Text("新建")
                                                }
                                            }
                                            Text(titles.getValue(current), style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (error != null) item(key = "error") {
                                    GlassCard(Modifier.fillMaxWidth()) {
                                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = state::clearError) { Text("关闭提示") }
                                    }
                                }
                                if (paging != null) {
                                    item(key = "calendar") { CalendarPanel(reference, today, state::selectReference) }
                                    item(key = "controls") {
                                        // 归档流不做分类筛选，归档视图隐藏分类胶囊，避免出现点了没反应的控件。
                                        EntryControls(kind, options, if (archivedView || wide) emptyList() else categories,
                                            { state.updateOptions(kind, it) }, { managingCategories = true })
                                    }
                                    item(key = "section") {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            SectionLabel(if (archivedView) "已归档记录"
                                                else if (kind == EntryKind.DATE) "我的日期" else "我的待办")
                                            Text(if (archivedView) "按最近归档时间排序 · 搜索覆盖标题和备注"
                                                else "${options.filter.label()} · 筛选和日数以 $reference 为准",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (archivedView) Text(
                                                "归档不删除数据，也不计入统计；恢复后回到正常列表。到期自动归档尚未实现。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            else if (kind == EntryKind.TODO) Text("未完成优先，组内按${options.sort.label()}；置顶记录保持在最前。长按卡片可批量选择。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (paging.loadState.refresh is LoadState.Loading) item(key = "loading") {
                                        GlassLoadingCard("正在读取记录…")
                                    }
                                    if (paging.loadState.refresh is LoadState.Error) item(key = "read-error") {
                                        ReadErrorCard("暂时无法读取记录，原有内容不会被清空。", paging::retry)
                                    } else if (paging.itemCount == 0 && paging.loadState.refresh is LoadState.NotLoading) {
                                        item(key = "empty") {
                                            val constrained = options.search.isNotBlank() || options.categoryId != null ||
                                                (options.filter != EntryFilter.ALL && options.filter != EntryFilter.ARCHIVED)
                                            EmptyRecordsCard(kind = kind, archived = archivedView, constrained = constrained,
                                                onCreate = { state.openEditor(kind) },
                                                onReset = { state.updateOptions(kind, EntryOptions()) })
                                        }
                                    }
                                    items(paging.itemCount, key = paging.itemKey { "entry-${it.id}" }) { index ->
                                        paging[index]?.let { entry ->
                                            EntryCard(entry, reference, busy, settings.density,
                                                selectionMode = selectionMode, selected = entry.id in selection,
                                                onEdit = { state.openEditor(entry.kind, entry) },
                                                onDelete = { deleting = entry },
                                                onComplete = { state.complete(entry) },
                                                onOpen = { state.openDetail(entry.id) },
                                                onPin = { state.setPinned(entry, !entry.pinned) },
                                                onArchive = { state.archive(entry) },
                                                onRestore = { state.restore(entry) },
                                                onLongPress = { state.toggleSelection(entry.id) },
                                                onToggleSelect = { state.toggleSelection(entry.id) })
                                        }
                                    }
                                    if (paging.loadState.append is LoadState.Error) item(key = "append-error") {
                                        TextButton(onClick = paging::retry) { Text("加载更多失败，点击重试") }
                                    }
                                    if (paging.loadState.append is LoadState.Loading) item(key = "append-loading") {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                } else if (current == NavEntry.ANALYSIS) {
                                    item(key = "analysis") { AnalysisContent(state) }
                                } else {
                                    item(key = "settings") {
                                        SettingsOverview(settings, categories, busy, error,
                                            onChange = state::updateSettings,
                                            onManageCategories = { managingCategories = true })
                                    }
                                }
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
    if (batchDeleting) {
        AlertDialog(onDismissRequest = { batchDeleting = false },
            title = { Text("删除选中的 ${selection.size} 条记录？") },
            text = { Text("批量删除同样不可恢复，分类与置顶信息会一起删除。") },
            confirmButton = {
                TextButton(onClick = { state.batchDelete(); batchDeleting = false }, enabled = !busy) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { batchDeleting = false }) { Text("保留") } })
    }
    if (managingCategories) CategoryManager(categories, busy, error,
        onCreate = { state.createCategory(it) }, onRename = { id, name -> state.renameCategory(id, name) },
        onMove = { id, offset -> state.moveCategory(id, offset) }, onDelete = { state.deleteCategory(it) },
        onDismiss = { managingCategories = false })
    CreateTypePanel(visible = creatingType, onDismiss = { creatingType = false },
        onCreate = { created -> creatingType = false; state.openEditor(created) })
    MessageHost(messages, state::dismissMessage)
}

/** 宽屏分类侧栏：与手机端分类胶囊共用同一筛选字段。 */
@Composable
private fun CategorySidebar(categories: List<Category>, selectedId: String?, archived: Boolean,
                            onSelect: (String?) -> Unit, onManage: () -> Unit) {
    GlassCard(Modifier.width(200.dp)) {
        Text("分类", style = MaterialTheme.typography.titleMedium)
        if (archived) {
            Text("归档列表不按分类筛选", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SidebarItem("全部分类", selectedId == null) { onSelect(null) }
            categories.forEach { category ->
                SidebarItem(category.name, selectedId == category.id) { onSelect(category.id) }
            }
            if (categories.isEmpty()) Text("还没有分类", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onManage) { Text("管理分类") }
    }
}

@Composable
private fun SidebarItem(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().selectable(active, role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(14.dp), color = if (active) colors.primaryContainer else Color.Transparent) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) colors.onPrimaryContainer else colors.onSurface)
    }
}

/** 批量操作条：全选只作用于当前已加载分页，删除与移动分类都要二次确认或明确目标。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(count: Int, busy: Boolean, categories: List<Category>,
                         onSelectAll: () -> Unit, onClear: () -> Unit,
                         onMove: (String?) -> Unit, onDelete: () -> Unit) {
    var moving by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("已选 $count 项", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSelectAll, enabled = !busy) { Text("全选已加载") }
            TextButton(onClick = onClear, enabled = !busy) { Text("取消选择") }
            Box {
                TextButton(onClick = { moving = true }, enabled = !busy) { Text("移动到分类") }
                DropdownMenu(expanded = moving, onDismissRequest = { moving = false }) {
                    DropdownMenuItem(text = { Text("未分类") }, onClick = { moving = false; onMove(null) })
                    categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) },
                            onClick = { moving = false; onMove(category.id) })
                    }
                    if (categories.isEmpty()) DropdownMenuItem(text = { Text("还没有分类") },
                        onClick = { moving = false }, enabled = false)
                }
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
        Text("批量操作只作用于当前选中记录，先确认再执行。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
