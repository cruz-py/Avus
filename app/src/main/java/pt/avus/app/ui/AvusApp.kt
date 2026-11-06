package pt.avus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

enum class AppTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOMES("Lares", Icons.Outlined.Home, Icons.Filled.Home),
    RESIDENT("Utente", Icons.Outlined.Person, Icons.Filled.Person),
    SEND("Enviar", Icons.Outlined.Email, Icons.Filled.Email),
    REPLIES("Respostas", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
}

@Composable
fun AvusApp(vm: AvusViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOMES) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    BackHandler(enabled = currentTab != AppTab.HOMES) { currentTab = AppTab.HOMES }

    // A reminder notification (or a recorded reply) asks to show "Respostas".
    val openReplies by vm.openReplies.collectAsStateWithLifecycle()
    LaunchedEffect(openReplies) {
        if (openReplies) {
            currentTab = AppTab.REPLIES
            vm.repliesTabOpened()
        }
    }

    // Back from a phone call to a home: ask how it went.
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    val replyPrompt by vm.replyPrompt.collectAsStateWithLifecycle()
    if (state.isLoaded) {
        replyPrompt?.let { prompt ->
            ReplySheet(
                prompt = prompt,
                state = state,
                onDismiss = { vm.dismissReplyPrompt() },
                onSave = { homeId, status, note -> vm.saveReply(homeId, status, note) },
            )
        }
    }

    Scaffold(
        containerColor = screenBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ShortNavigationBar {
                AppTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            val icon = if (selected) tab.selectedIcon else tab.icon
                            val badgeCount = if (tab == AppTab.SEND) state.selectedIds.size else 0
                            if (badgeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$badgeCount") } }) { Icon(icon, null) }
                            } else Icon(icon, null)
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (!state.isLoaded) {
                ContainedLoadingIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            // Fade-through between tabs, using the theme's expressive motion.
            val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                currentTab,
                transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                label = "tabs",
            ) { tab ->
                when (tab) {
                    AppTab.HOMES -> HomesScreen(state, vm, goToSend = { currentTab = AppTab.SEND })
                    AppTab.RESIDENT -> ResidentScreen(state, vm)
                    AppTab.SEND -> SendScreen(
                        state, vm,
                        goToHomes = { currentTab = AppTab.HOMES },
                        goToResident = { currentTab = AppTab.RESIDENT },
                        goToReplies = { currentTab = AppTab.REPLIES },
                    )
                    AppTab.REPLIES -> RepliesScreen(state, vm, goToHomes = { currentTab = AppTab.HOMES })
                }
            }
        }
    }
}
