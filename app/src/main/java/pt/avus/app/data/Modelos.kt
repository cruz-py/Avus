package pt.avus.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Uma Estrutura Residencial para Pessoas Idosas (ERPI / lar). */
data class Lar(
    val id: String,
    val nome: String,
    val entidade: String = "",
    val natureza: String = "",
    val morada: String = "",
    val codigoPostal: String = "",
    val localidade: String = "",
    val freguesia: String = "",
    val concelho: String = "",
    val distrito: String = "",
    val telefone: String = "",
    val email: String = "",
    val capacidade: String = "",
    val manual: Boolean = false,
) {
    val temEmail: Boolean get() = email.isNotBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("nome", nome).put("entidade", entidade).put("natureza", natureza)
        .put("morada", morada).put("codigoPostal", codigoPostal).put("localidade", localidade)
        .put("freguesia", freguesia).put("concelho", concelho).put("distrito", distrito)
        .put("telefone", telefone).put("email", email).put("capacidade", capacidade)
        .put("manual", manual)

    companion object {
        fun fromJson(o: JSONObject) = Lar(
            id = o.getString("id"),
            nome = o.optString("nome"),
            entidade = o.optString("entidade"),
            natureza = o.optString("natureza"),
            morada = o.optString("morada"),
            codigoPostal = o.optString("codigoPostal"),
            localidade = o.optString("localidade"),
            freguesia = o.optString("freguesia"),
            concelho = o.optString("concelho"),
            distrito = o.optString("distrito"),
            telefone = o.optString("telefone"),
            email = o.optString("email"),
            capacidade = o.optString("capacidade"),
            manual = o.optBoolean("manual"),
        )

        /** Identificador estável, para que reimportar a mesma lista não duplique lares. */
        fun gerarId(nome: String, morada: String, concelho: String): String =
            listOf(nome, morada, concelho)
                .joinToString("|") { normalizar(it) }
                .hashCode()
                .toUInt()
                .toString(16)
    }
}

/** Um documento que normalmente é pedido pelos lares na inscrição. */
data class Documento(
    val id: String,
    val nome: String,
    val descricao: String = "",
    /** Nome do ficheiro copiado para o armazenamento privado da app (ou null se ainda não anexado). */
    val ficheiro: String? = null,
    val nomeOriginal: String? = null,
    val personalizado: Boolean = false,
) {
    val anexado: Boolean get() = ficheiro != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("nome", nome).put("descricao", descricao)
        .put("ficheiro", ficheiro ?: JSONObject.NULL)
        .put("nomeOriginal", nomeOriginal ?: JSONObject.NULL)
        .put("personalizado", personalizado)

    companion object {
        fun fromJson(o: JSONObject) = Documento(
            id = o.getString("id"),
            nome = o.optString("nome"),
            descricao = o.optString("descricao"),
            ficheiro = o.optStringOrNull("ficheiro"),
            nomeOriginal = o.optStringOrNull("nomeOriginal"),
            personalizado = o.optBoolean("personalizado"),
        )
    }
}

enum class EstadoContacto(val rotulo: String) {
    ENVIADO("Enviado"),
    SEM_RESPOSTA("Sem resposta"),
    RESPONDEU("Respondeu"),
    VISITA("Visita marcada"),
    LISTA_ESPERA("Em lista de espera"),
    VAGA("Vaga disponível"),
    RECUSADO("Sem vaga / recusado"),
}

/** Registo do contacto feito com um lar, para acompanhamento. */
data class Contacto(
    val larId: String,
    val estado: EstadoContacto = EstadoContacto.ENVIADO,
    val dataEnvio: Long = System.currentTimeMillis(),
    val dataAtualizacao: Long = dataEnvio,
    val notas: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("larId", larId).put("estado", estado.name).put("dataEnvio", dataEnvio)
        .put("dataAtualizacao", dataAtualizacao).put("notas", notas)

    companion object {
        fun fromJson(o: JSONObject) = Contacto(
            larId = o.getString("larId"),
            estado = runCatching { EstadoContacto.valueOf(o.getString("estado")) }
                .getOrDefault(EstadoContacto.ENVIADO),
            dataEnvio = o.optLong("dataEnvio"),
            dataAtualizacao = o.optLong("dataAtualizacao", o.optLong("dataEnvio")),
            notas = o.optString("notas"),
        )
    }
}

internal fun JSONObject.optStringOrNull(nome: String): String? =
    if (isNull(nome)) null else optString(nome).ifEmpty { null }

internal inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }

/** Minúsculas e sem acentos, para pesquisas ("Évora" == "evora"). */
fun normalizar(texto: String): String =
    java.text.Normalizer.normalize(texto.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
