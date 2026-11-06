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
            home("abc", "Lar Importado"),                            // from a user CSV -> kept
        )
        val fresh = listOf(home("cs-1", "Lar A (novo nome)"), home("cs-4", "Lar D", email = "d@d.pt"))
        val merged = OfficialList.merge(current, fresh, keepIds = setOf("cs-3"))

        assertEquals(listOf("cs-1", "cs-3", "cs-4", "abc", "manual-1").sorted(), merged.map { it.id }.sorted())
        val a = merged.first { it.id == "cs-1" }
        assertEquals("Lar A (novo nome)", a.name)
        assertEquals("added@by.user", a.email)
        assertTrue(a.official)
        assertTrue(merged.first { it.id == "cs-4" }.official)
        assertFalse(merged.first { it.id == "abc" }.official)
    }

    @Test fun installsOnFirstLaunchAndNewerListsButNeverAfterRemoval() {
        val bundled = OfficialList.Meta("2026-11-06", 2627)
        assertTrue(OfficialList.shouldInstall(null, bundled))
        assertTrue(OfficialList.shouldInstall(State(removed = false, date = "2026-05-01"), bundled))
        assertFalse(OfficialList.shouldInstall(State(removed = false, date = "2026-11-06"), bundled))
        assertFalse(OfficialList.shouldInstall(State(removed = true, date = "2026-05-01"), bundled))
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
