package pt.avus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.avus.app.data.CareHome
import pt.avus.app.data.CsvImporter
import pt.avus.app.data.DistanceReference
import pt.avus.app.data.Geo
import pt.avus.app.data.GeoPoint
import pt.avus.app.data.ProfileFields

class GeoTest {

    private fun home(id: String, cp: String, municipality: String, lat: Double, lon: Double, precision: String = "código postal") =
        CareHome(id, id, postalCode = cp, municipality = municipality, latitude = lat, longitude = lon, locationPrecision = precision)

    private val homes = listOf(
        home("a", "7440-251", "Alter do Chão", 39.304, -7.781),
        home("b", "7440-022", "Alter do Chão", 39.198, -7.659),
        home("c", "1600-498", "Lisboa", 38.759, -9.168),
        home("d", "1600-100", "Lisboa", 38.761, -9.160, "código postal (4 dígitos)"),
    )

    @Test fun distanceLisbonToPorto() {
        val km = Geo.distanceKm(GeoPoint(38.7223, -9.1393), GeoPoint(41.1579, -8.6291))
        assertTrue(km in 270.0..280.0)
        assertEquals("12 km", Geo.formatDistance(12.7))
        assertEquals("850 m", Geo.formatDistance(0.87))
        assertEquals("< 100 m", Geo.formatDistance(0.0))
    }

    @Test fun findsPostalCodesInAddresses() {
        assertEquals("7440-251", Geo.postalCodeIn("Rua X, 7440-251 Cunheira"))
        assertEquals("7440", Geo.postalCodeIn("7440 Alter do Chão"))
        assertNull(Geo.postalCodeIn("Rua da Paz, n.º 12"))
    }

    @Test fun resolvesPostalCodeThenFourDigitsThenMunicipality() {
        assertEquals(Geo.Precision.POSTAL_CODE, Geo.resolve("7440-251", null, homes)!!.precision)
        assertEquals(39.304, Geo.resolve("7440-251", null, homes)!!.point.latitude, 1e-9)
        // Unknown full code -> centre of the homes with the same first 4 digits.
        val cp4 = Geo.resolve("7440-999", null, homes)!!
        assertEquals(Geo.Precision.POSTAL_CODE_4, cp4.precision)
        assertEquals((39.304 + 39.198) / 2, cp4.point.latitude, 1e-9)
        // Unknown postal code -> the municipality (accents and case ignored).
        assertEquals(Geo.Precision.MUNICIPALITY, Geo.resolve("9999-999", "alter do chao", homes)!!.precision)
        assertNull(Geo.resolve("9999-999", "Nenhures", homes))
    }

    @Test fun referenceFromProfileAndRoundTrip() {
        val profile = mapOf(ProfileFields.RELATIVE_ADDRESS to "Rua A, 1600-498 Lisboa")
        val located = DistanceReference.Contact.locate(profile, homes)!!
        assertEquals("Morada do contacto (1600-498)", located.label)
        assertNull(DistanceReference.Resident.locate(profile, homes))
        listOf(DistanceReference.Resident, DistanceReference.Contact, DistanceReference.PostalCode("7440-251"),
            DistanceReference.Municipality("Alter do Chão")).forEach {
            assertEquals(it, DistanceReference.parse(it.serialize()))
        }
    }

    @Test fun residentUsesThePostalCodeFieldThenFallsBackToItsTown() {
        // Unknown code: the town after it ("Alter do Chão") is used as the municipality — not the street.
        val profile = mapOf(
            ProfileFields.POSTAL_CODE_LOCALITY to "9999-999 Alter do Chão",
            ProfileFields.ADDRESS to "Rua Direita, 12",
        )
        val located = DistanceReference.Resident.locate(profile, homes)!!
        assertEquals(Geo.Precision.MUNICIPALITY, located.precision)
        // No code in that field: the street address is tried.
        val fromAddress = DistanceReference.Resident.locate(mapOf(ProfileFields.ADDRESS to "Rua A, 1600-498 Lisboa"), homes)!!
        assertEquals(Geo.Precision.POSTAL_CODE, fromAddress.precision)
    }

    @Test fun importsCoordinatesFromTheCsv() {
        val csv = "Nome do Equipamento;Resposta Social;Código Postal;Concelho;Localidade;Latitude;Longitude;Localização\n" +
            "Lar A;ERPI;7440-251;Alter do Chão;Cunheira;39.304028;-7.780609;código postal\n"
        val r = CsvImporter.importText(csv).homes.single()
        assertEquals(39.304028, r.latitude!!, 1e-9)
        assertEquals(-7.780609, r.longitude!!, 1e-9)
        assertEquals("código postal", r.locationPrecision)
        assertEquals("Cunheira", r.locality)   // "Localização" did not steal the locality column
    }
}
