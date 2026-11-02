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

enum class AppGlyph { Calendar, Check, Chart, Settings, Plus, Back, Next, More, Close }

/** 自绘中性功能图标，按钮语义由宿主提供，不使用品牌图代替功能图标。 */
@Composable
fun AppIcon(glyph: AppGlyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        val width = 1.8f * unit
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(tint, point(x, y), point(x2, y2), width, StrokeCap.Round)
        when (glyph) {
            AppGlyph.Calendar -> {
                drawRoundRect(tint, point(4f, 5f), Size(16f * unit, 16f * unit),
                    CornerRadius(3f * unit), style = Stroke(width))
                line(4f, 10f, 20f, 10f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f)
                drawCircle(tint, 1.5f * unit, point(9f, 15f))
            }
            AppGlyph.Check -> {
                drawRoundRect(tint, point(3f, 3f), Size(18f * unit, 18f * unit),
                    CornerRadius(6f * unit), style = Stroke(width))
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
        }
    }
}

@Composable
fun GlassNavigation(selected: Int, vertical: Boolean = false, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labels = listOf("任务", "待办", "统计", "设置")
    val icons = listOf(AppGlyph.Calendar, AppGlyph.Check, AppGlyph.Chart, AppGlyph.Settings)
    Surface(shape = RoundedCornerShape(30.dp), color = colors.surface.copy(alpha = 0.96f),
        shadowElevation = 10.dp, border = BorderStroke(1.dp, colors.outlineVariant)) {
        if (vertical) Column(Modifier.width(92.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.indices.forEach { index ->
                NavigationItem(labels[index], icons[index], selected == index, Modifier.fillMaxWidth()) { onSelect(index) }
            }
        } else Row(Modifier.padding(7.dp).fillMaxWidth()) {
            labels.indices.forEach { index ->
                NavigationItem(labels[index], icons[index], selected == index, Modifier.weight(1f)) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun NavigationItem(label: String, icon: AppGlyph, active: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.selectable(selected = active, role = Role.Tab, onClick = onSelect),
        shape = RoundedCornerShape(23.dp), color = if (active) colors.primaryContainer else Color.Transparent) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppIcon(icon, tint = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
    }
}
