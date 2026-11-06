package pt.avus.app.data

/**
 * Ficha do utente (a pessoa a inscrever) e do familiar responsável.
 * Guardada como mapa campo -> valor, para que acrescentar campos não obrigue a migrações.
 */
typealias Perfil = Map<String, String>

enum class TipoCampo { TEXTO, MULTILINHA, OPCOES, TELEFONE, EMAIL, NUMERO, DATA }

data class Campo(
    val chave: String,
    val rotulo: String,
    val tipo: TipoCampo = TipoCampo.TEXTO,
    val opcoes: List<String> = emptyList(),
    val ajuda: String = "",
)

data class SecaoPerfil(val titulo: String, val campos: List<Campo>)

object CamposPerfil {
    const val NOME = "nome"
    const val SEXO = "sexo"
    const val DATA_NASCIMENTO = "dataNascimento"
    const val ESTADO_CIVIL = "estadoCivil"
    const val NIF = "nif"
    const val NISS = "niss"
    const val NUM_UTENTE = "numUtente"
    const val CC = "cc"
    const val MORADA = "morada"
    const val CP_LOCALIDADE = "cpLocalidade"

    const val SITUACAO = "situacao"
    const val LOCAL_INTERNAMENTO = "localInternamento"
    const val PREVISAO_ALTA = "previsaoAlta"
    const val DEPENDENCIA = "dependencia"
    const val MOBILIDADE = "mobilidade"
    const val COGNITIVO = "cognitivo"
    const val SAUDE = "saude"
    const val MEDICACAO = "medicacao"
    const val CUIDADOS = "cuidados"
    const val MEDICO = "medico"

    const val TIPO_VAGA = "tipoVaga"
    const val QUARTO = "quarto"
    const val DATA_ENTRADA = "dataEntrada"
    const val ZONAS = "zonas"
    const val OBSERVACOES = "observacoes"

    const val RESP_NOME = "respNome"
    const val RESP_PARENTESCO = "respParentesco"
    const val RESP_TELEFONE = "respTelefone"
    const val RESP_EMAIL = "respEmail"
    const val RESP_MORADA = "respMorada"

    const val MASCULINO = "Masculino"
    const val FEMININO = "Feminino"
    const val INTERNADO = "Internado(a) em hospital"
    const val CUIDADOS_CONTINUADOS = "Em unidade de cuidados continuados"

