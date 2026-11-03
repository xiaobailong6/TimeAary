package local.daybook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import local.daybook.model.EntryKind

/** 统一的加载 / 空 / 失败状态卡，页面不再各自拼一遍玻璃容器。 */
@Composable
fun ReadErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
fun GlassLoadingCard(text: String = "正在读取…") {
    GlassCard(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GlassStatusCard(icon: AppGlyph, title: String, description: String,
                    actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    GlassCard(Modifier.fillMaxWidth()) {
        AppIcon(icon, Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 列表空状态：区分“没有记录”“条件过窄”“还没有归档”三种语境，避免同一句文案混用。 */
@Composable
fun EmptyRecordsCard(kind: EntryKind, archived: Boolean, constrained: Boolean,
                     onCreate: () -> Unit, onReset: () -> Unit) {
    val title = when {
        constrained -> "没有匹配的记录"
        archived -> "还没有归档记录"
        kind == EntryKind.DATE -> "总有一天，值得记住"
        else -> "今天，想完成什么？"
    }
    val description = when {
        constrained -> "试试更短的关键词，或清除筛选。搜索覆盖标题和备注。"
        archived -> "记录卡片菜单里的“归档”会把内容移动到这里，可随时恢复。"
        else -> "点击新建添加记录，所有内容仅保存在本机。"
    }
    GlassCard(Modifier.fillMaxWidth()) {
        AppIcon(if (kind == EntryKind.DATE) AppGlyph.Calendar else AppGlyph.Check, Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { if (constrained || archived) onReset() else onCreate() }) {
            Text(if (constrained || archived) "回到全部记录" else "添加第一条记录")
        }
    }
}

/** 新建类型面板里的单个入口；未实现的类型保留位置并明确标注，不伪装可用。 */
@Composable
fun CreateTypeCell(glyph: AppGlyph, title: String, note: String, supported: Boolean,
                   modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = supported,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (supported) colors.surfaceVariant.copy(alpha = 0.5f)
        else colors.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(glyph, tint = if (supported) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (supported) colors.onSurface else colors.onSurfaceVariant)
            Text(note, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
