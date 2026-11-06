package pt.avus.app.carta

import pt.avus.app.data.CamposPerfil as C
import pt.avus.app.data.Documento
import pt.avus.app.data.Perfil
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Gera a carta de pré-inscrição em português de Portugal a partir da ficha do utente. */
object GeradorCarta {

    private val ptPT: Locale = Locale.forLanguageTag("pt-PT")
    private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val formatoEntrada = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    private val formatoDataExtenso = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ptPT)

    fun assunto(perfil: Perfil): String {
        val nome = perfil[C.NOME].orEmpty().ifBlank { "utente" }
        return "Pedido de inscrição em ERPI – $nome"
    }

    fun camposEmFalta(perfil: Perfil): List<String> =
        C.obrigatoriosRotulos.filterKeys { perfil[it].isNullOrBlank() }.values.toList()

    fun idade(dataNascimento: String?, hoje: LocalDate = LocalDate.now()): Int? {
        val data = parseData(dataNascimento) ?: return null
        return Period.between(data, hoje).years.takeIf { it in 0..130 }
    }

    fun parseData(texto: String?): LocalDate? {
        if (texto.isNullOrBlank()) return null
        val limpo = texto.trim().replace('-', '/').replace('.', '/')
        return runCatching { LocalDate.parse(limpo, formatoEntrada) }.getOrNull()
    }

    /**
     * @param documentosAnexados documentos que vão em anexo no email
     * @param documentosEmFalta documentos ainda não anexados (serão entregues mais tarde)
     */
    fun gerar(
        perfil: Perfil,
        documentosAnexados: List<Documento>,
        documentosEmFalta: List<Documento>,
        hoje: LocalDate = LocalDate.now(),
    ): String {
        fun v(chave: String) = perfil[chave].orEmpty().trim()

        val feminino = v(C.SEXO) == C.FEMININO
        fun g(masc: String, fem: String) = if (feminino) fem else masc

        val nome = v(C.NOME).ifBlank { "[nome do utente]" }
        val tratamento = g("o Sr.", "a Sra.")
        val idade = idade(v(C.DATA_NASCIMENTO), hoje)
        val respNome = v(C.RESP_NOME).ifBlank { "[o seu nome]" }
        val parentesco = v(C.RESP_PARENTESCO)

        return buildString {
            appendLine("Exmos. Senhores,")
            appendLine()

            // Apresentação
            append("O meu nome é $respNome")
            if (parentesco.isNotBlank()) append(" e sou $parentesco de $nome") else append(", familiar de $nome")
            append(". Venho, por este meio, manifestar o interesse na admissão d$tratamento ")
            append(nome)
            append(" na vossa Estrutura Residencial para Pessoas Idosas (ERPI)")
            val tipoVaga = v(C.TIPO_VAGA)
            if (tipoVaga == "Permanente" || tipoVaga == "Temporária") append(", em regime de vaga ${tipoVaga.lowercase()}")
            appendLine(".")
            appendLine()

            // Situação atual
            val frases = mutableListOf<String>()
            val dados = mutableListOf<String>()
            if (idade != null) dados += "tem $idade anos"
            parseData(v(C.DATA_NASCIMENTO))?.let { dados += "nasceu a ${it.format(formatoData)}" }
            val morada = listOf(v(C.MORADA), v(C.CP_LOCALIDADE)).filter { it.isNotBlank() }.joinToString(", ")
            if (morada.isNotBlank()) dados += "reside em $morada"
            if (dados.isNotEmpty()) frases += g("O utente ", "A utente ") + juntarLista(dados) + "."

            when (v(C.SITUACAO)) {
                C.INTERNADO -> {
                    val local = v(C.LOCAL_INTERNAMENTO)
                    frases += "Encontra-se atualmente ${g("internado", "internada")}" +
                        (if (local.isNotBlank()) " no $local" else " em meio hospitalar") +
                        ", a aguardar vaga numa resposta residencial" +
                        (v(C.PREVISAO_ALTA).takeIf { it.isNotBlank() }?.let { " (previsão de alta: $it)" } ?: "") + "."
                }
                C.CUIDADOS_CONTINUADOS -> {
                    val local = v(C.LOCAL_INTERNAMENTO)
                    frases += "Encontra-se atualmente numa unidade de cuidados continuados" +
                        (if (local.isNotBlank()) " ($local)" else "") + "."
                }
                "Em casa" -> frases += "Encontra-se atualmente no domicílio, mas já não é possível assegurar os cuidados de que necessita em casa."
            }
            if (frases.isNotEmpty()) {
                appendLine(frases.joinToString(" "))
                appendLine()
            }

            // Caracterização
            val caracterizacao = listOf(
                "Grau de dependência" to v(C.DEPENDENCIA),
                "Mobilidade" to v(C.MOBILIDADE),
                "Estado cognitivo" to v(C.COGNITIVO),
                "Problemas de saúde" to v(C.SAUDE),
                "Medicação habitual" to v(C.MEDICACAO),
                "Cuidados especiais" to v(C.CUIDADOS),
                "Médico de família" to v(C.MEDICO),
                "Quarto pretendido" to v(C.QUARTO).takeIf { it != "Indiferente" }.orEmpty(),
                "Data pretendida de admissão" to v(C.DATA_ENTRADA),
                "Observações" to v(C.OBSERVACOES),
            ).filter { it.second.isNotBlank() }
            if (caracterizacao.isNotEmpty()) {
                appendLine("Breve caracterização:")
                caracterizacao.forEach { (rotulo, valor) ->
                    appendLine("• $rotulo: ${valor.replace(Regex("\\s*\\n\\s*"), "; ")}")
                }
                appendLine()
            }

            // Identificação
            val identificacao = listOf(
                "NIF" to v(C.NIF),
                "N.º de Segurança Social" to v(C.NISS),
                "N.º de utente do SNS" to v(C.NUM_UTENTE),
                "Cartão de Cidadão" to v(C.CC),
                "Estado civil" to v(C.ESTADO_CIVIL),
            ).filter { it.second.isNotBlank() }
            if (identificacao.isNotEmpty()) {
                appendLine("Dados de identificação:")
                identificacao.forEach { (rotulo, valor) -> appendLine("• $rotulo: $valor") }
                appendLine()
            }

            // Pedido
            appendLine(
                "Venho assim solicitar que me informem sobre a existência de vaga ou, não existindo, " +
                    "sobre a possibilidade de inscrição em lista de espera. Agradecia também informação " +
                    "sobre os documentos e procedimentos necessários para formalizar a candidatura, " +
                    "o valor da mensalidade ou da comparticipação familiar e a possibilidade de agendar uma visita."
            )
            appendLine()

            if (documentosAnexados.isNotEmpty()) {
                appendLine("Junto envio, desde já, os seguintes documentos:")
                documentosAnexados.forEach { appendLine("• ${it.nome}") }
                if (documentosEmFalta.isNotEmpty()) {
                    appendLine("Os restantes documentos que considerem necessários poderão ser entregues posteriormente.")
                }
                appendLine()
            } else {
                appendLine("Estou disponível para enviar ou entregar todos os documentos que considerem necessários.")
                appendLine()
            }

            // Contactos
            appendLine("Para qualquer esclarecimento, poderão contactar-me através de:")
            appendLine(respNome + if (parentesco.isNotBlank()) " ($parentesco)" else "")
            v(C.RESP_TELEFONE).takeIf { it.isNotBlank() }?.let { appendLine("Telemóvel: $it") }
            v(C.RESP_EMAIL).takeIf { it.isNotBlank() }?.let { appendLine("Email: $it") }
            v(C.RESP_MORADA).takeIf { it.isNotBlank() }?.let { appendLine("Morada: $it") }
            appendLine()
            appendLine("Agradeço desde já a atenção dispensada e fico a aguardar o vosso contacto.")
            appendLine()
            appendLine("Com os melhores cumprimentos,")
            appendLine(respNome)
            append(hoje.format(formatoDataExtenso))
        }
    }

    /** "a, b e c" */
    private fun juntarLista(itens: List<String>): String = when (itens.size) {
        0 -> ""
        1 -> itens[0]
        else -> itens.dropLast(1).joinToString(", ") + " e " + itens.last()
    }

    /** Acrescenta o destinatário no topo da carta (envio individual a cada lar). */
    fun personalizar(texto: String, nomeLar: String): String = "À Direção Técnica – $nomeLar\n\n$texto"
}
