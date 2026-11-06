package pt.avus.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import pt.avus.app.carta.Envio
import pt.avus.app.carta.GeradorCarta
import pt.avus.app.data.Contacto
import pt.avus.app.data.Documento
import pt.avus.app.data.EstadoContacto
import pt.avus.app.data.ErroImportacao
import pt.avus.app.data.ImportadorCsv
import pt.avus.app.data.Lar
import pt.avus.app.data.Perfil
import pt.avus.app.data.Repositorio
import pt.avus.app.data.CamposPerfil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class EstadoApp(
    val carregado: Boolean = false,
    val lares: List<Lar> = emptyList(),
    val selecionados: Set<String> = emptySet(),
    val perfil: Perfil = emptyMap(),
    val documentos: List<Documento> = emptyList(),
    val contactos: List<Contacto> = emptyList(),
    /** Texto da carta editado à mão (null = usar o texto gerado automaticamente). */
    val cartaPersonalizada: String? = null,
    val aImportar: Boolean = false,
) {
    val laresSelecionados: List<Lar> get() = lares.filter { it.id in selecionados }
    val documentosAnexados: List<Documento> get() = documentos.filter { it.anexado }

    val cartaGerada: String
        get() = GeradorCarta.gerar(perfil, documentosAnexados, documentos.filterNot { it.anexado })

    val carta: String get() = cartaPersonalizada ?: cartaGerada
}

class AvusViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)
    private val _estado = MutableStateFlow(EstadoApp())
    val estado: StateFlow<EstadoApp> = _estado.asStateFlow()

    /** Mensagens curtas para mostrar numa Snackbar. */
    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private var gravarPerfilJob: Job? = null
    private var gravarCartaJob: Job? = null

    init {
        viewModelScope.launch {
            _estado.value = EstadoApp(
                carregado = true,
                lares = repo.carregarLares().sortedBy { it.nome.lowercase() },
                selecionados = repo.carregarSelecao(),
                perfil = repo.carregarPerfil(),
                documentos = repo.carregarDocumentos(),
                contactos = repo.carregarContactos(),
                cartaPersonalizada = repo.carregarCartaPersonalizada(),
            )
        }
    }

    fun mensagemMostrada() { _mensagem.value = null }
    fun mostrar(texto: String) { _mensagem.value = texto }

    // ---------- Lares ----------

    fun importarCsv(uri: Uri) {
        _estado.update { it.copy(aImportar = true) }
        viewModelScope.launch {
            try {
                val bytes = repo.lerBytes(uri)
                val resultado = withContext(Dispatchers.Default) { ImportadorCsv.importar(bytes) }
                if (resultado.lares.isEmpty()) {
                    mostrar(
                        if (resultado.filtrouPorResposta)
                            "Não foram encontradas ERPI no ficheiro (${resultado.linhasLidas} linhas lidas)."
                        else "Não foram encontrados lares no ficheiro."
                    )
                    return@launch
                }
                // Junta à lista atual: permite importar vários ficheiros (ex.: um por distrito).
                val atuais = _estado.value.lares.associateBy { it.id }.toMutableMap()
                var novos = 0
                resultado.lares.forEach { lar ->
                    val existente = atuais[lar.id]
                    if (existente == null) novos++
                    // Não substitui um email que o utilizador tenha acrescentado à mão.
                    atuais[lar.id] = if (existente != null && lar.email.isBlank()) lar.copy(email = existente.email) else lar
                }
                val lista = atuais.values.sortedBy { it.nome.lowercase() }
                _estado.update { it.copy(lares = lista) }
                repo.guardarLares(lista)
                val comEmail = resultado.lares.count { it.temEmail }
                mostrar(
                    "Importados ${resultado.lares.size} lares ($novos novos, $comEmail com email)." +
                        if (!resultado.filtrouPorResposta) " Atenção: o ficheiro não indica a resposta social, por isso foram importadas todas as linhas." else ""
                )
            } catch (e: ErroImportacao) {
                mostrar(e.message ?: "Erro ao importar.")
            } catch (e: Exception) {
                mostrar("Não foi possível ler o ficheiro: ${e.message}")
            } finally {
                _estado.update { it.copy(aImportar = false) }
            }
        }
    }

    fun guardarLar(lar: Lar) {
        val novo = if (lar.id.isBlank()) lar.copy(id = "manual-" + UUID.randomUUID(), manual = true) else lar
        val lista = (_estado.value.lares.filterNot { it.id == novo.id } + novo).sortedBy { it.nome.lowercase() }
        _estado.update { it.copy(lares = lista) }
        viewModelScope.launch { repo.guardarLares(lista) }
    }

    fun apagarLar(id: String) {
        val lista = _estado.value.lares.filterNot { it.id == id }
        _estado.update { it.copy(lares = lista, selecionados = it.selecionados - id) }
        viewModelScope.launch {
            repo.guardarLares(lista)
            repo.guardarSelecao(_estado.value.selecionados)
        }
    }

    fun apagarLaresImportados() {
        val lista = _estado.value.lares.filter { it.manual }
        val ids = lista.map { it.id }.toSet()
        _estado.update { it.copy(lares = lista, selecionados = it.selecionados.intersect(ids)) }
        viewModelScope.launch {
            repo.guardarLares(lista)
            repo.guardarSelecao(_estado.value.selecionados)
        }
    }

    fun alternarSelecao(id: String) = definirSelecao(
        _estado.value.selecionados.let { if (id in it) it - id else it + id }
    )

    fun selecionarTodos(ids: Collection<String>) = definirSelecao(_estado.value.selecionados + ids)
    fun limparSelecao() = definirSelecao(emptySet())

    private fun definirSelecao(ids: Set<String>) {
        _estado.update { it.copy(selecionados = ids) }
        viewModelScope.launch { repo.guardarSelecao(ids) }
    }

    // ---------- Perfil ----------

    fun atualizarCampo(chave: String, valor: String) {
        _estado.update { it.copy(perfil = it.perfil + (chave to valor)) }
        // Grava pouco depois de o utilizador parar de escrever.
        gravarPerfilJob?.cancel()
        gravarPerfilJob = viewModelScope.launch {
            delay(400)
            repo.guardarPerfil(_estado.value.perfil)
        }
    }

    // ---------- Documentos ----------

    fun anexarDocumento(docId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val (ficheiro, nomeOriginal) = repo.copiarDocumento(uri)
                val anterior = _estado.value.documentos.firstOrNull { it.id == docId }?.ficheiro
                atualizarDocumentos { docs ->
                    docs.map { if (it.id == docId) it.copy(ficheiro = ficheiro, nomeOriginal = nomeOriginal) else it }
                }
                anterior?.let { repo.apagarFicheiroDocumento(it) }
            } catch (e: Exception) {
                mostrar("Não foi possível anexar o ficheiro: ${e.message}")
            }
        }
    }

    fun removerAnexo(docId: String) {
        val anterior = _estado.value.documentos.firstOrNull { it.id == docId }?.ficheiro ?: return
        atualizarDocumentos { docs -> docs.map { if (it.id == docId) it.copy(ficheiro = null, nomeOriginal = null) else it } }
        viewModelScope.launch { repo.apagarFicheiroDocumento(anterior) }
    }

    fun adicionarDocumento(nome: String) {
        if (nome.isBlank()) return
        atualizarDocumentos { it + Documento("doc-" + UUID.randomUUID(), nome.trim(), personalizado = true) }
    }

    fun apagarDocumento(docId: String) {
        val doc = _estado.value.documentos.firstOrNull { it.id == docId } ?: return
        atualizarDocumentos { docs -> docs.filterNot { it.id == docId } }
        doc.ficheiro?.let { viewModelScope.launch { repo.apagarFicheiroDocumento(it) } }
    }

    private fun atualizarDocumentos(f: (List<Documento>) -> List<Documento>) {
        _estado.update { it.copy(documentos = f(it.documentos)) }
        viewModelScope.launch { repo.guardarDocumentos(_estado.value.documentos) }
    }

    fun ficheiroDocumento(doc: Documento) = doc.ficheiro?.let { repo.ficheiroDocumento(it) }

    // ---------- Carta ----------

    fun editarCarta(texto: String) {
        // Se o texto voltar a ser igual ao gerado, deixa de contar como "editado à mão".
        val personalizada = texto.takeIf { it != _estado.value.cartaGerada }
        _estado.update { it.copy(cartaPersonalizada = personalizada) }
        gravarCartaJob?.cancel()
        gravarCartaJob = viewModelScope.launch {
            delay(400)
            repo.guardarCartaPersonalizada(personalizada)
        }
    }

    fun reporCarta() {
        gravarCartaJob?.cancel()
        _estado.update { it.copy(cartaPersonalizada = null) }
        viewModelScope.launch { repo.guardarCartaPersonalizada(null) }
    }

    /** Anexos para o email: documentos do utente + (opcionalmente) a carta em PDF. */
    suspend fun prepararAnexos(texto: String, incluirPdf: Boolean): List<Uri> {
        val app = getApplication<Application>()
        val e = _estado.value
        val uris = e.documentosAnexados.mapNotNull { doc ->
            ficheiroDocumento(doc)?.takeIf { it.exists() }?.let { Envio.uriPartilhavel(app, it) }
        }
        if (!incluirPdf) return uris
        val pdf = Envio.gerarPdf(
            app,
            GeradorCarta.assunto(e.perfil),
            texto,
            Envio.nomePdf(e.perfil[CamposPerfil.NOME].orEmpty()),
        )
        return listOf(Envio.uriPartilhavel(app, pdf)) + uris
    }

    fun tamanhoAnexosBytes(): Long =
        _estado.value.documentosAnexados.sumOf { ficheiroDocumento(it)?.length() ?: 0L }

    // ---------- Acompanhamento ----------

    fun registarEnvio(larIds: Collection<String>) {
        val agora = System.currentTimeMillis()
        val atuais = _estado.value.contactos.associateBy { it.larId }.toMutableMap()
        larIds.forEach { id ->
            val existente = atuais[id]
            atuais[id] = existente?.copy(dataEnvio = agora, dataAtualizacao = agora,
                estado = if (existente.estado == EstadoContacto.SEM_RESPOSTA) EstadoContacto.ENVIADO else existente.estado)
                ?: Contacto(id, EstadoContacto.ENVIADO, agora, agora)
        }
        guardarContactos(atuais.values.toList())
    }

    fun atualizarContacto(contacto: Contacto) {
        val c = contacto.copy(dataAtualizacao = System.currentTimeMillis())
        guardarContactos(_estado.value.contactos.filterNot { it.larId == c.larId } + c)
    }

    fun apagarContacto(larId: String) = guardarContactos(_estado.value.contactos.filterNot { it.larId == larId })

    private fun guardarContactos(lista: List<Contacto>) {
        _estado.update { it.copy(contactos = lista) }
        viewModelScope.launch { repo.guardarContactos(lista) }
    }
}
