package local.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import local.daybook.datetime.DayCounter
import local.daybook.model.Entry
import local.daybook.model.EntryKind

@Composable
fun EntryCard(entry: Entry, reference: LocalDate, busy: Boolean,
              onEdit: () -> Unit, onDelete: () -> Unit, onComplete: () -> Unit, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val description = remember(entry, reference) {
        DayCounter(Clock.fixed(reference.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC))
            .describe(entry.date, entry.countUp, entry.includeFirstDay)
    }
    GlassCard(Modifier.fillMaxWidth().clickable(onClickLabel = "查看完整详情", onClick = onOpen)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(colors.primaryContainer, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center) {
                AppIcon(if (entry.kind == EntryKind.DATE) AppGlyph.Calendar else AppGlyph.Check)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(entry.date.toString(), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "${entry.title}，更多操作" }) {
                    AppIcon(AppGlyph.More, tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("查看详情") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("删除", color = colors.error) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        if (entry.kind == EntryKind.DATE) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (entry.countUp) "从这一天开始" else "距离这一天",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(description, style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp, lineHeight = 42.sp, fontFeatureSettings = "tnum"), color = colors.primary)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (entry.completed) "已完成" else description,
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    color = if (entry.completed) colors.secondary else colors.primary)
                Checkbox(checked = entry.completed, onCheckedChange = { onComplete() }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "${entry.title}，完成状态" })
            }
        }
        if (entry.note.isNotBlank()) {
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
            Text(entry.note, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
