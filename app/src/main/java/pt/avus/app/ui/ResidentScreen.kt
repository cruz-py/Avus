package pt.avus.app.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.avus.app.data.Document
import pt.avus.app.data.FieldType
import pt.avus.app.data.ProfileField
import pt.avus.app.data.ProfileFields
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.letter.Sender

@Composable
fun ResidentScreen(state: AppState, vm: AvusViewModel, onOpenSettings: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Utente") },
                subtitle = state.profile[ProfileFields.NAME]?.takeIf { it.isNotBlank() }?.let { name -> { Text(name) } },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Definições") } },
                colors = screenTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Connected button group instead of tabs (Material 3 Expressive).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                val labels = listOf("Ficha", "Contacto", "Documentos (${state.attachedDocuments.size}/${state.visibleDocuments.size})")
                labels.forEachIndexed { i, label ->
                    ToggleButton(
                        checked = selectedTab == i,
                        onCheckedChange = { selectedTab = i },
                        modifier = Modifier.weight(1f),
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        // Unchecked half on the bright surface, so it stands out from the tonal page.
                        colors = ToggleButtonDefaults.colors(containerColor = contentSurface),
                    ) {
                        // One line: the selected label scrolls if it does not fit ("Documentos (0/11)").
                        Text(
                            label,
                            maxLines = 1,
                            overflow = if (selectedTab == i) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (selectedTab == i) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                        )
                    }
                }
            }
            when (selectedTab) {
                0 -> ResidentForm(state, vm)
                1 -> ContactForm(state, vm)
                else -> DocumentList(state, vm)
            }
        }
    }
}

@Composable
private fun ResidentForm(state: AppState, vm: AvusViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Notice(
                "Estes dados servem para preencher a carta de pré-inscrição. Ficam guardados apenas neste " +
                    "telemóvel e só são enviados nos emails que decidir enviar."
            )
        }
        items(ProfileFields.residentSections, key = { it.title }) { section ->
            SectionCard(section.title) {
                section.fields.forEach { field ->
                    ProfileFieldInput(field, ProfileFields.labelFor(field, state.profile), state.profile[field.key].orEmpty()) { vm.updateField(field.key, it) }
                }
            }
        }
    }
}

/** The person who sends the letters: homes reply to them. */
@Composable
private fun ContactForm(state: AppState, vm: AvusViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Notice(
                "Os lares vão responder a esta pessoa. Estes contactos aparecem no fim da carta e, no envio para " +
                    "vários lares, esta pessoa recebe uma cópia do email."
            )
        }
        item {
            val section = ProfileFields.contactSection
            SectionCard(section.title) {
                section.fields.forEach { field ->
                    ProfileFieldInput(field, ProfileFields.labelFor(field, state.profile), state.profile[field.key].orEmpty()) { vm.updateField(field.key, it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFieldInput(field: ProfileField, fieldLabel: String, value: String, onChange: (String) -> Unit) {
    val required = field.key in ProfileFields.requiredKeys
    val label = fieldLabel + if (required) " *" else ""
    when (field.type) {
        FieldType.OPTIONS -> Column {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    CheckFilterChip(value == option, { onChange(if (value == option) "" else option) }, option)
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
                shape = MaterialTheme.shapes.large,
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
    val total = state.attachmentsSizeBytes

    fun attach(doc: Document) {
        pendingDocId = doc.id
        pickFile.launch(arrayOf("application/pdf", "image/*"))
    }

    fun view(doc: Document) {
        vm.documentFile(doc)?.takeIf { it.exists() }?.let { f ->
            val uri = Sender.shareableUri(context, f)
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }.onFailure { vm.showMessage("Não há nenhuma app para abrir este ficheiro.") }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item {
            Notice(
                "Documentos habitualmente pedidos pelos lares. Digitalize-os (PDF ou fotografia) e anexe os que já tiver: " +
                    "seguem em anexo no email. Os que faltarem podem ser entregues mais tarde.",
                Modifier.padding(bottom = 8.dp),
            )
        }
        if (total > 0) {
            item {
                Text(
                    "Total dos anexos: ${formatSize(total)}",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = if (total > ATTACHMENTS_LIMIT_BYTES) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            }
        }
        itemsIndexed(state.visibleDocuments, key = { _, doc -> doc.id }) { index, doc ->
            DocumentRow(
                doc = doc,
                index = index,
                count = state.visibleDocuments.size,
                onAttach = { attach(doc) },
                onView = { view(doc) },
                onRemove = { vm.removeAttachment(doc.id) },
                onDelete = { vm.deleteDocument(doc.id) },
                modifier = Modifier.animateItem(),
            )
        }
        item {
            OutlinedButton(
                onClick = { showNewDocument = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
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
                    shape = MaterialTheme.shapes.large,
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
private fun DocumentRow(
    doc: Document,
    index: Int,
    count: Int,
    onAttach: () -> Unit,
    onView: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    SegmentedListItem(
        colors = segmentedColors(),
        onClick = if (doc.isAttached) onView else onAttach,
        shapes = segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = {
            if (doc.isAttached) {
                ShapeIcon(
                    Icons.Filled.Check,
                    shape = MaterialShapes.Cookie4Sided,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                ShapeIcon(
                    Icons.Outlined.CheckCircle,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.outline,
                )
            }
        },
        supportingContent = {
            Column {
                if (doc.description.isNotBlank()) Text(doc.description)
                doc.originalName?.let {
                    Text(it, style = MaterialTheme.typography.labelMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                }
                if (!doc.isAttached) {
                    Text("Anexar ficheiro", style = MaterialTheme.typography.labelMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, if (doc.isAttached) "Anexado" else "Por anexar")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                    if (doc.isAttached) {
                        DropdownMenuItem(text = { Text("Ver") }, onClick = { menu = false; onView() })
                        DropdownMenuItem(text = { Text("Substituir") }, onClick = { menu = false; onAttach() })
                        DropdownMenuItem(text = { Text("Remover ficheiro") }, onClick = { menu = false; onRemove() })
                    } else {
                        DropdownMenuItem(text = { Text("Anexar ficheiro") }, onClick = { menu = false; onAttach() })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Retirar da lista") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        },
    ) {
        Text(doc.name, style = MaterialTheme.typography.titleSmallEmphasized)
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
