package pt.avus.app.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class ImportResult(
    val homes: List<CareHome>,
    val rowsRead: Int,
    val skippedOtherResponse: Int,
    val skippedNoName: Int,
    val recognizedColumns: List<String>,
    val filteredBySocialResponse: Boolean,
)

class ImportException(message: String) : Exception(message)

/**
 * Reads a CSV of social facilities (e.g. the one produced by tools/cartasocial from the Carta Social,
 * or municipal open data) and extracts the ERPI (care homes). Columns are recognised by their header
 * name, so it copes with format variations (";" or "," separator, UTF-8 or Windows-1252, any column order).
 */
object CsvImporter {

    private enum class CsvColumn(val label: String, val patterns: List<String>) {
        // Order matters: "Nome da entidade" must be claimed by OWNER before NAME looks for "nome".
        OWNER("Entidade", listOf("entidade proprietaria", "nome da entidade", "entidade gestora", "entidade", "instituicao")),
        LEGAL_FORM("Natureza", listOf("natureza juridica", "natureza", "tipo de entidade", "tipo entidade")),
        SOCIAL_RESPONSE("Resposta social", listOf("resposta social", "respostas sociais", "resposta", "valencia", "tipologia")),
        POSTAL_CODE("Código postal", listOf("codigo postal", "cod postal", "cod. postal", "cp")),
        NAME("Nome", listOf("nome do equipamento", "designacao do equipamento", "equipamento", "designacao", "denominacao", "nome")),
        ADDRESS("Morada", listOf("morada", "endereco", "rua")),
        DOOR_NUMBER("Número", listOf("numero policia", "numero de policia", "n policia", "numero porta")),
        LOCALITY("Localidade", listOf("localidade", "local")),
        PARISH("Freguesia", listOf("freguesia")),
        MUNICIPALITY("Concelho", listOf("concelho", "municipio")),
        DISTRICT("Distrito", listOf("distrito")),
        PHONE("Telefone", listOf("telefone", "telef", "contacto telefonico", "telemovel", "tel")),
        EMAIL("Email", listOf("email", "e-mail", "e mail", "correio eletronico", "correio electronico", "mail")),
        CAPACITY("Capacidade", listOf("capacidade", "lotacao")),
        // Municipal open-data Carta Social format (e.g. Cascais): no social-response column,
        // but Sim/Não columns per target group ("d_pes_idos" = elderly people).
        ELDERLY("Pessoas idosas", listOf("d pes idos", "pessoas idosas")),
    }

    private val emailRegex = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    fun importBytes(bytes: ByteArray): ImportResult = importText(decodeBytes(bytes))

    fun importText(text: String): ImportResult {
        val separator = detectSeparator(text)
        val rows = parseCsv(text, separator)
        if (rows.isEmpty()) throw ImportException("O ficheiro está vazio.")

        // The header may not be on the first line (some files have titles above it).
        val (headerIndex, columnMap) = rows.take(20).withIndex()
            .map { (i, l) -> i to mapColumns(l) }
            .maxByOrNull { it.second.size }
            ?: throw ImportException("Não foi encontrado o cabeçalho.")

        if (CsvColumn.NAME !in columnMap) {
            throw ImportException(
                "Não foi encontrada a coluna com o nome do equipamento. " +
                    "Confirme que o ficheiro é um CSV com cabeçalho (ex.: \"Nome\", \"Concelho\", \"Telefone\")."
            )
        }

        val filterBySocialResponse = CsvColumn.SOCIAL_RESPONSE in columnMap
        val filterByElderlyFlag = !filterBySocialResponse && CsvColumn.ELDERLY in columnMap
        var skippedOtherCount = 0
        var skippedNoNameCount = 0
        val byId = LinkedHashMap<String, CareHome>()
        val dataRows = rows.drop(headerIndex + 1).filter { l -> l.any { it.isNotBlank() } }

        for (row in dataRows) {
            fun value(c: CsvColumn) = columnMap[c]?.let { row.getOrNull(it) }?.let(::decodeHtmlEntities)?.trim().orEmpty()

            if ((filterBySocialResponse && !isErpi(value(CsvColumn.SOCIAL_RESPONSE))) ||
                (filterByElderlyFlag && normalizeForSearch(value(CsvColumn.ELDERLY)) !in setOf("sim", "s", "true", "1"))
            ) {
                skippedOtherCount++
                continue
            }
            val name = value(CsvColumn.NAME).ifBlank { value(CsvColumn.OWNER) }
            if (name.isBlank()) {
                skippedNoNameCount++
                continue
            }
            val doorNumber = value(CsvColumn.DOOR_NUMBER).takeUnless { it.isBlank() || it == "0" }
            val address = listOfNotNull(value(CsvColumn.ADDRESS).ifBlank { null }, doorNumber).joinToString(", ")
            // Municipal files have no municipality/district columns: infer them from the parish.
            val place = municipalityForParish(value(CsvColumn.PARISH))
            val municipality = value(CsvColumn.MUNICIPALITY).ifBlank { place?.first.orEmpty() }
            val id = CareHome.generateId(name, address, municipality)
            val home = CareHome(
                id = id,
                name = name,
                owner = value(CsvColumn.OWNER).takeIf { normalizeForSearch(it) != normalizeForSearch(name) }.orEmpty(),
                legalForm = value(CsvColumn.LEGAL_FORM),
                address = address,
                postalCode = value(CsvColumn.POSTAL_CODE),
                locality = value(CsvColumn.LOCALITY),
                parish = value(CsvColumn.PARISH),
                municipality = municipality,
                district = value(CsvColumn.DISTRICT).ifBlank { place?.second.orEmpty() },
                phone = cleanPhone(value(CsvColumn.PHONE)),
                email = extractEmail(value(CsvColumn.EMAIL)),
                capacity = value(CsvColumn.CAPACITY),
            )
            // The same facility may appear several times (once per social response): merge the rows.
            byId[id] = byId[id]?.let { merge(it, home) } ?: home
        }

        return ImportResult(
            homes = byId.values.toList(),
            rowsRead = dataRows.size,
            skippedOtherResponse = skippedOtherCount,
            skippedNoName = skippedNoNameCount,
            recognizedColumns = columnMap.keys.sortedBy { it.ordinal }.map { it.label },
            filteredBySocialResponse = filterBySocialResponse || filterByElderlyFlag,
        )
    }

