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
import pt.avus.app.carta.Envio
import pt.avus.app.data.Lar
import pt.avus.app.data.normalizar

private const val URL_CARTA_SOCIAL = "https://www.cartasocial.pt"
private const val URL_DADOS_GOV = "https://dados.gov.pt/pt/datasets/carta-social-equipamentos-e-respostas-sociais/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraLares(estado: EstadoApp, vm: AvusViewModel, irParaEnvio: () -> Unit) {
    val context = LocalContext.current
    var pesquisa by rememberSaveable { mutableStateOf("") }
    var distrito by rememberSaveable { mutableStateOf<String?>(null) }
    var concelho by rememberSaveable { mutableStateOf<String?>(null) }
    var soComEmail by rememberSaveable { mutableStateOf(false) }
    var soSelecionados by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var ajuda by remember { mutableStateOf(false) }
    var confirmarApagar by remember { mutableStateOf(false) }
    var larAberto by remember { mutableStateOf<Lar?>(null) }
    var larAEditar by remember { mutableStateOf<Lar?>(null) }

    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importarCsv(it) }
    }
    fun abrirImportacao() = importar.launch(
        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel")
    )

    val distritos = remember(estado.lares) {
        estado.lares.map { it.distrito.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { normalizar(it) }
    }
    val concelhos = remember(estado.lares, distrito) {
        estado.lares.filter { distrito == null || it.distrito.trim() == distrito }
            .map { it.concelho.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { normalizar(it) }
    }
    val contactados = remember(estado.contactos) { estado.contactos.associateBy { it.larId } }

    val filtrados = remember(estado.lares, pesquisa, distrito, concelho, soComEmail, soSelecionados, estado.selecionados) {
        val termos = normalizar(pesquisa).split(' ').filter { it.isNotBlank() }
        estado.lares.filter { lar ->
            (distrito == null || lar.distrito.trim() == distrito) &&
                (concelho == null || lar.concelho.trim() == concelho) &&
                (!soComEmail || lar.temEmail) &&
                (!soSelecionados || lar.id in estado.selecionados) &&
                (termos.isEmpty() || normalizar(
                    listOf(lar.nome, lar.entidade, lar.localidade, lar.freguesia, lar.concelho, lar.codigoPostal)
                        .joinToString(" ")
                ).let { texto -> termos.all { it in texto } })
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
                        DropdownMenuItem(text = { Text("Importar lista (CSV)") }, onClick = { menu = false; abrirImportacao() })
                        DropdownMenuItem(text = { Text("Adicionar lar manualmente") }, onClick = { menu = false; larAEditar = Lar("", "") })
                        DropdownMenuItem(text = { Text("Onde obter a lista oficial") }, onClick = { menu = false; ajuda = true })
                        if (estado.lares.any { !it.manual }) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Apagar lista importada") }, onClick = { menu = false; confirmarApagar = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (estado.selecionados.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = irParaEnvio,
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                    text = { Text("Preparar envio (${estado.selecionados.size})") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (estado.aImportar) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (estado.lares.isEmpty()) {
                EstadoVazio(
                    Icons.Filled.Home,
                    "Ainda não há lares na lista",
                    "Importe a lista oficial de Estruturas Residenciais para Pessoas Idosas (Carta Social) " +
                        "em formato CSV, ou adicione lares manualmente.",
                ) {
                    Button(onClick = { abrirImportacao() }) { Text("Importar lista (CSV)") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { larAEditar = Lar("", "") }) { Text("Adicionar lar manualmente") }
                    Spacer(Modifier.padding(4.dp))
                    TextButton(onClick = { ajuda = true }) { Text("Onde obter a lista oficial?") }
                }
                return@Column
            }

            OutlinedTextField(
                value = pesquisa,
                onValueChange = { pesquisa = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Pesquisar por nome, localidade, código postal…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (pesquisa.isNotEmpty()) IconButton(onClick = { pesquisa = "" }) { Icon(Icons.Filled.Clear, "Limpar") }
                },
                singleLine = true,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeletorOpcoes("Distrito", distrito, distritos, "Todos os distritos", { distrito = it; concelho = null })
                SeletorOpcoes("Concelho", concelho, concelhos, "Todos os concelhos", { concelho = it })
                FilterChip(selected = soComEmail, onClick = { soComEmail = !soComEmail }, label = { Text("Com email") })
                FilterChip(selected = soSelecionados, onClick = { soSelecionados = !soSelecionados }, label = { Text("Selecionados") })
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${filtrados.size} lares · ${estado.selecionados.size} selecionados",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (estado.selecionados.isNotEmpty()) {
                    TextButton(onClick = { vm.limparSelecao() }) { Text("Limpar") }
                }
                if (filtrados.isNotEmpty() && filtrados.size <= 200 && filtrados.any { it.id !in estado.selecionados }) {
                    TextButton(onClick = { vm.selecionarTodos(filtrados.map { it.id }) }) { Text("Selecionar todos") }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtrados, key = { it.id }) { lar ->
                    CartaoLar(
                        lar = lar,
                        selecionado = lar.id in estado.selecionados,
                        contactadoEm = contactados[lar.id]?.dataEnvio,
                        aoAlternar = { vm.alternarSelecao(lar.id) },
                        aoAbrir = { larAberto = lar },
                    )
                }
                if (filtrados.isEmpty()) {
                    item { Text("Nenhum lar corresponde aos filtros.", Modifier.padding(16.dp)) }
                }
            }
        }
    }

    larAberto?.let { lar ->
        DetalheLar(
            lar = lar,
            selecionado = lar.id in estado.selecionados,
            aoFechar = { larAberto = null },
            aoAlternar = { vm.alternarSelecao(lar.id) },
            aoEditar = { larAberto = null; larAEditar = lar },
            aoApagar = { larAberto = null; vm.apagarLar(lar.id) },
            aoLigar = { Envio.ligar(context, lar.telefone) },
            aoMapa = { Envio.abrirMapa(context, moradaCompleta(lar)) },
        )
    }

    larAEditar?.let { lar ->
        EditorLar(lar, aoFechar = { larAEditar = null }, aoGuardar = { vm.guardarLar(it); larAEditar = null })
    }

    if (ajuda) {
        AlertDialog(
            onDismissRequest = { ajuda = false },
            title = { Text("Onde obter a lista oficial") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "A lista oficial dos lares licenciados é a Carta Social, do Ministério do Trabalho, " +
                            "Solidariedade e Segurança Social."
                    )
                    Text(
                        "1. No portal de dados abertos (dados.gov.pt), abra o conjunto \"Carta Social – Equipamentos " +
                            "e Respostas Sociais\" e descarregue o ficheiro CSV.\n" +
                            "2. Se o ficheiro vier em Excel (.xlsx), abra-o e use \"Guardar como… CSV\".\n" +
                            "3. Aqui na app, escolha \"Importar lista (CSV)\"."
                    )
                    Text(
                        "A app reconhece as colunas automaticamente e guarda apenas as Estruturas Residenciais " +
                            "para Pessoas Idosas. Pode importar vários ficheiros (por exemplo, um por distrito)."
                    )
                    Text(
                        "Nem todos os lares têm email na Carta Social. Pode acrescentá-lo à mão, abrindo o lar e escolhendo \"Editar\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { Envio.abrirUrl(context, URL_DADOS_GOV) }) { Text("Abrir dados.gov.pt") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { Envio.abrirUrl(context, URL_CARTA_SOCIAL) }) { Text("Carta Social") }
                    TextButton(onClick = { ajuda = false }) { Text("Fechar") }
                }
            },
        )
    }

    if (confirmarApagar) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Apagar lista importada?") },
            text = { Text("Os lares adicionados manualmente e o histórico de contactos são mantidos.") },
            confirmButton = { TextButton(onClick = { vm.apagarLaresImportados(); confirmarApagar = false }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
        )
    }
}

