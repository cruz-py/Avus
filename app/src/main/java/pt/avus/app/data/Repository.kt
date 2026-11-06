package pt.avus.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
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

    private val store = FileStore(context.filesDir)
    private val dir get() = store.dir
    private val documentsDir get() = File(dir, "documents").apply { mkdirs() }

    /** Converts data saved by older versions; call once before loading anything. */
    suspend fun migrateLegacyStorage() = store.exclusive { LegacyStorageMigration.run(dir) }

    suspend fun loadHomes(): List<CareHome> = readArray("homes.json") { CareHome.fromJson(it) }
    suspend fun saveHomes(homes: List<CareHome>) = writeArray("homes.json", homes.map { it.toJson() })

    suspend fun loadContacts(): List<Contact> = readArray("contacts.json") { Contact.fromJson(it) }
    suspend fun saveContacts(c: List<Contact>) = writeArray("contacts.json", c.map { it.toJson() })

    suspend fun loadDocuments(): List<Document> {
        val saved = readArray("documents.json") { Document.fromJson(it) }
        if (saved.isEmpty()) return DefaultDocuments.list
        val savedById = saved.associateBy { it.id }
        val removed = removedDocumentIds()
        // Default documents in the app's order and with its current wording, keeping the attached files;
        // new defaults (e.g. after an app update) are added unless the user removed them.
        val defaults = DefaultDocuments.list.mapNotNull { d ->
            val s = savedById[d.id]
            when {
                s != null -> d.copy(file = s.file, originalName = s.originalName)
                d.id in removed -> null
                else -> d
            }
        }
        val defaultIds = DefaultDocuments.list.map { it.id }.toSet()
        // Retired defaults disappear, together with any file the user had attached to them.
        saved.filter { it.id in DefaultDocuments.retiredIds }.forEach { d -> d.file?.let { deleteDocumentFile(it) } }
        return defaults + saved.filter { it.id !in defaultIds && it.id !in DefaultDocuments.retiredIds }
    }

    suspend fun saveDocuments(d: List<Document>) {
        writeArray("documents.json", d.map { it.toJson() })
        val removed = DefaultDocuments.list.map { it.id } - d.map { it.id }.toSet()
        writeFile("removed_documents.json", JSONArray(removed).toString())
    }

    private suspend fun removedDocumentIds(): Set<String> {
        val text = store.read("removed_documents.json") ?: return emptySet()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptySet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
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

    /** homeId -> the contact's updatedAt it was last reminded about. */
    suspend fun loadReminded(): Map<String, Long> = withContext(Dispatchers.IO) {
        val f = File(dir, "reminders.json")
        if (!f.exists()) return@withContext emptyMap()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@withContext emptyMap()
        o.keys().asSequence().associateWith { o.optLong(it) }
    }

    suspend fun saveReminded(reminded: Map<String, Long>) = writeFile("reminders.json", JSONObject(reminded).toString())

    suspend fun loadDistanceReference(): DistanceReference? = withContext(Dispatchers.IO) {
        DistanceReference.parse(File(dir, "distance_reference.txt").takeIf { it.exists() }?.readText())
    }

    suspend fun saveDistanceReference(reference: DistanceReference?) {
        if (reference == null) store.delete("distance_reference.txt") else writeFile("distance_reference.txt", reference.serialize())
    }

    /** Writes the family's data to [uri] as a .zip (see [Backup]); runs after any pending save. */
    suspend fun exportBackup(uri: Uri) = store.exclusive {
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            Backup.export(dir, requireNotNull(out) { "Não foi possível criar o ficheiro." })
        }
    }

    /** Replaces the family's data with the backup in [uri] (see [Backup]). */
    suspend fun importBackup(uri: Uri) = store.exclusive {
        context.contentResolver.openInputStream(uri).use { input ->
            Backup.import(dir, requireNotNull(input) { "Não foi possível abrir o ficheiro." })
        }
    }

    fun hasAsset(name: String): Boolean = context.assets.list("")?.contains(name) == true

    suspend fun readAsset(name: String): ByteArray = withContext(Dispatchers.IO) {
        context.assets.open(name).use { it.readBytes() }
    }

    suspend fun loadCustomLetter(): String? = withContext(Dispatchers.IO) {
        File(dir, "letter.txt").takeIf { it.exists() }?.readText()
    }

    suspend fun saveCustomLetter(text: String?) {
        if (text == null) store.delete("letter.txt") else writeFile("letter.txt", text)
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

    private suspend fun <T> readArray(name: String, f: (JSONObject) -> T): List<T> = withContext(Dispatchers.IO) {
        val file = File(dir, name)
        if (!file.exists()) return@withContext emptyList()
        runCatching { JSONArray(file.readText()).mapObjects(f) }.getOrDefault(emptyList())
    }

    private suspend fun writeArray(name: String, objects: List<JSONObject>) =
        writeFile(name, JSONArray(objects).toString())

    /** Ordered, atomic write (see [FileStore]). */
    private suspend fun writeFile(name: String, text: String) = store.write(name, text)
}
