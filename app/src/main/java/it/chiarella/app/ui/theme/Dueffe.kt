package it.chiarella.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.chiarella.app.R

/** Stile Dueffe: nero caldo, superfici ambrate, testo avorio e un solo accento oro. */
object Dueffe {
    val Primary = androidx.compose.ui.graphics.Color(0xFFFFD84D)
    val PrimaryStrong = androidx.compose.ui.graphics.Color(0xFFFFB800)
    val Secondary = androidx.compose.ui.graphics.Color(0xFFFFF0AD)
    val LineStrong = androidx.compose.ui.graphics.Color(0x57FFD84D)

    val Reveal = CubicBezierEasing(0.22f, 0.78f, 0.16f, 1f)

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun bricolage(w: Int) = Font(R.font.bricolage, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

    val Display = FontFamily(bricolage(600), bricolage(700), bricolage(800))
    val Body = FontFamily(inter(400), inter(500), inter(600), inter(700))
}

// Scala tipografica del design system Dueffe: Bricolage per titoli, Inter per testo ed etichette.
internal val DueffeType = Typography().run {
    fun t(f: FontFamily, w: Int, size: Int, line: Int, track: Double = 0.0) =
        TextStyle(fontFamily = f, fontWeight = FontWeight(w), fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.em)
    val d = Dueffe.Display
    val b = Dueffe.Body
    copy(
        displayLarge = t(d, 800, 48, 48, -0.035),
        displayMedium = t(d, 800, 38, 40, -0.03),
        displaySmall = t(d, 700, 30, 34, -0.02),
        headlineLarge = t(d, 700, 30, 34, -0.02),
        headlineMedium = t(d, 700, 26, 30, -0.02),
        headlineSmall = t(d, 700, 22, 26, -0.01),
        titleLarge = t(d, 600, 20, 24),
        titleMedium = t(d, 600, 16, 20),
        titleSmall = t(b, 600, 14, 18),
        bodyLarge = t(b, 400, 16, 24),
        bodyMedium = t(b, 400, 14, 20),
        bodySmall = t(b, 400, 12, 16),
        labelLarge = t(b, 600, 14, 18),
        labelMedium = t(b, 600, 12, 16),
        labelSmall = t(b, 500, 11, 14, 0.04),
    )
}
