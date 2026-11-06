package pt.avus.app

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.avus.app.data.DefaultDocuments
import pt.avus.app.data.ProfileFields

class DefaultDocumentsTest {

    private fun idsFor(profile: Map<String, String>) =
        DefaultDocuments.list.filter { DefaultDocuments.appliesTo(it.id, profile) }.map { it.id }

    @Test fun ccNeedsOnlyTheCard() {
        val ids = idsFor(mapOf(ProfileFields.ID_DOCUMENT to ProfileFields.ID_CC))
        assertEquals("resident_id_card", ids.first())
        listOf("resident_bi", "tax_number", "social_security_number", "sns_card").forEach { assert(it !in ids) { it } }
        assertEquals(ids, idsFor(emptyMap()))   // not chosen yet: assume CC
    }

    @Test fun biNeedsTheBiAndProofOfNifNissAndSns() {
        val ids = idsFor(mapOf(ProfileFields.ID_DOCUMENT to ProfileFields.ID_BI))
        assertEquals(listOf("resident_bi", "tax_number", "social_security_number", "sns_card"), ids.take(4))
        assert("resident_id_card" !in ids)
    }

    @Test fun customDocumentsAlwaysApply() {
        assert(DefaultDocuments.appliesTo("doc-123", mapOf(ProfileFields.ID_DOCUMENT to ProfileFields.ID_BI)))
    }

    @Test fun numberLabelFollowsTheDocument() {
        val field = ProfileFields.residentSections.flatMap { it.fields }.first { it.key == ProfileFields.CITIZEN_CARD }
        assertEquals("N.º do Cartão de Cidadão", ProfileFields.labelFor(field, emptyMap()))
        assertEquals("N.º do Bilhete de Identidade", ProfileFields.labelFor(field, mapOf(ProfileFields.ID_DOCUMENT to ProfileFields.ID_BI)))
    }
}
