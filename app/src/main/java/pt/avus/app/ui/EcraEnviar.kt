package pt.avus.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.avus.app.carta.Envio
import pt.avus.app.carta.GeradorCarta
import pt.avus.app.data.CamposPerfil
import pt.avus.app.data.Lar
import kotlinx.coroutines.launch

private enum class ModoEnvio { UNICO, INDIVIDUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraEnviar(
    estado: EstadoApp,
    vm: AvusViewModel,
    irParaLares: () -> Unit,
    irParaUtente: () -> Unit,
    irParaRespostas: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modo by rememberSaveable { mutableStateOf(ModoEnvio.UNICO) }
    var incluirPdf by rememberSaveable { mutableStateOf(true) }
    var indiceIndividual by rememberSaveable { mutableIntStateOf(0) }
    var aPreparar by remember { mutableStateOf(false) }
    var confirmarReposicao by remember { mutableStateOf(false) }
    var concluido by remember { mutableStateOf<Int?>(null) }

    val selecionados = estado.laresSelecionados
    val comEmail = selecionados.filter { it.temEmail }
    val semEmail = selecionados.filterNot { it.temEmail }
    val emFalta = GeradorCarta.camposEmFalta(estado.perfil)
    val assunto = GeradorCarta.assunto(estado.perfil)
    val emailResponsavel = estado.perfil[CamposPerfil.RESP_EMAIL].orEmpty().trim()
    val tamanho = vm.tamanhoAnexosBytes()
    val podeEnviar = comEmail.isNotEmpty() && emFalta.isEmpty() && !aPreparar

    fun enviar(destino: List<Lar>, texto: String, aoAbrir: () -> Unit) {
        aPreparar = true
        scope.launch {
            try {
                val anexos = vm.prepararAnexos(texto, incluirPdf)
                val emails = destino.map { it.email }
                val ok = if (destino.size == 1) {
                    Envio.abrirEmail(context, emails, listOfNotNull(emailResponsavel.ifBlank { null }), assunto, texto, anexos)
                } else {
                    // Lares em Bcc: nenhum lar vê os endereços dos outros. O próprio familiar fica em "Para" (cópia).
                    Envio.abrirEmail(context, listOfNotNull(emailResponsavel.ifBlank { null }), emails, assunto, texto, anexos)
                }
                if (ok) {
                    vm.registarEnvio(destino.map { it.id })
                    aoAbrir()
                } else vm.mostrar("Não foi encontrada nenhuma app de email. Instale o Gmail ou outra app de email.")
            } catch (e: Exception) {
                vm.mostrar("Erro ao preparar o email: ${e.message}")
            } finally {
                aPreparar = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Enviar pré-inscrição") }) },
    ) { padding ->
        if (selecionados.isEmpty()) {
            EstadoVazio(
                Icons.Filled.Email,
                "Nenhum lar selecionado",
                "Escolha na lista os lares a que quer enviar a pré-inscrição.",
                Modifier.padding(padding),
            ) { Button(onClick = irParaLares) { Text("Escolher lares") } }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (emFalta.isNotEmpty()) {
                Aviso("Falta preencher na ficha do utente: ${emFalta.joinToString(", ")}.", erro = true)
                OutlinedButton(onClick = irParaUtente) { Text("Preencher ficha") }
            }

            // Destinatários
            CartaoSecao("Destinatários") {
                Text("${comEmail.size} de ${selecionados.size} lares selecionados têm email.")
                if (semEmail.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Sem email — contacte por telefone ou acrescente o email na lista:",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    semEmail.forEach { lar ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lar.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(lar.telefone.ifBlank { "sem telefone" }, style = MaterialTheme.typography.bodySmall)
                            }
                            if (lar.telefone.isNotBlank()) {
                                IconButton(onClick = { Envio.ligar(context, lar.telefone) }) { Icon(Icons.Filled.Phone, "Ligar") }
                            }
                        }
                    }
                }
                TextButton(onClick = irParaLares) { Text("Alterar seleção") }
            }

            // Modo de envio
            CartaoSecao("Como enviar") {
                OpcaoRadio(
                    "Um único email para todos",
                    "Mais rápido. Os lares seguem em Bcc (não veem os outros destinatários)" +
                        if (emailResponsavel.isNotBlank()) " e recebe uma cópia em $emailResponsavel." else ".",
                    modo == ModoEnvio.UNICO,
                ) { modo = ModoEnvio.UNICO }
                OpcaoRadio(
                    "Um email para cada lar",
                    "A carta é dirigida ao nome de cada lar. Mais pessoal, mas tem de carregar em \"Enviar\" uma vez por lar.",
                    modo == ModoEnvio.INDIVIDUAL,
                ) { modo = ModoEnvio.INDIVIDUAL; indiceIndividual = 0 }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anexar a carta em PDF")
                        Text("Útil para os lares imprimirem e arquivarem.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = incluirPdf, onCheckedChange = { incluirPdf = it })
                }
                Text(
                    "Documentos em anexo: ${estado.documentosAnexados.size}" +
                        if (tamanho > 0) " (${formatarTamanho(tamanho)})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (tamanho > LIMITE_ANEXOS) {
                    Aviso("Os anexos ultrapassam 20 MB e o email pode ser recusado. Retire ou comprima alguns documentos.", erro = true)
                }
            }

            // Carta
            CartaoSecao("Carta de pré-inscrição") {
                Text("Assunto: $assunto", style = MaterialTheme.typography.labelLarge)
                if (estado.cartaPersonalizada != null) {
                    Aviso("Editou o texto à mão: alterações na ficha do utente já não atualizam a carta automaticamente.")
                }
                OutlinedTextField(
                    value = estado.carta,
                    onValueChange = { vm.editarCarta(it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (estado.cartaPersonalizada != null) {
                        TextButton(onClick = { confirmarReposicao = true }) { Text("Repor texto automático") }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                val pdf = Envio.gerarPdf(context, assunto, estado.carta, Envio.nomePdf(estado.perfil[CamposPerfil.NOME].orEmpty()))
                                val uri = Envio.uriPartilhavel(context, pdf)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }.onFailure { vm.mostrar("Não há nenhuma app para abrir PDF.") }
                        }
                    }) { Text("Pré-visualizar PDF") }
                }
            }

            // Ação
            when (modo) {
                ModoEnvio.UNICO -> Button(
                    onClick = { enviar(comEmail, estado.carta) { concluido = comEmail.size } },
                    enabled = podeEnviar,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (aPreparar) "A preparar…" else "Abrir email para ${comEmail.size} lares")
                }
                ModoEnvio.INDIVIDUAL -> {
                    val atual = comEmail.getOrNull(indiceIndividual)
                    if (atual == null) {
                        if (comEmail.isNotEmpty()) {
                            Aviso("Emails preparados para todos os ${comEmail.size} lares.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { indiceIndividual = 0 }) { Text("Recomeçar") }
                                Button(onClick = irParaRespostas) { Text("Ver respostas") }
                            }
                        }
                    } else {
                        Text(
                            "Lar ${indiceIndividual + 1} de ${comEmail.size}: ${atual.nome}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { indiceIndividual++ }) { Text("Saltar") }
                            Button(
                                onClick = {
                                    enviar(listOf(atual), GeradorCarta.personalizar(estado.carta, atual.nome)) { indiceIndividual++ }
                                },
                                enabled = podeEnviar,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (aPreparar) "A preparar…" else "Abrir email", maxLines = 1)
                            }
                        }
                    }
                }
            }
            Text(
                "A app abre o email já preenchido na sua app de email (Gmail, Outlook…). Reveja e carregue em \"Enviar\". " +
                    "Os lares ficam registados em \"Respostas\" para acompanhar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmarReposicao) {
        AlertDialog(
            onDismissRequest = { confirmarReposicao = false },
            title = { Text("Repor texto automático?") },
            text = { Text("As alterações que fez à mão na carta serão perdidas.") },
            confirmButton = { TextButton(onClick = { vm.reporCarta(); confirmarReposicao = false }) { Text("Repor") } },
            dismissButton = { TextButton(onClick = { confirmarReposicao = false }) { Text("Cancelar") } },
        )
    }

    concluido?.let { n ->
        AlertDialog(
            onDismissRequest = { concluido = null },
            title = { Text("Email preparado") },
            text = {
                Text(
                    "Depois de enviar, os $n lares ficam em \"Respostas\" com o estado \"Enviado\". " +
                        "Quando algum responder, atualize lá o estado. Quer limpar a seleção atual?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.limparSelecao(); concluido = null; irParaRespostas() }) { Text("Limpar e ver respostas") }
            },
            dismissButton = { TextButton(onClick = { concluido = null }) { Text("Manter seleção") } },
        )
    }
}

@Composable
private fun OpcaoRadio(titulo: String, descricao: String, selecionado: Boolean, aoEscolher: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selecionado, onClick = aoEscolher, role = Role.RadioButton),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selecionado, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
