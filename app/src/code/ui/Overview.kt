package local.daybook.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        InstallIdentityCard()
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

@Composable
private fun InstallIdentityCard() {
    val context = LocalContext.current
    val identity = remember(context) { readInstallIdentity(context) }
    var copied by remember { mutableStateOf(false) }
    SectionLabel("安装与签名")
    GlassCard(Modifier.fillMaxWidth()) {
        val verified = identity.matchesPinnedCertificate()
        Text(if (BuildConfig.DEBUG) "独立调试包，不用于日常升级"
            else if (verified) "固定签名已核对" else "签名身份未通过核对",
            style = MaterialTheme.typography.titleMedium,
            color = if (verified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        SelectionContainer { Text(identity.summary(), style = MaterialTheme.typography.bodySmall) }
        Text(if (BuildConfig.DEBUG) "此包与日常签名版独立存储数据。debug 证书可能随构建环境变化，请勿把它作为连续升级渠道。"
            else "覆盖安装要求包名与旧包一致、证书一致且版本码不降级。若旧包来自 debug 渠道，本次固定签名不能绕过系统的证书检查。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("安装诊断", identity.summary()))
            copied = true
        }) { Text(if (copied) "已复制安装信息" else "复制安装信息") }
    }
}
