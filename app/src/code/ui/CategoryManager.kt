package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.daybook.model.Category

/** 分类管理：新增、重命名、排序与删除；删除只迁移记录，不删除事件。 */
@Composable
fun CategoryManager(
    categories: List<Category>,
    busy: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, Set<String>>?>(null) }
    // 只有确认新分类真的写入后才清空输入，重名或校验失败时保留用户已输入的名称。
    LaunchedEffect(categories, pending) {
        val request = pending ?: return@LaunchedEffect
        if (categories.any { it.name.equals(request.first, ignoreCase = true) && it.id !in request.second }) {
            newName = ""
            pending = null
        }
    }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renamingText by remember { mutableStateOf("") }
    var deletingId by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("分类管理") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("删除分类不会删除记录，其中的内容会回到未分类。当前最多 ${Category.LIMIT} 个分类。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it.take(Category.NAME_LIMIT) }, singleLine = true,
                        label = { Text("新分类名称") }, modifier = Modifier.weight(1f), enabled = !busy)
                    TextButton(onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            pending = name to categories.map { it.id }.toSet()
                            onCreate(name)
                        }
                    }, enabled = !busy && newName.isNotBlank()) { Text("新增") }
                }
                if (categories.isEmpty()) Text("还没有分类，先在上方输入名称新增。")
                categories.forEach { category ->
                    HorizontalDivider()
                    if (renamingId == category.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(renamingText, { renamingText = it.take(Category.NAME_LIMIT) },
                                singleLine = true, label = { Text("分类名称") }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth())
                            Row {
                                TextButton(onClick = { onRename(category.id, renamingText.trim()); renamingId = null },
                                    enabled = !busy && renamingText.isNotBlank()) { Text("保存名称") }
                                TextButton(onClick = { renamingId = null }, enabled = !busy) { Text("取消") }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onMove(category.id, -1) }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "${category.name}，上移" }) {
                                AppIcon(AppGlyph.Back, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onMove(category.id, 1) }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "${category.name}，下移" }) {
                                AppIcon(AppGlyph.Next, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { renamingId = category.id; renamingText = category.name },
                                enabled = !busy) { Text("重命名") }
                            TextButton(onClick = { deletingId = category.id }, enabled = !busy) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("完成") } })
    val deleting = categories.firstOrNull { it.id == deletingId }
    if (deleting != null) {
        AlertDialog(onDismissRequest = { deletingId = null },
            title = { Text("删除分类“${deleting.name}”？") },
            text = { Text("其中的记录会迁移到未分类，不会被删除。") },
            confirmButton = {
                TextButton(onClick = { onDelete(deleting.id); deletingId = null }, enabled = !busy) { Text("删除分类") }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("保留") } })
    }
}