    val secoes = listOf(
        SecaoPerfil(
            "Utente", listOf(
                Campo(NOME, "Nome completo"),
                Campo(SEXO, "Sexo", TipoCampo.OPCOES, listOf(MASCULINO, FEMININO)),
                Campo(DATA_NASCIMENTO, "Data de nascimento", TipoCampo.DATA, ajuda = "dd/mm/aaaa"),
                Campo(
                    ESTADO_CIVIL, "Estado civil", TipoCampo.OPCOES,
                    listOf("Solteiro(a)", "Casado(a)", "União de facto", "Divorciado(a)", "Viúvo(a)"),
                ),
                Campo(NIF, "NIF", TipoCampo.NUMERO),
                Campo(NISS, "N.º de Segurança Social (NISS)", TipoCampo.NUMERO),
                Campo(NUM_UTENTE, "N.º de utente do SNS", TipoCampo.NUMERO),
                Campo(CC, "N.º do Cartão de Cidadão"),
                Campo(MORADA, "Morada"),
                Campo(CP_LOCALIDADE, "Código postal e localidade"),
            )
        ),
        SecaoPerfil(
            "Situação atual e saúde", listOf(
                Campo(
                    SITUACAO, "Onde se encontra atualmente", TipoCampo.OPCOES,
                    listOf("Em casa", INTERNADO, CUIDADOS_CONTINUADOS, "Outra"),
                ),
                Campo(LOCAL_INTERNAMENTO, "Hospital / unidade e serviço", ajuda = "Ex.: Hospital de Santa Maria, Medicina Interna"),
                Campo(PREVISAO_ALTA, "Previsão de alta", ajuda = "Ex.: próxima semana / a aguardar vaga"),
                Campo(
                    DEPENDENCIA, "Grau de dependência", TipoCampo.OPCOES,
                    listOf("Autónomo(a)", "Parcialmente dependente", "Dependente", "Totalmente dependente"),
                ),
                Campo(
                    MOBILIDADE, "Mobilidade", TipoCampo.OPCOES,
                    listOf(
                        "Anda sem apoio",
                        "Anda com apoio (bengala / andarilho)",
                        "Desloca-se em cadeira de rodas",
                        "Acamado(a)",
                    ),
                ),
                Campo(
                    COGNITIVO, "Estado cognitivo", TipoCampo.OPCOES,
                    listOf("Sem alterações", "Alterações ligeiras de memória", "Demência diagnosticada"),
                ),
                Campo(SAUDE, "Principais problemas de saúde", TipoCampo.MULTILINHA),
                Campo(MEDICACAO, "Medicação habitual", TipoCampo.MULTILINHA),
                Campo(
                    CUIDADOS, "Cuidados especiais", TipoCampo.MULTILINHA,
                    ajuda = "Ex.: dieta, diabetes/insulina, pensos, oxigénio, algaliação",
                ),
                Campo(MEDICO, "Médico de família / centro de saúde"),
            )
        ),
        SecaoPerfil(
            "Preferências", listOf(
                Campo(TIPO_VAGA, "Tipo de vaga", TipoCampo.OPCOES, listOf("Permanente", "Temporária", "Indiferente")),
                Campo(QUARTO, "Quarto", TipoCampo.OPCOES, listOf("Individual", "Duplo / partilhado", "Indiferente")),
                Campo(DATA_ENTRADA, "Data pretendida de admissão", ajuda = "Ex.: o mais breve possível"),
                Campo(ZONAS, "Zonas preferidas"),
                Campo(OBSERVACOES, "Outras observações", TipoCampo.MULTILINHA),
            )
        ),
        SecaoPerfil(
            "Familiar responsável (contacto)", listOf(
                Campo(RESP_NOME, "Nome completo"),
                Campo(RESP_PARENTESCO, "Parentesco", ajuda = "Ex.: neto, filha"),
                Campo(RESP_TELEFONE, "Telemóvel", TipoCampo.TELEFONE),
                Campo(RESP_EMAIL, "Email", TipoCampo.EMAIL),
                Campo(RESP_MORADA, "Morada"),
            )
        ),
    )

    /** Campos sem os quais a carta não faz sentido (com o nome a mostrar quando faltam). */
    val obrigatoriosRotulos = linkedMapOf(
        NOME to "nome do utente",
        DATA_NASCIMENTO to "data de nascimento",
        RESP_NOME to "nome do familiar",
        RESP_TELEFONE to "telemóvel do familiar",
    )
    val obrigatorios: Set<String> get() = obrigatoriosRotulos.keys

    fun rotulo(chave: String): String =
        secoes.flatMap { it.campos }.firstOrNull { it.chave == chave }?.rotulo ?: chave
}

/** Documentos habitualmente pedidos pelas ERPI no processo de inscrição/admissão. */
object DocumentosPadrao {
    val lista = listOf(
        Documento("cc_utente", "Cartão de Cidadão do utente", "Ou Bilhete de Identidade + cartão de contribuinte"),
        Documento("sns", "Cartão de utente do SNS"),
        Documento("niss", "Comprovativo do n.º de Segurança Social (NISS)"),
        Documento("relatorio_medico", "Relatório médico atualizado", "Diagnósticos, grau de dependência e cuidados necessários"),
        Documento("medicacao", "Guia terapêutica / lista de medicação"),
        Documento("relatorio_social", "Relatório social do hospital", "Se estiver internado — pedir à assistente social"),
        Documento("irs", "Declaração de IRS e nota de liquidação", "Do último ano, ou certidão de isenção"),
        Documento("pensoes", "Comprovativo de pensões e rendimentos", "Declaração anual da Segurança Social / CGA"),
        Documento("morada", "Comprovativo de morada"),
        Documento("cc_responsavel", "Cartão de Cidadão do familiar responsável"),
        Documento("foto", "Fotografia tipo passe"),
    )
}
