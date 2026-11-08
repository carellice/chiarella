package it.chiarella.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.chiarella.app.R
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.spring
import kotlin.random.Random

/**
 * Materiali "fisici" dell'interfaccia: legno di noce, pelle cucita, ottone,
 * alluminio spazzolato, vetro e LED. Tutto è disegnato in Compose, le sole
 * immagini sono le due texture di legno e pelle.
 */
object Skeuo {
    val Brass = Color(0xFFD8B26A)
    val BrassLight = Color(0xFFF6DFA4)
    val BrassDark = Color(0xFF7A5A26)
    val Chrome = Color(0xFFE9E6E1)
    val ChromeDark = Color(0xFF6E6A66)
    val Gunmetal = Color(0xFF2E2A27)
    val Ink = Color(0xFF2B1A0E)
    val Paper = Color(0xFFF2E6CD)
    val Amber = Color(0xFFFFB648)
    val Shadow = Color(0xFF000000)
}

@Composable
fun rememberTexture(@DrawableRes id: Int): ShaderBrush {
    val image = ImageBitmap(id)
    return remember(image) { ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)) }
}

@Composable
private fun ImageBitmap(@DrawableRes id: Int) = androidx.compose.ui.graphics.ImageBitmap.imageResource(id)

/** Piano di noce lucidato con vignettatura calda, come il mobile di una vecchia radio. */
@Composable
fun Modifier.walnut(): Modifier {
    val wood = rememberTexture(R.drawable.tex_walnut)
    return background(wood).drawBehind {
        // Lucido della vernice: una fascia di luce in alto e bordi più scuri.
        drawRect(Brush.verticalGradient(0f to Color(0x22FFE2B0), 0.35f to Color.Transparent, 1f to Color(0x66000000)))
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent, 1f to Color(0xAA000000),
                center = Offset(size.width / 2, size.height * 0.42f), radius = size.maxDimension * 0.75f,
            )
        )
    }
}

/** Pelle scura con cuciture a vista e leggero rilievo. */
@Composable
fun Modifier.leather(shape: Shape, elevation: Dp = 10.dp, stitch: Boolean = true, tint: Color = Color.Transparent): Modifier {
    val hide = rememberTexture(R.drawable.tex_leather)
    return this
        .shadow(elevation, shape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
        .clip(shape)
        .background(hide)
        .drawBehind {
            if (tint.alpha > 0f) drawRect(tint)
            drawRect(Brush.verticalGradient(listOf(Color(0x1FFFE8C8), Color.Transparent, Color(0x55000000))))
            if (stitch) stitching(shape, 7.dp.toPx())
        }
        .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFE6C0), Color(0x99000000))), shape)
}

private fun DrawScope.stitching(shape: Shape, inset: Float) {
    val inner = Size(size.width - inset * 2, size.height - inset * 2)
    if (inner.width <= 0 || inner.height <= 0) return
    val outline = shape.createOutline(inner, layoutDirection, this)
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx()))
    translate(inset, inset + 1f) {
        drawOutline(outline, Color(0x88000000), style = Stroke(1.6.dp.toPx(), pathEffect = dash))
    }
    translate(inset, inset) {
        drawOutline(outline, Color(0xFFCBB089).copy(alpha = 0.7f), style = Stroke(1.2.dp.toPx(), pathEffect = dash))
    }
}

/** Pannello in rilievo: luce dall'alto, spigolo chiaro sopra e scuro sotto. */
fun Modifier.raised(shape: Shape, top: Color, bottom: Color, elevation: Dp = 6.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(top, bottom)))
    .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x10FFFFFF), Color(0x80000000))), shape)

/** Incavo scavato nel materiale: ombra interna in alto, riflesso sul bordo inferiore. */
fun Modifier.recessed(shape: Shape, color: Color = Color(0xFF120B07)): Modifier = this
    .clip(shape)
    .background(color)
    .drawBehind {
        drawRect(Brush.verticalGradient(0f to Color(0xCC000000), 0.25f to Color.Transparent))
    }
    .border(1.dp, Brush.verticalGradient(listOf(Color(0xAA000000), Color(0x22000000), Color(0x40FFE6C0))), shape)

/** Alluminio spazzolato (scuro o chiaro) con righe sottili orizzontali. */
fun Modifier.brushedMetal(shape: Shape, dark: Boolean = true, elevation: Dp = 12.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
    .clip(shape)
    .drawWithCache {
        val base = if (dark) listOf(Color(0xFF4A4541), Color(0xFF2A2623), Color(0xFF1C1917))
        else listOf(Color(0xFFF1EEEA), Color(0xFFC9C4BE), Color(0xFFA8A29C))
        val r = Random(11)
        val lines = List((size.height / 1.5f).toInt().coerceAtLeast(1)) { (it * 1.5f) to r.nextFloat() }
        onDrawBehind {
            drawRect(Brush.verticalGradient(base))
            lines.forEach { (y, a) ->
                val c = if (a > 0.5f) Color.White.copy(alpha = (a - 0.5f) * 0.10f) else Color.Black.copy(alpha = a * 0.12f)
                drawLine(c, Offset(0f, y), Offset(size.width, y), 1f)
            }
            // Riflesso diagonale della luce sul metallo.
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.45f to Color.White.copy(alpha = if (dark) 0.07f else 0.25f), 0.55f to Color.Transparent,
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
            )
        }
    }
    .border(1.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x22FFFFFF), Color(0xCC000000))), shape)

