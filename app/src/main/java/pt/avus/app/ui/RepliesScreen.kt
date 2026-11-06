package pt.avus.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pt.avus.app.data.CareHome
import pt.avus.app.data.Contact
import pt.avus.app.data.ContactStatus
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.letter.Sender
import pt.avus.app.reminders.ReplyReminderWorker
import java.util.concurrent.TimeUnit

@Composable
fun RepliesScreen(state: AppState, vm: AvusViewModel, goToHomes: () -> Unit) {
    val context = LocalContext.current
    var statusFilter by rememberSaveable { mutableStateOf<ContactStatus?>(null) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    val homes = remember(state.homes) { state.homes.associateBy { it.id } }
    val contacts = state.contacts.sortedByDescending { it.updatedAt }
    val visibleContacts = contacts.filter { statusFilter == null || it.status == statusFilter }
    val countsByStatus = contacts.groupingBy { it.status }.eachCount()

    // Reminders need notification permission (Android 13+); re-checked when coming back from Settings.
    var canNotify by remember { mutableStateOf(ReplyReminderWorker.canNotify(context)) }
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = ReplyReminderWorker.canNotify(context)
    }
    LifecycleResumeEffect(Unit) {
        canNotify = ReplyReminderWorker.canNotify(context)
        onPauseOrDispose { }
    }
    fun enableReminders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedPermission) {
            askedPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Already refused once (or notifications switched off): the system settings are the only way.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
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
                title = { Text("Respostas dos lares") },
                colors = screenTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Filled.List,
                "Ainda não contactou nenhum lar",
                "Depois de enviar a pré-inscrição, os lares aparecem aqui para registar as respostas, visitas e listas de espera.",
                Modifier.padding(padding),
            ) { Button(onClick = goToHomes, shapes = ButtonDefaults.shapes()) { Text("Escolher lares") } }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (!canNotify) {
                item(key = "reminders") {
                    ReminderCard(onEnable = ::enableReminders, modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 12.dp))
                }
            }
            item(key = "filters") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CheckFilterChip(statusFilter == null, { statusFilter = null }, "Todos (${contacts.size})")
                    ContactStatus.entries.filter { (countsByStatus[it] ?: 0) > 0 }.forEach { s ->
                        CheckFilterChip(statusFilter == s, { statusFilter = if (statusFilter == s) null else s }, "${s.label} (${countsByStatus[s]})")
                    }
                }
            }
            itemsIndexed(visibleContacts, key = { _, c -> c.homeId }) { index, c ->
                val home = homes[c.homeId]
                ContactRow(
                    contact = c,
                    home = home,
                    index = index,
                    count = visibleContacts.size,
                    onStatusChange = { vm.updateContact(c.copy(status = it)) },
                    onEditNotes = { editing = c },
                    onCall = { home?.let { vm.callStarted(it.id); Sender.dial(context, it.phone) } },
                    onEmail = {
                        home?.let {
                            Sender.openEmail(context, listOf(it.email), emptyList(), "Re: " + LetterGenerator.subject(state.profile), "", emptyList())
                        }
                    },
                    modifier = Modifier.animateItem().padding(horizontal = ScreenPadding),
                )
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
                        shape = MaterialTheme.shapes.large,
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
private fun ReminderCard(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(
                Icons.Filled.Notifications,
                shape = MaterialShapes.Cookie6Sided,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Lembretes", style = MaterialTheme.typography.titleSmallEmphasized)
                Text(
                    "Receba um aviso quando um lar não responder há mais de uma semana.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledTonalButton(onClick = onEnable, shapes = ButtonDefaults.shapes()) { Text("Ativar") }
        }
    }
}

/** Icon, shape and colours that identify each status at a glance. */
private data class StatusStyle(val icon: ImageVector, val shape: RoundedPolygon, val container: Color, val content: Color)

@Composable
private fun statusStyle(s: ContactStatus): StatusStyle {
    val c = MaterialTheme.colorScheme
    return when (s) {
        ContactStatus.SENT -> StatusStyle(Icons.AutoMirrored.Filled.Send, MaterialShapes.Circle, c.secondaryContainer, c.onSecondaryContainer)
        ContactStatus.NO_REPLY -> StatusStyle(Icons.Outlined.Info, MaterialShapes.Circle, c.surfaceContainerHighest, c.onSurfaceVariant)
        ContactStatus.REPLIED -> StatusStyle(Icons.Filled.Email, MaterialShapes.Square, c.tertiaryContainer, c.onTertiaryContainer)
        ContactStatus.VISIT_SCHEDULED -> StatusStyle(Icons.Filled.DateRange, MaterialShapes.Cookie4Sided, c.tertiaryContainer, c.onTertiaryContainer)
        ContactStatus.WAITING_LIST -> StatusStyle(Icons.AutoMirrored.Filled.List, MaterialShapes.Pill, c.tertiaryContainer, c.onTertiaryContainer)
        ContactStatus.PLACE_AVAILABLE -> StatusStyle(Icons.Filled.Star, MaterialShapes.Sunny, c.primary, c.onPrimary)
        ContactStatus.DECLINED -> StatusStyle(Icons.Filled.Close, MaterialShapes.Circle, c.errorContainer, c.onErrorContainer)
    }
}

@Composable
private fun statusColor(s: ContactStatus): Color = when (s) {
    ContactStatus.PLACE_AVAILABLE -> MaterialTheme.colorScheme.primary
    ContactStatus.REPLIED, ContactStatus.VISIT_SCHEDULED, ContactStatus.WAITING_LIST -> MaterialTheme.colorScheme.tertiary
    ContactStatus.DECLINED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ContactRow(
    contact: Contact,
    home: CareHome?,
    index: Int,
    count: Int,
    onStatusChange: (ContactStatus) -> Unit,
    onEditNotes: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - contact.sentAt)
    val style = statusStyle(contact.status)
    SegmentedListItem(
        colors = segmentedColors(),
        onClick = onEditNotes,
        shapes = segmentedShapes(index, count),
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        leadingContent = { ShapeIcon(style.icon, shape = style.shape, containerColor = style.container, contentColor = style.content) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                home?.let { locationLine(it) }?.takeIf { it.isNotBlank() }?.let { Text(it) }
                Text(
                    "Enviado a ${formatDate(contact.sentAt)}" + when {
                        daysAgo == 0L -> " (hoje)"
                        daysAgo == 1L -> " (há 1 dia)"
                        else -> " (há $daysAgo dias)"
                    },
                )
                if (contact.status == ContactStatus.SENT && daysAgo >= 7) {
                    Text(
                        "Sem resposta há mais de uma semana — vale a pena telefonar.",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (contact.notes.isNotBlank()) {
                    Text(contact.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        AssistChip(
                            onClick = { menu = true },
                            label = { Text(contact.status.label) },
                            leadingIcon = { StatusDot(statusColor(contact.status)) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = statusColor(contact.status)),
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                            ContactStatus.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { onStatusChange(s); menu = false },
                                    leadingIcon = { StatusDot(statusColor(s)) },
                                    trailingIcon = if (s == contact.status) ({ Icon(Icons.Filled.Check, null) }) else null,
                                )
                            }
                        }
                    }
                    if (home?.phone?.isNotBlank() == true) IconButton(onClick = onCall) { Icon(Icons.Filled.Phone, "Ligar") }
                    if (home?.hasEmail == true) IconButton(onClick = onEmail) { Icon(Icons.Filled.Email, "Enviar email") }
                }
            }
        },
    ) {
        Text(home?.name ?: "Lar removido da lista", style = MaterialTheme.typography.titleSmallEmphasized)
    }
}
