package it.chiarella.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Night = Color(0xFF080B19)
    val NightDeep = Color(0xFF05070F)
    val Indigo = Color(0xFF141A3F)
    val Surface = Color(0xFF161B33)
    val SurfaceHigh = Color(0xFF1E2444)
    val Glass = Color(0x14FFFFFF)
    val GlassBorder = Color(0x1FFFFFFF)
    val Text = Color(0xFFEDF0FA)
    val TextMuted = Color(0xFFA0A8C6)
    val TextFaint = Color(0xFF6B7396)
    val Lavender = Color(0xFF8B7CF6)
    val Sky = Color(0xFF5EC8F2)
    val Peach = Color(0xFFFFB38A)
}

private val Scheme = darkColorScheme(
    primary = Palette.Lavender,
    onPrimary = Color.White,
    secondary = Palette.Sky,
    background = Palette.Night,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    outline = Palette.GlassBorder,
)

private val Type = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
    )
}

@Composable
fun ChiarellaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
