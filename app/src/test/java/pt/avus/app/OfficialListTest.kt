package pt.avus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import pt.avus.app.data.CareHome
import pt.avus.app.data.CsvImporter
import pt.avus.app.data.OfficialList
import pt.avus.app.data.OfficialList.State
import java.io.File

class OfficialListTest {

    private fun home(id: String, name: String = id, email: String = "", official: Boolean = false, manual: Boolean = false) =
        CareHome(id, name, email = email, official = official, manual = manual)

    @Test fun mergeReplacesOfficialKeepsUserHomesAndUserEmails() {
        val current = listOf(
            home("cs-1", "Lar A", email = "added@by.user", official = true),
            home("cs-2", "Lar B", official = true),                 // closed, not referenced -> dropped
            home("cs-3", "Lar C", official = true),                 // closed but contacted -> kept
            home("manual-1", "Lar Manual", manual = true),
            home("abc", "Lar Importado"),                            // CSV import from an older version -> dropped
            home("def", "Lar Importado e contactado"),               // ... unless contacted -> kept
        )
        val fresh = listOf(home("cs-1", "Lar A (novo nome)"), home("cs-4", "Lar D", email = "d@d.pt"))
        val merged = OfficialList.merge(current, fresh, keepIds = setOf("cs-3", "def"))

        assertEquals(listOf("cs-1", "cs-3", "cs-4", "def", "manual-1").sorted(), merged.map { it.id }.sorted())
        val a = merged.first { it.id == "cs-1" }
        assertEquals("Lar A (novo nome)", a.name)
        assertEquals("added@by.user", a.email)
        assertTrue(a.official)
        assertTrue(merged.first { it.id == "cs-4" }.official)
        assertFalse(merged.first { it.id == "def" }.official)
    }

    @Test fun userCorrectionsSurviveAListUpdate() {
        val original = CareHome("cs-9", "Lar Z", phone = "210000000", email = "geral@z.pt", official = true)
        val corrected = original.copy(phone = "912345678").markEditsComparedTo(original)
        assertEquals(setOf("phone"), corrected.userEdited)

        val newList = listOf(CareHome("cs-9", "Lar Z (novo nome)", phone = "219999999", email = "novo@z.pt"))
        val merged = OfficialList.merge(listOf(corrected), newList, emptySet()).single()
        assertEquals("912345678", merged.phone)          // the user's correction wins
        assertEquals("Lar Z (novo nome)", merged.name)   // everything else updates
        assertEquals("novo@z.pt", merged.email)
        assertEquals(setOf("phone"), merged.userEdited)
    }

    @Test fun installsOnFirstLaunchAndNewerLists() {
        val bundled = OfficialList.Meta("2026-11-06", 2627)
        assertTrue(OfficialList.shouldInstall(null, bundled))
        assertTrue(OfficialList.shouldInstall(State(removed = false, date = "2026-05-01"), bundled))
        assertFalse(OfficialList.shouldInstall(State(removed = false, date = "2026-11-06"), bundled))
        // Users cannot remove the list any more; a "removed" state from an older version is reinstalled.
        assertTrue(OfficialList.shouldInstall(State(removed = true, date = "2026-05-01"), bundled))
        // Same collection date, regenerated file (e.g. coordinates added): refresh; old state strings still parse.
        val regenerated = OfficialList.Meta("2026-11-06", 2627, revision = "2026-11-06T14:10:00")
        assertTrue(OfficialList.shouldInstall(State.parse("installed 2026-11-06"), regenerated))
        assertFalse(OfficialList.shouldInstall(State(false, "2026-11-06", "2026-11-06T14:10:00"), regenerated))
    }

    @Test fun stateAndMetaRoundTrip() {
        assertEquals(State(true, "2026-11-06"), State.parse(State(true, "2026-11-06").serialize()))
        assertEquals(null, State.parse("garbage"))
        assertEquals(OfficialList.Meta("2026-11-06", 2627), OfficialList.parseMeta("# x\ndate=2026-11-06\ncount=2627\n"))
    }

    /** The CSV shipped in the app must import completely (one home per Carta Social facility). */
    @Test fun bundledListImportsCompletely() {
        val assets = File("src/main/assets")
        // The list is generated (not in git); skip on checkouts that have not generated it.
        assumeTrue(File(assets, OfficialList.CSV_ASSET).exists() && File(assets, OfficialList.META_ASSET).exists())
        val meta = OfficialList.parseMeta(File(assets, OfficialList.META_ASSET).readText())
        val result = CsvImporter.importBytes(File(assets, OfficialList.CSV_ASSET).readBytes())
        assertEquals(meta.count, result.homes.size)
        assertEquals(18, result.homes.map { it.district }.distinct().size)
        assertTrue(result.homes.all { it.id.startsWith("cs-") && it.cartaSocialId.isNotBlank() })
        assertTrue(result.homes.count { it.hasEmail } > meta.count * 9 / 10)
    }
}
