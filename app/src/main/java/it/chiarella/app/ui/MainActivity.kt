package it.chiarella.app.ui

import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import it.chiarella.app.ui.motion.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import it.chiarella.app.ChiarellaApp
import it.chiarella.app.ui.theme.ChiarellaTheme
import it.chiarella.app.ui.components.SystemVolumeNotice
import it.chiarella.app.ui.motion.Motion

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        val app = application as ChiarellaApp
        Motion.reduced = app.store.preferences.value.reducedAnimations

        // Uscita dallo splash: l'icona si espande e svanisce.
        splash.setOnExitAnimationListener { provider ->
            if (app.store.preferences.value.reducedAnimations) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            // Aperta da notifica, widget o app recenti, Android mostra lo splash senza icona:
            // in quel caso iconView non esiste (e leggerlo manda in crash), si sfuma solo lo sfondo.
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            icon?.animate()
                ?.scaleX(1.6f)?.scaleY(1.6f)?.alpha(0f)
                ?.setInterpolator(AnticipateInterpolator(1.2f))
                ?.setDuration(420L)
                ?.start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(160L)
                .setDuration(380L)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val preferences by app.store.preferences.collectAsStateWithLifecycle()
            SideEffect { Motion.reduced = preferences.reducedAnimations }
            ChiarellaTheme {
                var screen by rememberSaveable { mutableStateOf(if (app.store.onboardingDone) Screen.HOME else Screen.GUIDE) }
                // "Un momento per te" aperto dalla chat: alla chiusura si torna alla chat.
                var relaxFromChat by rememberSaveable { mutableStateOf(false) }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(600, delayMillis = 150)) + scaleIn(tween(600, delayMillis = 150), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.04f))
                    },
                    label = "screen",
                ) { current ->
                    when (current) {
                        Screen.GUIDE -> OnboardingScreen(onFinish = {
                            app.store.onboardingDone = true
                            screen = Screen.HOME
                        })
                        Screen.RELAX -> RelaxScreen(
                            store = app.store,
                            onClose = {
                                screen = if (relaxFromChat) Screen.CHAT else Screen.HOME
                                relaxFromChat = false
                            },
                            onChoice = { if (relaxFromChat) app.ai.sendEvent(it) },
                            onTalk = if (relaxFromChat) null else ({ proposal ->
                                app.ai.sendEvent(proposal)
                                screen = Screen.CHAT
                            }),
                        )
                        Screen.CHAT -> ChatScreen(
                            ai = app.ai, mixer = app.mixer,
                            onClose = { screen = Screen.HOME },
                            onMoment = {
                                relaxFromChat = true
                                screen = Screen.RELAX
                            },
                        )
                        Screen.HOME -> HomeScreen(
                            mixer = app.mixer, store = app.store, updater = app.updater,
                            onShowGuide = { screen = Screen.GUIDE },
                            onRelax = { screen = Screen.RELAX },
                            onChat = { screen = Screen.CHAT },
                        )
                    }
                }
                if (screen != Screen.GUIDE) SystemVolumeNotice(
                    enabled = preferences.showVolumeNotice,
                    onDisable = { app.store.setPreferences(preferences.copy(showVolumeNotice = false)) },
                )
            }
        }
    }
}

private enum class Screen { HOME, GUIDE, RELAX, CHAT }
