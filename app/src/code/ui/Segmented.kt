package local.daybook.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 统一分段控件：选中态用形状 + 颜色 + 角色语义表达，动效 200ms 以内。
 * 调用方负责把枚举或列表映射成文字，控件本身不关心业务含义。
 */
@Composable
fun <T> GlassSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = colors.surfaceVariant.copy(alpha = 0.4f)) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val active = option == selected
                val background by animateColorAsState(
                    targetValue = if (active) colors.primaryContainer else colors.surface.copy(alpha = 0f),
                    label = "segment",
                )
                Surface(
                    Modifier.weight(1f).heightIn(min = 44.dp)
                        .selectable(selected = active, enabled = enabled, role = Role.Tab) { onSelect(option) },
                    shape = RoundedCornerShape(14.dp), color = background,
                ) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label(option), style = MaterialTheme.typography.labelLarge,
                            color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
