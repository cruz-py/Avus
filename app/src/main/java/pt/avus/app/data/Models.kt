package pt.avus.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A residential care home for the elderly (ERPI / "lar"). */
data class CareHome(
    val id: String,
    val name: String,
    val owner: String = "",
    val legalForm: String = "",
    val address: String = "",
    val postalCode: String = "",
    val locality: String = "",
    val parish: String = "",
    val municipality: String = "",
    val district: String = "",
    val phone: String = "",
    val email: String = "",
    val capacity: String = "",
    val manual: Boolean = false,
    /** Current number of residents, as reported to the Carta Social. */
    val residents: String = "",
    /** Date the home's Carta Social record was last updated (yyyy-MM-dd). */
    val lastUpdated: String = "",
    /** Facility id on cartasocial.pt (links to its page there). */
    val cartaSocialId: String = "",
    /** Came from the official list bundled with the app (see [OfficialList]). */
    val official: Boolean = false,
) {
    val hasEmail: Boolean get() = email.isNotBlank()

    /** Free places reported to the Carta Social (capacity − residents, never negative); null if unknown. */
    val freePlaces: Int?
        get() {
            val cap = capacity.trim().toIntOrNull() ?: return null
            val res = residents.trim().toIntOrNull() ?: return null
            return if (cap > 0) (cap - res).coerceAtLeast(0) else null
        }

    /** Occupancy between 0 and 1, or null if unknown. */
    val occupancy: Float?
        get() {
            val cap = capacity.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
            val res = residents.trim().toIntOrNull() ?: return null
            return (res.toFloat() / cap).coerceIn(0f, 1f)
        }

    val cartaSocialUrl: String?
        get() = cartaSocialId.takeIf { it.isNotBlank() }?.let {
            "https://www.cartasocial.pt/resultados-da-pesquisa?p_p_id=SocialLetterPortlet_WAR_cartasocialportlet" +
                "&p_p_lifecycle=0&_SocialLetterPortlet_WAR_cartasocialportlet__facesViewIdRender=" +
                "%2Fviews%2FsocialLetter%2Flist%2Fview%2Fequipment%2Fequipment_detail.xhtml" +
                "&_SocialLetterPortlet_WAR_cartasocialportlet_idEquipment=$it"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("owner", owner).put("legalForm", legalForm)
        .put("address", address).put("postalCode", postalCode).put("locality", locality)
        .put("parish", parish).put("municipality", municipality).put("district", district)
        .put("phone", phone).put("email", email).put("capacity", capacity)
        .put("manual", manual)
        .put("residents", residents).put("lastUpdated", lastUpdated).put("cartaSocialId", cartaSocialId)
        .put("official", official)

    companion object {
        fun fromJson(o: JSONObject) = CareHome(
            id = o.getString("id"),
            name = o.optString("name"),
            owner = o.optString("owner"),
            legalForm = o.optString("legalForm"),
            address = o.optString("address"),
            postalCode = o.optString("postalCode"),
            locality = o.optString("locality"),
            parish = o.optString("parish"),
            municipality = o.optString("municipality"),
            district = o.optString("district"),
            phone = o.optString("phone"),
            email = o.optString("email"),
            capacity = o.optString("capacity"),
            manual = o.optBoolean("manual"),
            residents = o.optString("residents"),
            lastUpdated = o.optString("lastUpdated"),
            cartaSocialId = o.optString("cartaSocialId"),
            official = o.optBoolean("official"),
        )

        /** Stable id, so re-importing the same list does not duplicate homes. */
        fun generateId(name: String, address: String, municipality: String): String =
            listOf(name, address, municipality)
                .joinToString("|") { normalizeForSearch(it) }
                .hashCode()
                .toUInt()
                .toString(16)
    }
}

/** A document that care homes usually ask for when applying. */
data class Document(
    val id: String,
    val name: String,
    val description: String = "",
    /** Name of the file copied into the app's private storage (null if not attached yet). */
    val file: String? = null,
    val originalName: String? = null,
    val custom: Boolean = false,
) {
    val isAttached: Boolean get() = file != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("description", description)
        .put("file", file ?: JSONObject.NULL)
        .put("originalName", originalName ?: JSONObject.NULL)
        .put("custom", custom)

    companion object {
        fun fromJson(o: JSONObject) = Document(
            id = o.getString("id"),
            name = o.optString("name"),
            description = o.optString("description"),
            file = o.optStringOrNull("file"),
            originalName = o.optStringOrNull("originalName"),
            custom = o.optBoolean("custom"),
        )
    }
}

enum class ContactStatus(val label: String) {
    SENT("Enviado"),
    NO_REPLY("Sem resposta"),
    REPLIED("Respondeu"),
    VISIT_SCHEDULED("Visita marcada"),
    WAITING_LIST("Em lista de espera"),
    PLACE_AVAILABLE("Vaga disponível"),
    DECLINED("Sem vaga / recusado"),
}

/** Record of the contact made with a care home, for follow-up. */
data class Contact(
    val homeId: String,
    val status: ContactStatus = ContactStatus.SENT,
    val sentAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = sentAt,
    val notes: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("homeId", homeId).put("status", status.name).put("sentAt", sentAt)
        .put("updatedAt", updatedAt).put("notes", notes)

    companion object {
        fun fromJson(o: JSONObject) = Contact(
            homeId = o.getString("homeId"),
            status = runCatching { ContactStatus.valueOf(o.getString("status")) }
                .getOrDefault(ContactStatus.SENT),
            sentAt = o.optLong("sentAt"),
            updatedAt = o.optLong("updatedAt", o.optLong("sentAt")),
            notes = o.optString("notes"),
        )
    }
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).ifEmpty { null }

internal inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }

/** Lower-case and accent-free, for searching ("Évora" == "evora"). */
fun normalizeForSearch(text: String): String =
    java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
