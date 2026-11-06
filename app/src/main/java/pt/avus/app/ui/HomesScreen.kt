package pt.avus.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.avus.app.data.CareHome
import pt.avus.app.data.OfficialList
import pt.avus.app.data.normalizeForSearch
import pt.avus.app.letter.Sender

private const val CARTA_SOCIAL_URL = "https://www.cartasocial.pt"

@Composable
fun HomesScreen(state: AppState, vm: AvusViewModel, goToSend: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf<String?>(null) }
    var municipality by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyWithEmail by rememberSaveable { mutableStateOf(false) }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    var onlyWithPlaces by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var openedHomeId by rememberSaveable { mutableStateOf<String?>(null) }
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

    val filtered = remember(state.homes, searchQuery, district, municipality, onlyWithEmail, onlyWithPlaces, onlySelected, state.selectedIds) {
        val terms = normalizeForSearch(searchQuery).split(' ').filter { it.isNotBlank() }
        state.homes.filter { home ->
            (district == null || home.district.trim() == district) &&
                (municipality == null || home.municipality.trim() == municipality) &&
                (!onlyWithEmail || home.hasEmail) &&
                (!onlyWithPlaces || (home.freePlaces ?: 0) > 0) &&
                (!onlySelected || home.id in state.selectedIds) &&
                (terms.isEmpty() || normalizeForSearch(
                    listOf(home.name, home.owner, home.locality, home.parish, home.municipality, home.postalCode)
                        .joinToString(" ")
                ).let { text -> terms.all { it in text } })
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Lares (ERPI)") },
                subtitle = if (state.homes.isEmpty()) null else ({
                    Text("${filtered.size} lares · ${state.selectedIds.size} selecionados")
                }),
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(text = { Text("Importar lista (CSV)") }, onClick = { menu = false; openImport() })
                        DropdownMenuItem(text = { Text("Adicionar lar manualmente") }, onClick = { menu = false; homeToEdit = CareHome("", "") })
                        DropdownMenuItem(text = { Text("Onde obter a lista oficial") }, onClick = { menu = false; showHelp = true })
                        if (state.homes.none { it.official }) {
                            DropdownMenuItem(text = { Text("Restaurar lista oficial") }, onClick = { menu = false; vm.restoreOfficialList() })
                        }
                        if (state.homes.any { !it.manual }) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Apagar lista importada") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
                colors = screenTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(state.selectedIds.isNotEmpty(), enter = scaleIn(), exit = scaleOut()) {
                MediumExtendedFloatingActionButton(onClick = goToSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Preparar envio (${state.selectedIds.size})")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isImporting && state.homes.isNotEmpty()) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding))

            if (state.homes.isEmpty() && state.isImporting) {
                // First launch: the bundled official list is being loaded.
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator(Modifier.size(96.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("A carregar a lista oficial de lares…", style = MaterialTheme.typography.titleMediumEmphasized)
                }
                return@Column
            }

            if (state.homes.isEmpty()) {
                EmptyState(
                    Icons.Filled.Home,
                    "Ainda não há lares na lista",
                    "Carregue a lista oficial de Estruturas Residenciais para Pessoas Idosas (Carta Social), " +
                        "importe outra lista em CSV ou adicione lares manualmente.",
                ) {
                    Button(onClick = { vm.restoreOfficialList() }, enabled = !state.isImporting, shapes = ButtonDefaults.shapes()) {
                        Text("Carregar lista oficial")
                    }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { openImport() }, shapes = ButtonDefaults.shapes()) { Text("Importar lista (CSV)") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { homeToEdit = CareHome("", "") }, shapes = ButtonDefaults.shapes()) {
                        Text("Adicionar lar manualmente")
                    }
                    Spacer(Modifier.padding(4.dp))
                    TextButton(onClick = { showHelp = true }, shapes = ButtonDefaults.shapes()) { Text("Onde obter a lista oficial?") }
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "search") {
                    SearchField(searchQuery, onChange = { searchQuery = it }, Modifier.padding(horizontal = ScreenPadding).padding(bottom = 8.dp))
                }
                item(key = "filters") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OptionPicker("Distrito", district, districts, "Todos os distritos", { district = it; municipality = null })
                        OptionPicker("Concelho", municipality, municipalities, "Todos os concelhos", { municipality = it })
                        CheckFilterChip(onlyWithPlaces, { onlyWithPlaces = !onlyWithPlaces }, "Com vagas")
                        CheckFilterChip(onlyWithEmail, { onlyWithEmail = !onlyWithEmail }, "Com email")
                        CheckFilterChip(onlySelected, { onlySelected = !onlySelected }, "Selecionados")
                    }
                }
                item(key = "selection") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 8.dp).padding(horizontal = ScreenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val date = state.officialListDate
                        if (date != null && state.homes.any { it.official }) {
                            // Attribution of the bundled list, with the date it was collected.
                            Text(
                                "Fonte: ${OfficialList.SOURCE} · dados de ${formatIsoDate(date)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                        } else Spacer(Modifier.weight(1f))
                        if (state.selectedIds.isNotEmpty()) {
                            TextButton(onClick = { vm.clearSelection() }, shapes = ButtonDefaults.shapes()) { Text("Limpar") }
                        }
                        if (filtered.isNotEmpty() && filtered.size <= 200 && filtered.any { it.id !in state.selectedIds }) {
                            TextButton(onClick = { vm.selectAll(filtered.map { it.id }) }, shapes = ButtonDefaults.shapes()) {
                                Text("Selecionar todos")
                            }
                        }
                    }
                }
                itemsIndexed(filtered, key = { _, home -> home.id }) { index, home ->
                    CareHomeRow(
                        home = home,
                        index = index,
                        count = filtered.size,
                        selected = home.id in state.selectedIds,
                        contactedAt = contactsByHome[home.id]?.sentAt,
                        onToggle = { vm.toggleSelection(home.id) },
                        onOpen = { openedHomeId = home.id },
                        modifier = Modifier.animateItem().padding(horizontal = ScreenPadding),
                    )
                }
                if (filtered.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "Nenhum lar corresponde aos filtros.",
                            Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    state.homes.firstOrNull { it.id == openedHomeId }?.let { home ->
        CareHomeDetails(
            home = home,
            selected = home.id in state.selectedIds,
            onClose = { openedHomeId = null },
            onToggle = { vm.toggleSelection(home.id) },
            onEdit = { openedHomeId = null; homeToEdit = home },
            onDelete = { openedHomeId = null; vm.deleteHome(home.id) },
            onCall = { vm.callStarted(home.id); Sender.dial(context, home.phone) },
            onMap = { Sender.openMap(context, fullAddress(home)) },
            onCartaSocial = { home.cartaSocialUrl?.let { Sender.openUrl(context, it) } },
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
                        "A app já inclui a lista oficial dos lares licenciados de Portugal continental, recolhida da " +
                            "Carta Social, publicada pela Direção-Geral da Solidariedade e Segurança Social (DGSSS). " +
                            "A data dos dados aparece por baixo dos filtros. Se apagou a lista, escolha " +
                            "\"Restaurar lista oficial\" no menu."
                    )
                    Text(
                        "• Com um ficheiro CSV — por exemplo, os dados abertos de um município (como o portal " +
                            "dadosabertos.cascais.pt). Escolha \"Importar lista (CSV)\": a app reconhece as colunas " +
                            "automaticamente e guarda apenas os lares. Se o ficheiro vier em Excel (.xlsx), abra-o e use " +
                            "\"Guardar como… CSV\"."
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

/** Pill-shaped search field on a tonal surface, as in Android 16/17 system apps. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Pesquisar por nome, localidade, código postal…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Limpar") }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = contentSurface,
            unfocusedContainerColor = contentSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun CareHomeRow(
    home: CareHome,
    index: Int,
    count: Int,
    selected: Boolean,
    contactedAt: Long?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (selected) {
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            supportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else segmentedColors()
    SegmentedListItem(
        onClick = onOpen,
        shapes = segmentedShapes(index, count),
        modifier = modifier,
        colors = colors,
        leadingContent = {
            SelectableAvatar(
                selected = selected,
                initials = initialsOf(home.name),
                onToggle = onToggle,
                contentDescription = if (selected) "Retirar da seleção" else "Selecionar",
            )
        },
        supportingContent = {
            Column {
                val place = locationLine(home)
                if (place.isNotBlank()) Text(place, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contactedAt != null) {
                    Text(
                        "Contactado a ${formatDate(contactedAt)}",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (home.legalForm.isNotBlank()) {
                    Text(home.legalForm, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OccupancyBar(home, Modifier.padding(top = 6.dp, end = 8.dp))
            }
        },
        trailingContent = {
            Icon(
                if (home.hasEmail) Icons.Filled.Email else Icons.Outlined.Email,
                contentDescription = if (home.hasEmail) "Tem email" else "Sem email",
                modifier = Modifier.size(20.dp),
                tint = if (home.hasEmail) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        },
    ) {
        Text(home.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmallEmphasized)
    }
}

/** Details of a care home in a bottom sheet: quick actions, the facts as a segmented list, and select. */
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
    onCartaSocial: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    Icons.Filled.Home,
                    shape = MaterialShapes.Cookie9Sided,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 56.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(home.name, style = MaterialTheme.typography.titleLargeEmphasized)
                    val place = locationLine(home)
                    if (place.isNotBlank()) {
                        Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (home.phone.isNotBlank()) QuickAction(Icons.Filled.Phone, "Ligar", onCall)
                if (fullAddress(home).isNotBlank()) QuickAction(Icons.Filled.LocationOn, "Ver no mapa", onMap)
                QuickAction(Icons.Filled.Edit, "Editar", onEdit)
                QuickAction(Icons.Filled.Delete, "Apagar") { confirmDelete = true }
            }
            Spacer(Modifier.height(12.dp))
            if (home.occupancy != null) {
                Occupancy(home)
                Spacer(Modifier.height(12.dp))
            }
            val facts = listOf(
                "Entidade" to home.owner,
                "Natureza" to home.legalForm,
                "Morada" to fullAddress(home),
                "Freguesia" to home.parish,
                "Distrito" to home.district,
                "Capacidade" to home.capacity.takeIf { home.occupancy == null }.orEmpty(),
                "Atualizado na Carta Social" to home.lastUpdated.takeIf { home.occupancy == null }.orEmpty().let {
                    if (it.isBlank()) it else formatIsoDate(it)
                },
                "Telefone" to home.phone,
                "Email" to home.email.ifBlank { "— (pode acrescentar em Editar)" },
            ).filter { it.second.isNotBlank() }
            facts.forEachIndexed { i, (label, value) ->
                SegmentedListItem(
                    colors = segmentedColors(),
                    shapes = segmentedShapes(i, facts.size),
                    overlineContent = { Text(label) },
                ) { Text(value) }
            }
            if (home.cartaSocialUrl != null) {
                TextButton(onClick = onCartaSocial, shapes = ButtonDefaults.shapes()) { Text("Ver na Carta Social") }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onToggle(); onClose() },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    if (selected) "Retirar da seleção" else "Selecionar",
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar este lar da lista?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Occupancy in the details sheet: residents of capacity, free places, and where the numbers come from. */
@Composable
private fun Occupancy(home: CareHome) {
    androidx.compose.material3.Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = contentSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ocupação", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${home.residents} de ${home.capacity} utentes",
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            OccupancyBar(home, thickness = 10.dp)
            Text(
                "Segundo a Carta Social" +
                    (if (home.lastUpdated.isNotBlank()) " (atualizado a ${formatIsoDate(home.lastUpdated)})" else "") +
                    ". Confirme sempre com o lar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Round tonal button with a label underneath (like the actions in Contacts). */
@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(icon, label) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
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
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (home.id.isBlank()) "Adicionar lar" else "Editar lar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
