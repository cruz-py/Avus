package pt.avus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

enum class AppTab(val label: String, val icon: ImageVector) {
    HOMES("Lares", Icons.Filled.Home),
    RESIDENT("Utente", Icons.Filled.Person),
    SEND("Enviar", Icons.Filled.Email),
    REPLIES("Respostas", Icons.AutoMirrored.Filled.List),
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { s ->
                    NavigationBarItem(
                        selected = currentTab == s,
                        onClick = { currentTab = s },
                        icon = {
                            val badgeCount = when (s) {
                                AppTab.SEND -> state.selectedIds.size
                                else -> 0
                            }
                            if (badgeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$badgeCount") } }) { Icon(s.icon, null) }
                            } else Icon(s.icon, null)
                        },
                        label = { Text(s.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (!state.isLoaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else when (currentTab) {
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
