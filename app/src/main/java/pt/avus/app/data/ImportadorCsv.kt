package pt.avus.app.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class ResultadoImportacao(
    val lares: List<Lar>,
    val linhasLidas: Int,
    val ignoradasOutraResposta: Int,
    val ignoradasSemNome: Int,
    val colunasReconhecidas: List<String>,
    val filtrouPorResposta: Boolean,
)

class ErroImportacao(mensagem: String) : Exception(mensagem)

/**
 * Lê um CSV com equipamentos sociais (ex.: o exportado da Carta Social / dados.gov.pt)
 * e extrai as ERPI. As colunas são reconhecidas pelo nome do cabeçalho, por isso funciona
 * com variações de formato (separador ";" ou ",", UTF-8 ou Windows-1252, colunas noutra ordem).
 */
object ImportadorCsv {

    private enum class Coluna(val rotulo: String, val padroes: List<String>) {
        // A ordem importa: "Nome da entidade" deve ir para ENTIDADE antes de NOME procurar "nome".
        ENTIDADE("Entidade", listOf("entidade proprietaria", "nome da entidade", "entidade gestora", "entidade", "instituicao")),
        NATUREZA("Natureza", listOf("natureza juridica", "natureza", "tipo de entidade", "tipo entidade")),
        RESPOSTA("Resposta social", listOf("resposta social", "respostas sociais", "resposta", "valencia", "tipologia")),
        CODIGO_POSTAL("Código postal", listOf("codigo postal", "cod postal", "cod. postal", "cp")),
        NOME("Nome", listOf("nome do equipamento", "designacao do equipamento", "equipamento", "designacao", "denominacao", "nome")),
        MORADA("Morada", listOf("morada", "endereco", "rua")),
        LOCALIDADE("Localidade", listOf("localidade", "local")),
        FREGUESIA("Freguesia", listOf("freguesia")),
        CONCELHO("Concelho", listOf("concelho", "municipio")),
        DISTRITO("Distrito", listOf("distrito")),
        TELEFONE("Telefone", listOf("telefone", "telef", "contacto telefonico", "telemovel", "tel")),
        EMAIL("Email", listOf("email", "e-mail", "e mail", "correio eletronico", "correio electronico", "mail")),
        CAPACIDADE("Capacidade", listOf("capacidade", "lotacao")),
    }

    private val regexEmail = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    fun importar(bytes: ByteArray): ResultadoImportacao = importarTexto(decodificar(bytes))

    fun importarTexto(texto: String): ResultadoImportacao {
        val separador = detetarSeparador(texto)
        val linhas = parseCsv(texto, separador)
        if (linhas.isEmpty()) throw ErroImportacao("O ficheiro está vazio.")

        // O cabeçalho pode não estar na primeira linha (alguns ficheiros têm títulos antes).
        val (indiceCabecalho, mapa) = linhas.take(20).withIndex()
            .map { (i, l) -> i to mapearColunas(l) }
            .maxByOrNull { it.second.size }
            ?: throw ErroImportacao("Não foi encontrado o cabeçalho.")

        if (Coluna.NOME !in mapa) {
            throw ErroImportacao(
                "Não foi encontrada a coluna com o nome do equipamento. " +
                    "Confirme que o ficheiro é um CSV com cabeçalho (ex.: \"Nome\", \"Concelho\", \"Telefone\")."
            )
        }

        val filtrar = Coluna.RESPOSTA in mapa
        var ignoradasResposta = 0
        var ignoradasNome = 0
        val porId = LinkedHashMap<String, Lar>()
        val dados = linhas.drop(indiceCabecalho + 1).filter { l -> l.any { it.isNotBlank() } }

        for (linha in dados) {
            fun valor(c: Coluna) = mapa[c]?.let { linha.getOrNull(it) }?.trim().orEmpty()

            if (filtrar && !eErpi(valor(Coluna.RESPOSTA))) {
                ignoradasResposta++
                continue
            }
            val nome = valor(Coluna.NOME).ifBlank { valor(Coluna.ENTIDADE) }
            if (nome.isBlank()) {
                ignoradasNome++
                continue
            }
            val morada = valor(Coluna.MORADA)
            val concelho = valor(Coluna.CONCELHO)
            val id = Lar.gerarId(nome, morada, concelho)
            val lar = Lar(
                id = id,
                nome = nome,
                entidade = valor(Coluna.ENTIDADE).takeIf { normalizar(it) != normalizar(nome) }.orEmpty(),
                natureza = valor(Coluna.NATUREZA),
                morada = morada,
                codigoPostal = valor(Coluna.CODIGO_POSTAL),
                localidade = valor(Coluna.LOCALIDADE),
                freguesia = valor(Coluna.FREGUESIA),
                concelho = concelho,
                distrito = valor(Coluna.DISTRITO),
                telefone = limparTelefone(valor(Coluna.TELEFONE)),
                email = extrairEmail(valor(Coluna.EMAIL)),
                capacidade = valor(Coluna.CAPACIDADE),
            )
            // O mesmo equipamento pode surgir várias vezes (uma por resposta social): junta a informação.
            porId[id] = porId[id]?.let { juntar(it, lar) } ?: lar
        }

        return ResultadoImportacao(
            lares = porId.values.toList(),
            linhasLidas = dados.size,
            ignoradasOutraResposta = ignoradasResposta,
            ignoradasSemNome = ignoradasNome,
            colunasReconhecidas = mapa.keys.sortedBy { it.ordinal }.map { it.rotulo },
            filtrouPorResposta = filtrar,
        )
    }

