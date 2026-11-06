package pt.avus.app

import pt.avus.app.letter.LetterGenerator
import pt.avus.app.data.ProfileFields as C
import pt.avus.app.data.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LetterGeneratorTest {
    private val today = LocalDate.of(2026, 11, 6)
    private val profile = mapOf(
        C.NAME to "Manuel Silva",
        C.SEX to C.MALE,
        C.BIRTH_DATE to "15/03/1938",
        C.CURRENT_SITUATION to C.HOSPITALISED,
        C.CARE_FACILITY to "Hospital de São José",
        C.DEPENDENCY to "Dependente",
        C.PLACE_TYPE to "Permanente",
        C.RELATIVE_NAME to "Rui Cruz",
        C.RELATIVE_RELATIONSHIP to "neto",
        C.RELATIVE_PHONE to "912345678",
        C.CITIZEN_CARD to "01234567",
        C.NIF to "123456789",
        C.NISS to "11234567890",
    )

    @Test fun computesAge() {
        assertEquals(88, LetterGenerator.age("15/03/1938", today))
        assertEquals(null, LetterGenerator.age("31/02/1938", today))
    }

    @Test fun letterMale() {
        val docs = listOf(Document("cc", "Cartão de Cidadão do utente"))
        val letter = LetterGenerator.generate(profile, docs, emptyList(), today)
        assertTrue(letter.startsWith("Exmos. Senhores,"))
        assertTrue(letter.contains("sou neto de Manuel Silva"))
        assertTrue(letter.contains("admissão do Sr. Manuel Silva"))
        assertTrue(letter.contains("em regime de vaga permanente"))
        assertTrue(letter.contains("tem 88 anos"))
        assertTrue(letter.contains("internado no Hospital de São José"))
        assertTrue(letter.contains("• Cartão de Cidadão do utente"))
        assertTrue(letter.contains("6 de novembro de 2026"))
    }

    @Test fun letterFemale() {
        val letter = LetterGenerator.generate(profile + (C.SEX to C.FEMALE), emptyList(), emptyList(), today)
        assertTrue(letter.contains("da Sra. Manuel Silva"))
        assertTrue(letter.contains("internada"))
        assertTrue(letter.contains("A utente"))
    }

    @Test fun editedLetterGetsTodaysDate() {
        val edited = "Exmos. Senhores,\n\nTexto alterado à mão.\n\nCom os melhores cumprimentos,\nRui Cruz\n5 de novembro de 2026"
        val refreshed = LetterGenerator.refreshDate(edited, today)
        assertTrue(refreshed.endsWith("Rui Cruz\n6 de novembro de 2026"))
        assertTrue(refreshed.contains("Texto alterado à mão."))
        // No date on the last line (the user removed or moved it): leave the text alone.
        val noDate = "Exmos. Senhores,\nObrigado."
        assertEquals(noDate, LetterGenerator.refreshDate(noDate, today))
    }

    @Test fun idDocumentLabelFollowsCcOrBi() {
        assertTrue(LetterGenerator.generate(profile, emptyList(), emptyList(), today).contains("• Cartão de Cidadão: 01234567"))
        val bi = LetterGenerator.generate(profile + (C.ID_DOCUMENT to C.ID_BI), emptyList(), emptyList(), today)
        assertTrue(bi.contains("• Bilhete de Identidade: 01234567"))
        assertFalse(bi.contains("Cartão de Cidadão:"))
    }

    @Test fun nifNissAndIdNumberAreRequired() {
        assertEquals(
            listOf("n.º do documento de identificação", "NIF", "NISS"),
            LetterGenerator.missingFields(profile - C.CITIZEN_CARD - C.NIF - C.NISS),
        )
    }

    @Test fun contactDetailsIncludeBestTimeAndOtherContact() {
        val letter = LetterGenerator.generate(
            profile + (C.RELATIVE_BEST_TIME to "dias úteis depois das 18h") + (C.RELATIVE_OTHER_CONTACT to "João (irmão), 913 456 789"),
            emptyList(), emptyList(), today,
        )
        assertTrue(letter.contains("Melhor horário para contacto: dias úteis depois das 18h"))
        assertTrue(letter.contains("Outro contacto: João (irmão), 913 456 789"))
    }

    @Test fun missingFields() {
        assertTrue(LetterGenerator.missingFields(profile).isEmpty())
        assertEquals(listOf("telemóvel do familiar"), LetterGenerator.missingFields(profile - C.RELATIVE_PHONE))
    }

    @Test fun addRecipientHeaderPrependsHomeName() {
        val text = LetterGenerator.addRecipientHeader("Exmos. Senhores,", "Lar X")
        assertTrue(text.startsWith("À Direção Técnica – Lar X"))
        assertFalse(LetterGenerator.generate(profile, emptyList(), emptyList(), today).contains("Direção Técnica –"))
    }
}
