package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.spring
import it.chiarella.app.ui.motion.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.chiarella.app.audio.Sounds
import it.chiarella.app.data.Preset
import it.chiarella.app.ui.theme.Palette

@Composable
fun PresetRow(
    presets: List<Preset>,
    activeId: String?,
    playing: Boolean,
    onApply: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "new") { NewPresetCard(onCreate) }
        if (presets.isEmpty()) {
            item(key = "empty") { EmptyPresetsHint(Modifier.animateItem()) }
        }
        items(presets, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                selected = preset.id == activeId,
                playing = playing,
                onClick = { onApply(preset) },
                onLongClick = { onDelete(preset) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val sounds = preset.mix.keys.mapNotNull { Sounds[it] }
    val colors = sounds.map { it.primary }.ifEmpty { listOf(Palette.Lavender) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.94f
            selected -> 1.03f
            else -> 1f
        },
        spring(dampingRatio = 0.5f, stiffness = 420f), label = "presetScale",
    )
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(450), label = "presetGlow")
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier
            .padding(vertical = 6.dp)
            .width(158.dp)
            .height(112.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.first().copy(alpha = 0.38f + 0.22f * glow),
                        (colors.getOrNull(1) ?: colors.first()).copy(alpha = 0.14f + 0.16f * glow),
                    )
                )
            )
            .background(Palette.Glass)
            .border(
                width = (1f + glow).dp,
                color = Color.White.copy(alpha = 0.12f + 0.6f * glow),
                shape = shape,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconStack(sounds, size = 28.dp, max = 3)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(selected, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Equalizer(playing, Color.White, Modifier.size(width = 16.dp, height = 14.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                preset.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (sounds.size == 1) "1 suono" else "${sounds.size} suoni",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun NewPresetCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .width(96.dp)
            .height(112.dp)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.Text)
            }
            Spacer(Modifier.height(8.dp))
            Text("Salva mix", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        }
    }
}

/** Al primo utilizzo non ci sono profili: spiega come crearne uno. */
@Composable
private fun EmptyPresetsHint(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .padding(vertical = 6.dp)
            .width(236.dp)
            .height(112.dp)
            .clip(shape)
            .background(Palette.Glass)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ancora nessun profilo", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Accendi i suoni che ti piacciono, regola i volumi e tocca «Salva mix».",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
    }
}
