package pt.avus.app.data

/**
 * Profile of the resident (the person applying) and of the responsible relative.
 * Stored as a field -> value map, so adding fields does not require migrations.
 */
typealias Profile = Map<String, String>

enum class FieldType { TEXT, MULTILINE, OPTIONS, PHONE, EMAIL, NUMBER, DATE }

data class ProfileField(
    val key: String,
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val options: List<String> = emptyList(),
    val hint: String = "",
)

data class ProfileSection(val title: String, val fields: List<ProfileField>)

object ProfileFields {
    const val NAME = "name"
    const val SEX = "sex"
    const val BIRTH_DATE = "birthDate"
    const val MARITAL_STATUS = "maritalStatus"
    const val NIF = "nif"
    const val NISS = "niss"
    const val SNS_NUMBER = "snsNumber"
    /** "Cartão de Cidadão" or "Bilhete de Identidade": decides the ID number label and the documents asked for. */
    const val ID_DOCUMENT = "idDocument"
    /** Number of the ID document (CC or BI; the key predates the BI option). */
    const val CITIZEN_CARD = "citizenCard"
    const val ADDRESS = "address"
    const val POSTAL_CODE_LOCALITY = "postalCodeLocality"

    const val CURRENT_SITUATION = "currentSituation"
    const val CARE_FACILITY = "careFacility"
    const val EXPECTED_DISCHARGE = "expectedDischarge"
    const val DEPENDENCY = "dependency"
    const val MOBILITY = "mobility"
    const val COGNITIVE = "cognitive"
    const val HEALTH = "health"
    const val MEDICATION = "medication"
    const val SPECIAL_CARE = "specialCare"
    const val FAMILY_DOCTOR = "familyDoctor"

    const val PLACE_TYPE = "placeType"
    const val ROOM = "room"
    const val ADMISSION_DATE = "admissionDate"
    const val PREFERRED_AREAS = "preferredAreas"
    const val NOTES = "notes"

    const val RELATIVE_NAME = "relativeName"
    const val RELATIVE_RELATIONSHIP = "relativeRelationship"
    const val RELATIVE_PHONE = "relativePhone"
    const val RELATIVE_EMAIL = "relativeEmail"
    const val RELATIVE_ADDRESS = "relativeAddress"
    const val RELATIVE_BEST_TIME = "relativeBestTime"
    const val RELATIVE_OTHER_CONTACT = "relativeOtherContact"

    const val ID_CC = "Cartão de Cidadão"
    const val ID_BI = "Bilhete de Identidade"

    const val MALE = "Masculino"
    const val FEMALE = "Feminino"
    const val HOSPITALISED = "Internado(a) em hospital"
    const val CONTINUING_CARE = "Em unidade de cuidados continuados"

    /** The resident's own data ("Ficha" tab). */
    val residentSections = listOf(
        ProfileSection(
            "Utente", listOf(
                ProfileField(NAME, "Nome completo"),
                ProfileField(SEX, "Sexo", FieldType.OPTIONS, listOf(MALE, FEMALE)),
                ProfileField(BIRTH_DATE, "Data de nascimento", FieldType.DATE, hint = "dd/mm/aaaa"),
                ProfileField(
                    MARITAL_STATUS, "Estado civil", FieldType.OPTIONS,
                    listOf("Solteiro(a)", "Casado(a)", "União de facto", "Divorciado(a)", "Viúvo(a)"),
                ),
                ProfileField(
                    ID_DOCUMENT, "Documento de identificação", FieldType.OPTIONS, listOf(ID_CC, ID_BI),
                    hint = "Com Bilhete de Identidade, os lares pedem também os comprovativos do NIF e do NISS",
                ),
                ProfileField(CITIZEN_CARD, "N.º do Cartão de Cidadão"),
                ProfileField(NIF, "NIF", FieldType.NUMBER),
                ProfileField(NISS, "N.º de Segurança Social (NISS)", FieldType.NUMBER),
                ProfileField(SNS_NUMBER, "N.º de utente do SNS", FieldType.NUMBER),
                ProfileField(ADDRESS, "Morada"),
                ProfileField(POSTAL_CODE_LOCALITY, "Código postal e localidade"),
            )
        ),
        ProfileSection(
            "Situação atual e saúde", listOf(
                ProfileField(
                    CURRENT_SITUATION, "Onde se encontra atualmente", FieldType.OPTIONS,
                    listOf("Em casa", HOSPITALISED, CONTINUING_CARE, "Outra"),
                ),
                ProfileField(CARE_FACILITY, "Hospital / unidade e serviço", hint = "Ex.: Hospital de Santa Maria, Medicina Interna"),
                ProfileField(EXPECTED_DISCHARGE, "Previsão de alta", hint = "Ex.: próxima semana / a aguardar vaga"),
                ProfileField(
                    DEPENDENCY, "Grau de dependência", FieldType.OPTIONS,
                    listOf("Autónomo(a)", "Parcialmente dependente", "Dependente", "Totalmente dependente"),
                ),
                ProfileField(
                    MOBILITY, "Mobilidade", FieldType.OPTIONS,
                    listOf(
                        "Anda sem apoio",
                        "Anda com apoio (bengala / andarilho)",
                        "Desloca-se em cadeira de rodas",
                        "Acamado(a)",
                    ),
                ),
                ProfileField(
                    COGNITIVE, "Estado cognitivo", FieldType.OPTIONS,
                    listOf("Sem alterações", "Alterações ligeiras de memória", "Demência diagnosticada"),
                ),
                ProfileField(HEALTH, "Principais problemas de saúde", FieldType.MULTILINE),
                ProfileField(MEDICATION, "Medicação habitual", FieldType.MULTILINE),
                ProfileField(
                    SPECIAL_CARE, "Cuidados especiais", FieldType.MULTILINE,
                    hint = "Ex.: dieta, diabetes/insulina, pensos, oxigénio, algaliação",
                ),
                ProfileField(FAMILY_DOCTOR, "Médico de família / centro de saúde"),
            )
        ),
        ProfileSection(
            "Preferências", listOf(
                ProfileField(PLACE_TYPE, "Tipo de vaga", FieldType.OPTIONS, listOf("Permanente", "Temporária", "Indiferente")),
                ProfileField(ROOM, "Quarto", FieldType.OPTIONS, listOf("Individual", "Duplo / partilhado", "Indiferente")),
                ProfileField(ADMISSION_DATE, "Data pretendida de admissão", hint = "Ex.: o mais breve possível"),
                ProfileField(PREFERRED_AREAS, "Zonas preferidas"),
                ProfileField(NOTES, "Outras observações", FieldType.MULTILINE),
            )
        ),
    )

