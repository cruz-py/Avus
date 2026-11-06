package pt.avus.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Stores everything as JSON files in the app's private storage.
 * Nothing leaves the phone except when the user sends the email.
 */
class Repository(private val context: Context) {

    private val dir get() = context.filesDir
    private val writeLock = Mutex()
    private val documentsDir get() = File(dir, "documents").apply { mkdirs() }

    /** Converts data saved by older versions; call once before loading anything. */
    suspend fun migrateLegacyStorage() = withContext(Dispatchers.IO) {
        writeLock.withLock { LegacyStorageMigration.run(dir) }
    }

    suspend fun loadHomes(): List<CareHome> = readArray("homes.json") { CareHome.fromJson(it) }
    suspend fun saveHomes(homes: List<CareHome>) = writeArray("homes.json", homes.map { it.toJson() })

    suspend fun loadContacts(): List<Contact> = readArray("contacts.json") { Contact.fromJson(it) }
    suspend fun saveContacts(c: List<Contact>) = writeArray("contacts.json", c.map { it.toJson() })

    suspend fun loadDocuments(): List<Document> {
        val saved = readArray("documents.json") { Document.fromJson(it) }
        if (saved.isEmpty()) return DefaultDocuments.list
        // Add new default documents that are not there yet (e.g. after an app update).
        val ids = saved.map { it.id }.toSet()
        return saved + DefaultDocuments.list.filter { it.id !in ids && !wasRemoved(it.id) }
    }

    suspend fun saveDocuments(d: List<Document>) {
        writeArray("documents.json", d.map { it.toJson() })
        val removed = DefaultDocuments.list.map { it.id } - d.map { it.id }.toSet()
        writeFile("removed_documents.json", JSONArray(removed).toString())
    }

    private fun wasRemoved(id: String): Boolean {
        val f = File(dir, "removed_documents.json")
        if (!f.exists()) return false
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return false
        return (0 until arr.length()).any { arr.getString(it) == id }
    }

    suspend fun loadProfile(): Profile = withContext(Dispatchers.IO) {
        val f = File(dir, "profile.json")
        if (!f.exists()) return@withContext emptyMap()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@withContext emptyMap()
        o.keys().asSequence().associateWith { o.optString(it) }
    }

    suspend fun saveProfile(p: Profile) = writeFile("profile.json", JSONObject(p).toString())

    suspend fun loadSelection(): Set<String> = withContext(Dispatchers.IO) {
        val f = File(dir, "selection.json")
        if (!f.exists()) return@withContext emptySet()
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return@withContext emptySet()
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    suspend fun saveSelection(ids: Set<String>) = writeFile("selection.json", JSONArray(ids.toList()).toString())

    suspend fun loadOfficialListState(): OfficialList.State? = withContext(Dispatchers.IO) {
        OfficialList.State.parse(File(dir, "official_list.txt").takeIf { it.exists() }?.readText())
    }

    suspend fun saveOfficialListState(state: OfficialList.State) = writeFile("official_list.txt", state.serialize())

    suspend fun readAsset(name: String): ByteArray = withContext(Dispatchers.IO) {
        context.assets.open(name).use { it.readBytes() }
    }

    suspend fun loadCustomLetter(): String? = withContext(Dispatchers.IO) {
        File(dir, "letter.txt").takeIf { it.exists() }?.readText()
    }

    suspend fun saveCustomLetter(text: String?) = withContext(Dispatchers.IO) {
        val f = File(dir, "letter.txt")
        if (text == null) f.delete() else f.writeText(text)
    }

    /** Copies the chosen file into private storage (the original may be moved/deleted later). */
    suspend fun copyDocument(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val originalName = fileNameOf(uri) ?: "documento"
        // Each document gets its own subfolder so it keeps its original name:
        // that is the name the care home sees on the email attachment.
        val safeName = originalName.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120)
        val name = UUID.randomUUID().toString() + "/" + safeName
        val target = File(documentsDir, name).apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o ficheiro." }
            target.outputStream().use { input.copyTo(it) }
        }
        name to originalName
    }

    fun documentFile(name: String): File = File(documentsDir, name)

    suspend fun deleteDocumentFile(name: String) = withContext(Dispatchers.IO) {
        val f = documentFile(name)
        f.delete()
        f.parentFile?.takeIf { it != documentsDir }?.delete()
    }

    fun fileNameOf(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    suspend fun readUriBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri).use {
            requireNotNull(it) { "Não foi possível abrir o ficheiro." }.readBytes()
        }
    }

    private suspend fun <T> readArray(name: String, f: (JSONObject) -> T): List<T> = withContext(Dispatchers.IO) {
        val file = File(dir, name)
        if (!file.exists()) return@withContext emptyList()
        runCatching { JSONArray(file.readText()).mapObjects(f) }.getOrDefault(emptyList())
    }

    private suspend fun writeArray(name: String, objects: List<JSONObject>) =
        writeFile(name, JSONArray(objects).toString())

    /** Atomic write: write to a temp file, then replace. */
    private suspend fun writeFile(name: String, text: String) = withContext(Dispatchers.IO) {
        writeLock.withLock { writeAtomically(name, text) }
    }

    private fun writeAtomically(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        val target = File(dir, name)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}