/** Vetro sopra un display: riflesso curvo nella metà superiore. */
fun DrawScope.glassGlare(alpha: Float = 0.16f) {
    drawRect(
        Brush.verticalGradient(0f to Color.White.copy(alpha = alpha), 0.48f to Color.White.copy(alpha = alpha * 0.25f), 0.5f to Color.Transparent),
    )
}

/** Vite in ottone con taglio, per gli angoli dei pannelli. */
fun DrawScope.screw(center: Offset, radius: Float, angle: Float = 35f) {
    drawCircle(Color(0x99000000), radius + 1.2f, center + Offset(0f, 1.2f))
    drawCircle(
        Brush.radialGradient(listOf(Skeuo.BrassLight, Skeuo.Brass, Skeuo.BrassDark), center = center - Offset(radius * 0.4f, radius * 0.4f), radius = radius * 1.8f),
        radius, center,
    )
    val rad = Math.toRadians(angle.toDouble())
    val dx = (kotlin.math.cos(rad) * radius * 0.75f).toFloat()
    val dy = (kotlin.math.sin(rad) * radius * 0.75f).toFloat()
    drawLine(Color(0xCC3A2608), center - Offset(dx, dy), center + Offset(dx, dy), radius * 0.32f)
}

/** Spia luminosa: vetro colorato che si accende con alone. */
@Composable
fun Led(color: Color, level: Float, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        if (level > 0f) drawCircle(color.copy(alpha = 0.45f * level), r * 2.6f)
        drawCircle(Color(0xFF0C0806), r * 1.15f)
        val lit = lerp(color.copy(red = color.red * 0.25f, green = color.green * 0.25f, blue = color.blue * 0.25f), color, level)
        drawCircle(Brush.radialGradient(listOf(lerp(lit, Color.White, 0.55f * level), lit, lit.copy(alpha = 0.9f)), radius = r), r)
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.32f, center - Offset(r * 0.3f, r * 0.35f))
    }
}

/**
 * Pulsante tondo in metallo: ghiera esterna, cappuccio in alluminio tornito
 * (riflessi conici) e affondamento quando viene premuto.
 */
@Composable
fun MetalButton(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "metalPress")
    Box(
        modifier
            .size(size)
            .graphicsLayer { val s = 1f - 0.05f * press; scaleX = s; scaleY = s }
            .shadow((6f * (1f - press) + 1f).dp, CircleShape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
            .clip(CircleShape)
            .drawBehind {
                val r = this.size.minDimension / 2
                // Ghiera.
                drawCircle(Brush.linearGradient(listOf(Color(0xFFBDB6AE), Color(0xFF3A3531)), start = Offset.Zero, end = Offset(this.size.width, this.size.height)), r)
                val face = r * 0.84f
                val tones = if (dark) listOf(Color(0xFF5C5650), Color(0xFF2B2724), Color(0xFF6B655F), Color(0xFF26221F), Color(0xFF5C5650))
                else listOf(Color(0xFFF4F1EC), Color(0xFFADA7A0), Color(0xFFEDE9E3), Color(0xFF9E9891), Color(0xFFF4F1EC))
                drawCircle(Brush.sweepGradient(tones), face)
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f - 0.12f * press), Color.Black.copy(alpha = 0.15f + 0.25f * press))), face)
                drawCircle(Color.Black.copy(alpha = 0.5f), face, style = Stroke(1f))
            }
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Bottone "gelatina" lucido e colorato (stile vetro soffiato). */
@Composable
fun GlossyButton(
    size: Dp,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "glossPress")
    Box(
        modifier
            .size(size)
            .graphicsLayer { val s = 1f - 0.07f * press; scaleX = s; scaleY = s }
            .shadow(10.dp, CircleShape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
            .clip(CircleShape)
            .drawBehind {
                val r = this.size.minDimension / 2
                drawCircle(Brush.linearGradient(listOf(Color(0xFFD9D3CB), Color(0xFF2E2A27)), start = Offset.Zero, end = Offset(this.size.width, this.size.height)), r)
                val face = r * 0.86f
                val deep = lerp(color, Color.Black, 0.55f)
                drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color, deep), center = center + Offset(0f, face * 0.35f), radius = face * 1.25f), face)
                drawCircle(Color.Black.copy(alpha = 0.25f * press), face)
                // Lucido superiore.
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.05f)), startY = center.y - face * 0.92f, endY = center.y),
                    topLeft = Offset(center.x - face * 0.68f, center.y - face * 0.92f),
                    size = Size(face * 1.36f, face * 0.95f),
                )
                drawCircle(deep, face, style = Stroke(1.5f))
            }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Targhetta in ottone satinato. */
