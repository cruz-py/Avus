package pt.avus.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.avus.app.data.Backup
import pt.avus.app.data.CareHome
import pt.avus.app.data.mapObjects
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun writeHomes(dir: File, homes: List<CareHome>) =
        File(dir, "homes.json").writeText(JSONArray(homes.map { it.toJson() }).toString())

    private fun readHomes(dir: File) = JSONArray(File(dir, "homes.json").readText()).mapObjects { CareHome.fromJson(it) }

    private val doc = "documents/123e4567-e89b-12d3-a456-426614174000/CC frente e verso.pdf"

    @Test fun roundTripBetweenPhones() {
        // Phone A: the family's data, plus the official list with one correction and one home added by hand.
        val a = tmp.newFolder("a")
        File(a, "profile.json").writeText("""{"name":"Manuel"}""")
        File(a, "contacts.json").writeText("""[{"homeId":"cs-1","status":"WAITING_LIST","sentAt":1,"updatedAt":2,"notes":"ligar"}]""")
        File(a, "letter.txt").writeText("Carta editada")
        File(a, doc).apply { parentFile.mkdirs() }.writeText("%PDF")
        writeHomes(a, listOf(
            CareHome("cs-1", "Lar A", phone = "912345678", official = true, userEdited = setOf("phone")),
            CareHome("cs-2", "Lar B", official = true),
            CareHome("manual-1", "Lar à mão", manual = true),
        ))
        val zip = ByteArrayOutputStream().also { Backup.export(a, it) }.toByteArray()

        // The official list is not in the backup: only the corrected and hand-added homes.
        val names = ZipInputStream(ByteArrayInputStream(zip)).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertTrue(doc in names)
        val exported = JSONArray(
            ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                generateSequence { z.nextEntry }.first { it.name == "homes.json" }; z.readBytes().decodeToString()
            }
        ).length()
        assertEquals(2, exported)

        // Phone B: its own (newer) official list and some old data that must be replaced.
        val b = tmp.newFolder("b")
        File(b, "letter.txt").writeText("antiga")
        File(b, "selection.json").writeText("""["cs-9"]""")
        File(b, "documents/old/x.pdf").apply { parentFile.mkdirs() }.writeText("old")
        writeHomes(b, listOf(
            CareHome("cs-1", "Lar A (novo nome)", phone = "210000000", official = true),
            CareHome("cs-2", "Lar B", official = true),
            CareHome("cs-3", "Lar C", official = true),
        ))
        Backup.import(b, ByteArrayInputStream(zip))

        assertEquals("""{"name":"Manuel"}""", File(b, "profile.json").readText())
        assertEquals("Carta editada", File(b, "letter.txt").readText())
        assertFalse(File(b, "selection.json").exists())                 // not in the backup -> cleared
        assertEquals("%PDF", File(b, doc).readText())
        assertFalse(File(b, "documents/old/x.pdf").exists())
        val homes = readHomes(b).associateBy { it.id }
        assertEquals(setOf("cs-1", "cs-2", "cs-3", "manual-1"), homes.keys)
        assertEquals("Lar A (novo nome)", homes.getValue("cs-1").name)   // B's list
        assertEquals("912345678", homes.getValue("cs-1").phone)          // A's correction
        assertFalse(File(b, "backup-import.tmp").exists())
    }

    @Test fun ignoresUnknownAndEscapingEntries() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
                put("avus-backup.json", """{"format":1}""")
                put("../evil.txt", "x")
                put("documents/../../evil2.txt", "x")
                put("shared_prefs/x.xml", "x")
                put("profile.json", """{"name":"Ana"}""")
            }
        }.toByteArray()
        val root = tmp.newFolder("root"); val dir = File(root, "files").apply { mkdirs() }
        Backup.import(dir, ByteArrayInputStream(zip))
        assertEquals("""{"name":"Ana"}""", File(dir, "profile.json").readText())
        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(root, "evil2.txt").exists())
        assertFalse(File(dir, "shared_prefs").exists())
    }

    @Test(expected = Backup.InvalidBackupException::class)
    fun rejectsFilesThatAreNotAvusBackups() {
        val dir = tmp.newFolder("c")
        File(dir, "profile.json").writeText("""{"name":"keep"}""")
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("profile.json")); z.write("{}".toByteArray()); z.closeEntry() }
        }.toByteArray()
        try {
            Backup.import(dir, ByteArrayInputStream(zip))
        } finally {
            assertEquals("""{"name":"keep"}""", File(dir, "profile.json").readText())   // nothing replaced
        }
    }
}
