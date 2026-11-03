package local.daybook.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import local.daybook.BuildConfig
import local.daybook.R
import local.daybook.model.AppSettings
import local.daybook.model.CardDensity
import local.daybook.model.Category
import local.daybook.model.FontScale
import local.daybook.model.NavEntry
import local.daybook.model.ThemeMode

/** 设置页：品牌与版本、外观、入口顺序、分类入口、安装身份与隐私说明。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsOverview(
    settings: AppSettings,
    categories: List<Category>,
    busy: Boolean,
    error: String?,
    onChange: (AppSettings) -> Unit,
    onManageCategories: () -> Unit,
) {
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
        if (error != null) GlassCard(Modifier.fillMaxWidth()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        SectionLabel("外观")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("主题", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = settings.themeMode == mode,
                        onClick = { onChange(settings.copy(themeMode = mode)) },
                        label = { Text(mode.label()) }, enabled = !busy)
                }
            }
            HorizontalDivider()
            Text("字号", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontScale.entries.forEach { scale ->
                    FilterChip(selected = settings.fontScale == scale,
                        onClick = { onChange(settings.copy(fontScale = scale)) },
                        label = { Text(scale.label()) }, enabled = !busy)
                }
            }
            Text("字号与系统字体缩放叠加生效；标题与正文按同一比例缩放。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("卡片密度", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardDensity.entries.forEach { density ->
                    FilterChip(selected = settings.density == density,
                        onClick = { onChange(settings.copy(density = density)) },
                        label = { Text(density.label()) }, enabled = !busy)
                }
            }
            Text("紧凑密度只显示一行备注摘要，完整内容仍在详情页。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionLabel("底栏入口")
        GlassCard(Modifier.fillMaxWidth()) {
            val allEntries = settings.navEntries + NavEntry.entries.filter { it !in settings.navEntries }
            allEntries.forEach { entry ->
                val visible = settings.isVisible(entry)
                val index = settings.navEntries.indexOf(entry)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.label() + if (visible) "" else "（已隐藏）", Modifier.weight(1f),
                        color = if (visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry == NavEntry.SETTINGS) {
                        Text("固定显示", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = { onChange(settings.toggleEntry(entry)) }, enabled = !busy) {
                            Text(if (visible) "隐藏" else "显示")
                        }
                    }
                    IconButton(onClick = { onChange(settings.moveEntry(entry, -1)) },
                        enabled = !busy && visible && index > 0) {
                        AppIcon(AppGlyph.Back, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onChange(settings.moveEntry(entry, 1)) },
                        enabled = !busy && visible && index >= 0 && index < settings.navEntries.lastIndex) {
                        AppIcon(AppGlyph.Next, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
            Text("设置入口始终保留，且至少保留一个其他入口；顺序与显隐会立即生效并保存。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionLabel("分类")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("当前 ${categories.size} 个分类", style = MaterialTheme.typography.titleMedium)
            Text(if (categories.isEmpty()) "还没有分类，新增后可在列表按分类筛选。"
                else categories.joinToString("、") { it.name },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onManageCategories, enabled = !busy) { Text("管理分类") }
        }

        InstallIdentityCard()

        SectionLabel("数据与隐私")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("请勿把开发版作为唯一记录", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error)
            Text("分类、置顶与归档已接入；备份与恢复尚未实现，卸载会删除设备中的记录。提醒、应用锁、小组件与图片分享也仍在开发，当前没有开启这些能力。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

fun FontScale.label(): String = when (this) {
    FontScale.SMALL -> "小"
    FontScale.DEFAULT -> "默认"
    FontScale.MEDIUM -> "中"
    FontScale.LARGE -> "大"
}

fun CardDensity.label(): String = when (this) {
    CardDensity.COMPACT -> "紧凑"
    CardDensity.STANDARD -> "标准"
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
