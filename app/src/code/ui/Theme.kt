package local.daybook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightPalette = lightColorScheme(
    primary = Color(0xFF245EC7), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF), onPrimaryContainer = Color(0xFF173D7B),
    secondary = Color(0xFF167267), onSecondary = Color.White,
    background = Color(0xFFEDF3FA), onBackground = Color(0xFF152338),
    surface = Color(0xFFF9FCFF), onSurface = Color(0xFF152338),
    surfaceVariant = Color(0xFFE0EAF6), onSurfaceVariant = Color(0xFF475B73),
    outline = Color(0xFF72859C), outlineVariant = Color(0xFFC6D5E7),
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFAFCBFF), onPrimary = Color(0xFF002F6B),
    primaryContainer = Color(0xFF263F65), onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFF92D5C8), onSecondary = Color(0xFF00382F),
    background = Color(0xFF101B2A), onBackground = Color(0xFFE5EEFC),
    surface = Color(0xFF1B2A3F), onSurface = Color(0xFFE5EEFC),
    surfaceVariant = Color(0xFF273A52), onSurfaceVariant = Color(0xFFB8CAE1),
    outline = Color(0xFF91A7C2), outlineVariant = Color(0xFF3E526D),
)
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkPalette else LightPalette,
        typography = AppTypography, content = content)
}

/** 所有页面共享静态材质；只绘制容器背景，不模糊文字或建立实时采样链。 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(26.dp), color = colors.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f))) {
        Column(Modifier.background(Brush.linearGradient(listOf(
            colors.surface, colors.surface.copy(alpha = 0.65f), colors.primaryContainer.copy(alpha = 0.22f)
        ))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        colors.primaryContainer.copy(alpha = 0.55f), colors.background, colors.background
    ))), content = content)
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
}
