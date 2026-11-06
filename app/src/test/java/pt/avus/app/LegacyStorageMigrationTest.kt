package pt.avus.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.avus.app.data.CareHome
import pt.avus.app.data.Contact
import pt.avus.app.data.ContactStatus
import pt.avus.app.data.Document
import pt.avus.app.data.LegacyStorageMigration
import pt.avus.app.data.ProfileFields
import java.io.File

class LegacyStorageMigrationTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun write(dir: File, name: String, text: String) = File(dir, name).writeText(text)

    @Test fun migratesFirstVersionData() {
        val dir = tmp.root
        write(dir, "lares.json", """[{"id":"abc","nome":"Lar A","entidade":"SCM","natureza":"IPSS","morada":"Rua X",
            "codigoPostal":"1000-001","localidade":"Lisboa","freguesia":"Arroios","concelho":"Lisboa","distrito":"Lisboa",
            "telefone":"213456789","email":"a@a.pt","capacidade":"40","manual":false}]""")
        write(dir, "contactos.json", """[{"larId":"abc","estado":"LISTA_ESPERA","dataEnvio":10,"dataAtualizacao":20,"notas":"ligar"}]""")
        write(dir, "documentos.json", """[{"id":"cc_utente","nome":"Cartão","descricao":"","ficheiro":"u1/cc.pdf",
            "nomeOriginal":"cc.pdf","personalizado":false}]""")
        write(dir, "documentos_removidos.json", """["foto","irs"]""")
        write(dir, "perfil.json", """{"nome":"Manuel","respTelefone":"912345678","sexo":"Masculino"}""")
        write(dir, "selecao.json", """["abc"]""")
        write(dir, "carta.txt", "Exmos. Senhores,")
        File(dir, "documentos/u1").mkdirs()
        write(dir, "documentos/u1/cc.pdf", "%PDF")

        LegacyStorageMigration.run(dir)

        val home = CareHome.fromJson(JSONArray(File(dir, "homes.json").readText()).getJSONObject(0))
        assertEquals(CareHome("abc", "Lar A", "SCM", "IPSS", "Rua X", "1000-001", "Lisboa", "Arroios", "Lisboa",
            "Lisboa", "213456789", "a@a.pt", "40", false), home)

        val contact = Contact.fromJson(JSONArray(File(dir, "contacts.json").readText()).getJSONObject(0))
        assertEquals(Contact("abc", ContactStatus.WAITING_LIST, 10, 20, "ligar"), contact)

        val doc = Document.fromJson(JSONArray(File(dir, "documents.json").readText()).getJSONObject(0))
        assertEquals(Document("resident_id_card", "Cartão", "", "u1/cc.pdf", "cc.pdf", false), doc)
        assertTrue(File(dir, "documents/u1/cc.pdf").exists())

        assertEquals("""["photo","tax_return"]""", File(dir, "removed_documents.json").readText())
        val profile = JSONObject(File(dir, "profile.json").readText())
        assertEquals("Manuel", profile.getString(ProfileFields.NAME))
        assertEquals("912345678", profile.getString(ProfileFields.RELATIVE_PHONE))
        assertEquals(ProfileFields.MALE, profile.getString(ProfileFields.SEX))
        assertEquals("""["abc"]""", File(dir, "selection.json").readText())
        assertEquals("Exmos. Senhores,", File(dir, "letter.txt").readText())

        listOf("lares.json", "contactos.json", "documentos.json", "documentos_removidos.json", "perfil.json",
            "selecao.json", "carta.txt", "documentos").forEach { assertFalse(it, File(dir, it).exists()) }
    }

    @Test fun doesNothingWithoutOldFilesAndNeverOverwritesNewOnes() {
        val dir = tmp.root
        write(dir, "homes.json", "[]")
        write(dir, "lares.json", """[{"id":"old","nome":"Old"}]""")
        LegacyStorageMigration.run(dir)
        assertEquals("[]", File(dir, "homes.json").readText())
        assertFalse(File(dir, "lares.json").exists())
        LegacyStorageMigration.run(dir) // second run is a no-op
        assertEquals("[]", File(dir, "homes.json").readText())
    }

    @Test fun keepsUnreadableOldFiles() {
        val dir = tmp.root
        write(dir, "lares.json", "not json")
        LegacyStorageMigration.run(dir)
        assertTrue(File(dir, "lares.json").exists())
        assertFalse(File(dir, "homes.json").exists())
    }
}