fun Modifier.brassPlate(shape: Shape, elevation: Dp = 3.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Skeuo.BrassLight, Skeuo.Brass, Color(0xFFA9823F))))
    .border(1.dp, Brush.verticalGradient(listOf(Color(0xCCFFF6D8), Color(0xFF5E4219))), shape)

/** Cappuccio in alluminio tornito con ghiera, da usare su qualsiasi Box tondo cliccabile. */
fun Modifier.metalCap(dark: Boolean = true, elevation: Dp = 5.dp): Modifier = this
    .shadow(elevation, CircleShape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
    .clip(CircleShape)
    .drawBehind {
        val r = size.minDimension / 2
        drawCircle(Brush.linearGradient(listOf(Color(0xFFCFC8BF), Color(0xFF34302C)), start = Offset.Zero, end = Offset(size.width, size.height)), r)
        val face = r * 0.86f
        val tones = if (dark) listOf(Color(0xFF5C5650), Color(0xFF2B2724), Color(0xFF6B655F), Color(0xFF26221F), Color(0xFF5C5650))
        else listOf(Color(0xFFF4F1EC), Color(0xFFADA7A0), Color(0xFFEDE9E3), Color(0xFF9E9891), Color(0xFFF4F1EC))
        drawCircle(Brush.sweepGradient(tones), face)
        drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.18f))), face)
        drawCircle(Color.Black.copy(alpha = 0.5f), face, style = Stroke(1f))
    }

/** Vetro colorato bombato e lucido, come un tasto di una vecchia radio. */
fun Modifier.glossyCap(color: Color, elevation: Dp = 9.dp): Modifier = this
    .shadow(elevation, CircleShape, ambientColor = Skeuo.Shadow, spotColor = Skeuo.Shadow)
    .clip(CircleShape)
    .drawBehind {
        val r = size.minDimension / 2
        drawCircle(Brush.linearGradient(listOf(Color(0xFFD9D3CB), Color(0xFF2E2A27)), start = Offset.Zero, end = Offset(size.width, size.height)), r)
        val face = r * 0.86f
        val deep = lerp(color, Color.Black, 0.55f)
        drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color, deep), center = center + Offset(0f, face * 0.35f), radius = face * 1.25f), face)
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.05f)), startY = center.y - face * 0.92f, endY = center.y),
            topLeft = Offset(center.x - face * 0.68f, center.y - face * 0.92f),
            size = Size(face * 1.36f, face * 0.95f),
        )
        drawCircle(deep, face, style = Stroke(1.5f))
    }

/** Display LCD ambra retroilluminato, incassato nel pannello. */
fun Modifier.lcd(shape: Shape): Modifier = this
    .recessed(shape, Color(0xFF1C1206))
    .drawBehind {
        drawRect(Brush.radialGradient(listOf(Color(0x33FFB648), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.5f), radius = size.maxDimension * 0.8f))
        var y = 0f
        while (y < size.height) { drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y), 1f); y += 3f }
        glassGlare(0.08f)
    }

private val Brass = Brush.verticalGradient(listOf(Skeuo.BrassLight, Skeuo.Brass, Color(0xFFA9823F)))

/** Ottone nello stile skeuomorfico, il gradiente lavanda-cielo in quello classico. */
val BrassBrush: Brush get() = when {
    Look.skeuo -> Brass
    Look.metro -> androidx.compose.ui.graphics.SolidColor(Palette.Lavender)
    else -> Brush.linearGradient(listOf(Palette.Lavender, Palette.Sky))
}

/** Tasto rettangolare in ottone (o metallo scuro) con bordo smussato. */
fun Modifier.keyCap(shape: Shape, brass: Boolean, elevation: Dp = 5.dp): Modifier =
    if (brass) brassPlate(shape, elevation)
    else brushedMetal(shape, dark = true, elevation = elevation)

/** Ramo per stile: applica [skeuo], [metro] o [classic] secondo Look.style. */
inline fun Modifier.look(
    skeuo: Modifier.() -> Modifier,
    classic: Modifier.() -> Modifier,
    metro: Modifier.() -> Modifier,
): Modifier = when {
    Look.skeuo -> skeuo()
    Look.metro -> metro()
    else -> classic()
}

/** Come sopra, ma lo stile Metro riusa il ramo [classic]. */
inline fun Modifier.look(skeuo: Modifier.() -> Modifier, classic: Modifier.() -> Modifier): Modifier =
    look(skeuo, classic, classic)
