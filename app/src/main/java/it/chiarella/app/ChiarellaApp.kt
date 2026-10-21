package it.chiarella.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import it.chiarella.app.audio.Mixer
import it.chiarella.app.data.Store
import it.chiarella.app.update.Updater
import it.chiarella.app.widget.ChiarellaWidget
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ChiarellaApp : Application() {

    lateinit var store: Store
        private set
    lateinit var mixer: Mixer
        private set
    lateinit var updater: Updater
        private set

    private val scope = MainScope()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        mixer = Mixer(this, store)
        updater = Updater(this)

        // Tiene il widget allineato con lo stato della riproduzione e con i profili.
        scope.launch {
            combine(
                mixer.state.map { Triple(it.playing, it.active, it.presetId) }.distinctUntilChanged(),
                store.presets,
            ) { a, b -> a to b }
                .drop(1)
                .debounce(250)
                .collect { runCatching { ChiarellaWidget().updateAll(this@ChiarellaApp) } }
        }
    }
}
