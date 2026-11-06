package pt.avus.app

import pt.avus.app.data.ImportadorCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportadorCsvTest {

    private val csv = """
        Distrito;Concelho;Freguesia;Nome do Equipamento;Entidade Proprietária;Natureza Jurídica;Resposta Social;Morada;Código Postal;Localidade;Telefone;E-mail;Capacidade
        Lisboa;Lisboa;Arroios;Lar Exemplo A;Associação A;IPSS;Estrutura Residencial para Pessoas Idosas (ERPI);"Rua X, 1";1000-001;Lisboa;213 456 789 / 912 345 678;geral@exemplo-a.pt;40
        Lisboa;Lisboa;Arroios;Lar Exemplo A;Associação A;IPSS;Centro de Dia;"Rua X, 1";1000-001;Lisboa;213456789;;
        Porto;Porto;Bonfim;Creche B;Associação B;IPSS;Creche;Rua Y;4000-001;Porto;223456789;b@b.pt;30
        Évora;Évora;Sé;Residência C;Empresa C, Lda;Entidade lucrativa;ERPI;Rua Z;7000-001;Évora;266000000;"Direção: DIRECAO@C.PT; outro@c.pt";25
    """.trimIndent()

    @Test fun importaApenasErpi() {
        val r = ImportadorCsv.importarTexto(csv)
        assertTrue(r.filtrouPorResposta)
        assertEquals(listOf("Lar Exemplo A", "Residência C"), r.lares.map { it.nome })
        assertEquals(2, r.ignoradasOutraResposta)
    }

    @Test fun limpaContactos() {
        val r = ImportadorCsv.importarTexto(csv)
        val a = r.lares.first()
        assertEquals("213456789", a.telefone)
        assertEquals("geral@exemplo-a.pt", a.email)
        assertEquals("Rua X, 1", a.morada)
        assertEquals("Associação A", a.entidade)
        assertEquals("Lisboa", a.distrito)
        assertEquals("direcao@c.pt", r.lares[1].email)
    }

    @Test fun reconheceSeparadorVirgulaECabecalhoDeslocado() {
        val texto = "Lista de equipamentos sociais\n\nNome,Concelho,Telefone,Email\nLar D,Braga,253000000,d@d.pt\n"
        val r = ImportadorCsv.importarTexto(texto)
        assertFalse(r.filtrouPorResposta)
        assertEquals(1, r.lares.size)
        assertEquals("Braga", r.lares[0].concelho)
    }

    @Test fun decodificaWindows1252() {
        val bytes = "Nome;Concelho\nLar São João;Évora\n".toByteArray(charset("windows-1252"))
        val r = ImportadorCsv.importar(bytes)
        assertEquals("Lar São João", r.lares[0].nome)
        assertEquals("Évora", r.lares[0].concelho)
    }

    @Test fun reconheceVariantesDeErpi() {
        assertTrue(ImportadorCsv.eErpi("ESTRUTURA RESIDENCIAL PARA PESSOAS IDOSAS"))
        assertTrue(ImportadorCsv.eErpi("Lar de Idosos"))
        assertFalse(ImportadorCsv.eErpi("Lar Residencial"))
        assertFalse(ImportadorCsv.eErpi("Serviço de Apoio Domiciliário"))
    }

    @Test fun importaFormatoCartaSocialMunicipal() {
        val texto = "\uFEFFFID,designacao,freguesia,local,rua,numero_policia,codigo_postal,telefone,email,d_inf_juv,d_pes_idos,entidade_gestora\n" +
            "carta_social.1,Creche X,Alcabideche,Cobre,Rua A,,2750-571 Cascais,,,Sim,Não,Empresa X\n" +
            "carta_social.2,Lar Costa do Sol,Parede,Parede,Rua B,298,2775-001 Parede,214604580 / 919594878,geral@costa.pt,Não,Sim,VALE &amp; GONÇALVES LDA\n" +
            "carta_social.3,Centro de Convívio &quot;Os Vinhais&quot;,Cascais,Cascais,Rua C,0,2750-001,,dep@scmc.pt,Não,Sim,SCMC\n"
        val r = ImportadorCsv.importarTexto(texto)
        assertTrue(r.filtrouPorResposta)
        assertEquals(1, r.ignoradasOutraResposta)
        assertEquals(listOf("Lar Costa do Sol", "Centro de Convívio \"Os Vinhais\""), r.lares.map { it.nome })
        val lar = r.lares[0]
        assertEquals("Rua B, 298", lar.morada)
        assertEquals("Parede", lar.localidade)
        assertEquals("VALE & GONÇALVES LDA", lar.entidade)
        assertEquals("214604580", lar.telefone)
        assertEquals("Rua C", r.lares[1].morada)
        assertEquals("Cascais", lar.concelho)
        assertEquals("Lisboa", lar.distrito)
    }

    @Test fun deduzConcelhoPelaFreguesia() {
        assertEquals("Cascais" to "Lisboa", ImportadorCsv.concelhoDaFreguesia("U.F. Cascais e Estoril"))
        assertEquals("Cascais" to "Lisboa", ImportadorCsv.concelhoDaFreguesia("União das Freguesias de Carcavelos e Parede"))
        assertEquals("Cascais" to "Lisboa", ImportadorCsv.concelhoDaFreguesia("São Domingos de Rana"))
        assertEquals(null, ImportadorCsv.concelhoDaFreguesia("Arroios"))
    }

    @Test fun importaCsvDoRecolhedorCartaSocial() {
        // Cabeçalho e linha reais gerados por tools/cartasocial/recolher_erpi.py
        val texto = "\uFEFFDistrito;Concelho;Freguesia;Nome do Equipamento;Entidade Proprietária;Natureza Jurídica;Resposta Social;Morada;Código Postal;Localidade;Telefone;Email;Capacidade;Utentes;Última atualização;ID Carta Social\n" +
            "Portalegre;Alter do Chão;Cunheira;LAR NOSSA SENHORA DA CONCEIÇÃO;GRUPO SOCIAL DE CUNHEIRA;Associação de Solidariedade Social;Estrutura Residencial para Pessoas Idosas ( Lar de Idosos e Residência);LARGO SANTO AMARO, 14 - CUNHEIRA;7440-251;Cunheira;245697157;gscunheira@sapo.pt;40;31;2026-09-06;1806\n"
        val r = ImportadorCsv.importarTexto(texto)
        assertTrue(r.filtrouPorResposta)
        val lar = r.lares.single()
        assertEquals("LAR NOSSA SENHORA DA CONCEIÇÃO", lar.nome)
        assertEquals("GRUPO SOCIAL DE CUNHEIRA", lar.entidade)
        assertEquals("Alter do Chão", lar.concelho)
        assertEquals("Portalegre", lar.distrito)
        assertEquals("Cunheira", lar.freguesia)
        assertEquals("7440-251", lar.codigoPostal)
        assertEquals("gscunheira@sapo.pt", lar.email)
        assertEquals("40", lar.capacidade)
    }
}
