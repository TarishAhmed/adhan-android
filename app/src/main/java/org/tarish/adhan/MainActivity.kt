package org.tarish.adhan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.tarish.adhan.ui.AdhanRoot
import org.tarish.adhan.ui.RootViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootVm: RootViewModel by viewModels()
    private val deepLinks = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until onboarding state and theme are read — no flash of Home (§8.1).
        splash.setKeepOnScreenCondition { rootVm.startOnboarding.value == null || rootVm.themeMode.value == null }
        // The logo grows a little and fades into the app rather than cutting away.
        splash.setOnExitAnimationListener { p ->
            p.iconView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(SPLASH_EXIT_MS).start()
            p.view.animate().alpha(0f).setDuration(SPLASH_EXIT_MS).withEndAction { p.remove() }.start()
        }
        enableEdgeToEdge()
        setContent { AdhanRoot(rootVm, deepLinks.receiveAsFlow()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data?.scheme == "adhan") deepLinks.trySend(Unit)
    }
}

private const val SPLASH_EXIT_MS = 300L
