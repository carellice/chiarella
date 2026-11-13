package it.chiarella.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.chiarella.app.R

/**
 * Stile Nothing: nero OLED, monocromo, tipografia tecnica (Space Grotesk, Space Mono,
 * Doto a matrice di punti) e un solo accento rosso come "spia" di ciò che è vivo.
 */
object Nothing {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF111111)
    val SurfaceRaised = Color(0xFF1A1A1A)
    val Border = Color(0xFF222222)
    val BorderVisible = Color(0xFF333333)
    val TextDisabled = Color(0xFF666666)
    val TextSecondary = Color(0xFF999999)
    val TextPrimary = Color(0xFFE8E8E8)
    val TextDisplay = Color(0xFFFFFFFF)
    val Accent = Color(0xFFD71921)

    /** Ease-out sobrio del design system: niente rimbalzi. */
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun grotesk(w: Int) = Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val Grotesk = FontFamily(grotesk(300), grotesk(400), grotesk(500), grotesk(700))
    val Mono = FontFamily(Font(R.font.space_mono, FontWeight.Normal), Font(R.font.space_mono_bold, FontWeight.Bold))

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val Doto = FontFamily(Font(R.font.doto, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))

    /** Etichetta "da pannello strumenti": Space Mono maiuscolo e spaziato. */
    val Label = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.08.em, lineHeight = 14.sp)

    /** Numeri e dati: sempre Space Mono. */
    val Data = TextStyle(fontFamily = Mono, fontSize = 13.sp, letterSpacing = 0.02.em)
}

internal val NothingType = Typography().run {
    fun TextStyle.g(size: Int, w: FontWeight = FontWeight.Normal, track: Double = 0.0) =
        copy(fontFamily = Nothing.Grotesk, fontSize = size.sp, fontWeight = w, letterSpacing = track.em)
    copy(
        displayLarge = displayLarge.copy(fontFamily = Nothing.Doto, letterSpacing = (-0.02).em),
        displayMedium = displayMedium.copy(fontFamily = Nothing.Doto, letterSpacing = (-0.02).em),
        displaySmall = TextStyle(fontFamily = Nothing.Doto, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em),
        headlineLarge = headlineLarge.g(32, FontWeight.Light, -0.02),
        headlineMedium = headlineMedium.g(28, FontWeight.Light, -0.02),
        headlineSmall = headlineSmall.g(24, FontWeight.Normal, -0.01),
        titleLarge = titleLarge.g(22, FontWeight.Normal, -0.01),
        titleMedium = titleMedium.g(16, FontWeight.Medium),
        titleSmall = titleSmall.g(14, FontWeight.Medium),
        bodyLarge = bodyLarge.g(16),
        bodyMedium = bodyMedium.g(14, track = 0.01),
        bodySmall = bodySmall.g(12, track = 0.02),
        labelLarge = Nothing.Label.copy(fontSize = 13.sp, letterSpacing = 0.06.em),
        labelMedium = Nothing.Label.copy(fontSize = 12.sp),
        labelSmall = Nothing.Label,
    )
}

/** Griglia di punti sullo sfondo, il motivo a matrice di Nothing. */
fun Modifier.dotGrid(color: Color = Nothing.BorderVisible, step: Float = 16f, alpha: Float = 0.5f) = drawBehind {
    val s = step.dp.toPx()
    val r = 1.dp.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) { drawCircle(color, r, Offset(x, y), alpha = alpha); x += s }
        y += s
    }
}
