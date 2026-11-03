package local.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/** 轻量提示只承载“刚刚发生了什么”，失败状态仍留在页面内的持久错误卡。 */
data class AppMessage(val id: Long, val text: String)

/**
 * 统一消息入口：按队列逐条显示，同文案不重复堆积，4 秒后自动消失，
 * 也可手动关闭；文本以 liveRegion 通知无障碍服务。
 * 使用 Popup 浮在页面之上，不额外占用布局高度，也不会遮住底栏点击。
 */
@Composable
fun MessageHost(messages: List<AppMessage>, onDismiss: (Long) -> Unit) {
    val current = messages.firstOrNull() ?: return
    LaunchedEffect(current.id) {
        delay(4_000)
        onDismiss(current.id)
    }
    Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false)) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.widthIn(max = 520.dp).padding(horizontal = 20.dp, vertical = 96.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(current.text, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface)
                TextButton(onClick = { onDismiss(current.id) }) {
                    Text("知道了", color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
