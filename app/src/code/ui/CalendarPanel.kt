package local.daybook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import local.daybook.datetime.CalendarDates

enum class CalendarView { WEEK, MONTH }

/**
 * 周 / 月历参考日期面板：只决定“参考日期”，不冒充按 occurrence 过滤，
 * 折叠状态、视图与月份锚点都在进程恢复后保留。
 */
@Composable
fun CalendarPanel(reference: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var viewName by rememberSaveable { mutableStateOf(CalendarView.WEEK.name) }
    val view = CalendarView.entries.firstOrNull { it.name == viewName } ?: CalendarView.WEEK
    var monthAnchorText by rememberSaveable { mutableStateOf(reference.toString()) }
    val monthAnchor = runCatching { LocalDate.parse(monthAnchorText) }.getOrDefault(reference)
    val colors = MaterialTheme.colorScheme
    // 选择日期、回到今天或翻周后，月份锚点跟随参考日期所在月。
    LaunchedEffect(reference) { monthAnchorText = reference.toString() }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(reference.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)),
                    style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "参考日期决定筛选与日数" else "日历已收起",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            TextButton(onClick = { onSelect(today) }) { Text("今天") }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }
        }
        GlassSegmentedControl(options = CalendarView.entries.toList(), selected = view,
            label = { if (it == CalendarView.WEEK) "周" else "月" },
            onSelect = { viewName = it.name })
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (view == CalendarView.WEEK) {
                    WeekRow(CalendarDates.weekStart(reference), reference, today, onSelect)
                } else {
                    MonthGrid(monthAnchor, reference, today, onSelect,
                        onShift = { amount -> monthAnchorText = CalendarDates.shiftMonth(monthAnchor, amount).toString() })
                }
            }
        }
    }
}

@Composable
private fun WeekRow(start: LocalDate, reference: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    // 使用有限宽度分配七格；窄屏可滚动，不在无限约束下依赖 weight。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = ((maxWidth - 24.dp) / 7).coerceAtLeast(44.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(7) { offset ->
                val date = start.plusDays(offset.toLong())
                DayCell(date, reference, today, Modifier.width(cellWidth), weekdays[offset],
                    dimmed = false, onSelect = onSelect)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onSelect(reference.minusWeeks(1)) },
            enabled = reference.minusWeeks(1).year >= 1900) { Text("上一周") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onSelect(reference.plusWeeks(1)) },
            enabled = reference.plusWeeks(1).year <= 2200) { Text("下一周") }
    }
}

@Composable
private fun MonthGrid(anchor: LocalDate, reference: LocalDate, today: LocalDate,
                      onSelect: (LocalDate) -> Unit, onShift: (Long) -> Unit) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    val month = CalendarDates.monthStart(anchor)
    val grid = remember(month) { CalendarDates.monthGrid(month) }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) },
            modifier = Modifier.semantics { contentDescription = "上一月" }) {
            AppIcon(AppGlyph.Back, tint = colors.onSurfaceVariant)
        }
        Text(month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)),
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        IconButton(onClick = { onShift(1) },
            modifier = Modifier.semantics { contentDescription = "下一月" }) {
            AppIcon(AppGlyph.Next, tint = colors.onSurfaceVariant)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { label ->
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
        }
    }
    grid.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { date ->
                val inMonth = date.year == month.year && date.month == month.month
                DayCell(date, reference, today, Modifier.weight(1f), weekdayLabel = null,
                    dimmed = !inMonth, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, reference: LocalDate, today: LocalDate,
                    modifier: Modifier, weekdayLabel: String?, dimmed: Boolean,
                    onSelect: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = date == reference
    val isToday = date == today
    val textColor = when {
        active -> colors.onPrimary
        dimmed -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        else -> colors.onSurface
    }
    Surface(
        modifier = modifier
            .selectable(selected = active, role = Role.Tab, enabled = date.year in 1900..2200) { onSelect(date) }
            .semantics {
                contentDescription = buildString {
                    append(date.toString())
                    if (weekdayLabel != null) append(" 星期").append(weekdayLabel)
                    if (isToday) append("，今天")
                    if (dimmed) append("，非本月")
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = if (active) colors.primary
        else if (isToday) colors.primaryContainer.copy(alpha = 0.55f)
        else Color.Transparent,
        border = if (isToday && !active) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (weekdayLabel != null) {
                Text(weekdayLabel, style = MaterialTheme.typography.labelSmall, color = textColor)
            }
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(if (isToday) "今" else "·", style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}
