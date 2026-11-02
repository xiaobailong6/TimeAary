package local.daybook.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import local.daybook.BuildConfig
import local.daybook.R

@Composable
fun AnalysisIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            AppIcon(AppGlyph.Chart, Modifier.size(40.dp))
            Text("每一份投入，都值得看见", style = MaterialTheme.typography.titleLarge)
            Text("统计功能尚在开发", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Text("数值记录、专注时长与贡献热力图将在对应任务功能完成后接入。这里不会把演示曲线当作你的真实数据。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("计划中的统计视角")
        GlassCard(Modifier.fillMaxWidth()) {
            InfoRow("趋势", "按日、周、月、年查看记录变化")
            HorizontalDivider()
            InfoRow("贡献日历", "查看投入的日期与持续记录")
            HorizontalDivider()
            InfoRow("时间线", "追溯补记、修改与任务操作")
        }
    }
}

@Composable
fun SettingsOverview() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.ic_brand), contentDescription = null, modifier = Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text("本地生活记录", style = MaterialTheme.typography.titleLarge)
                    Text("${BuildConfig.VERSION_NAME} · 开发版", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("无账号，无会员。你的日子，由你保管。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("当前状态")
        GlassCard(Modifier.fillMaxWidth()) {
            InfoRow("外观", "跟随系统深浅色 · 静态水晶材质")
            HorizontalDivider()
            InfoRow("数据", "仅保存在本机，不上传事件")
            HorizontalDivider()
            InfoRow("许可", "GPL-3.0-only")
        }
        SectionLabel("数据与隐私")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("请勿把开发版作为唯一记录", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error)
            Text("备份与恢复尚未接入，卸载会删除设备中的记录。提醒、应用锁、主题自定义与小组件也仍在开发，当前没有开启这些能力。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
