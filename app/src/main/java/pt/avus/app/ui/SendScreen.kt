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
import pt.avus.app.letter.Sender
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.data.ProfileFields
import pt.avus.app.data.CareHome
import kotlinx.coroutines.launch

private enum class SendMode { SINGLE, INDIVIDUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    state: AppState,
    vm: AvusViewModel,
    goToHomes: () -> Unit,
    goToResident: () -> Unit,
    goToReplies: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(SendMode.SINGLE) }
    var includePdf by rememberSaveable { mutableStateOf(true) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var isPreparing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var completedCount by remember { mutableStateOf<Int?>(null) }

    val selectedHomes = state.selectedHomes
    val withEmail = selectedHomes.filter { it.hasEmail }
    val withoutEmail = selectedHomes.filterNot { it.hasEmail }
    val missing = LetterGenerator.missingFields(state.profile)
    val subject = LetterGenerator.subject(state.profile)
    val relativeEmail = state.profile[ProfileFields.RELATIVE_EMAIL].orEmpty().trim()
    val attachmentsSize = vm.attachmentsSizeBytes()
    val canSend = withEmail.isNotEmpty() && missing.isEmpty() && !isPreparing

    fun send(target: List<CareHome>, text: String, onOpen: () -> Unit) {
        isPreparing = true
        scope.launch {
            try {
                val attachments = vm.prepareAttachments(text, includePdf)
                val emails = target.map { it.email }
                val ok = if (target.size == 1) {
                    Sender.openEmail(context, emails, listOfNotNull(relativeEmail.ifBlank { null }), subject, text, attachments)
                } else {
                    // Homes in Bcc so none sees the others' addresses. The relative goes in "To" (as a copy).
                    Sender.openEmail(context, listOfNotNull(relativeEmail.ifBlank { null }), emails, subject, text, attachments)
                }
                if (ok) {
                    vm.recordSent(target.map { it.id })
                    onOpen()
                } else vm.showMessage("Não foi encontrada nenhuma app de email. Instale o Gmail ou outra app de email.")
            } catch (e: Exception) {
                vm.showMessage("Erro ao preparar o email: ${e.message}")
            } finally {
                isPreparing = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Enviar pré-inscrição") }) },
    ) { padding ->
        if (selectedHomes.isEmpty()) {
            EmptyState(
                Icons.Filled.Email,
                "Nenhum lar selecionado",
                "Escolha na lista os lares a que quer enviar a pré-inscrição.",
                Modifier.padding(padding),
            ) { Button(onClick = goToHomes) { Text("Escolher lares") } }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (missing.isNotEmpty()) {
                Notice("Falta preencher na ficha do utente: ${missing.joinToString(", ")}.", isError = true)
                OutlinedButton(onClick = goToResident) { Text("Preencher ficha") }
            }

            // Recipients
            SectionCard("Destinatários") {
                Text("${withEmail.size} de ${selectedHomes.size} lares selecionados têm email.")
                if (withoutEmail.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Sem email — contacte por telefone ou acrescente o email na lista:",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    withoutEmail.forEach { home ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(home.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(home.phone.ifBlank { "sem telefone" }, style = MaterialTheme.typography.bodySmall)
                            }
                            if (home.phone.isNotBlank()) {
                                IconButton(onClick = { Sender.dial(context, home.phone) }) { Icon(Icons.Filled.Phone, "Ligar") }
                            }
                        }
                    }
                }
                TextButton(onClick = goToHomes) { Text("Alterar seleção") }
            }

            // Sending mode
            SectionCard("Como enviar") {
                RadioOption(
                    "Um único email para todos",
                    "Mais rápido. Os lares seguem em Bcc (não veem os outros destinatários)" +
                        if (relativeEmail.isNotBlank()) " e recebe uma cópia em $relativeEmail." else ".",
                    mode == SendMode.SINGLE,
                ) { mode = SendMode.SINGLE }
                RadioOption(
                    "Um email para cada lar",
                    "A carta é dirigida ao nome de cada lar. Mais pessoal, mas tem de carregar em \"Enviar\" uma vez por lar.",
                    mode == SendMode.INDIVIDUAL,
                ) { mode = SendMode.INDIVIDUAL; currentIndex = 0 }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anexar a carta em PDF")
                        Text("Útil para os lares imprimirem e arquivarem.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = includePdf, onCheckedChange = { includePdf = it })
                }
                Text(
                    "Documentos em anexo: ${state.attachedDocuments.size}" +
                        if (attachmentsSize > 0) " (${formatSize(attachmentsSize)})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (attachmentsSize > ATTACHMENTS_LIMIT_BYTES) {
                    Notice("Os anexos ultrapassam 20 MB e o email pode ser recusado. Retire ou comprima alguns documentos.", isError = true)
                }
            }

            // Letter
            SectionCard("Carta de pré-inscrição") {
                Text("Assunto: $subject", style = MaterialTheme.typography.labelLarge)
                if (state.customLetter != null) {
                    Notice("Editou o texto à mão: alterações na ficha do utente já não atualizam a carta automaticamente.")
                }
                OutlinedTextField(
                    value = state.letter,
                    onValueChange = { vm.editLetter(it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.customLetter != null) {
                        TextButton(onClick = { confirmReset = true }) { Text("Repor texto automático") }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                val pdf = Sender.generatePdf(context, subject, state.letter, Sender.pdfFileName(state.profile[ProfileFields.NAME].orEmpty()))
                                val uri = Sender.shareableUri(context, pdf)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }.onFailure { vm.showMessage("Não há nenhuma app para abrir PDF.") }
                        }
                    }) { Text("Pré-visualizar PDF") }
                }
            }

            // Action
            when (mode) {
                SendMode.SINGLE -> Button(
                    onClick = { send(withEmail, state.letter) { completedCount = withEmail.size } },
                    enabled = canSend,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPreparing) "A preparar…" else "Abrir email para ${withEmail.size} lares")
                }
                SendMode.INDIVIDUAL -> {
                    val current = withEmail.getOrNull(currentIndex)
                    if (current == null) {
                        if (withEmail.isNotEmpty()) {
                            Notice("Emails preparados para todos os ${withEmail.size} lares.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { currentIndex = 0 }) { Text("Recomeçar") }
                                Button(onClick = goToReplies) { Text("Ver respostas") }
                            }
                        }
                    } else {
                        Text(
                            "Lar ${currentIndex + 1} de ${withEmail.size}: ${current.name}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { currentIndex++ }) { Text("Saltar") }
                            Button(
                                onClick = {
                                    send(listOf(current), LetterGenerator.addRecipientHeader(state.letter, current.name)) { currentIndex++ }
                                },
                                enabled = canSend,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isPreparing) "A preparar…" else "Abrir email", maxLines = 1)
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

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Repor texto automático?") },
            text = { Text("As alterações que fez à mão na carta serão perdidas.") },
            confirmButton = { TextButton(onClick = { vm.resetLetter(); confirmReset = false }) { Text("Repor") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }

    completedCount?.let { n ->
        AlertDialog(
            onDismissRequest = { completedCount = null },
            title = { Text("Email preparado") },
            text = {
                Text(
                    "Depois de enviar, os $n lares ficam em \"Respostas\" com o estado \"Enviado\". " +
                        "Quando algum responder, atualize lá o estado. Quer limpar a seleção atual?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearSelection(); completedCount = null; goToReplies() }) { Text("Limpar e ver respostas") }
            },
            dismissButton = { TextButton(onClick = { completedCount = null }) { Text("Manter seleção") } },
        )
    }
}

@Composable
private fun RadioOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
