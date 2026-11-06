package pt.avus.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import android.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.avus.app.data.ThemeMode
import pt.avus.app.ui.AvusApp
import pt.avus.app.ui.AvusViewModel
import pt.avus.app.ui.theme.AvusTheme

class MainActivity : ComponentActivity() {

    private val vm: AvusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = when (state.settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status/navigation bar icons follow the app's theme, also when it differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            AvusTheme(darkTheme = dark, dynamicColor = state.settings.dynamicColor) {
                AvusApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Text shared into the app (a reply from a home) or a tap on a reminder notification. */
    private fun handle(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) vm.receiveSharedReply(text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
            }
            intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_REPLIES -> vm.requestRepliesTab()
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_REPLIES = "replies"
    }
}
