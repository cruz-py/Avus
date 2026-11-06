package pt.avus.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pt.avus.app.ui.AvusApp
import pt.avus.app.ui.AvusViewModel
import pt.avus.app.ui.theme.AvusTheme

class MainActivity : ComponentActivity() {

    private val vm: AvusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            AvusTheme {
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
