package pt.avus.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import pt.avus.app.letter.Sender
import pt.avus.app.reminders.ReplyReminderWorker
import pt.avus.app.letter.LetterGenerator
import pt.avus.app.data.Contact
import pt.avus.app.data.DefaultDocuments
import pt.avus.app.data.Document
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.CsvImporter
import pt.avus.app.data.AppSettings
import pt.avus.app.data.CareHome
import pt.avus.app.data.DistanceReference
import pt.avus.app.data.FollowUp
import pt.avus.app.data.OfficialList
import pt.avus.app.data.Profile
import pt.avus.app.data.Repository
import pt.avus.app.data.normalizeForSearch
import pt.avus.app.data.updateSuspending
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
    /** Sort homes by distance from this place (null = alphabetical). */
    val distanceReference: DistanceReference? = null,
    /** Total size of the attached documents (kept up to date in the background). */
    val attachmentsSizeBytes: Long = 0,
    val settings: AppSettings = AppSettings(),
) {
    val selectedHomes: List<CareHome> get() = homes.filter { it.id in selectedIds }
    /** Documents that apply to this resident (e.g. CC vs BI). */
    val visibleDocuments: List<Document> get() = documents.filter { DefaultDocuments.appliesTo(it.id, profile) }
    val attachedDocuments: List<Document> get() = visibleDocuments.filter { it.isAttached }

    val generatedLetter: String
        get() = LetterGenerator.generate(profile, attachedDocuments, visibleDocuments.filterNot { it.isAttached })

    val letter: String get() = customLetter?.let { LetterGenerator.refreshDate(it) } ?: generatedLetter
}

class AvusViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Short messages to show in a Snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Search index for the Lares list, kept here so it survives tab switches: the screen leaves the
     * composition when another tab is shown, and rebuilding it for ~2,600 homes made returning slow.
     */
    private var homeIndexCache: Pair<List<CareHome>, Map<String, HomeIndex>>? = null

    fun homeIndex(homes: List<CareHome>): Map<String, HomeIndex> =
        homeIndexCache?.takeIf { it.first === homes }?.second
            ?: buildHomeIndex(homes).also { homeIndexCache = homes to it }

    private var saveProfileJob: Job? = null
    private var saveLetterJob: Job? = null

    init {
        viewModelScope.launch {
            repo.migrateLegacyStorage()
            load()
            refreshAttachmentsSize()
            installOfficialList()
        }
        ReplyReminderWorker.schedule(app)
    }

    /** (Re)reads everything from storage. */
    private suspend fun load() {
        _state.value = AppState(
            isLoaded = true,
            homes = repo.loadHomes().sortedBy { normalizeForSearch(it.name) },
            selectedIds = repo.loadSelection(),
            profile = repo.loadProfile(),
            documents = repo.loadDocuments(),
            contacts = repo.loadContacts(),
            customLetter = repo.loadCustomLetter(),
            officialListDate = repo.loadOfficialListState()?.takeUnless { it.removed }?.date,
            officialListAvailable = repo.hasAsset(OfficialList.CSV_ASSET) && repo.hasAsset(OfficialList.META_ASSET),
            distanceReference = repo.loadDistanceReference(),
            settings = repo.loadSettings(),
        )
    }

    // ---------- Settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
        viewModelScope.launch { repo.saveSettings(_state.value.settings) }
    }

    /** Deletes the family's data from this phone (see [Repository.deleteUserData]); the official list is reinstalled. */
    fun deleteMyData() {
        viewModelScope.launch {
            saveProfileJob?.cancel()
            saveLetterJob?.cancel()
            repo.deleteUserData()
            load()
            refreshAttachmentsSize()
            installOfficialList()
            showMessage("Os seus dados foram apagados deste telemóvel.")
        }
    }

    // ---------- Backup of the family's own data ----------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                saveProfileJob?.join()
                saveLetterJob?.join()
                repo.exportBackup(uri)
                showMessage("Dados exportados. O ficheiro contém documentos pessoais: guarde-o num local seguro.")
            } catch (e: Exception) {
                showMessage("Não foi possível exportar: ${e.message}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                repo.importBackup(uri)
                load()
                refreshAttachmentsSize()
                showMessage("Dados importados.")
            } catch (e: Exception) {
                showMessage(e.message ?: "Não foi possível importar o ficheiro.")
            }
        }
    }

    fun messageShown() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }

    // ---------- Care homes ----------

    fun saveHome(home: CareHome) {
        val before = _state.value.homes.firstOrNull { it.id == home.id }
        val newValue = when {
            home.id.isBlank() -> home.copy(id = "manual-" + UUID.randomUUID(), manual = true)
            // Remember what the user corrected in an official home, so list updates keep it.
            before != null && before.official -> home.markEditsComparedTo(before)
            else -> home
        }
        val list = (_state.value.homes.filterNot { it.id == newValue.id } + newValue).sortedBy { normalizeForSearch(it.name) }
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

    /** Loads the official ERPI list bundled with the app (first launch, or a newer list in an app update). */
    private suspend fun installOfficialList() {
        if (!_state.value.officialListAvailable) return // build without the generated list
        val meta = OfficialList.parseMeta(repo.readAsset(OfficialList.META_ASSET).decodeToString())
        if (!OfficialList.shouldInstall(repo.loadOfficialListState(), meta)) return
        _state.update { it.copy(isImporting = true) }
        try {
            val official = withContext(Dispatchers.Default) {
                CsvImporter.importBytes(repo.readAsset(OfficialList.CSV_ASSET)).homes
            }
            // Merge against the current state; if the user changed something meanwhile (e.g. added a
            // home while the list was loading), merge again rather than overwrite their change.
            val updated = _state.updateSuspending { s ->
                val keep = s.selectedIds + s.contacts.map { it.homeId }
                val merged = withContext(Dispatchers.Default) { OfficialList.merge(s.homes, official, keep) }
                s.copy(homes = merged, officialListDate = meta.date)
            }
            repo.saveHomes(updated.homes)
            repo.saveOfficialListState(OfficialList.State(removed = false, date = meta.date, revision = meta.revision))
        } catch (e: Exception) {
            showMessage("Não foi possível carregar a lista oficial: ${e.message}")
        } finally {
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun setDistanceReference(reference: DistanceReference?) {
        _state.update { it.copy(distanceReference = reference) }
        viewModelScope.launch { repo.saveDistanceReference(reference) }
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
        // CC vs BI changes which documents apply, and so what gets attached.
        if (key == ProfileFields.ID_DOCUMENT) refreshAttachmentsSize()
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
        refreshAttachmentsSize()
    }

    private fun refreshAttachmentsSize() {
        val docs = _state.value.attachedDocuments
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { docs.sumOf { documentFile(it)?.length() ?: 0L } }
            _state.update { it.copy(attachmentsSizeBytes = size) }
        }
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
