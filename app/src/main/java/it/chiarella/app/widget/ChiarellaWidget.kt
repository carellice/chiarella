package it.chiarella.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.chiarella.app.ChiarellaApp
import it.chiarella.app.R
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.data.Preset
import it.chiarella.app.playback.PlaybackService
import it.chiarella.app.ui.MainActivity

class ChiarellaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChiarellaWidget()
}

class ChiarellaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ChiarellaApp
        provideContent {
            val state by app.mixer.state.collectAsState()
            val presets by app.store.presets.collectAsState()
            WidgetContent(context, state, presets, canResume = state.hasMix || app.store.lastMix.isNotEmpty())
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 100.dp)
        private val WIDE = DpSize(250.dp, 100.dp)
    }
}

private val White = ColorProvider(Color.White)
private val Muted = ColorProvider(Color(0xFFA9B0CC))

@Composable
private fun WidgetContent(context: Context, state: Mixer.State, presets: List<Preset>, canResume: Boolean) {
    val wide = LocalSize.current.width >= 250.dp
    val sounds = state.active.mapNotNull { Sounds[it] }
    val preset = state.presetId?.let { id -> presets.firstOrNull { it.id == id } }
    val title = when {
        preset != null -> preset.name
        sounds.isEmpty() -> "Rilassati"
        sounds.size == 1 -> sounds[0].name
        else -> "${sounds[0].name} +${sounds.size - 1}"
    }
    val subtitle = when {
        sounds.isEmpty() -> if (canResume) "Tocca ▶ per riprendere" else "Tocca ▶ per scegliere i suoni"
        state.playing -> "In riproduzione"
        else -> "In pausa"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("CHIARELLA", style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(2.dp))
                Text(title, maxLines = 2, style = TextStyle(color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(2.dp))
                Text(subtitle, maxLines = 1, style = TextStyle(color = Muted, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .size(56.dp)
                    .background(ImageProvider(R.drawable.widget_play_bg))
                    .cornerRadius(28.dp)
                    .clickable(
                        // Niente da riprendere (primo utilizzo): apre l'app per scegliere i suoni.
                        if (canResume) actionStartService(serviceIntent(context, PlaybackService.ACTION_TOGGLE), isForegroundService = true)
                        else actionStartActivity<MainActivity>()
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (state.playing) "Pausa" else "Riproduci",
                    colorFilter = ColorFilter.tint(White),
                    modifier = GlanceModifier.size(26.dp),
                )
            }
        }
        if (wide && presets.isEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Text(
                "Salva un profilo nell'app per averlo qui a portata di tocco.",
                maxLines = 2,
                style = TextStyle(color = Muted, fontSize = 12.sp),
            )
        }
        if (wide && presets.isNotEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                presets.take(3).forEachIndexed { index, p ->
                    if (index > 0) Spacer(GlanceModifier.width(8.dp))
                    val selected = p.id == state.presetId && state.playing
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(36.dp)
                            .background(ImageProvider(if (selected) R.drawable.widget_chip_active_bg else R.drawable.widget_chip_bg))
                            .cornerRadius(16.dp)
                            .clickable(actionStartService(presetIntent(context, p.id), isForegroundService = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            p.name, maxLines = 1,
                            modifier = GlanceModifier.padding(horizontal = 8.dp),
                            style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}

private fun serviceIntent(context: Context, action: String) =
    Intent(context, PlaybackService::class.java).setAction(action)

private fun presetIntent(context: Context, id: String) =
    serviceIntent(context, PlaybackService.ACTION_PRESET)
        .setData(Uri.parse("chiarella://preset/$id"))
        .putExtra(PlaybackService.EXTRA_PRESET, id)
