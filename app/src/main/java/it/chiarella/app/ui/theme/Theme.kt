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

/** Stile dell'interfaccia scelto nelle impostazioni: classico (notte) o skeuomorfico (legno, pelle, ottone). */
object Look {
    var skeuo by mutableStateOf(false)
}

private fun pick(classic: Long, skeuo: Long) = if (Look.skeuo) Color(skeuo) else Color(classic)

/** I colori seguono lo stile attivo: leggerli in composizione o nel disegno aggiorna la UI al cambio. */
object Palette {
    val Night get() = pick(0xFF080B19, 0xFF1B110A)
    val NightDeep get() = pick(0xFF05070F, 0xFF0E0805)
    val Indigo get() = pick(0xFF141A3F, 0xFF2C1C11)
    val Surface get() = pick(0xFF161B33, 0xFF2A1C13)
    val SurfaceHigh get() = pick(0xFF1E2444, 0xFF3A2819)
    val Glass get() = pick(0x14FFFFFF, 0x1AF6E3C0)
    val GlassBorder get() = pick(0x1FFFFFFF, 0x4DD8B26A)
    val Text get() = pick(0xFFEDF0FA, 0xFFF6EAD2)
    val TextMuted get() = pick(0xFFA0A8C6, 0xFFCDB792)
    val TextFaint get() = pick(0xFF6B7396, 0xFF8F7A5C)
    val Lavender get() = pick(0xFF8B7CF6, 0xFFE0A24A)
    val Sky get() = pick(0xFF5EC8F2, 0xFFF2D28C)
    val Peach get() = pick(0xFFFFB38A, 0xFFF08A5D)
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

@Composable
fun ChiarellaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = if (Look.skeuo) SkeuoType else ClassicType, content = content)
}
