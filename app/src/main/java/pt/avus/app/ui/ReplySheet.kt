package pt.avus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.normalizeForSearch

/**
 * Records a reply from a home: which home (guessed from shared text, or the one just called),
 * its new status and a note. Shown for text shared into Avus and after a phone call.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplySheet(
    prompt: ReplyPrompt,
    state: AppState,
    onDismiss: () -> Unit,
    onSave: (homeId: String, status: ContactStatus, note: String) -> Unit,
) {
    val isCall = prompt.kind == ReplyPrompt.Kind.CALL
    var homeId by remember(prompt) { mutableStateOf(prompt.homeId) }
    val currentStatus = state.contacts.firstOrNull { it.homeId == homeId }?.status
    var status by remember(prompt) {
        mutableStateOf(if (isCall) currentStatus ?: ContactStatus.REPLIED else ContactStatus.REPLIED)
    }
    var note by remember(prompt) { mutableStateOf(prompt.text) }
    var query by remember(prompt) { mutableStateOf("") }
    val home = state.homes.firstOrNull { it.id == homeId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            Text(
                if (isCall) "Como correu a chamada?" else "Registar resposta",
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(8.dp))

            ListHeader("Lar")
            if (home != null) {
                SegmentedListItem(
                    colors = segmentedColors(),
                    shapes = segmentedShapes(0, 1),
                    supportingContent = { Text(locationLine(home)) },
                    trailingContent = if (isCall) null else ({
                        TextButton(onClick = { homeId = null }, shapes = ButtonDefaults.shapes()) { Text("Alterar") }
                    }),
                ) { Text(home.name, style = MaterialTheme.typography.titleSmallEmphasized) }
            } else {
                // Pick the home: contacted homes first, or search the whole list.
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    placeholder = { Text("Pesquisar lar…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = contentSurface,
                        unfocusedContainerColor = contentSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                val candidates = remember(query, state.homes, state.contacts) {
                    val terms = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
                    if (terms.isEmpty()) {
                        val byId = state.homes.associateBy { it.id }
                        state.contacts.sortedByDescending { it.updatedAt }.mapNotNull { byId[it.homeId] }
                    } else {
                        state.homes.filter { h ->
                            val text = normalizeForSearch("${h.name} ${h.locality} ${h.municipality}")
                            terms.all { it in text }
                        }
                    }.take(6)
                }
                if (candidates.isEmpty()) {
                    Text(
                        "Nenhum lar encontrado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                candidates.forEachIndexed { i, h ->
                    SegmentedListItem(
                        onClick = { homeId = h.id },
                        colors = segmentedColors(),
                        shapes = segmentedShapes(i, candidates.size),
                        supportingContent = { Text(locationLine(h), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    ) { Text(h.name, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }

            Spacer(Modifier.height(8.dp))
            ListHeader("Estado")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactStatus.entries.forEach { s -> CheckFilterChip(status == s, { status = s }, s.label) }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                label = { Text("Notas") },
                placeholder = { Text("Ex.: Ligar de novo na segunda; visita quinta às 15h") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.large,
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(if (isCall) "Não liguei" else "Cancelar") }
                Button(
                    onClick = { homeId?.let { onSave(it, status, note) } },
                    enabled = homeId != null,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Guardar") }
            }
        }
    }
}
