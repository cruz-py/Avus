package pt.avus.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Guarda tudo em ficheiros JSON no armazenamento privado da app.
 * Nada sai do telemóvel exceto quando o utilizador envia o email.
 */
class Repositorio(private val context: Context) {

    private val dir get() = context.filesDir
    private val escrita = Mutex()
    private val pastaDocumentos get() = File(dir, "documentos").apply { mkdirs() }

    suspend fun carregarLares(): List<Lar> = lerArray("lares.json") { Lar.fromJson(it) }
    suspend fun guardarLares(lares: List<Lar>) = escreverArray("lares.json", lares.map { it.toJson() })

    suspend fun carregarContactos(): List<Contacto> = lerArray("contactos.json") { Contacto.fromJson(it) }
    suspend fun guardarContactos(c: List<Contacto>) = escreverArray("contactos.json", c.map { it.toJson() })

    suspend fun carregarDocumentos(): List<Documento> {
        val guardados = lerArray("documentos.json") { Documento.fromJson(it) }
        if (guardados.isEmpty()) return DocumentosPadrao.lista
        // Acrescenta documentos padrão novos que ainda não existam (ex.: após atualização da app).
        val ids = guardados.map { it.id }.toSet()
        return guardados + DocumentosPadrao.lista.filter { it.id !in ids && !foiRemovido(it.id) }
    }

    suspend fun guardarDocumentos(d: List<Documento>) {
        escreverArray("documentos.json", d.map { it.toJson() })
        val removidos = DocumentosPadrao.lista.map { it.id } - d.map { it.id }.toSet()
        escreverTexto("documentos_removidos.json", JSONArray(removidos).toString())
    }

    private fun foiRemovido(id: String): Boolean {
        val f = File(dir, "documentos_removidos.json")
        if (!f.exists()) return false
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return false
        return (0 until arr.length()).any { arr.getString(it) == id }
    }

    suspend fun carregarPerfil(): Perfil = withContext(Dispatchers.IO) {
        val f = File(dir, "perfil.json")
        if (!f.exists()) return@withContext emptyMap()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@withContext emptyMap()
        o.keys().asSequence().associateWith { o.optString(it) }
    }

    suspend fun guardarPerfil(p: Perfil) = escreverTexto("perfil.json", JSONObject(p).toString())

    suspend fun carregarSelecao(): Set<String> = withContext(Dispatchers.IO) {
        val f = File(dir, "selecao.json")
        if (!f.exists()) return@withContext emptySet()
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return@withContext emptySet()
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    suspend fun guardarSelecao(ids: Set<String>) = escreverTexto("selecao.json", JSONArray(ids.toList()).toString())

    suspend fun carregarCartaPersonalizada(): String? = withContext(Dispatchers.IO) {
        File(dir, "carta.txt").takeIf { it.exists() }?.readText()
    }

    suspend fun guardarCartaPersonalizada(texto: String?) = withContext(Dispatchers.IO) {
        val f = File(dir, "carta.txt")
        if (texto == null) f.delete() else f.writeText(texto)
    }

    /** Copia o ficheiro escolhido para a pasta privada (o original pode ser movido/apagado). */
    suspend fun copiarDocumento(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val nomeOriginal = nomeDoFicheiro(uri) ?: "documento"
        // Cada documento fica numa subpasta própria para manter o nome original:
        // é esse o nome que o lar vê no anexo do email.
        val seguro = nomeOriginal.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120)
        val nome = UUID.randomUUID().toString() + "/" + seguro
        val destino = File(pastaDocumentos, nome).apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o ficheiro." }
            destino.outputStream().use { input.copyTo(it) }
        }
        nome to nomeOriginal
    }

    fun ficheiroDocumento(nome: String): File = File(pastaDocumentos, nome)

    suspend fun apagarFicheiroDocumento(nome: String) = withContext(Dispatchers.IO) {
        val f = ficheiroDocumento(nome)
        f.delete()
        f.parentFile?.takeIf { it != pastaDocumentos }?.delete()
    }

    fun nomeDoFicheiro(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    suspend fun lerBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri).use {
            requireNotNull(it) { "Não foi possível abrir o ficheiro." }.readBytes()
        }
    }

    private suspend fun <T> lerArray(nome: String, f: (JSONObject) -> T): List<T> = withContext(Dispatchers.IO) {
        val ficheiro = File(dir, nome)
        if (!ficheiro.exists()) return@withContext emptyList()
        runCatching { JSONArray(ficheiro.readText()).mapObjects(f) }.getOrDefault(emptyList())
    }

    private suspend fun escreverArray(nome: String, itens: List<JSONObject>) =
        escreverTexto(nome, JSONArray(itens).toString())

    /** Escrita atómica: escreve num temporário e só depois substitui. */
    private suspend fun escreverTexto(nome: String, texto: String) = withContext(Dispatchers.IO) {
        escrita.withLock { escreverAtomico(nome, texto) }
    }

    private fun escreverAtomico(nome: String, texto: String) {
        val tmp = File(dir, "$nome.tmp")
        tmp.writeText(texto)
        val destino = File(dir, nome)
        if (!tmp.renameTo(destino)) {
            destino.writeText(texto)
            tmp.delete()
        }
    }
}
