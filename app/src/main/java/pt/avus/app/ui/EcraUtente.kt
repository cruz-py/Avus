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
import pt.avus.app.carta.Envio
import pt.avus.app.carta.GeradorCarta
import pt.avus.app.data.Campo
import pt.avus.app.data.CamposPerfil
import pt.avus.app.data.Documento
import pt.avus.app.data.TipoCampo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraUtente(estado: EstadoApp, vm: AvusViewModel) {
    var aba by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(title = { Text("Utente") })
                PrimaryTabRow(selectedTabIndex = aba) {
                    Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Ficha") })
                    val anexados = estado.documentosAnexados.size
                    Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Documentos ($anexados/${estado.documentos.size})") })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (aba == 0) FichaUtente(estado, vm) else ListaDocumentos(estado, vm)
        }
    }
}

@Composable
private fun FichaUtente(estado: EstadoApp, vm: AvusViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Aviso(
                "Estes dados servem para preencher a carta de pré-inscrição. Ficam guardados apenas neste " +
                    "telemóvel e só são enviados nos emails que decidir enviar."
            )
        }
        items(CamposPerfil.secoes, key = { it.titulo }) { secao ->
            CartaoSecao(secao.titulo) {
                secao.campos.forEach { campo ->
                    CampoPerfil(campo, estado.perfil[campo.chave].orEmpty()) { vm.atualizarCampo(campo.chave, it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampoPerfil(campo: Campo, valor: String, aoMudar: (String) -> Unit) {
    val obrigatorio = campo.chave in CamposPerfil.obrigatorios
    val rotulo = campo.rotulo + if (obrigatorio) " *" else ""
    when (campo.tipo) {
        TipoCampo.OPCOES -> Column {
            Text(rotulo, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                campo.opcoes.forEach { opcao ->
                    FilterChip(
                        selected = valor == opcao,
                        onClick = { aoMudar(if (valor == opcao) "" else opcao) },
                        label = { Text(opcao) },
                    )
                }
            }
        }
        else -> {
            val teclado = when (campo.tipo) {
                TipoCampo.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                TipoCampo.TELEFONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                TipoCampo.NUMERO -> KeyboardOptions(keyboardType = KeyboardType.Number)
                TipoCampo.DATA -> KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            }
            val ajuda = when (campo.tipo) {
                TipoCampo.DATA -> {
                    val idade = GeradorCarta.idade(valor)
                    when {
                        valor.isBlank() -> campo.ajuda
                        idade != null -> "$idade anos"
                        else -> "Use o formato dd/mm/aaaa"
                    }
                }
                else -> campo.ajuda
            }
            val erro = campo.tipo == TipoCampo.DATA && valor.isNotBlank() && valor.length >= 8 && GeradorCarta.idade(valor) == null
            OutlinedTextField(
                value = valor,
                onValueChange = { novo -> aoMudar(if (campo.tipo == TipoCampo.DATA) formatarDataDigitada(valor, novo) else novo) },
                label = { Text(rotulo) },
                supportingText = if (ajuda.isNotBlank()) ({ Text(ajuda) }) else null,
                isError = erro,
                singleLine = campo.tipo != TipoCampo.MULTILINHA,
                minLines = if (campo.tipo == TipoCampo.MULTILINHA) 2 else 1,
                keyboardOptions = teclado,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Insere as barras automaticamente: "01021940" -> "01/02/1940". */
private fun formatarDataDigitada(anterior: String, novo: String): String {
    if (novo.length < anterior.length) return novo // a apagar: não interferir
    val digitos = novo.filter { it.isDigit() }.take(8)
    if (novo.any { it == '/' } && novo.length <= 10) return novo.take(10)
    return buildString {
        digitos.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

@Composable
private fun ListaDocumentos(estado: EstadoApp, vm: AvusViewModel) {
    val context = LocalContext.current
    var docPendente by rememberSaveable { mutableStateOf<String?>(null) }
    var novoDocumento by remember { mutableStateOf(false) }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = docPendente
        if (uri != null && id != null) vm.anexarDocumento(id, uri)
        docPendente = null
    }
    val total = vm.tamanhoAnexosBytes()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Aviso(
                "Documentos habitualmente pedidos pelos lares. Digitalize-os (PDF ou fotografia) e anexe os que já tiver: " +
                    "seguem em anexo no email. Os que faltarem podem ser entregues mais tarde."
            )
        }
        if (total > 0) {
            item {
                Text(
                    "Total dos anexos: ${formatarTamanho(total)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (total > LIMITE_ANEXOS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(estado.documentos, key = { it.id }) { doc ->
            CartaoDocumento(
                doc = doc,
                aoAnexar = {
                    docPendente = doc.id
                    escolher.launch(arrayOf("application/pdf", "image/*"))
                },
                aoVer = {
                    vm.ficheiroDocumento(doc)?.takeIf { it.exists() }?.let { f ->
                        val uri = Envio.uriPartilhavel(context, f)
                        val tipo = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(uri, tipo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }.onFailure { vm.mostrar("Não há nenhuma app para abrir este ficheiro.") }
                    }
                },
                aoRemover = { vm.removerAnexo(doc.id) },
                aoApagar = { vm.apagarDocumento(doc.id) },
            )
        }
        item {
            OutlinedButton(onClick = { novoDocumento = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Text("Adicionar outro documento", Modifier.padding(start = 8.dp))
            }
        }
    }

    if (novoDocumento) {
        var nome by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { novoDocumento = false },
            title = { Text("Novo documento") },
            text = {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do documento") },
                    placeholder = { Text("Ex.: Declaração de compromisso") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = nome.isNotBlank(), onClick = { vm.adicionarDocumento(nome); novoDocumento = false }) {
                    Text("Adicionar")
                }
            },
            dismissButton = { TextButton(onClick = { novoDocumento = false }) { Text("Cancelar") } },
        )
    }
}

/** Limite prudente: a maioria dos servidores de email recusa mensagens acima de 20–25 MB. */
const val LIMITE_ANEXOS = 20_000_000L

@Composable
private fun CartaoDocumento(
    doc: Documento,
    aoAnexar: () -> Unit,
    aoVer: () -> Unit,
    aoRemover: () -> Unit,
    aoApagar: () -> Unit,
) {
    var confirmarApagar by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = if (doc.anexado) "Anexado" else "Por anexar",
                    tint = if (doc.anexado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(doc.nome, style = MaterialTheme.typography.titleSmall)
                    if (doc.descricao.isNotBlank()) {
                        Text(doc.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    doc.nomeOriginal?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { confirmarApagar = true }) { Icon(Icons.Filled.Delete, "Retirar da lista") }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (doc.anexado) {
                    TextButton(onClick = aoVer) { Text("Ver") }
                    TextButton(onClick = aoRemover) { Text("Remover ficheiro") }
                    TextButton(onClick = aoAnexar) { Text("Substituir") }
                } else {
                    TextButton(onClick = aoAnexar) { Text("Anexar ficheiro") }
                }
            }
        }
    }
    if (confirmarApagar) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Retirar \"${doc.nome}\" da lista?") },
            text = { Text("Use isto para documentos que não se aplicam ao seu caso.") },
            confirmButton = { TextButton(onClick = { confirmarApagar = false; aoApagar() }) { Text("Retirar") } },
            dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
        )
    }
}
