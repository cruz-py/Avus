package pt.avus.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.avus.app.data.CareHome
import pt.avus.app.data.ProfileFields
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.letter.Sender

private enum class SendMode { SINGLE, INDIVIDUAL }

/** Most email services treat one message to many recipients as spam; bigger sends go out in groups. */
private const val MAX_RECIPIENTS_PER_EMAIL = 30

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
    var batchIndex by rememberSaveable { mutableIntStateOf(0) }
    var isPreparing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var completedCount by remember { mutableStateOf<Int?>(null) }

    val selectedHomes = state.selectedHomes
    val withEmail = selectedHomes.filter { it.hasEmail }
    val withoutEmail = selectedHomes.filterNot { it.hasEmail }
    val missing = LetterGenerator.missingFields(state.profile)
    val subject = LetterGenerator.subject(state.profile)
    val relativeEmail = state.profile[ProfileFields.RELATIVE_EMAIL].orEmpty().trim()
    val attachmentsSize = state.attachmentsSizeBytes
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Enviar pré-inscrição") },
                subtitle = if (selectedHomes.isEmpty()) null else ({
                    Text("${withEmail.size} de ${selectedHomes.size} lares selecionados têm email.")
                }),
                colors = screenTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (selectedHomes.isEmpty()) {
            EmptyState(
                Icons.Filled.Email,
                "Nenhum lar selecionado",
                "Escolha na lista os lares a que quer enviar a pré-inscrição.",
                Modifier.padding(padding),
            ) { Button(onClick = goToHomes, shapes = ButtonDefaults.shapes()) { Text("Escolher lares") } }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (missing.isNotEmpty()) {
                Notice("Falta preencher na ficha do utente: ${missing.joinToString(", ")}.", isError = true)
                OutlinedButton(onClick = goToResident, shapes = ButtonDefaults.shapes()) { Text("Preencher ficha") }
            }

            // Recipients without email (the subtitle already says how many have one)
            if (withoutEmail.isNotEmpty()) {
                ListHeader("Sem email — contacte por telefone ou acrescente o email na lista:")
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    withoutEmail.forEachIndexed { i, home ->
                        SegmentedListItem(
                            colors = segmentedColors(),
                            shapes = segmentedShapes(i, withoutEmail.size),
                            leadingContent = { ShapeIcon(Icons.Filled.Phone) },
                            supportingContent = { Text(home.phone.ifBlank { "sem telefone" }) },
                            trailingContent = if (home.phone.isNotBlank()) ({
                                FilledTonalIconButton(onClick = { Sender.dial(context, home.phone) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Filled.Phone, "Ligar")
                                }
                            }) else null,
                        ) { Text(home.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            TextButton(onClick = goToHomes, shapes = ButtonDefaults.shapes()) { Text("Alterar seleção") }

            // Sending mode
            SectionCard("Como enviar") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    ToggleButton(
                        checked = mode == SendMode.SINGLE,
                        onCheckedChange = { mode = SendMode.SINGLE },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    ) { Text("Um único email para todos", textAlign = TextAlign.Center) }
                    ToggleButton(
                        checked = mode == SendMode.INDIVIDUAL,
                        onCheckedChange = { mode = SendMode.INDIVIDUAL; currentIndex = 0 },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    ) { Text("Um email para cada lar", textAlign = TextAlign.Center) }
                }
                AnimatedContent(mode, label = "modeDescription") { m ->
                    Text(
                        if (m == SendMode.SINGLE) {
                            "Mais rápido. Os lares seguem em Bcc (não veem os outros destinatários)" +
                                if (relativeEmail.isNotBlank()) " e recebe uma cópia em $relativeEmail." else "."
                        } else {
                            "A carta é dirigida ao nome de cada lar. Mais pessoal, mas tem de carregar em \"Enviar\" uma vez por lar."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anexar a carta em PDF", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Útil para os lares imprimirem e arquivarem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                Text("Assunto: $subject", style = MaterialTheme.typography.labelLargeEmphasized)
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
                    shape = MaterialTheme.shapes.large,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.customLetter != null) {
                        TextButton(onClick = { confirmReset = true }, shapes = ButtonDefaults.shapes()) { Text("Repor texto automático") }
                    }
                    TextButton(
                        onClick = {
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
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Pré-visualizar PDF") }
                }
            }

            // Action
            when (mode) {
                SendMode.SINGLE -> {
                    val batches = withEmail.chunked(MAX_RECIPIENTS_PER_EMAIL)
                    if (batches.size <= 1) {
                        SendButton(
                            text = if (isPreparing) "A preparar…" else "Abrir email para ${withEmail.size} lares",
                            isPreparing = isPreparing,
                            enabled = canSend,
                            modifier = Modifier.fillMaxWidth(),
                        ) { send(withEmail, state.letter) { completedCount = withEmail.size } }
                    } else {
                        Notice(
                            "São ${withEmail.size} lares: para os emails não serem tratados como spam, são enviados em " +
                                "${batches.size} grupos de até $MAX_RECIPIENTS_PER_EMAIL. Carregue em \"Enviar\" uma vez por grupo."
                        )
                        val batch = batches.getOrNull(batchIndex)
                        if (batch == null) {
                            Notice("Emails preparados para todos os ${withEmail.size} lares.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { batchIndex = 0 }, shapes = ButtonDefaults.shapes()) { Text("Recomeçar") }
                                Button(onClick = goToReplies, shapes = ButtonDefaults.shapes()) { Text("Ver respostas") }
                            }
                        } else {
                            SendButton(
                                text = if (isPreparing) "A preparar…" else "Abrir email: grupo ${batchIndex + 1} de ${batches.size} (${batch.size} lares)",
                                isPreparing = isPreparing,
                                enabled = canSend,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                send(batch, state.letter) {
                                    batchIndex++
                                    if (batchIndex >= batches.size) completedCount = withEmail.size
                                }
                            }
                        }
                    }
                }
                SendMode.INDIVIDUAL -> {
                    val current = withEmail.getOrNull(currentIndex)
                    if (current == null) {
                        if (withEmail.isNotEmpty()) {
                            Notice("Emails preparados para todos os ${withEmail.size} lares.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { currentIndex = 0 }, shapes = ButtonDefaults.shapes()) { Text("Recomeçar") }
                                Button(onClick = goToReplies, shapes = ButtonDefaults.shapes()) { Text("Ver respostas") }
                            }
                        }
                    } else {
                        Text(
                            "Lar ${currentIndex + 1} de ${withEmail.size}: ${current.name}",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { currentIndex++ },
                                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            ) { Text("Saltar", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
                            SendButton(
                                text = if (isPreparing) "A preparar…" else "Abrir email",
                                isPreparing = isPreparing,
                                enabled = canSend,
                                modifier = Modifier.weight(1f),
                            ) { send(listOf(current), LetterGenerator.addRecipientHeader(state.letter, current.name)) { currentIndex++ } }
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

/** Medium-size primary button whose shape morphs on press; shows the loading indicator while preparing. */
@Composable
private fun SendButton(
    text: String,
    isPreparing: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val height: Dp = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        if (isPreparing) LoadingIndicator(Modifier.padding(end = 4.dp).heightIn(max = 32.dp).width(32.dp))
        else Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.padding(end = 4.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = ButtonDefaults.textStyleFor(height), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
