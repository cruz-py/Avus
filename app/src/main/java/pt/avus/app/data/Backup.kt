package pt.avus.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The family's own data as a single .zip the user saves where they like (no cloud): profile, contact
 * person, documents and their files, follow-up, selection, edited letter, "Perto de", homes added by
 * hand and corrections to official homes. The official list itself is not included (the app ships it).
 *
 * Works on the app's files folder directly, so it can be tested on the JVM.
 */
object Backup {
    private const val MANIFEST = "avus-backup.json"
    private const val FORMAT = 1

    /** Plain files copied as they are. */
    private val FILES = listOf(
        "profile.json", "contacts.json", "documents.json", "removed_documents.json",
        "selection.json", "letter.txt", "distance_reference.txt",
    )
    private const val HOMES = "homes.json"
    private val DOCUMENT_ENTRY = Regex("documents/[0-9a-f\\-]{36}/[^/\\\\]{1,200}")

    class InvalidBackupException(message: String) : Exception(message)

    fun export(dir: File, out: OutputStream, createdAt: Long = System.currentTimeMillis()) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            put(MANIFEST, JSONObject().put("format", FORMAT).put("createdAt", createdAt).toString().toByteArray())
            FILES.map { File(dir, it) }.filter { it.isFile }.forEach { put(it.name, it.readBytes()) }

            // Only the homes the user added or corrected: the official list comes with the app.
            File(dir, HOMES).takeIf { it.isFile }?.let { f ->
                val own = readHomes(f.readText()).filter { !it.official || it.userEdited.isNotEmpty() }
                put(HOMES, JSONArray(own.map { it.toJson() }).toString().toByteArray())
            }

            val docsDir = File(dir, "documents")
            docsDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val name = "documents/" + f.relativeTo(docsDir).invariantSeparatorsPath
                if (DOCUMENT_ENTRY.matches(name)) {
                    zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
    }

    /**
     * Replaces the family's data in [dir] with the backup. Homes: the phone's official list is kept,
     * with the backup's corrections applied to it, plus the backup's own homes.
     */
    fun import(dir: File, input: InputStream) {
        val staging = File(dir, "backup-import.tmp").apply { deleteRecursively(); mkdirs() }
        try {
            var manifestOk = false
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val name = entry.name
                    val allowed = name == MANIFEST || name == HOMES || name in FILES || DOCUMENT_ENTRY.matches(name)
                    if (entry.isDirectory || !allowed) return@forEach
                    val target = File(staging, name)
                    // Defence in depth against "../" tricks (the patterns above already exclude them).
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) return@forEach
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    if (name == MANIFEST) {
                        manifestOk = runCatching { JSONObject(target.readText()).getInt("format") <= FORMAT }.getOrDefault(false)
                    }
                }
            }
            if (!manifestOk) throw InvalidBackupException("O ficheiro não é uma cópia de segurança do Avus.")

            // Plain files: the backup's version, or nothing if the backup did not have it.
            FILES.forEach { name ->
                val from = File(staging, name)
                val to = File(dir, name)
                if (from.isFile) from.copyTo(to, overwrite = true) else to.delete()
            }

            // Documents folder replaced by the backup's.
            val docs = File(dir, "documents")
            docs.deleteRecursively()
            File(staging, "documents").takeIf { it.isDirectory }?.copyRecursively(docs, overwrite = true)

            val current = File(dir, HOMES).takeIf { it.isFile }?.let { readHomes(it.readText()) }.orEmpty()
            val fromBackup = File(staging, HOMES).takeIf { it.isFile }?.let { readHomes(it.readText()) }.orEmpty()
            File(dir, HOMES).writeText(JSONArray(mergeHomes(current, fromBackup).map { it.toJson() }).toString())
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Phone's official homes (with the backup's corrections) + the backup's non-official homes. */
    fun mergeHomes(current: List<CareHome>, fromBackup: List<CareHome>): List<CareHome> {
        val backupById = fromBackup.associateBy { it.id }
        val official = current.filter { it.official }.map { home ->
            backupById[home.id]?.let { home.withUserEditsFrom(it) } ?: home
        }
        val officialIds = official.map { it.id }.toSet()
        val own = fromBackup.filter { it.id !in officialIds && (!it.official || it.userEdited.isNotEmpty()) }
        return (official + own).sortedBy { normalizeForSearch(it.name) }
    }

    private fun readHomes(text: String): List<CareHome> =
        runCatching { JSONArray(text).mapObjects { CareHome.fromJson(it) } }.getOrDefault(emptyList())
}
