package local.daybook.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import local.daybook.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisContent(state: AppViewModel) {
    val summary by state.summary.collectAsStateWithLifecycle()
    val distribution by state.distribution.collectAsStateWithLifecycle()
    val window by state.window.collectAsStateWithLifecycle()
    val today by state.today.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("截至今天 $today 的记录状态")
        when (val result = summary) {
            ReadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is ReadState.Failed -> ReadErrorCard(result.message, state::retryReads)
            is ReadState.Ready -> {
                val totals = result.value
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("待办完成 ${totals.completedCount} / ${totals.todoCount}", style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(progress = { totals.completionFraction() }, modifier = Modifier.fillMaxWidth())
                    Text(if (totals.todoCount == 0L) "还没有待办，完成比例按 0% 显示。"
                        else "当前完成比例 ${(totals.completionFraction() * 100).toInt()}%")
                    HorizontalDivider()
                    Text("未完成：${totals.todoCount - totals.completedCount} 项")
                    Text("逾期未完成：${totals.overdueCount} 项", color = MaterialTheme.colorScheme.error)
                    Text("日期记录：${totals.dateCount} 项，其中今天及未来 ${totals.upcomingDateCount} 项")
                }
            }
        }
        SectionLabel("计划日期分布")
        GlassCard(Modifier.fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalysisPeriod.entries.forEach { period ->
                    FilterChip(selected = window.period == period, onClick = { state.setAnalysisPeriod(period) },
                        label = { Text(when (period) { AnalysisPeriod.WEEK -> "周"; AnalysisPeriod.MONTH -> "月"; AnalysisPeriod.YEAR -> "年" }) })
                }
            }
            Text("${window.start()} 至 ${window.endInclusive()}", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { state.moveAnalysis(-1) }, enabled = window.shifted(-1).anchor.year >= 1900) { Text("上一期间") }
                TextButton(onClick = state::resetAnalysis) { Text("回到当前期间") }
                TextButton(onClick = { state.moveAnalysis(1) }, enabled = window.shifted(1).anchor.year <= 2200) { Text("下一期间") }
            }
            Text("按记录的原始日期／待办计划日期统计，蓝色柱表示记录数量。待办完成量为当前状态，不是该期间实际完成次数。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val result = distribution) {
                ReadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is ReadState.Failed -> ReadErrorCard(result.message, state::retryReads)
                is ReadState.Ready -> {
                    if (result.value.first != window) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else {
                        val points = remember(window, result.value.second) { distributionPoints(window, result.value.second) }
                        DistributionChart(points, window)
                    }
                }
            }
        }
    }
}

@Composable
fun ReadErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun DistributionChart(points: List<DistributionPoint>, window: AnalysisWindow) {
    var selected by rememberSaveable(window) { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    val maximum = points.maxOfOrNull { it.total() }?.coerceAtLeast(1L) ?: 1L
    if (points.all { it.total() == 0L }) Text("这个期间还没有安排记录。")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        points.forEachIndexed { index, point ->
            Column(Modifier.width(48.dp).selectable(selected == index, role = Role.Tab, onClick = { selected = index })
                .semantics { contentDescription = "${point.start}，日期 ${point.dateCount} 项，待办 ${point.todoCount} 项，当前完成 ${point.completedCount} 项" }
                .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(point.total().toString(), style = MaterialTheme.typography.labelSmall)
                Box(Modifier.height(92.dp).fillMaxWidth().padding(horizontal = 10.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((point.total().toDouble() / maximum * 84).dp.coerceAtLeast(2.dp))
                        .background(if (selected == index) colors.primary else colors.primaryContainer, RoundedCornerShape(6.dp)))
                }
                Text(if (window.period == AnalysisPeriod.YEAR) "${point.start.monthValue}月" else point.start.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val point = points.getOrNull(selected) ?: points.firstOrNull()
    if (point != null) {
        HorizontalDivider()
        Text(if (point.start == point.endInclusive) point.start.toString() else "${point.start} 至 ${point.endInclusive}",
            style = MaterialTheme.typography.titleMedium)
        Text("日期 ${point.dateCount} 项 · 待办 ${point.todoCount} 项 · 当前已完成 ${point.completedCount} 项")
    }
}
