package pt.avus.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Distances and "near where?" resolution, done entirely on the phone from the homes' own positions. */
object Geo {

    private val fullPostalCode = Regex("\\b(\\d{4})-(\\d{3})\\b")
    private val shortPostalCode = Regex("\\b(\\d{4})\\b")

    /** Great-circle distance in km. */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** "1600-498 Lisboa" -> "1600-498"; "7440 Alter do Chão" -> "7440"; otherwise null. */
    fun postalCodeIn(text: String?): String? {
        if (text.isNullOrBlank()) return null
        fullPostalCode.find(text)?.let { return it.value }
        return shortPostalCode.find(text)?.value?.takeIf { it.first() != '0' }
    }

    enum class Precision { POSTAL_CODE, POSTAL_CODE_4, MUNICIPALITY }

    data class Resolved(val point: GeoPoint, val precision: Precision)

    private fun centre(points: List<GeoPoint>): GeoPoint? =
        if (points.isEmpty()) null
        else GeoPoint(points.sumOf { it.latitude } / points.size, points.sumOf { it.longitude } / points.size)

    /**
     * Where is [postalCode] (or the [municipality], as fallback)? Uses homes with the same full postal
     * code, then the same first 4 digits, then the homes of that municipality — no network needed.
     */
    fun resolve(postalCode: String?, municipality: String?, homes: List<CareHome>): Resolved? {
        val located = homes.filter { it.point != null }
        val cp = postalCode?.trim().orEmpty()
        if (fullPostalCode.matches(cp)) {
            centre(located.filter { it.postalCode == cp && it.locationPrecision == "código postal" }.mapNotNull { it.point })
                ?.let { return Resolved(it, Precision.POSTAL_CODE) }
        }
        val cp4 = cp.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
        if (cp4 != null) {
            centre(located.filter { it.postalCode.startsWith(cp4) }.mapNotNull { it.point })
                ?.let { return Resolved(it, Precision.POSTAL_CODE_4) }
        }
        if (!municipality.isNullOrBlank()) {
            val m = normalizeForSearch(municipality)
            centre(located.filter { normalizeForSearch(it.municipality) == m }.mapNotNull { it.point })
                ?.let { return Resolved(it, Precision.MUNICIPALITY) }
        }
        return null
    }

    /** "850 m", "12 km", "1,5 km". */
    fun formatDistance(km: Double): String = when {
        km < 0.1 -> "< 100 m"
        km < 1 -> "${(km * 1000).toInt() / 50 * 50} m"
        km < 10 -> String.format(java.util.Locale.forLanguageTag("pt-PT"), "%.1f km", km)
        else -> "${km.toInt()} km"
    }
}
