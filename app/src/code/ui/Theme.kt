package local.daybook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightPalette = lightColorScheme(
    primary = Color(0xFF245EC7), onPrimary = Color.White,
    background = Color(0xFFEDF3FA), onBackground = Color(0xFF152338),
    surface = Color(0xFFF9FBFF), onSurface = Color(0xFF152338),
    surfaceVariant = Color(0xFFDDE7F4), onSurfaceVariant = Color(0xFF34445C),
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFAFCBFF), onPrimary = Color(0xFF002F6B),
    background = Color(0xFF101B2A), onBackground = Color(0xFFE5EEFC),
    surface = Color(0xFF1B2A3F), onSurface = Color(0xFFE5EEFC),
    surfaceVariant = Color(0xFF30415A), onSurfaceVariant = Color(0xFFD2E0F5),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkPalette else LightPalette, content = content)
}

/** 标准档玻璃材质：稳定底色与细描边，不对文字或每个列表项施加模糊。 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), content = content)
    }
}
