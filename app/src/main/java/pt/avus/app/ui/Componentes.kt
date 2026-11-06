package pt.avus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalePT: Locale = Locale.forLanguageTag("pt-PT")

fun formatarData(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", LocalePT).format(Date(millis))

fun formatarTamanho(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(LocalePT, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

/** Botão compacto que abre uma lista de opções (ex.: filtro por distrito). */
@Composable
fun SeletorOpcoes(
    rotulo: String,
    valor: String?,
    opcoes: List<String>,
    textoTodos: String,
    aoEscolher: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { aberto = true },
            label = {
                Text(
                    valor ?: rotulo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
            enabled = opcoes.isNotEmpty(),
        )
        DropdownMenu(
            expanded = aberto,
            onDismissRequest = { aberto = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            DropdownMenuItem(text = { Text(textoTodos) }, onClick = { aoEscolher(null); aberto = false })
            opcoes.forEach { opcao ->
                DropdownMenuItem(text = { Text(opcao) }, onClick = { aoEscolher(opcao); aberto = false })
            }
        }
    }
}

@Composable
fun CartaoSecao(
    titulo: String,
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
fun Aviso(texto: String, modifier: Modifier = Modifier, erro: Boolean = false) {
    val cores = if (erro) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Card(modifier.fillMaxWidth(), colors = cores) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (erro) Icons.Filled.Warning else Icons.Filled.Info, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acoes: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icone, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(8.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(4.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(12.dp))
        acoes()
    }
}
