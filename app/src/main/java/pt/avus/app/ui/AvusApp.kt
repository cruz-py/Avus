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

enum class Separador(val rotulo: String, val icone: ImageVector) {
    LARES("Lares", Icons.Filled.Home),
    UTENTE("Utente", Icons.Filled.Person),
    ENVIAR("Enviar", Icons.Filled.Email),
    ACOMPANHAR("Respostas", Icons.AutoMirrored.Filled.List),
}

@Composable
fun AvusApp(vm: AvusViewModel = viewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    var separador by rememberSaveable { mutableStateOf(Separador.LARES) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbar.showSnackbar(it)
            vm.mensagemMostrada()
        }
    }

    BackHandler(enabled = separador != Separador.LARES) { separador = Separador.LARES }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Separador.entries.forEach { s ->
                    NavigationBarItem(
                        selected = separador == s,
                        onClick = { separador = s },
                        icon = {
                            val contagem = when (s) {
                                Separador.ENVIAR -> estado.selecionados.size
                                else -> 0
                            }
                            if (contagem > 0) {
                                BadgedBox(badge = { Badge { Text("$contagem") } }) { Icon(s.icone, null) }
                            } else Icon(s.icone, null)
                        },
                        label = { Text(s.rotulo) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (!estado.carregado) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else when (separador) {
                Separador.LARES -> EcraLares(estado, vm, irParaEnvio = { separador = Separador.ENVIAR })
                Separador.UTENTE -> EcraUtente(estado, vm)
                Separador.ENVIAR -> EcraEnviar(
                    estado, vm,
                    irParaLares = { separador = Separador.LARES },
                    irParaUtente = { separador = Separador.UTENTE },
                    irParaRespostas = { separador = Separador.ACOMPANHAR },
                )
                Separador.ACOMPANHAR -> EcraRespostas(estado, vm, irParaLares = { separador = Separador.LARES })
            }
        }
    }
}
