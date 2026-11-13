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
    val nothing get() = style == UiStyle.NOTHING
    val dueffe get() = style == UiStyle.DUEFFE
}

private fun pick(classic: Long, skeuo: Long, metro: Long, nothing: Long, dueffe: Long) = Color(
    when (Look.style) {
        UiStyle.CLASSIC -> classic
        UiStyle.SKEUO -> skeuo
        UiStyle.METRO -> metro
        UiStyle.NOTHING -> nothing
        UiStyle.DUEFFE -> dueffe
    }
)

/** I colori seguono lo stile attivo: leggerli in composizione o nel disegno aggiorna la UI al cambio. */
object Palette {
    val Night get() = pick(0xFF080B19, 0xFF1B110A, 0xFF000000, 0xFF000000, 0xFF050505)
    val NightDeep get() = pick(0xFF05070F, 0xFF0E0805, 0xFF000000, 0xFF000000, 0xFF050505)
    val Indigo get() = pick(0xFF141A3F, 0xFF2C1C11, 0xFF1F1F1F, 0xFF111111, 0xFF0B0A08)
    val Surface get() = pick(0xFF161B33, 0xFF2A1C13, 0xFF1F1F1F, 0xFF111111, 0xFF11100D)
    val SurfaceHigh get() = pick(0xFF1E2444, 0xFF3A2819, 0xFF1F1F1F, 0xFF1A1A1A, 0xFF1A1711)
    val Glass get() = pick(0x14FFFFFF, 0x1AF6E3C0, 0xFF262626, 0xFF111111, 0xFF11100D)
    val GlassBorder get() = pick(0x1FFFFFFF, 0x4DD8B26A, 0xFFFFFFFF, 0xFF333333, 0x29FFDC80)
    val Text get() = pick(0xFFEDF0FA, 0xFFF6EAD2, 0xFFFFFFFF, 0xFFE8E8E8, 0xFFFFFAF0)
    val TextMuted get() = pick(0xFFA0A8C6, 0xFFCDB792, 0xFFA6A6A6, 0xFF999999, 0xFFB8AA8D)
    val TextFaint get() = pick(0xFF6B7396, 0xFF8F7A5C, 0xFF737373, 0xFF666666, 0xFF8A7F69)
    val Lavender get() = pick(0xFF8B7CF6, 0xFFE0A24A, 0xFF1BA1E2, 0xFFFFFFFF, 0xFFFFD84D)
    val Sky get() = pick(0xFF5EC8F2, 0xFFF2D28C, 0xFF1BA1E2, 0xFFFFFFFF, 0xFFFFB800)
    val Peach get() = pick(0xFFFFB38A, 0xFFF08A5D, 0xFF1BA1E2, 0xFFD71921, 0xFFFF9B85)
    /** Testo e icone sopra l'accento (Lavender): bianco, ma nero sul bianco di Nothing. */
    val OnAccent get() = pick(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF161006)
}

private fun scheme(): ColorScheme = darkColorScheme(
    primary = Palette.Lavender,
    onPrimary = if (Look.skeuo) Color(0xFF2B1A0E) else if (Look.nothing) Color.Black else if (Look.dueffe) Palette.OnAccent else Color.White,
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
    MaterialTheme(colorScheme = scheme(), typography = when (Look.style) { UiStyle.CLASSIC -> ClassicType; UiStyle.SKEUO -> SkeuoType; UiStyle.METRO -> MetroType; UiStyle.NOTHING -> NothingType; UiStyle.DUEFFE -> DueffeType }, content = content)
}
