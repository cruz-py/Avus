package pt.avus.app

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.avus.app.data.CareHome
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.OfficialList
import pt.avus.app.data.ProfileFields
import pt.avus.app.data.ThemeMode
import pt.avus.app.ui.AvusViewModel
import java.io.File

/** The ViewModel against a real Application, files folder and bundled list (Robolectric, API 36). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AvusViewModelTest {

    private lateinit var app: Application
    private val files get() = app.filesDir

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    /** Runs the main looper until [condition] holds (work also happens on IO threads). */
    private fun waitFor(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        error("Timed out waiting for: $what")
    }

    private fun loadedViewModel(): AvusViewModel =
        AvusViewModel(app).also { vm -> waitFor("load") { vm.state.value.isLoaded && !vm.state.value.isImporting } }

    private fun bundledListAvailable() = runCatching { app.assets.open(OfficialList.CSV_ASSET).close() }.isSuccess

    @Test fun firstLaunchInstallsTheListAndKeepsHomesAddedByHand() {
        assumeTrue("the list is generated, not in git", bundledListAvailable())
        File(files, "homes.json").writeText(JSONArray(listOf(CareHome("manual-1", "Lar à mão", manual = true).toJson())).toString())

        val vm = loadedViewModel()
        waitFor("official list") { vm.state.value.officialListDate != null }

        val homes = vm.state.value.homes
        val expected = OfficialList.parseMeta(app.assets.open(OfficialList.META_ASSET).reader().readText()).count
        assertEquals(expected, homes.count { it.official })
        assertTrue(homes.any { it.id == "manual-1" })
    }

    @Test fun rapidSelectionChangesSurviveARestart() {
        val vm = loadedViewModel()
        val ids = (1..60).map { "cs-$it" }
        ids.forEach { vm.toggleSelection(it) }                 // select 60
        ids.filter { it.removePrefix("cs-").toInt() % 2 == 0 }.forEach { vm.toggleSelection(it) } // unselect evens
        val expected = ids.filter { it.removePrefix("cs-").toInt() % 2 == 1 }.toSet()
        assertEquals(expected, vm.state.value.selectedIds)

        // "Restart": a new ViewModel reads what was saved.
        waitFor("selection saved") {
            runCatching { JSONArray(File(files, "selection.json").readText()) }.getOrNull()
                ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } == expected
        }
        assertEquals(expected, loadedViewModel().state.value.selectedIds)
    }

    @Test fun recordingAReplyCreatesTheContactWithADatedNote() {
        val vm = loadedViewModel()
        vm.saveReply("cs-42", ContactStatus.PLACE_AVAILABLE, "Vaga a partir de março")
        val contact = vm.state.value.contacts.single { it.homeId == "cs-42" }
        assertEquals(ContactStatus.PLACE_AVAILABLE, contact.status)
        assertTrue(contact.notes.matches(Regex("\\d{2}/\\d{2}: Vaga a partir de março")))
        assertTrue(vm.openReplies.value)
    }

    @Test fun biAsksForTheBiAndProofOfNumbers() {
        val vm = loadedViewModel()
        assertTrue(vm.state.value.visibleDocuments.any { it.id == "resident_id_card" })
        vm.updateField(ProfileFields.ID_DOCUMENT, ProfileFields.ID_BI)
        val ids = vm.state.value.visibleDocuments.map { it.id }
        assertTrue("resident_bi" in ids && "tax_number" in ids && "social_security_number" in ids)
        assertFalse("resident_id_card" in ids)
        assertFalse(vm.state.value.documents.any { it.id == "relative_id_card" })
    }

    @Test fun correctingAnOfficialHomeIsRemembered() {
        assumeTrue("the list is generated, not in git", bundledListAvailable())
        val vm = loadedViewModel()
        waitFor("official list") { vm.state.value.officialListDate != null }
        val home = vm.state.value.homes.first { it.official }
        vm.saveHome(home.copy(phone = "912345678"))
        val saved = vm.state.value.homes.single { it.id == home.id }
        assertEquals("912345678", saved.phone)
        assertEquals(setOf("phone"), saved.userEdited)
        assertTrue(saved.official)
    }

    @Test fun settingsArePersisted() {
        val vm = loadedViewModel()
        vm.updateSettings { it.copy(theme = ThemeMode.DARK, reminders = false) }
        waitFor("settings saved") { File(files, "settings.json").exists() }
        val again = loadedViewModel().state.value.settings
        assertEquals(ThemeMode.DARK, again.theme)
        assertFalse(again.reminders)
    }

    @Test fun deletingMyDataKeepsTheOfficialListAndSettings() {
        assumeTrue("the list is generated, not in git", bundledListAvailable())
        val vm = loadedViewModel()
        waitFor("official list") { vm.state.value.officialListDate != null }
        vm.updateField(ProfileFields.NAME, "Manuel")
        vm.saveReply("cs-42", ContactStatus.REPLIED, "nota")
        vm.saveHome(CareHome("", "Lar à mão"))
        vm.updateSettings { it.copy(theme = ThemeMode.LIGHT) }
        waitFor("saved") { File(files, "contacts.json").exists() && File(files, "settings.json").exists() }

        vm.deleteMyData()
        waitFor("deleted and reloaded") {
            vm.state.value.contacts.isEmpty() && vm.state.value.profile.isEmpty() &&
                vm.state.value.officialListDate != null && !vm.state.value.isImporting &&
                vm.state.value.homes.isNotEmpty()
        }
        assertTrue(vm.state.value.homes.none { it.manual })
        assertEquals(vm.state.value.homes.size, vm.state.value.homes.count { it.official })
        assertEquals(ThemeMode.LIGHT, vm.state.value.settings.theme)
    }
}
