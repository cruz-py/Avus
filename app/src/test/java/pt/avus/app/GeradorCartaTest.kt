package pt.avus.app

import pt.avus.app.carta.GeradorCarta
import pt.avus.app.data.CamposPerfil as C
import pt.avus.app.data.Documento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GeradorCartaTest {
    private val hoje = LocalDate.of(2026, 11, 6)
    private val perfil = mapOf(
        C.NOME to "Manuel Silva",
        C.SEXO to C.MASCULINO,
        C.DATA_NASCIMENTO to "15/03/1938",
        C.SITUACAO to C.INTERNADO,
        C.LOCAL_INTERNAMENTO to "Hospital de São José",
        C.DEPENDENCIA to "Dependente",
        C.TIPO_VAGA to "Permanente",
        C.RESP_NOME to "Rui Cruz",
        C.RESP_PARENTESCO to "neto",
        C.RESP_TELEFONE to "912345678",
    )

    @Test fun calculaIdade() {
        assertEquals(88, GeradorCarta.idade("15/03/1938", hoje))
        assertEquals(null, GeradorCarta.idade("31/02/1938", hoje))
    }

    @Test fun cartaMasculino() {
        val docs = listOf(Documento("cc", "Cartão de Cidadão do utente"))
        val carta = GeradorCarta.gerar(perfil, docs, emptyList(), hoje)
        assertTrue(carta.startsWith("Exmos. Senhores,"))
        assertTrue(carta.contains("sou neto de Manuel Silva"))
        assertTrue(carta.contains("admissão do Sr. Manuel Silva"))
        assertTrue(carta.contains("em regime de vaga permanente"))
        assertTrue(carta.contains("tem 88 anos"))
        assertTrue(carta.contains("internado no Hospital de São José"))
        assertTrue(carta.contains("• Cartão de Cidadão do utente"))
        assertTrue(carta.contains("6 de novembro de 2026"))
    }

    @Test fun cartaFeminino() {
        val carta = GeradorCarta.gerar(perfil + (C.SEXO to C.FEMININO), emptyList(), emptyList(), hoje)
        assertTrue(carta.contains("da Sra. Manuel Silva"))
        assertTrue(carta.contains("internada"))
        assertTrue(carta.contains("A utente"))
    }

    @Test fun camposEmFalta() {
        assertTrue(GeradorCarta.camposEmFalta(perfil).isEmpty())
        assertEquals(listOf("telemóvel do familiar"), GeradorCarta.camposEmFalta(perfil - C.RESP_TELEFONE))
    }

    @Test fun personalizarAcrescentaDestinatario() {
        val texto = GeradorCarta.personalizar("Exmos. Senhores,", "Lar X")
        assertTrue(texto.startsWith("À Direção Técnica – Lar X"))
        assertFalse(GeradorCarta.gerar(perfil, emptyList(), emptyList(), hoje).contains("Direção Técnica –"))
    }
}
