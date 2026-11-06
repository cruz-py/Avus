package pt.avus.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import pt.avus.app.letter.Sender
import pt.avus.app.reminders.ReplyReminderWorker
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.data.Contact
import pt.avus.app.data.Document
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.ImportException
import pt.avus.app.data.CsvImporter
import pt.avus.app.data.CareHome
import pt.avus.app.data.FollowUp
import pt.avus.app.data.OfficialList
import pt.avus.app.data.Profile
import pt.avus.app.data.Repository
import pt.avus.app.data.ProfileFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Asks the user to record a reply: from text shared into the app, or after a phone call. */
data class ReplyPrompt(val kind: Kind, val homeId: String?, val text: String = "") {
    enum class Kind { SHARED, CALL }
}

data class AppState(
    val isLoaded: Boolean = false,
    val homes: List<CareHome> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val profile: Profile = emptyMap(),
    val documents: List<Document> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    /** Letter text edited by hand (null = use the generated text). */
    val customLetter: String? = null,
    val isImporting: Boolean = false,
    /** Collection date (yyyy-MM-dd) of the bundled official list, if it is installed. */
    val officialListDate: String? = null,
    /** The official list is bundled in this build (it is generated, not kept in git). */
    val officialListAvailable: Boolean = false,
) {
    val selectedHomes: List<CareHome> get() = homes.filter { it.id in selectedIds }
    val attachedDocuments: List<Document> get() = documents.filter { it.isAttached }

    val generatedLetter: String
        get() = LetterGenerator.generate(profile, attachedDocuments, documents.filterNot { it.isAttached })

    val letter: String get() = customLetter ?: generatedLetter
}

class AvusViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Short messages to show in a Snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var saveProfileJob: Job? = null
    private var saveLetterJob: Job? = null

    init {
        viewModelScope.launch {
            repo.migrateLegacyStorage()
            _state.value = AppState(
                isLoaded = true,
                homes = repo.loadHomes().sortedBy { it.name.lowercase() },
                selectedIds = repo.loadSelection(),
                profile = repo.loadProfile(),
                documents = repo.loadDocuments(),
                contacts = repo.loadContacts(),
                customLetter = repo.loadCustomLetter(),
                officialListDate = repo.loadOfficialListState()?.takeUnless { it.removed }?.date,
                officialListAvailable = repo.hasAsset(OfficialList.CSV_ASSET) && repo.hasAsset(OfficialList.META_ASSET),
            )
            installOfficialList(force = false)
        }
        ReplyReminderWorker.schedule(app)
    }

    fun messageShown() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }

    // ---------- Care homes ----------

    fun importCsv(uri: Uri) {
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val bytes = repo.readUriBytes(uri)
                val result = withContext(Dispatchers.Default) { CsvImporter.importBytes(bytes) }
                if (result.homes.isEmpty()) {
                    showMessage(
                        if (result.filteredBySocialResponse)
                            "Não foram encontradas ERPI no ficheiro (${result.rowsRead} linhas lidas)."
                        else "Não foram encontrados lares no ficheiro."
                    )
                    return@launch
                }
                // Merge into the current list: allows importing several files (e.g. one per district).
                val existingById = _state.value.homes.associateBy { it.id }.toMutableMap()
                var newCount = 0
                result.homes.forEach { home ->
                    val existing = existingById[home.id]
                    if (existing == null) newCount++
                    // Do not overwrite an email the user added by hand.
                    existingById[home.id] = if (existing != null && home.email.isBlank()) home.copy(email = existing.email) else home
                }
                val list = existingById.values.sortedBy { it.name.lowercase() }
                _state.update { it.copy(homes = list) }
                repo.saveHomes(list)
                val withEmail = result.homes.count { it.hasEmail }
                showMessage(
                    "Importados ${result.homes.size} lares ($newCount novos, $withEmail com email)." +
                        if (!result.filteredBySocialResponse) " Atenção: o ficheiro não indica a resposta social, por isso foram importadas todas as linhas." else ""
                )
            } catch (e: ImportException) {
                showMessage(e.message ?: "Erro ao importar.")
            } catch (e: Exception) {
                showMessage("Não foi possível ler o ficheiro: ${e.message}")
            } finally {
                _state.update { it.copy(isImporting = false) }
            }
        }
    }

    fun saveHome(home: CareHome) {
        val newValue = if (home.id.isBlank()) home.copy(id = "manual-" + UUID.randomUUID(), manual = true) else home
        val list = (_state.value.homes.filterNot { it.id == newValue.id } + newValue).sortedBy { it.name.lowercase() }
        _state.update { it.copy(homes = list) }
        viewModelScope.launch { repo.saveHomes(list) }
    }

    fun deleteHome(id: String) {
        val list = _state.value.homes.filterNot { it.id == id }
        _state.update { it.copy(homes = list, selectedIds = it.selectedIds - id) }
        viewModelScope.launch {
            repo.saveHomes(list)
            repo.saveSelection(_state.value.selectedIds)
        }
    }

    // ---------- Official list ----------

    /** Loads the official ERPI list bundled with the app (first launch, app update, or "restore"). */
    private suspend fun installOfficialList(force: Boolean) {
        if (!_state.value.officialListAvailable) return // build without the generated list
        val meta = OfficialList.parseMeta(repo.readAsset(OfficialList.META_ASSET).decodeToString())
        if (!force && !OfficialList.shouldInstall(repo.loadOfficialListState(), meta)) return
        _state.update { it.copy(isImporting = true) }
        try {
            val official = withContext(Dispatchers.Default) {
                CsvImporter.importBytes(repo.readAsset(OfficialList.CSV_ASSET)).homes
            }
            val s = _state.value
            val keep = s.selectedIds + s.contacts.map { it.homeId }
            val merged = withContext(Dispatchers.Default) { OfficialList.merge(s.homes, official, keep) }
            _state.update { it.copy(homes = merged, officialListDate = meta.date) }
            repo.saveHomes(merged)
            repo.saveOfficialListState(OfficialList.State(removed = false, date = meta.date))
        } catch (e: Exception) {
            showMessage("Não foi possível carregar a lista oficial: ${e.message}")
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun restoreOfficialList() {
        viewModelScope.launch {
            installOfficialList(force = true)
            showMessage("Lista oficial carregada: ${_state.value.homes.count { it.official }} lares.")
        }
    }

    fun deleteImportedHomes() {
        val hadOfficial = _state.value.homes.any { it.official }
        val list = _state.value.homes.filter { it.manual }
        val ids = list.map { it.id }.toSet()
        _state.update { it.copy(homes = list, selectedIds = it.selectedIds.intersect(ids)) }
        if (hadOfficial) _state.update { it.copy(officialListDate = null) }
        viewModelScope.launch {
            repo.saveHomes(list)
            repo.saveSelection(_state.value.selectedIds)
            // Remember the choice, so app updates do not bring the list back by themselves.
            if (hadOfficial) {
                val date = repo.loadOfficialListState()?.date.orEmpty()
                repo.saveOfficialListState(OfficialList.State(removed = true, date = date))
            }
        }
    }

    fun toggleSelection(id: String) = setSelection(
        _state.value.selectedIds.let { if (id in it) it - id else it + id }
    )

    fun selectAll(ids: Collection<String>) = setSelection(_state.value.selectedIds + ids)
    fun clearSelection() = setSelection(emptySet())

    private fun setSelection(ids: Set<String>) {
        _state.update { it.copy(selectedIds = ids) }
        viewModelScope.launch { repo.saveSelection(ids) }
    }

    // ---------- Profile ----------

    fun updateField(key: String, value: String) {
        _state.update { it.copy(profile = it.profile + (key to value)) }
        // Save shortly after the user stops typing.
        saveProfileJob?.cancel()
        saveProfileJob = viewModelScope.launch {
            delay(400)
            repo.saveProfile(_state.value.profile)
        }
    }

    // ---------- Documents ----------

    fun attachDocument(docId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val (file, originalName) = repo.copyDocument(uri)
                val previous = _state.value.documents.firstOrNull { it.id == docId }?.file
                updateDocuments { docs ->
                    docs.map { if (it.id == docId) it.copy(file = file, originalName = originalName) else it }
                }
                previous?.let { repo.deleteDocumentFile(it) }
            } catch (e: Exception) {
                showMessage("Não foi possível anexar o ficheiro: ${e.message}")
            }
        }
    }

    fun removeAttachment(docId: String) {
        val previous = _state.value.documents.firstOrNull { it.id == docId }?.file ?: return
        updateDocuments { docs -> docs.map { if (it.id == docId) it.copy(file = null, originalName = null) else it } }
        viewModelScope.launch { repo.deleteDocumentFile(previous) }
    }

    fun addDocument(name: String) {
        if (name.isBlank()) return
        updateDocuments { it + Document("doc-" + UUID.randomUUID(), name.trim(), custom = true) }
    }

    fun deleteDocument(docId: String) {
        val doc = _state.value.documents.firstOrNull { it.id == docId } ?: return
        updateDocuments { docs -> docs.filterNot { it.id == docId } }
        doc.file?.let { viewModelScope.launch { repo.deleteDocumentFile(it) } }
    }

    private fun updateDocuments(f: (List<Document>) -> List<Document>) {
        _state.update { it.copy(documents = f(it.documents)) }
        viewModelScope.launch { repo.saveDocuments(_state.value.documents) }
    }

    fun documentFile(doc: Document) = doc.file?.let { repo.documentFile(it) }

    // ---------- Letter ----------

    fun editLetter(text: String) {
        // If the text goes back to matching the generated one, it no longer counts as hand-edited.
        val custom = text.takeIf { it != _state.value.generatedLetter }
        _state.update { it.copy(customLetter = custom) }
        saveLetterJob?.cancel()
        saveLetterJob = viewModelScope.launch {
            delay(400)
            repo.saveCustomLetter(custom)
        }
    }

    fun resetLetter() {
        saveLetterJob?.cancel()
        _state.update { it.copy(customLetter = null) }
        viewModelScope.launch { repo.saveCustomLetter(null) }
    }

    /** Email attachments: the resident's documents + (optionally) the letter as PDF. */
    suspend fun prepareAttachments(text: String, includePdf: Boolean): List<Uri> {
        val app = getApplication<Application>()
        val e = _state.value
        val uris = e.attachedDocuments.mapNotNull { doc ->
            documentFile(doc)?.takeIf { it.exists() }?.let { Sender.shareableUri(app, it) }
        }
        if (!includePdf) return uris
        val pdf = Sender.generatePdf(
            app,
            LetterGenerator.subject(e.profile),
            text,
            Sender.pdfFileName(e.profile[ProfileFields.NAME].orEmpty()),
        )
        return listOf(Sender.shareableUri(app, pdf)) + uris
    }

    fun attachmentsSizeBytes(): Long =
        _state.value.attachedDocuments.sumOf { documentFile(it)?.length() ?: 0L }

    // ---------- Follow-up ----------

    fun recordSent(homeIds: Collection<String>) {
        val now = System.currentTimeMillis()
        val existingById = _state.value.contacts.associateBy { it.homeId }.toMutableMap()
        homeIds.forEach { id ->
            val existing = existingById[id]
            existingById[id] = existing?.copy(sentAt = now, updatedAt = now,
                status = if (existing.status == ContactStatus.NO_REPLY) ContactStatus.SENT else existing.status)
                ?: Contact(id, ContactStatus.SENT, now, now)
        }
        saveContacts(existingById.values.toList())
    }

    fun updateContact(contact: Contact) {
        val c = contact.copy(updatedAt = System.currentTimeMillis())
        saveContacts(_state.value.contacts.filterNot { it.homeId == c.homeId } + c)
    }

    fun deleteContact(homeId: String) = saveContacts(_state.value.contacts.filterNot { it.homeId == homeId })

    private val _replyPrompt = MutableStateFlow<ReplyPrompt?>(null)
    val replyPrompt: StateFlow<ReplyPrompt?> = _replyPrompt.asStateFlow()

    /** Set by a reminder notification; the UI switches to "Respostas" and clears it. */
    private val _openReplies = MutableStateFlow(false)
    val openReplies: StateFlow<Boolean> = _openReplies.asStateFlow()

    private var pendingCallHomeId: String? = null

    fun requestRepliesTab() { _openReplies.value = true }
    fun repliesTabOpened() { _openReplies.value = false }

    /** Text shared into Avus (e.g. a home's email): guess the home and ask how to record it. */
    fun receiveSharedReply(text: String, subject: String?) {
        viewModelScope.launch {
            val s = _state.first { it.isLoaded }
            val home = FollowUp.matchHome(text, subject, s.homes, s.contacts.map { it.homeId }.toSet())
            val body = listOfNotNull(subject?.takeIf { it.isNotBlank() }, text.trim()).joinToString("\n").take(1500)
            _replyPrompt.value = ReplyPrompt(ReplyPrompt.Kind.SHARED, home?.id, body)
        }
    }

    /** Called when the user starts a call to a home; on return the app asks how it went. */
    fun callStarted(homeId: String) {
        if (_state.value.contacts.any { it.homeId == homeId }) pendingCallHomeId = homeId
    }

    fun onResume() {
        val id = pendingCallHomeId ?: return
        pendingCallHomeId = null
        if (_replyPrompt.value == null) _replyPrompt.value = ReplyPrompt(ReplyPrompt.Kind.CALL, id)
    }

    fun dismissReplyPrompt() { _replyPrompt.value = null }

    /** Records a reply: new status and a dated note; creates the contact if the home was not contacted yet. */
    fun saveReply(homeId: String, status: ContactStatus, note: String) {
        val now = System.currentTimeMillis()
        val existing = _state.value.contacts.firstOrNull { it.homeId == homeId }
        val contact = (existing ?: Contact(homeId, status, now, now)).let {
            it.copy(status = status, notes = FollowUp.appendNote(it.notes, note, formatDate(now).substring(0, 5)))
        }
        updateContact(contact)
        _replyPrompt.value = null
        val name = _state.value.homes.firstOrNull { it.id == homeId }?.name ?: "lar"
        showMessage("Resposta registada: $name")
        requestRepliesTab()
    }

    private fun saveContacts(list: List<Contact>) {
        _state.update { it.copy(contacts = list) }
        viewModelScope.launch { repo.saveContacts(list) }
    }
}
