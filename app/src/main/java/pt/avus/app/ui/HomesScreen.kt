package pt.avus.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.avus.app.letter.Sender
import pt.avus.app.data.CareHome
import pt.avus.app.data.normalizeForSearch

private const val CARTA_SOCIAL_URL = "https://www.cartasocial.pt"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomesScreen(state: AppState, vm: AvusViewModel, goToSend: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf<String?>(null) }
    var municipality by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyWithEmail by rememberSaveable { mutableStateOf(false) }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var openedHome by remember { mutableStateOf<CareHome?>(null) }
    var homeToEdit by remember { mutableStateOf<CareHome?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importCsv(it) }
    }
    fun openImport() = importLauncher.launch(
        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel")
    )

    val districts = remember(state.homes) {
        state.homes.map { it.district.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { normalizeForSearch(it) }
    }
    val municipalities = remember(state.homes, district) {
        state.homes.filter { district == null || it.district.trim() == district }
            .map { it.municipality.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { normalizeForSearch(it) }
    }
    val contactsByHome = remember(state.contacts) { state.contacts.associateBy { it.homeId } }

    val filtered = remember(state.homes, searchQuery, district, municipality, onlyWithEmail, onlySelected, state.selectedIds) {
        val terms = normalizeForSearch(searchQuery).split(' ').filter { it.isNotBlank() }
        state.homes.filter { home ->
            (district == null || home.district.trim() == district) &&
                (municipality == null || home.municipality.trim() == municipality) &&
                (!onlyWithEmail || home.hasEmail) &&
                (!onlySelected || home.id in state.selectedIds) &&
                (terms.isEmpty() || normalizeForSearch(
                    listOf(home.name, home.owner, home.locality, home.parish, home.municipality, home.postalCode)
                        .joinToString(" ")
                ).let { text -> terms.all { it in text } })
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Lares (ERPI)") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Importar lista (CSV)") }, onClick = { menu = false; openImport() })
                        DropdownMenuItem(text = { Text("Adicionar lar manualmente") }, onClick = { menu = false; homeToEdit = CareHome("", "") })
                        DropdownMenuItem(text = { Text("Onde obter a lista oficial") }, onClick = { menu = false; showHelp = true })
                        if (state.homes.any { !it.manual }) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Apagar lista importada") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.selectedIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = goToSend,
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                    text = { Text("Preparar envio (${state.selectedIds.size})") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (state.homes.isEmpty()) {
                EmptyState(
                    Icons.Filled.Home,
                    "Ainda não há lares na lista",
                    "Importe a lista oficial de Estruturas Residenciais para Pessoas Idosas (Carta Social) " +
                        "em formato CSV, ou adicione lares manualmente.",
                ) {
                    Button(onClick = { openImport() }) { Text("Importar lista (CSV)") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { homeToEdit = CareHome("", "") }) { Text("Adicionar lar manualmente") }
                    Spacer(Modifier.padding(4.dp))
                    TextButton(onClick = { showHelp = true }) { Text("Onde obter a lista oficial?") }
                }
                return@Column
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Pesquisar por nome, localidade, código postal…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Clear, "Limpar") }
                },
                singleLine = true,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionPicker("Distrito", district, districts, "Todos os distritos", { district = it; municipality = null })
                OptionPicker("Concelho", municipality, municipalities, "Todos os concelhos", { municipality = it })
                FilterChip(selected = onlyWithEmail, onClick = { onlyWithEmail = !onlyWithEmail }, label = { Text("Com email") })
                FilterChip(selected = onlySelected, onClick = { onlySelected = !onlySelected }, label = { Text("Selecionados") })
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${filtered.size} lares · ${state.selectedIds.size} selecionados",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (state.selectedIds.isNotEmpty()) {
                    TextButton(onClick = { vm.clearSelection() }) { Text("Limpar") }
                }
                if (filtered.isNotEmpty() && filtered.size <= 200 && filtered.any { it.id !in state.selectedIds }) {
                    TextButton(onClick = { vm.selectAll(filtered.map { it.id }) }) { Text("Selecionar todos") }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { home ->
                    CareHomeCard(
                        home = home,
                        selected = home.id in state.selectedIds,
                        contactedAt = contactsByHome[home.id]?.sentAt,
                        onToggle = { vm.toggleSelection(home.id) },
                        onOpen = { openedHome = home },
                    )
                }
                if (filtered.isEmpty()) {
                    item { Text("Nenhum lar corresponde aos filtros.", Modifier.padding(16.dp)) }
                }
            }
        }
    }

    openedHome?.let { home ->
        CareHomeDetails(
            home = home,
            selected = home.id in state.selectedIds,
            onClose = { openedHome = null },
            onToggle = { vm.toggleSelection(home.id) },
            onEdit = { openedHome = null; homeToEdit = home },
            onDelete = { openedHome = null; vm.deleteHome(home.id) },
            onCall = { Sender.dial(context, home.phone) },
            onMap = { Sender.openMap(context, fullAddress(home)) },
        )
    }

    homeToEdit?.let { home ->
        CareHomeEditor(home, onClose = { homeToEdit = null }, onSave = { vm.saveHome(it); homeToEdit = null })
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Onde obter a lista de lares") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "A lista oficial dos lares licenciados é a Carta Social, do Ministério do Trabalho, " +
                            "Solidariedade e Segurança Social. O portal permite pesquisar, mas não descarregar a lista, " +
                            "por isso há duas formas de trazer os lares para a app:"
                    )
                    Text(
                        "• Com um ficheiro CSV — por exemplo, a lista nacional das ERPI recolhida da Carta Social, " +
                            "ou os dados abertos de um município (como o portal dadosabertos.cascais.pt). " +
                            "Escolha \"Importar lista (CSV)\": a app reconhece as colunas automaticamente e guarda " +
                            "apenas os lares. Se o ficheiro vier em Excel (.xlsx), abra-o e use \"Guardar como… CSV\"."
                    )
                    Text(
                        "• À mão — na Carta Social, pesquise \"Pessoas Idosas\" → \"Estrutura Residencial para " +
                            "Pessoas Idosas\" → distrito, abra cada lar que lhe interesse e copie os contactos com " +
                            "\"Adicionar lar manualmente\"."
                    )
                    Text(
                        "Os dados municipais podem incluir centros de dia e apoio domiciliário: confirme a lista " +
                            "antes de enviar. Açores e Madeira não estão na Carta Social.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Nem todos os lares têm email na Carta Social. Pode acrescentá-lo à mão, abrindo o lar e escolhendo \"Editar\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { Sender.openUrl(context, CARTA_SOCIAL_URL) }) { Text("Abrir Carta Social") } },
            dismissButton = { TextButton(onClick = { showHelp = false }) { Text("Fechar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar lista importada?") },
            text = { Text("Os lares adicionados manualmente e o histórico de contactos são mantidos.") },
            confirmButton = { TextButton(onClick = { vm.deleteImportedHomes(); confirmDelete = false }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

fun fullAddress(home: CareHome): String =
    listOf(home.address, listOf(home.postalCode, home.locality).filter { it.isNotBlank() }.joinToString(" "), home.municipality)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")

fun locationLine(home: CareHome): String =
    listOf(home.locality, home.municipality, home.district).map { it.trim() }.filter { it.isNotBlank() }
        .distinctBy { normalizeForSearch(it) }.joinToString(" · ")

@Composable
private fun CareHomeCard(
    home: CareHome,
    selected: Boolean,
    contactedAt: Long?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(home.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val place = locationLine(home)
                if (place.isNotBlank()) {
                    Text(place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (home.legalForm.isNotBlank()) {
                        Text(home.legalForm, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (contactedAt != null) {
                        Text("Contactado a ${formatDate(contactedAt)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Icon(
                Icons.Filled.Email,
                contentDescription = if (home.hasEmail) "Tem email" else "Sem email",
                modifier = Modifier.size(18.dp),
                tint = if (home.hasEmail) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun CareHomeDetails(
    home: CareHome,
    selected: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onMap: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(home.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fun entry(label: String, value: String) = label to value
                listOf(
                    entry("Entidade", home.owner),
                    entry("Natureza", home.legalForm),
                    entry("Morada", fullAddress(home)),
                    entry("Freguesia", home.parish),
                    entry("Distrito", home.district),
                    entry("Capacidade", home.capacity),
                    entry("Telefone", home.phone),
                    entry("Email", home.email.ifBlank { "— (pode acrescentar em Editar)" }),
                ).filter { it.second.isNotBlank() }.forEach { (r, v) ->
                    Column {
                        Text(r, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (home.phone.isNotBlank()) IconButton(onClick = onCall) { Icon(Icons.Filled.Phone, "Ligar") }
                    if (fullAddress(home).isNotBlank()) IconButton(onClick = onMap) { Icon(Icons.Filled.LocationOn, "Ver no mapa") }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Apagar") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onToggle(); onClose() }) { Text(if (selected) "Retirar da seleção" else "Selecionar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Fechar") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar este lar da lista?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CareHomeEditor(home: CareHome, onClose: () -> Unit, onSave: (CareHome) -> Unit) {
    var current by remember { mutableStateOf(home) }
    val invalidEmail = current.email.isNotBlank() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(current.email.trim())

    @Composable
    fun field(label: String, value: String, type: KeyboardType = KeyboardType.Text, isError: Boolean = false, f: (String) -> CareHome) =
        OutlinedTextField(
            value = value,
            onValueChange = { current = f(it) },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth(),
        )

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (home.id.isBlank()) "Adicionar lar" else "Editar lar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                field("Nome *", current.name) { current.copy(name = it) }
                field("Email", current.email, KeyboardType.Email, invalidEmail) { current.copy(email = it) }
                field("Telefone", current.phone, KeyboardType.Phone) { current.copy(phone = it) }
                field("Natureza (IPSS, Misericórdia, privado…)", current.legalForm) { current.copy(legalForm = it) }
                field("Morada", current.address) { current.copy(address = it) }
                field("Código postal", current.postalCode) { current.copy(postalCode = it) }
                field("Localidade", current.locality) { current.copy(locality = it) }
                field("Concelho", current.municipality) { current.copy(municipality = it) }
                field("Distrito", current.district) { current.copy(district = it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.name.isNotBlank() && !invalidEmail,
                onClick = { onSave(current.copy(name = current.name.trim(), email = current.email.trim().lowercase())) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}
