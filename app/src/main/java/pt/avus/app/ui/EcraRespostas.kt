package pt.avus.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import pt.avus.app.carta.Envio
import pt.avus.app.carta.GeradorCarta
import pt.avus.app.data.Contacto
import pt.avus.app.data.EstadoContacto
import pt.avus.app.data.Lar
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraRespostas(estado: EstadoApp, vm: AvusViewModel, irParaLares: () -> Unit) {
    val context = LocalContext.current
    var filtro by rememberSaveable { mutableStateOf<EstadoContacto?>(null) }
    var aEditar by remember { mutableStateOf<Contacto?>(null) }
    val lares = remember(estado.lares) { estado.lares.associateBy { it.id } }
    val contactos = estado.contactos.sortedByDescending { it.dataAtualizacao }
    val visiveis = contactos.filter { filtro == null || it.estado == filtro }
    val contagens = contactos.groupingBy { it.estado }.eachCount()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Respostas dos lares") }) },
    ) { padding ->
        if (contactos.isEmpty()) {
            EstadoVazio(
                Icons.AutoMirrored.Filled.List,
                "Ainda não contactou nenhum lar",
                "Depois de enviar a pré-inscrição, os lares aparecem aqui para registar as respostas, visitas e listas de espera.",
                Modifier.padding(padding),
            ) { Button(onClick = irParaLares) { Text("Escolher lares") } }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todos (${contactos.size})") })
                EstadoContacto.entries.filter { (contagens[it] ?: 0) > 0 }.forEach { e ->
                    FilterChip(
                        selected = filtro == e,
                        onClick = { filtro = if (filtro == e) null else e },
                        label = { Text("${e.rotulo} (${contagens[e]})") },
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visiveis, key = { it.larId }) { c ->
                    val lar = lares[c.larId]
                    CartaoContacto(
                        contacto = c,
                        lar = lar,
                        aoMudarEstado = { vm.atualizarContacto(c.copy(estado = it)) },
                        aoEditarNotas = { aEditar = c },
                        aoLigar = { lar?.let { Envio.ligar(context, it.telefone) } },
                        aoEmail = {
                            lar?.let {
                                Envio.abrirEmail(context, listOf(it.email), emptyList(), "Re: " + GeradorCarta.assunto(estado.perfil), "", emptyList())
                            }
                        },
                    )
                }
            }
        }
    }

    aEditar?.let { c ->
        var notas by remember(c.larId) { mutableStateOf(c.notas) }
        var confirmarApagar by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { aEditar = null },
            title = { Text(lares[c.larId]?.nome ?: "Lar") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = notas,
                        onValueChange = { notas = it },
                        label = { Text("Notas") },
                        placeholder = { Text("Ex.: Falei com a Dra. Ana, mensalidade 1200 €, visita dia 12") },
                        minLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { confirmarApagar = true }) {
                        Text("Retirar do acompanhamento", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.atualizarContacto(c.copy(notas = notas)); aEditar = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { aEditar = null }) { Text("Cancelar") } },
        )
        if (confirmarApagar) {
            AlertDialog(
                onDismissRequest = { confirmarApagar = false },
                title = { Text("Retirar do acompanhamento?") },
                text = { Text("O lar continua na lista; apenas o registo do contacto e as notas são apagados.") },
                confirmButton = {
                    TextButton(onClick = { vm.apagarContacto(c.larId); confirmarApagar = false; aEditar = null }) { Text("Retirar") }
                },
                dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
private fun corEstado(e: EstadoContacto): Color = when (e) {
    EstadoContacto.VAGA -> MaterialTheme.colorScheme.primary
    EstadoContacto.RESPONDEU, EstadoContacto.VISITA, EstadoContacto.LISTA_ESPERA -> MaterialTheme.colorScheme.tertiary
    EstadoContacto.RECUSADO -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun CartaoContacto(
    contacto: Contacto,
    lar: Lar?,
    aoMudarEstado: (EstadoContacto) -> Unit,
    aoEditarNotas: () -> Unit,
    aoLigar: () -> Unit,
    aoEmail: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val dias = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - contacto.dataEnvio)
    Card(Modifier.fillMaxWidth().clickable(onClick = aoEditarNotas)) {
        Column(Modifier.padding(12.dp)) {
            Text(lar?.nome ?: "Lar removido da lista", style = MaterialTheme.typography.titleSmall)
            lar?.let { localizacao(it) }?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Enviado a ${formatarData(contacto.dataEnvio)}" + when {
                    dias == 0L -> " (hoje)"
                    dias == 1L -> " (há 1 dia)"
                    else -> " (há $dias dias)"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (contacto.estado == EstadoContacto.ENVIADO && dias >= 7) {
                Text(
                    "Sem resposta há mais de uma semana — vale a pena telefonar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (contacto.notas.isNotBlank()) {
                Text(contacto.notas, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AssistChip(
                        onClick = { menu = true },
                        label = { Text(contacto.estado.rotulo) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = corEstado(contacto.estado)),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        EstadoContacto.entries.forEach { e ->
                            DropdownMenuItem(text = { Text(e.rotulo) }, onClick = { aoMudarEstado(e); menu = false })
                        }
                    }
                }
                if (lar?.telefone?.isNotBlank() == true) IconButton(onClick = aoLigar) { Icon(Icons.Filled.Phone, "Ligar") }
                if (lar?.temEmail == true) IconButton(onClick = aoEmail) { Icon(Icons.Filled.Email, "Enviar email") }
            }
        }
    }
}