    fun eErpi(resposta: String): Boolean {
        val r = normalizar(resposta)
        return "estrutura residencial para pessoas idosas" in r ||
            "estrutura residencial para idosos" in r ||
            Regex("\\berpi\\b").containsMatchIn(r) ||
            "lar de idosos" in r ||
            "lar para idosos" in r ||
            "lar de terceira idade" in r
    }

    fun extrairEmail(texto: String): String = regexEmail.find(texto)?.value?.lowercase().orEmpty()

    fun limparTelefone(texto: String): String {
        if (texto.isBlank()) return ""
        // Mantém apenas o primeiro número quando há vários ("212345678 / 912345678").
        val primeiro = texto.split('/', ';', ',', '|').first()
        val digitos = primeiro.filter { it.isDigit() || it == '+' }
        return if (digitos.count { it.isDigit() } >= 9) digitos else texto.trim()
    }

    private fun juntar(a: Lar, b: Lar) = a.copy(
        entidade = a.entidade.ifBlank { b.entidade },
        natureza = a.natureza.ifBlank { b.natureza },
        codigoPostal = a.codigoPostal.ifBlank { b.codigoPostal },
        localidade = a.localidade.ifBlank { b.localidade },
        freguesia = a.freguesia.ifBlank { b.freguesia },
        distrito = a.distrito.ifBlank { b.distrito },
        telefone = a.telefone.ifBlank { b.telefone },
        email = a.email.ifBlank { b.email },
        capacidade = a.capacidade.ifBlank { b.capacidade },
    )

    private fun mapearColunas(cabecalho: List<String>): Map<Coluna, Int> {
        val nomes = cabecalho.map { normalizar(it).replace('_', ' ').replace(Regex("\\s+"), " ") }
        val usadas = mutableSetOf<Int>()
        val mapa = mutableMapOf<Coluna, Int>()
        for (coluna in Coluna.entries) {
            val indice = coluna.padroes.firstNotNullOfOrNull { padrao ->
                nomes.indices.firstOrNull { it !in usadas && nomes[it] == padrao }
                    ?: if (padrao.length <= 4) null // padrões curtos ("cp", "tel") só por igualdade
                    else nomes.indices.firstOrNull { it !in usadas && padrao in nomes[it] }
            }
            if (indice != null) {
                mapa[coluna] = indice
                usadas += indice
            }
        }
        return mapa
    }

    fun decodificar(bytes: ByteArray): String {
        val texto = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return texto.removePrefix("﻿")
    }

    private fun detetarSeparador(texto: String): Char {
        val amostra = texto.lineSequence().take(10).joinToString("\n")
        return listOf(';', ',', '\t').maxBy { sep -> amostra.count { it == sep } }
    }

    /** Parser CSV (RFC 4180): aspas, aspas duplicadas e quebras de linha dentro de campos. */
    fun parseCsv(texto: String, separador: Char): List<List<String>> {
        val linhas = mutableListOf<List<String>>()
        var linha = mutableListOf<String>()
        val campo = StringBuilder()
        var entreAspas = false
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (entreAspas) {
                if (c == '"') {
                    if (i + 1 < texto.length && texto[i + 1] == '"') {
                        campo.append('"'); i++
                    } else entreAspas = false
                } else campo.append(c)
            } else when (c) {
                '"' -> entreAspas = true
                separador -> { linha.add(campo.toString()); campo.clear() }
                '\r' -> {}
                '\n' -> {
                    linha.add(campo.toString()); campo.clear()
                    linhas.add(linha); linha = mutableListOf()
                }
                else -> campo.append(c)
            }
            i++
        }
        if (campo.isNotEmpty() || linha.isNotEmpty()) {
            linha.add(campo.toString())
            linhas.add(linha)
        }
        return linhas
    }
}