fun moradaCompleta(lar: Lar): String =
    listOf(lar.morada, listOf(lar.codigoPostal, lar.localidade).filter { it.isNotBlank() }.joinToString(" "), lar.concelho)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")

fun localizacao(lar: Lar): String =
    listOf(lar.localidade, lar.concelho, lar.distrito).map { it.trim() }.filter { it.isNotBlank() }
        .distinctBy { normalizar(it) }.joinToString(" · ")

@Composable
private fun CartaoLar(
    lar: Lar,
    selecionado: Boolean,
    contactadoEm: Long?,
    aoAlternar: () -> Unit,
    aoAbrir: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = aoAbrir),
        colors = if (selecionado) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selecionado, onCheckedChange = { aoAlternar() })
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(lar.nome, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val local = localizacao(lar)
                if (local.isNotBlank()) {
                    Text(local, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lar.natureza.isNotBlank()) {
                        Text(lar.natureza, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (contactadoEm != null) {
                        Text("Contactado a ${formatarData(contactadoEm)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Icon(
                Icons.Filled.Email,
                contentDescription = if (lar.temEmail) "Tem email" else "Sem email",
                modifier = Modifier.size(18.dp),
                tint = if (lar.temEmail) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun DetalheLar(
    lar: Lar,
    selecionado: Boolean,
    aoFechar: () -> Unit,
    aoAlternar: () -> Unit,
    aoEditar: () -> Unit,
    aoApagar: () -> Unit,
    aoLigar: () -> Unit,
    aoMapa: () -> Unit,
) {
    var confirmar by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(lar.nome) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fun linha(rotulo: String, valor: String) = rotulo to valor
                listOf(
                    linha("Entidade", lar.entidade),
                    linha("Natureza", lar.natureza),
                    linha("Morada", moradaCompleta(lar)),
                    linha("Freguesia", lar.freguesia),
                    linha("Distrito", lar.distrito),
                    linha("Capacidade", lar.capacidade),
                    linha("Telefone", lar.telefone),
                    linha("Email", lar.email.ifBlank { "— (pode acrescentar em Editar)" }),
                ).filter { it.second.isNotBlank() }.forEach { (r, v) ->
                    Column {
                        Text(r, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lar.telefone.isNotBlank()) IconButton(onClick = aoLigar) { Icon(Icons.Filled.Phone, "Ligar") }
                    if (moradaCompleta(lar).isNotBlank()) IconButton(onClick = aoMapa) { Icon(Icons.Filled.LocationOn, "Ver no mapa") }
                    IconButton(onClick = aoEditar) { Icon(Icons.Filled.Edit, "Editar") }
                    IconButton(onClick = { confirmar = true }) { Icon(Icons.Filled.Delete, "Apagar") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoAlternar(); aoFechar() }) { Text(if (selecionado) "Retirar da seleção" else "Selecionar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Apagar este lar da lista?") },
            confirmButton = { TextButton(onClick = { confirmar = false; aoApagar() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EditorLar(lar: Lar, aoFechar: () -> Unit, aoGuardar: (Lar) -> Unit) {
    var atual by remember { mutableStateOf(lar) }
    val emailInvalido = atual.email.isNotBlank() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(atual.email.trim())

    @Composable
    fun campo(rotulo: String, valor: String, tipo: KeyboardType = KeyboardType.Text, erro: Boolean = false, f: (String) -> Lar) =
        OutlinedTextField(
            value = valor,
            onValueChange = { atual = f(it) },
            label = { Text(rotulo) },
            singleLine = true,
            isError = erro,
            keyboardOptions = KeyboardOptions(keyboardType = tipo),
            modifier = Modifier.fillMaxWidth(),
        )

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (lar.id.isBlank()) "Adicionar lar" else "Editar lar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                campo("Nome *", atual.nome) { atual.copy(nome = it) }
                campo("Email", atual.email, KeyboardType.Email, emailInvalido) { atual.copy(email = it) }
                campo("Telefone", atual.telefone, KeyboardType.Phone) { atual.copy(telefone = it) }
                campo("Natureza (IPSS, Misericórdia, privado…)", atual.natureza) { atual.copy(natureza = it) }
                campo("Morada", atual.morada) { atual.copy(morada = it) }
                campo("Código postal", atual.codigoPostal) { atual.copy(codigoPostal = it) }
                campo("Localidade", atual.localidade) { atual.copy(localidade = it) }
                campo("Concelho", atual.concelho) { atual.copy(concelho = it) }
                campo("Distrito", atual.distrito) { atual.copy(distrito = it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = atual.nome.isNotBlank() && !emailInvalido,
                onClick = { aoGuardar(atual.copy(nome = atual.nome.trim(), email = atual.email.trim().lowercase())) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
