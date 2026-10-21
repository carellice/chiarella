package it.chiarella.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import it.chiarella.app.ChiarellaApp
import it.chiarella.app.ui.theme.ChiarellaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Uscita dallo splash: l'icona si espande e svanisce.
        splash.setOnExitAnimationListener { provider ->
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

        val app = application as ChiarellaApp
        setContent {
            ChiarellaTheme {
                var showGuide by rememberSaveable { mutableStateOf(!app.store.onboardingDone) }
                AnimatedContent(
                    targetState = showGuide,
                    transitionSpec = {
                        (fadeIn(tween(600, delayMillis = 150)) + scaleIn(tween(600, delayMillis = 150), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.04f))
                    },
                    label = "guide",
                ) { guide ->
                    if (guide) {
                        OnboardingScreen(onFinish = {
                            app.store.onboardingDone = true
                            showGuide = false
                        })
                    } else {
                        HomeScreen(mixer = app.mixer, store = app.store, updater = app.updater, onShowGuide = { showGuide = true })
                    }
                }
            }
        }
    }
}
