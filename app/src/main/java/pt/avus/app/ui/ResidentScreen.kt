package pt.avus.app.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.avus.app.letter.Sender
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.data.ProfileField
import pt.avus.app.data.ProfileFields
import pt.avus.app.data.Document
import pt.avus.app.data.FieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentScreen(state: AppState, vm: AvusViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(title = { Text("Utente") })
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Ficha") })
                    val attachedCount = state.attachedDocuments.size
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Documentos ($attachedCount/${state.documents.size})") })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab == 0) ResidentForm(state, vm) else DocumentList(state, vm)
        }
    }
}

@Composable
private fun ResidentForm(state: AppState, vm: AvusViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Notice(
                "Estes dados servem para preencher a carta de pré-inscrição. Ficam guardados apenas neste " +
                    "telemóvel e só são enviados nos emails que decidir enviar."
            )
        }
        items(ProfileFields.sections, key = { it.title }) { section ->
            SectionCard(section.title) {
                section.fields.forEach { field ->
                    ProfileFieldInput(field, state.profile[field.key].orEmpty()) { vm.updateField(field.key, it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFieldInput(field: ProfileField, value: String, onChange: (String) -> Unit) {
    val required = field.key in ProfileFields.requiredKeys
    val label = field.label + if (required) " *" else ""
    when (field.type) {
        FieldType.OPTIONS -> Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { onChange(if (value == option) "" else option) },
                        label = { Text(option) },
                    )
                }
            }
        }
        else -> {
            val keyboard = when (field.type) {
                FieldType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                FieldType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                FieldType.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                FieldType.DATE -> KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            }
            val hint = when (field.type) {
                FieldType.DATE -> {
                    val age = LetterGenerator.age(value)
                    when {
                        value.isBlank() -> field.hint
                        age != null -> "$age anos"
                        else -> "Use o formato dd/mm/aaaa"
                    }
                }
                else -> field.hint
            }
            val isError = field.type == FieldType.DATE && value.isNotBlank() && value.length >= 8 && LetterGenerator.age(value) == null
            OutlinedTextField(
                value = value,
                onValueChange = { newValue -> onChange(if (field.type == FieldType.DATE) formatTypedDate(value, newValue) else newValue) },
                label = { Text(label) },
                supportingText = if (hint.isNotBlank()) ({ Text(hint) }) else null,
                isError = isError,
                singleLine = field.type != FieldType.MULTILINE,
                minLines = if (field.type == FieldType.MULTILINE) 2 else 1,
                keyboardOptions = keyboard,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Inserts the slashes automatically: "01021940" -> "01/02/1940". */
private fun formatTypedDate(previous: String, newValue: String): String {
    if (newValue.length < previous.length) return newValue // deleting: do not interfere
    val digits = newValue.filter { it.isDigit() }.take(8)
    if (newValue.any { it == '/' } && newValue.length <= 10) return newValue.take(10)
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

@Composable
private fun DocumentList(state: AppState, vm: AvusViewModel) {
    val context = LocalContext.current
    var pendingDocId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewDocument by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingDocId
        if (uri != null && id != null) vm.attachDocument(id, uri)
        pendingDocId = null
    }
    val total = vm.attachmentsSizeBytes()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Notice(
                "Documentos habitualmente pedidos pelos lares. Digitalize-os (PDF ou fotografia) e anexe os que já tiver: " +
                    "seguem em anexo no email. Os que faltarem podem ser entregues mais tarde."
            )
        }
        if (total > 0) {
            item {
                Text(
                    "Total dos anexos: ${formatSize(total)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (total > ATTACHMENTS_LIMIT_BYTES) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.documents, key = { it.id }) { doc ->
            DocumentCard(
                doc = doc,
                onAttach = {
                    pendingDocId = doc.id
                    pickFile.launch(arrayOf("application/pdf", "image/*"))
                },
                onView = {
                    vm.documentFile(doc)?.takeIf { it.exists() }?.let { f ->
                        val uri = Sender.shareableUri(context, f)
                        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }.onFailure { vm.showMessage("Não há nenhuma app para abrir este ficheiro.") }
                    }
                },
                onRemove = { vm.removeAttachment(doc.id) },
                onDelete = { vm.deleteDocument(doc.id) },
            )
        }
        item {
            OutlinedButton(onClick = { showNewDocument = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Text("Adicionar outro documento", Modifier.padding(start = 8.dp))
            }
        }
    }

    if (showNewDocument) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDocument = false },
            title = { Text("Novo documento") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do documento") },
                    placeholder = { Text("Ex.: Declaração de compromisso") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.addDocument(name); showNewDocument = false }) {
                    Text("Adicionar")
                }
            },
            dismissButton = { TextButton(onClick = { showNewDocument = false }) { Text("Cancelar") } },
        )
    }
}

/** Conservative limit: most mail servers reject messages above 20–25 MB. */
const val ATTACHMENTS_LIMIT_BYTES = 20_000_000L

@Composable
private fun DocumentCard(
    doc: Document,
    onAttach: () -> Unit,
    onView: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = if (doc.isAttached) "Anexado" else "Por anexar",
                    tint = if (doc.isAttached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(doc.name, style = MaterialTheme.typography.titleSmall)
                    if (doc.description.isNotBlank()) {
                        Text(doc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    doc.originalName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Retirar da lista") }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (doc.isAttached) {
                    TextButton(onClick = onView) { Text("Ver") }
                    TextButton(onClick = onRemove) { Text("Remover ficheiro") }
                    TextButton(onClick = onAttach) { Text("Substituir") }
                } else {
                    TextButton(onClick = onAttach) { Text("Anexar ficheiro") }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Retirar \"${doc.name}\" da lista?") },
            text = { Text("Use isto para documentos que não se aplicam ao seu caso.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Retirar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
