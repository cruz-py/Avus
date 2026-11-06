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
import pt.avus.app.letter.Sender
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.data.Contact
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.CareHome
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepliesScreen(state: AppState, vm: AvusViewModel, goToHomes: () -> Unit) {
    val context = LocalContext.current
    var statusFilter by rememberSaveable { mutableStateOf<ContactStatus?>(null) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    val homes = remember(state.homes) { state.homes.associateBy { it.id } }
    val contacts = state.contacts.sortedByDescending { it.updatedAt }
    val visibleContacts = contacts.filter { statusFilter == null || it.status == statusFilter }
    val countsByStatus = contacts.groupingBy { it.status }.eachCount()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Respostas dos lares") }) },
    ) { padding ->
        if (contacts.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Filled.List,
                "Ainda não contactou nenhum lar",
                "Depois de enviar a pré-inscrição, os lares aparecem aqui para registar as respostas, visitas e listas de espera.",
                Modifier.padding(padding),
            ) { Button(onClick = goToHomes) { Text("Escolher lares") } }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("Todos (${contacts.size})") })
                ContactStatus.entries.filter { (countsByStatus[it] ?: 0) > 0 }.forEach { e ->
                    FilterChip(
                        selected = statusFilter == e,
                        onClick = { statusFilter = if (statusFilter == e) null else e },
                        label = { Text("${e.label} (${countsByStatus[e]})") },
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleContacts, key = { it.homeId }) { c ->
                    val home = homes[c.homeId]
                    ContactCard(
                        contact = c,
                        home = home,
                        onStatusChange = { vm.updateContact(c.copy(status = it)) },
                        onEditNotes = { editing = c },
                        onCall = { home?.let { Sender.dial(context, it.phone) } },
                        onEmail = {
                            home?.let {
                                Sender.openEmail(context, listOf(it.email), emptyList(), "Re: " + LetterGenerator.subject(state.profile), "", emptyList())
                            }
                        },
                    )
                }
            }
        }
    }

    editing?.let { c ->
        var notes by remember(c.homeId) { mutableStateOf(c.notes) }
        var confirmDelete by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(homes[c.homeId]?.name ?: "Lar") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas") },
                        placeholder = { Text("Ex.: Falei com a Dra. Ana, mensalidade 1200 €, visita dia 12") },
                        minLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Retirar do acompanhamento", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.updateContact(c.copy(notes = notes)); editing = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Retirar do acompanhamento?") },
                text = { Text("O lar continua na lista; apenas o registo do contacto e as notas são apagados.") },
                confirmButton = {
                    TextButton(onClick = { vm.deleteContact(c.homeId); confirmDelete = false; editing = null }) { Text("Retirar") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
private fun statusColor(e: ContactStatus): Color = when (e) {
    ContactStatus.PLACE_AVAILABLE -> MaterialTheme.colorScheme.primary
    ContactStatus.REPLIED, ContactStatus.VISIT_SCHEDULED, ContactStatus.WAITING_LIST -> MaterialTheme.colorScheme.tertiary
    ContactStatus.DECLINED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ContactCard(
    contact: Contact,
    home: CareHome?,
    onStatusChange: (ContactStatus) -> Unit,
    onEditNotes: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - contact.sentAt)
    Card(Modifier.fillMaxWidth().clickable(onClick = onEditNotes)) {
        Column(Modifier.padding(12.dp)) {
            Text(home?.name ?: "Lar removido da lista", style = MaterialTheme.typography.titleSmall)
            home?.let { locationLine(it) }?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Enviado a ${formatDate(contact.sentAt)}" + when {
                    daysAgo == 0L -> " (hoje)"
                    daysAgo == 1L -> " (há 1 dia)"
                    else -> " (há $daysAgo dias)"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (contact.status == ContactStatus.SENT && daysAgo >= 7) {
                Text(
                    "Sem resposta há mais de uma semana — vale a pena telefonar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (contact.notes.isNotBlank()) {
                Text(contact.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AssistChip(
                        onClick = { menu = true },
                        label = { Text(contact.status.label) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = statusColor(contact.status)),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ContactStatus.entries.forEach { e ->
                            DropdownMenuItem(text = { Text(e.label) }, onClick = { onStatusChange(e); menu = false })
                        }
                    }
                }
                if (home?.phone?.isNotBlank() == true) IconButton(onClick = onCall) { Icon(Icons.Filled.Phone, "Ligar") }
                if (home?.hasEmail == true) IconButton(onClick = onEmail) { Icon(Icons.Filled.Email, "Enviar email") }
            }
        }
    }
}
