package pt.avus.app

import pt.avus.app.data.CsvImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImporterTest {

    private val csv = """
        Distrito;Concelho;Freguesia;Nome do Equipamento;Entidade Proprietária;Natureza Jurídica;Resposta Social;Morada;Código Postal;Localidade;Telefone;E-mail;Capacidade
        Lisboa;Lisboa;Arroios;Lar Exemplo A;Associação A;IPSS;Estrutura Residencial para Pessoas Idosas (ERPI);"Rua X, 1";1000-001;Lisboa;213 456 789 / 912 345 678;geral@exemplo-a.pt;40
        Lisboa;Lisboa;Arroios;Lar Exemplo A;Associação A;IPSS;Centro de Dia;"Rua X, 1";1000-001;Lisboa;213456789;;
        Porto;Porto;Bonfim;Creche B;Associação B;IPSS;Creche;Rua Y;4000-001;Porto;223456789;b@b.pt;30
        Évora;Évora;Sé;Residência C;Empresa C, Lda;Entidade lucrativa;ERPI;Rua Z;7000-001;Évora;266000000;"Direção: DIRECAO@C.PT; outro@c.pt";25
    """.trimIndent()

    @Test fun importsOnlyErpi() {
        val r = CsvImporter.importText(csv)
        assertTrue(r.filteredBySocialResponse)
        assertEquals(listOf("Lar Exemplo A", "Residência C"), r.homes.map { it.name })
        assertEquals(2, r.skippedOtherResponse)
    }

    @Test fun cleansContacts() {
        val r = CsvImporter.importText(csv)
        val a = r.homes.first()
        assertEquals("213456789", a.phone)
        assertEquals("geral@exemplo-a.pt", a.email)
        assertEquals("Rua X, 1", a.address)
        assertEquals("Associação A", a.owner)
        assertEquals("Lisboa", a.district)
        assertEquals("direcao@c.pt", r.homes[1].email)
    }

    @Test fun detectsCommaSeparatorAndOffsetHeader() {
        val text = "Lista de equipamentos sociais\n\nNome,Concelho,Telefone,Email\nLar D,Braga,253000000,d@d.pt\n"
        val r = CsvImporter.importText(text)
        assertFalse(r.filteredBySocialResponse)
        assertEquals(1, r.homes.size)
        assertEquals("Braga", r.homes[0].municipality)
    }

    @Test fun decodesWindows1252() {
        val bytes = "Nome;Concelho\nLar São João;Évora\n".toByteArray(charset("windows-1252"))
        val r = CsvImporter.importBytes(bytes)
        assertEquals("Lar São João", r.homes[0].name)
        assertEquals("Évora", r.homes[0].municipality)
    }

    @Test fun recognizesErpiVariants() {
        assertTrue(CsvImporter.isErpi("ESTRUTURA RESIDENCIAL PARA PESSOAS IDOSAS"))
        assertTrue(CsvImporter.isErpi("Lar de Idosos"))
        assertFalse(CsvImporter.isErpi("Lar Residencial"))
        assertFalse(CsvImporter.isErpi("Serviço de Apoio Domiciliário"))
    }

    @Test fun importsMunicipalCartaSocialFormat() {
        val text = "\uFEFFFID,designacao,freguesia,local,rua,numero_policia,codigo_postal,telefone,email,d_inf_juv,d_pes_idos,entidade_gestora\n" +
            "carta_social.1,Creche X,Alcabideche,Cobre,Rua A,,2750-571 Cascais,,,Sim,Não,Empresa X\n" +
            "carta_social.2,Lar Costa do Sol,Parede,Parede,Rua B,298,2775-001 Parede,214604580 / 919594878,geral@costa.pt,Não,Sim,VALE &amp; GONÇALVES LDA\n" +
            "carta_social.3,Centro de Convívio &quot;Os Vinhais&quot;,Cascais,Cascais,Rua C,0,2750-001,,dep@scmc.pt,Não,Sim,SCMC\n"
        val r = CsvImporter.importText(text)
        assertTrue(r.filteredBySocialResponse)
        assertEquals(1, r.skippedOtherResponse)
        assertEquals(listOf("Lar Costa do Sol", "Centro de Convívio \"Os Vinhais\""), r.homes.map { it.name })
        val home = r.homes[0]
        assertEquals("Rua B, 298", home.address)
        assertEquals("Parede", home.locality)
        assertEquals("VALE & GONÇALVES LDA", home.owner)
        assertEquals("214604580", home.phone)
        assertEquals("Rua C", r.homes[1].address)
        assertEquals("Cascais", home.municipality)
        assertEquals("Lisboa", home.district)
    }

    @Test fun infersMunicipalityFromParish() {
        assertEquals("Cascais" to "Lisboa", CsvImporter.municipalityForParish("U.F. Cascais e Estoril"))
        assertEquals("Cascais" to "Lisboa", CsvImporter.municipalityForParish("União das Freguesias de Carcavelos e Parede"))
        assertEquals("Cascais" to "Lisboa", CsvImporter.municipalityForParish("São Domingos de Rana"))
        assertEquals(null, CsvImporter.municipalityForParish("Arroios"))
    }

    @Test fun importsCartaSocialScraperCsv() {
        // Real header and row produced by tools/cartasocial/scrape_erpi.py
        val text = "\uFEFFDistrito;Concelho;Freguesia;Nome do Equipamento;Entidade Proprietária;Natureza Jurídica;Resposta Social;Morada;Código Postal;Localidade;Telefone;Email;Capacidade;Utentes;Última atualização;ID Carta Social\n" +
            "Portalegre;Alter do Chão;Cunheira;LAR NOSSA SENHORA DA CONCEIÇÃO;GRUPO SOCIAL DE CUNHEIRA;Associação de Solidariedade Social;Estrutura Residencial para Pessoas Idosas ( Lar de Idosos e Residência);LARGO SANTO AMARO, 14 - CUNHEIRA;7440-251;Cunheira;245697157;gscunheira@sapo.pt;40;31;2026-09-06;1806\n"
        val r = CsvImporter.importText(text)
        assertTrue(r.filteredBySocialResponse)
        val home = r.homes.single()
        assertEquals("LAR NOSSA SENHORA DA CONCEIÇÃO", home.name)
        assertEquals("GRUPO SOCIAL DE CUNHEIRA", home.owner)
        assertEquals("Alter do Chão", home.municipality)
        assertEquals("Portalegre", home.district)
        assertEquals("Cunheira", home.parish)
        assertEquals("7440-251", home.postalCode)
        assertEquals("gscunheira@sapo.pt", home.email)
        assertEquals("40", home.capacity)
        assertEquals("31", home.residents)
        assertEquals("2026-09-06", home.lastUpdated)
        assertEquals("1806", home.cartaSocialId)
        assertEquals("cs-1806", home.id)
    }
}