    /** The person who writes to the homes and to whom they reply (shown in its own "Contacto" tab). */
    val contactSection = ProfileSection(
        "Familiar responsável (contacto)", listOf(
            ProfileField(RELATIVE_NAME, "Nome completo"),
            ProfileField(RELATIVE_RELATIONSHIP, "Parentesco", hint = "Ex.: neto, filha"),
            ProfileField(RELATIVE_PHONE, "Telemóvel", FieldType.PHONE),
            ProfileField(RELATIVE_EMAIL, "Email", FieldType.EMAIL),
            ProfileField(RELATIVE_ADDRESS, "Morada", hint = "Inclua o código postal: serve também para ordenar os lares por distância"),
            ProfileField(RELATIVE_BEST_TIME, "Melhor horário para contactar", hint = "Ex.: dias úteis depois das 18h"),
            ProfileField(RELATIVE_OTHER_CONTACT, "Outro contacto", hint = "Ex.: João (irmão), 913 456 789"),
        )
    )

    /** All sections, resident first. */
    val sections: List<ProfileSection> get() = residentSections + contactSection

    /** Fields the letter cannot do without (with the label shown when they are missing). */
    val requiredLabels = linkedMapOf(
        NAME to "nome do utente",
        BIRTH_DATE to "data de nascimento",
        CITIZEN_CARD to "n.º do documento de identificação",
        NIF to "NIF",
        NISS to "NISS",
        RELATIVE_NAME to "nome do familiar",
        RELATIVE_PHONE to "telemóvel do familiar",
    )
    val requiredKeys: Set<String> get() = requiredLabels.keys

    fun usesBi(profile: Profile): Boolean = profile[ID_DOCUMENT] == ID_BI

    /** Label shown for [field], adapted to the profile (CC vs BI number). */
    fun labelFor(field: ProfileField, profile: Profile): String =
        if (field.key == CITIZEN_CARD && usesBi(profile)) "N.º do Bilhete de Identidade" else field.label

    fun label(key: String): String =
        sections.flatMap { it.fields }.firstOrNull { it.key == key }?.label ?: key
}

/**
 * Documents care homes usually ask for during application/admission. With a Cartão de Cidadão only
 * the card is needed (it carries the NIF, NISS and SNS numbers); with a Bilhete de Identidade the
 * homes also ask for proof of those numbers.
 */
object DefaultDocuments {
    private enum class AppliesTo { ALWAYS, CC, BI }

    private val entries = listOf(
        Document("resident_id_card", "Cartão de Cidadão do utente", "Frente e verso. Já inclui o NIF, o NISS e o n.º de utente do SNS.") to AppliesTo.CC,
        Document("resident_bi", "Bilhete de Identidade do utente", "Frente e verso") to AppliesTo.BI,
        Document("tax_number", "Cartão de contribuinte (NIF) do utente", "Ou outro documento com o número de contribuinte") to AppliesTo.BI,
        Document("social_security_number", "Comprovativo do n.º de Segurança Social (NISS)", "Ex.: cartão ou declaração da Segurança Social") to AppliesTo.BI,
        Document("sns_card", "Cartão de utente do SNS") to AppliesTo.BI,
        Document("medical_report", "Relatório médico atualizado", "Diagnósticos, grau de dependência e cuidados necessários") to AppliesTo.ALWAYS,
        Document("medication_list", "Guia terapêutica / lista de medicação") to AppliesTo.ALWAYS,
        Document("social_report", "Relatório social do hospital", "Se estiver internado — pedir à assistente social") to AppliesTo.ALWAYS,
        Document("tax_return", "Declaração de IRS e nota de liquidação", "Do último ano, ou certidão de isenção") to AppliesTo.ALWAYS,
        Document("income_proof", "Comprovativo de pensões e rendimentos", "Declaração anual da Segurança Social / CGA") to AppliesTo.ALWAYS,
        Document("proof_of_address", "Comprovativo de morada") to AppliesTo.ALWAYS,
        Document("photo", "Fotografia tipo passe") to AppliesTo.ALWAYS,
    )

    val list: List<Document> = entries.map { it.first }

    /** Defaults dropped in later versions (the responsible relative does not submit an ID). */
    val retiredIds = setOf("relative_id_card")

    private val rules = entries.associate { it.first.id to it.second }

    /** Whether a document applies to this resident (custom documents always do). */
    fun appliesTo(documentId: String, profile: Profile): Boolean = when (rules[documentId]) {
        AppliesTo.CC -> !ProfileFields.usesBi(profile)
        AppliesTo.BI -> ProfileFields.usesBi(profile)
        else -> true
    }
}
