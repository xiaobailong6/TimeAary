package local.daybook.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import local.daybook.datetime.CalendarDates

@Composable
fun WeekCalendar(selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val start = CalendarDates.weekStart(selected)
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(selected.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSelect(today) }) { Text("今天") }
        }
        // 使用有限宽度分配七格；窄屏可滚动，不在无限约束下依赖 weight。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cellWidth = ((maxWidth - 24.dp) / 7).coerceAtLeast(48.dp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { offset ->
                    val date = start.plusDays(offset.toLong())
                    val active = date == selected
                    val colors = MaterialTheme.colorScheme
                    Surface(Modifier.width(cellWidth).selectable(active, role = Role.Tab,
                        enabled = date.year in 1900..2200, onClick = { onSelect(date) }).semantics {
                            contentDescription = "$date 星期${weekdays[offset]}${if (date == today) "，今天" else ""}"
                        }, shape = RoundedCornerShape(18.dp),
                        color = if (active) colors.primary else colors.surfaceVariant.copy(alpha = 0.35f),
                        contentColor = if (active) colors.onPrimary else colors.onSurface) {
                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(weekdays[offset], style = MaterialTheme.typography.labelMedium)
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                            Text(if (date == today) "今" else "·", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelect(selected.minusWeeks(1)) }, enabled = selected.minusWeeks(1).year >= 1900) { Text("上一周") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSelect(selected.plusWeeks(1)) }, enabled = selected.plusWeeks(1).year <= 2200) { Text("下一周") }
        }
    }
}
