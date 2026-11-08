package it.chiarella.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.chiarella.app.data.UiStyle

/** Stile dell'interfaccia scelto nelle impostazioni: classico (notte), skeuomorfico (legno, pelle, ottone) o Metro (Windows Phone). */
object Look {
    var style by mutableStateOf(UiStyle.CLASSIC)
    val skeuo get() = style == UiStyle.SKEUO
    val metro get() = style == UiStyle.METRO
}

private fun pick(classic: Long, skeuo: Long, metro: Long) = Color(
    when (Look.style) {
        UiStyle.CLASSIC -> classic
        UiStyle.SKEUO -> skeuo
        UiStyle.METRO -> metro
    }
)

/** I colori seguono lo stile attivo: leggerli in composizione o nel disegno aggiorna la UI al cambio. */
object Palette {
    val Night get() = pick(0xFF080B19, 0xFF1B110A, 0xFF000000)
    val NightDeep get() = pick(0xFF05070F, 0xFF0E0805, 0xFF000000)
    val Indigo get() = pick(0xFF141A3F, 0xFF2C1C11, 0xFF1F1F1F)
    val Surface get() = pick(0xFF161B33, 0xFF2A1C13, 0xFF1F1F1F)
    val SurfaceHigh get() = pick(0xFF1E2444, 0xFF3A2819, 0xFF1F1F1F)
    val Glass get() = pick(0x14FFFFFF, 0x1AF6E3C0, 0xFF262626)
    val GlassBorder get() = pick(0x1FFFFFFF, 0x4DD8B26A, 0xFFFFFFFF)
    val Text get() = pick(0xFFEDF0FA, 0xFFF6EAD2, 0xFFFFFFFF)
    val TextMuted get() = pick(0xFFA0A8C6, 0xFFCDB792, 0xFFA6A6A6)
    val TextFaint get() = pick(0xFF6B7396, 0xFF8F7A5C, 0xFF737373)
    val Lavender get() = pick(0xFF8B7CF6, 0xFFE0A24A, 0xFF1BA1E2)
    val Sky get() = pick(0xFF5EC8F2, 0xFFF2D28C, 0xFF1BA1E2)
    val Peach get() = pick(0xFFFFB38A, 0xFFF08A5D, 0xFF1BA1E2)
}

private fun scheme(): ColorScheme = darkColorScheme(
    primary = Palette.Lavender,
    onPrimary = if (Look.skeuo) Color(0xFF2B1A0E) else Color.White,
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

private val ClassicType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
    )
}

// Testo inciso: ombra scura sotto ogni lettera, titoli con grazie come su una targa.
private val Engraved = Shadow(Color(0xB3000000), Offset(0f, 2.5f), 3f)

private fun TextStyle.engraved(serif: Boolean = false) =
    copy(shadow = Engraved, fontFamily = if (serif) FontFamily.Serif else fontFamily)

private val SkeuoType = Typography().run {
    copy(
        displayLarge = displayLarge.engraved(true),
        displayMedium = displayMedium.engraved(true),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp).engraved(true),
        headlineLarge = headlineLarge.engraved(true),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp).engraved(true),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold).engraved(true),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold).engraved(true),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp).engraved(true),
        titleSmall = titleSmall.engraved(true),
        bodyLarge = bodyLarge.engraved(),
        bodyMedium = bodyMedium.engraved(),
        bodySmall = bodySmall.engraved(),
        labelLarge = labelLarge.engraved(),
        labelMedium = labelMedium.engraved(),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp).engraved(),
    )
}

// Metro: caratteri sottili e grandi come Segoe WP, niente ombre, niente grazie.
private val MetroType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Light),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Light),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-0.5).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

@Composable
fun ChiarellaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = when (Look.style) { UiStyle.CLASSIC -> ClassicType; UiStyle.SKEUO -> SkeuoType; UiStyle.METRO -> MetroType }, content = content)
}
