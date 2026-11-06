package pt.avus.app

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.avus.app.data.FileStore
import java.util.concurrent.Executors

class FileStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    /**
     * Saves are started one after another from a single thread, like the UI does (each launch runs
     * until its first suspension). The file must end with the LAST value, every time.
     */
    @Test fun lastWriteWinsWhenSavesComeInQuickSuccession() = runBlocking {
        val ui = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val store = FileStore(tmp.root)
        repeat(20) { round ->
            withContext(ui) {
                (1..500).map { i ->
                    launch(start = CoroutineStart.UNDISPATCHED) { store.write("selection.json", "$round-$i") }
                }.joinAll()
            }
            assertEquals("$round-500", store.read("selection.json"))
        }
        ui.close()
    }

    @Test fun deleteIsOrderedWithWrites() = runBlocking {
        val ui = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val store = FileStore(tmp.root)
        withContext(ui) {
            listOf(
                launch(start = CoroutineStart.UNDISPATCHED) { store.write("letter.txt", "texto") },
                launch(start = CoroutineStart.UNDISPATCHED) { store.delete("letter.txt") },
            ).joinAll()
        }
        assertEquals(null, store.read("letter.txt"))
        ui.close()
    }
}