    fun isErpi(response: String): Boolean {
        val r = normalizeForSearch(response)
        return "estrutura residencial para pessoas idosas" in r ||
            "estrutura residencial para idosos" in r ||
            Regex("\\berpi\\b").containsMatchIn(r) ||
            "lar de idosos" in r ||
            "lar para idosos" in r ||
            "lar de terceira idade" in r
    }

    /**
     * Parish (normalised) → (municipality, district), for files without those columns.
     * To support another municipal portal, add its parishes here.
     */
    private val parishes: Map<String, Pair<String, String>> = mapOf(
        "cascais e estoril" to ("Cascais" to "Lisboa"),
        "carcavelos e parede" to ("Cascais" to "Lisboa"),
        "alcabideche" to ("Cascais" to "Lisboa"),
        "sao domingos de rana" to ("Cascais" to "Lisboa"),
        // Pre-2013 parishes (before the administrative merger), still found in older files.
        "cascais" to ("Cascais" to "Lisboa"),
        "estoril" to ("Cascais" to "Lisboa"),
        "carcavelos" to ("Cascais" to "Lisboa"),
        "parede" to ("Cascais" to "Lisboa"),
    )

    fun municipalityForParish(parish: String): Pair<String, String>? {
        val f = normalizeForSearch(parish)
            .replace(Regex("^(u\\.\\s*f\\.|uf|uniao (das|de) freguesias( de| do| da| dos| das)?)\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return parishes[f]
    }

    private val htmlEntities = mapOf("&quot;" to "\"", "&amp;" to "&", "&apos;" to "'", "&#39;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ")

    /** Some portals export text with HTML entities (e.g. "&quot;Os Vinhais&quot;"). */
    fun decodeHtmlEntities(text: String): String =
        if ('&' !in text) text
        else htmlEntities.entries.fold(text) { t, (e, c) -> t.replace(e, c) }

    fun extractEmail(text: String): String = emailRegex.find(text)?.value?.lowercase().orEmpty()

    fun cleanPhone(text: String): String {
        if (text.isBlank()) return ""
        // Keep only the first number when there are several ("212345678 / 912345678").
        val firstNumber = text.split('/', ';', ',', '|').first()
        val digits = firstNumber.filter { it.isDigit() || it == '+' }
        return if (digits.count { it.isDigit() } >= 9) digits else text.trim()
    }

    private fun merge(a: CareHome, b: CareHome) = a.copy(
        owner = a.owner.ifBlank { b.owner },
        legalForm = a.legalForm.ifBlank { b.legalForm },
        postalCode = a.postalCode.ifBlank { b.postalCode },
        locality = a.locality.ifBlank { b.locality },
        parish = a.parish.ifBlank { b.parish },
        district = a.district.ifBlank { b.district },
        phone = a.phone.ifBlank { b.phone },
        email = a.email.ifBlank { b.email },
        capacity = a.capacity.ifBlank { b.capacity },
    )

    private fun mapColumns(header: List<String>): Map<CsvColumn, Int> {
        val names = header.map { normalizeForSearch(it).replace('_', ' ').replace(Regex("\\s+"), " ") }
        val usedIndices = mutableSetOf<Int>()
        val columnMap = mutableMapOf<CsvColumn, Int>()
        for (column in CsvColumn.entries) {
            val index = column.patterns.firstNotNullOfOrNull { pattern ->
                names.indices.firstOrNull { it !in usedIndices && names[it] == pattern }
                    ?: if (pattern.length <= 4) null // short patterns ("cp", "tel") only match exactly
                    else names.indices.firstOrNull { it !in usedIndices && pattern in names[it] }
            }
            if (index != null) {
                columnMap[column] = index
                usedIndices += index
            }
        }
        return columnMap
    }

    fun decodeBytes(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    private fun detectSeparator(text: String): Char {
        val sample = text.lineSequence().take(10).joinToString("\n")
        return listOf(';', ',', '\t').maxBy { sep -> sample.count { it == sep } }
    }

    /** RFC 4180 CSV parser: quotes, doubled quotes and line breaks inside fields. */
    fun parseCsv(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                separator -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> {
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
