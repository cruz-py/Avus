package pt.avus.app.data

/** The place homes are sorted by distance from (persisted as a short string). */
sealed interface DistanceReference {
    data object Resident : DistanceReference
    data object Contact : DistanceReference
    data class PostalCode(val code: String) : DistanceReference
    data class Municipality(val name: String) : DistanceReference

    fun serialize(): String = when (this) {
        Resident -> "resident"
        Contact -> "contact"
        is PostalCode -> "cp:$code"
        is Municipality -> "concelho:$name"
    }

    /** Where it is, worked out from the profile and the homes' positions (no network). */
    data class Located(val reference: DistanceReference, val label: String, val point: GeoPoint, val precision: Geo.Precision)

    fun locate(profile: Profile, homes: List<CareHome>): Located? {
        fun fromText(text: String?, label: String): Located? {
            val cp = Geo.postalCodeIn(text) ?: return null
            // Text after the postal code is usually the locality, often also the municipality name.
            val town = text.orEmpty().substringAfter(cp).trim().trimStart(',', ' ').substringBefore(',').trim()
            return Geo.resolve(cp, town, homes)?.let { Located(this, "$label ($cp)", it.point, it.precision) }
        }
        return when (this) {
            // "Código postal e localidade" on its own (so the text after the code is the town), else the street address.
            Resident -> fromText(profile[ProfileFields.POSTAL_CODE_LOCALITY], "Morada do utente")
                ?: fromText(profile[ProfileFields.ADDRESS], "Morada do utente")
            Contact -> fromText(profile[ProfileFields.RELATIVE_ADDRESS], "Morada do contacto")
            is PostalCode -> Geo.resolve(code, null, homes)?.let { Located(this, code, it.point, it.precision) }
            is Municipality -> Geo.resolve(null, name, homes)?.let { Located(this, "Concelho de $name", it.point, it.precision) }
        }
    }

    companion object {
        fun parse(text: String?): DistanceReference? {
            val t = text?.trim().orEmpty()
            return when {
                t == "resident" -> Resident
                t == "contact" -> Contact
                t.startsWith("cp:") -> PostalCode(t.removePrefix("cp:"))
                t.startsWith("concelho:") -> Municipality(t.removePrefix("concelho:"))
                else -> null
            }
        }
    }
}
