package local.daybook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import local.daybook.model.NavEntry

enum class AppGlyph {
    Calendar, Check, Chart, Settings, Plus, Back, Next, More, Close,
    Star, Heart, Flag, Book, Gift, Note, Work, Travel,
}

fun NavEntry.label(): String = when (this) {
    NavEntry.TASKS -> "任务"
    NavEntry.TODOS -> "待办"
    NavEntry.ANALYSIS -> "统计"
    NavEntry.SETTINGS -> "设置"
}

fun NavEntry.glyph(): AppGlyph = when (this) {
    NavEntry.TASKS -> AppGlyph.Calendar
    NavEntry.TODOS -> AppGlyph.Check
    NavEntry.ANALYSIS -> AppGlyph.Chart
    NavEntry.SETTINGS -> AppGlyph.Settings
}

/** 自绘中性功能图标，按钮语义由宿主提供，不使用品牌图代替功能图标。 */
@Composable
fun AppIcon(glyph: AppGlyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        val width = 1.8f * unit
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(tint, point(x, y), point(x2, y2), width, StrokeCap.Round)
        fun outline(x: Float, y: Float, w: Float, h: Float, radius: Float) =
            drawRoundRect(tint, point(x, y), Size(w * unit, h * unit), CornerRadius(radius * unit), style = Stroke(width))
        when (glyph) {
            AppGlyph.Calendar -> {
                outline(4f, 5f, 16f, 16f, 3f)
                line(4f, 10f, 20f, 10f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f)
                drawCircle(tint, 1.5f * unit, point(9f, 15f))
            }
            AppGlyph.Check -> {
                outline(3f, 3f, 18f, 18f, 6f)
                line(7f, 12f, 10.5f, 15.5f); line(10.5f, 15.5f, 17f, 9f)
            }
            AppGlyph.Chart -> { line(4f, 20f, 20f, 20f); line(6f, 15f, 6f, 10f)
                line(12f, 15f, 12f, 4f); line(18f, 15f, 18f, 7f) }
            AppGlyph.Settings -> {
                listOf(6f to 8f, 12f to 16f, 18f to 10f).forEach { (x, y) ->
                    line(x, 3f, x, y - 3f); line(x, y + 3f, x, 21f)
                    drawCircle(tint, 2.5f * unit, point(x, y), style = Stroke(width))
                }
            }
            AppGlyph.Plus -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            AppGlyph.Back -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            AppGlyph.Next -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
            AppGlyph.More -> listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.7f * unit, point(it, 12f)) }
            AppGlyph.Close -> { line(6f, 6f, 18f, 18f); line(6f, 18f, 18f, 6f) }
            AppGlyph.Star -> listOf(
                12f to 3f, 14.6f to 9.4f, 21f to 9.6f, 16f to 13.6f, 17.6f to 20f,
                12f to 16.4f, 6.4f to 20f, 8f to 13.6f, 3f to 9.6f, 9.4f to 9.4f, 12f to 3f,
            ).zipWithNext().forEach { (from, to) -> line(from.first, from.second, to.first, to.second) }
            AppGlyph.Heart -> {
                drawCircle(tint, 3.2f * unit, point(9f, 10f), style = Stroke(width))
                drawCircle(tint, 3.2f * unit, point(15f, 10f), style = Stroke(width))
                line(5.95f, 11.2f, 12f, 19.5f); line(18.05f, 11.2f, 12f, 19.5f)
            }
            AppGlyph.Flag -> {
                line(6f, 3f, 6f, 21f)
                line(6f, 4.5f, 18f, 8f); line(18f, 8f, 6f, 11.5f)
            }
            AppGlyph.Book -> { outline(4f, 4f, 16f, 16f, 3f); line(12f, 4f, 12f, 20f) }
            AppGlyph.Gift -> {
                outline(4f, 9f, 16f, 11f, 2.5f)
                line(4f, 13f, 20f, 13f); line(12f, 9f, 12f, 20f)
                line(8f, 6.5f, 12f, 9f); line(16f, 6.5f, 12f, 9f)
            }
            AppGlyph.Note -> {
                drawCircle(tint, 3f * unit, point(8.5f, 17.5f))
                line(11.5f, 17.5f, 11.5f, 6.5f); line(11.5f, 6.5f, 18f, 8.5f)
            }
            AppGlyph.Work -> {
                outline(3f, 8f, 18f, 11f, 3f)
                line(9f, 8f, 9f, 5.5f); line(15f, 8f, 15f, 5.5f); line(9f, 5.5f, 15f, 5.5f)
            }
            AppGlyph.Travel -> {
                line(3f, 12.5f, 21f, 5.5f); line(21f, 5.5f, 13.5f, 20f)
                line(13.5f, 20f, 11f, 14.5f); line(11f, 14.5f, 3f, 12.5f)
            }
        }
    }
}

/**
 * 入口顺序与显隐来自设置：[entries] 已经是可见顺序，
 * 选中状态同时用形状、标签和角色语义表达，不只靠颜色。
 */
@Composable
fun GlassNavigation(entries: List<NavEntry>, selected: NavEntry, vertical: Boolean = false,
                    onSelect: (NavEntry) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(30.dp), color = colors.surface.copy(alpha = 0.96f),
        shadowElevation = 10.dp, border = BorderStroke(1.dp, colors.outlineVariant)) {
        if (vertical) Column(Modifier.width(92.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                NavigationItem(entry, selected == entry, Modifier.fillMaxWidth()) { onSelect(entry) }
            }
        } else Row(Modifier.padding(7.dp).fillMaxWidth()) {
            entries.forEach { entry ->
                NavigationItem(entry, selected == entry, Modifier.weight(1f)) { onSelect(entry) }
            }
        }
    }
}

@Composable
private fun NavigationItem(entry: NavEntry, active: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.selectable(selected = active, role = Role.Tab, onClick = onSelect),
        shape = RoundedCornerShape(23.dp), color = if (active) colors.primaryContainer else Color.Transparent) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppIcon(entry.glyph(), tint = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
            Text(entry.label(), style = MaterialTheme.typography.labelMedium,
                color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
    }
}
