package pt.avus.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small text-file store in the app's private folder.
 *
 * Writes go through one shared single-lane dispatcher, so they hit the disk in the order they were
 * requested: a quick succession of saves (e.g. tapping two homes) can never leave an older state on
 * disk. Each write is atomic (temp file, then rename).
 */
class FileStore(val dir: File, private val writer: CoroutineDispatcher = sharedWriter) {

    fun file(name: String) = File(dir, name)

    suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        file(name).takeIf { it.exists() }?.readText()
    }

    suspend fun write(name: String, text: String) = withContext(writer) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        val target = File(dir, name)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    suspend fun delete(name: String) = withContext(writer) { file(name).delete() }

    /** Runs [block] on the write lane, after every write requested before it. */
    suspend fun <T> exclusive(block: () -> T): T = withContext(writer) { block() }

    companion object {
        /** One lane for the whole app (the UI and the reminder worker write to the same files). */
        val sharedWriter: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
