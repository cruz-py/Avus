package pt.avus.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One-time migration of data saved by the first version of the app, which used Portuguese
 * file names and JSON keys (lares.json, "nome", "dataEnvio"…). Runs before anything is loaded
 * and does nothing once the old files are gone. Can be deleted when no install still has old data.
 */
internal object LegacyStorageMigration {

    private val homeKeys = mapOf(
        "nome" to "name", "entidade" to "owner", "natureza" to "legalForm", "morada" to "address",
        "codigoPostal" to "postalCode", "localidade" to "locality", "freguesia" to "parish",
        "concelho" to "municipality", "distrito" to "district", "telefone" to "phone", "capacidade" to "capacity",
    )
    private val documentKeys = mapOf(
        "nome" to "name", "descricao" to "description", "ficheiro" to "file",
        "nomeOriginal" to "originalName", "personalizado" to "custom",
    )
    private val contactKeys = mapOf(
        "larId" to "homeId", "estado" to "status", "dataEnvio" to "sentAt",
        "dataAtualizacao" to "updatedAt", "notas" to "notes",
    )
    private val contactStatuses = mapOf(
        "ENVIADO" to "SENT", "SEM_RESPOSTA" to "NO_REPLY", "RESPONDEU" to "REPLIED", "VISITA" to "VISIT_SCHEDULED",
        "LISTA_ESPERA" to "WAITING_LIST", "VAGA" to "PLACE_AVAILABLE", "RECUSADO" to "DECLINED",
    )
    private val profileKeys = mapOf(
        "nome" to "name", "sexo" to "sex", "dataNascimento" to "birthDate", "estadoCivil" to "maritalStatus",
        "numUtente" to "snsNumber", "cc" to "citizenCard", "morada" to "address", "cpLocalidade" to "postalCodeLocality",
        "situacao" to "currentSituation", "localInternamento" to "careFacility", "previsaoAlta" to "expectedDischarge",
        "dependencia" to "dependency", "mobilidade" to "mobility", "cognitivo" to "cognitive", "saude" to "health",
        "medicacao" to "medication", "cuidados" to "specialCare", "medico" to "familyDoctor", "tipoVaga" to "placeType",
        "quarto" to "room", "dataEntrada" to "admissionDate", "zonas" to "preferredAreas", "observacoes" to "notes",
        "respNome" to "relativeName", "respParentesco" to "relativeRelationship", "respTelefone" to "relativePhone",
        "respEmail" to "relativeEmail", "respMorada" to "relativeAddress",
    )
    private val documentIds = mapOf(
        "cc_utente" to "resident_id_card", "sns" to "sns_card", "niss" to "social_security_number",
        "relatorio_medico" to "medical_report", "medicacao" to "medication_list", "relatorio_social" to "social_report",
        "irs" to "tax_return", "pensoes" to "income_proof", "morada" to "proof_of_address",
        "cc_responsavel" to "relative_id_card", "foto" to "photo",
    )

    fun run(dir: File) {
        migrateArray(dir, "lares.json", "homes.json") { renameKeys(it, homeKeys) }
        migrateArray(dir, "contactos.json", "contacts.json") { o ->
            renameKeys(o, contactKeys).also { c -> contactStatuses[c.optString("status")]?.let { c.put("status", it) } }
        }
        migrateArray(dir, "documentos.json", "documents.json") { o ->
            renameKeys(o, documentKeys).also { d -> documentIds[d.optString("id")]?.let { d.put("id", it) } }
        }
        migrate(dir, "documentos_removidos.json", "removed_documents.json") { text ->
            val ids = JSONArray(text)
            JSONArray((0 until ids.length()).map { ids.getString(it).let { id -> documentIds[id] ?: id } }).toString()
        }
        migrate(dir, "perfil.json", "profile.json") { renameKeys(JSONObject(it), profileKeys).toString() }
        migrate(dir, "selecao.json", "selection.json") { it }
        migrate(dir, "carta.txt", "letter.txt") { it }

        // Attachment folder: Document.file holds paths relative to it, so a rename is enough.
        val oldDocs = File(dir, "documentos")
        val newDocs = File(dir, "documents")
        if (oldDocs.isDirectory && !newDocs.exists()) oldDocs.renameTo(newDocs)
    }

    private fun migrateArray(dir: File, from: String, to: String, convert: (JSONObject) -> JSONObject) =
        migrate(dir, from, to) { text ->
            val arr = JSONArray(text)
            JSONArray((0 until arr.length()).map { convert(arr.getJSONObject(it)) }).toString()
        }

    /** Converts [from] into [to] (unless [to] already exists), then removes [from]. Unreadable files are left alone. */
    private fun migrate(dir: File, from: String, to: String, convert: (String) -> String) {
        val old = File(dir, from)
        if (!old.exists()) return
        val new = File(dir, to)
        if (!new.exists()) {
            val converted = runCatching { convert(old.readText()) }.getOrNull() ?: return
            val tmp = File(dir, "$to.tmp")
            tmp.writeText(converted)
            if (!tmp.renameTo(new)) return
        }
        old.delete()
    }

    private fun renameKeys(o: JSONObject, keys: Map<String, String>): JSONObject {
        val out = JSONObject()
        o.keys().forEach { k -> out.put(keys[k] ?: k, o.get(k)) }
        return out
    }
}
