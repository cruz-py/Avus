package pt.avus.app.letter

import pt.avus.app.data.ProfileFields as C
import pt.avus.app.data.Document
import pt.avus.app.data.Profile
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Generates the pre-registration letter (in European Portuguese) from the resident's profile. */
object LetterGenerator {

    private val ptPT: Locale = Locale.forLanguageTag("pt-PT")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val inputDateFormat = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    private val longDateFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ptPT)

    fun subject(profile: Profile): String {
        val name = profile[C.NAME].orEmpty().ifBlank { "utente" }
        return "Pedido de inscrição em ERPI – $name"
    }

    fun missingFields(profile: Profile): List<String> =
        C.requiredLabels.filterKeys { profile[it].isNullOrBlank() }.values.toList()

    fun age(birthDate: String?, today: LocalDate = LocalDate.now()): Int? {
        val data = parseDate(birthDate) ?: return null
        return Period.between(data, today).years.takeIf { it in 0..130 }
    }

    fun parseDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.trim().replace('-', '/').replace('.', '/')
        return runCatching { LocalDate.parse(cleaned, inputDateFormat) }.getOrNull()
    }

    /**
     * @param attachedDocuments documents attached to the email
     * @param missingDocuments documents not attached yet (to be delivered later)
     */
    fun generate(
        profile: Profile,
        attachedDocuments: List<Document>,
        missingDocuments: List<Document>,
        today: LocalDate = LocalDate.now(),
    ): String {
        fun v(key: String) = profile[key].orEmpty().trim()

        val isFemale = v(C.SEX) == C.FEMALE
        fun g(male: String, female: String) = if (isFemale) female else male

        val name = v(C.NAME).ifBlank { "[nome do utente]" }
        val honorific = g("o Sr.", "a Sra.")
        val age = age(v(C.BIRTH_DATE), today)
        val relativeName = v(C.RELATIVE_NAME).ifBlank { "[o seu nome]" }
        val relationship = v(C.RELATIVE_RELATIONSHIP)

        return buildString {
            appendLine("Exmos. Senhores,")
            appendLine()

            // Introduction
            append("O meu nome é $relativeName")
            if (relationship.isNotBlank()) append(" e sou $relationship de $name") else append(", familiar de $name")
            append(". Venho, por este meio, manifestar o interesse na admissão d$honorific ")
            append(name)
            append(" na vossa Estrutura Residencial para Pessoas Idosas (ERPI)")
            val placeType = v(C.PLACE_TYPE)
            if (placeType == "Permanente" || placeType == "Temporária") append(", em regime de vaga ${placeType.lowercase()}")
            appendLine(".")
            appendLine()

            // Current situation
            val sentences = mutableListOf<String>()
            val facts = mutableListOf<String>()
            if (age != null) facts += "tem $age anos"
            parseDate(v(C.BIRTH_DATE))?.let { facts += "nasceu a ${it.format(dateFormat)}" }
            val address = listOf(v(C.ADDRESS), v(C.POSTAL_CODE_LOCALITY)).filter { it.isNotBlank() }.joinToString(", ")
            if (address.isNotBlank()) facts += "reside em $address"
            if (facts.isNotEmpty()) sentences += g("O utente ", "A utente ") + joinWithAnd(facts) + "."

            when (v(C.CURRENT_SITUATION)) {
                C.HOSPITALISED -> {
                    val place = v(C.CARE_FACILITY)
                    sentences += "Encontra-se atualmente ${g("internado", "internada")}" +
                        (if (place.isNotBlank()) " no $place" else " em meio hospitalar") +
                        ", a aguardar vaga numa resposta residencial" +
                        (v(C.EXPECTED_DISCHARGE).takeIf { it.isNotBlank() }?.let { " (previsão de alta: $it)" } ?: "") + "."
                }
                C.CONTINUING_CARE -> {
                    val place = v(C.CARE_FACILITY)
                    sentences += "Encontra-se atualmente numa unidade de cuidados continuados" +
                        (if (place.isNotBlank()) " ($place)" else "") + "."
                }
                "Em casa" -> sentences += "Encontra-se atualmente no domicílio, mas já não é possível assegurar os cuidados de que necessita em casa."
            }
            if (sentences.isNotEmpty()) {
                appendLine(sentences.joinToString(" "))
                appendLine()
            }

            // Summary
            val summary = listOf(
                "Grau de dependência" to v(C.DEPENDENCY),
                "Mobilidade" to v(C.MOBILITY),
                "Estado cognitivo" to v(C.COGNITIVE),
                "Problemas de saúde" to v(C.HEALTH),
                "Medicação habitual" to v(C.MEDICATION),
                "Cuidados especiais" to v(C.SPECIAL_CARE),
                "Médico de família" to v(C.FAMILY_DOCTOR),
                "Quarto pretendido" to v(C.ROOM).takeIf { it != "Indiferente" }.orEmpty(),
                "Data pretendida de admissão" to v(C.ADMISSION_DATE),
                "Observações" to v(C.NOTES),
            ).filter { it.second.isNotBlank() }
            if (summary.isNotEmpty()) {
                appendLine("Breve caracterização:")
                summary.forEach { (label, value) ->
                    appendLine("• $label: ${value.replace(Regex("\\s*\\n\\s*"), "; ")}")
                }
                appendLine()
            }

            // Identification
            val identification = listOf(
                "NIF" to v(C.NIF),
                "N.º de Segurança Social" to v(C.NISS),
                "N.º de utente do SNS" to v(C.SNS_NUMBER),
                "Cartão de Cidadão" to v(C.CITIZEN_CARD),
                "Estado civil" to v(C.MARITAL_STATUS),
            ).filter { it.second.isNotBlank() }
            if (identification.isNotEmpty()) {
                appendLine("Dados de identificação:")
                identification.forEach { (label, value) -> appendLine("• $label: $value") }
                appendLine()
            }

            // Request
            appendLine(
                "Venho assim solicitar que me informem sobre a existência de vaga ou, não existindo, " +
                    "sobre a possibilidade de inscrição em lista de espera. Agradecia também informação " +
                    "sobre os documentos e procedimentos necessários para formalizar a candidatura, " +
                    "o valor da mensalidade ou da comparticipação familiar e a possibilidade de agendar uma visita."
            )
            appendLine()

            if (attachedDocuments.isNotEmpty()) {
                appendLine("Junto envio, desde já, os seguintes documentos:")
                attachedDocuments.forEach { appendLine("• ${it.name}") }
                if (missingDocuments.isNotEmpty()) {
                    appendLine("Os restantes documentos que considerem necessários poderão ser entregues posteriormente.")
                }
                appendLine()
            } else {
                appendLine("Estou disponível para enviar ou entregar todos os documentos que considerem necessários.")
                appendLine()
            }

            // Contact details
            appendLine("Para qualquer esclarecimento, poderão contactar-me através de:")
            appendLine(relativeName + if (relationship.isNotBlank()) " ($relationship)" else "")
            v(C.RELATIVE_PHONE).takeIf { it.isNotBlank() }?.let { appendLine("Telemóvel: $it") }
            v(C.RELATIVE_EMAIL).takeIf { it.isNotBlank() }?.let { appendLine("Email: $it") }
            v(C.RELATIVE_ADDRESS).takeIf { it.isNotBlank() }?.let { appendLine("Morada: $it") }
            appendLine()
            appendLine("Agradeço desde já a atenção dispensada e fico a aguardar o vosso contacto.")
            appendLine()
            appendLine("Com os melhores cumprimentos,")
            appendLine(relativeName)
            append(today.format(longDateFormat))
        }
    }

    /** "a, b e c" (Portuguese "and") */
    private fun joinWithAnd(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " e " + parts.last()
    }

    /** Adds the recipient at the top of the letter (when sending one email per home). */
    fun addRecipientHeader(text: String, homeName: String): String = "À Direção Técnica – $homeName\n\n$text"
}
