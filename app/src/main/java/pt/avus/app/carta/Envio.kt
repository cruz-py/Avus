package pt.avus.app.carta

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Envio {

    private fun autoridade(context: Context) = "${context.packageName}.ficheiros"

    fun uriPartilhavel(context: Context, ficheiro: File): Uri =
        FileProvider.getUriForFile(context, autoridade(context), ficheiro)

    /** Gera um PDF A4 com a carta, para enviar em anexo ou imprimir. */
    suspend fun gerarPdf(context: Context, titulo: String, texto: String, nomeFicheiro: String): File =
        withContext(Dispatchers.IO) {
            val largura = 595 // A4 em pontos (1/72")
            val altura = 842
            val margem = 56f
            val larguraTexto = (largura - 2 * margem).toInt()

            val paintTitulo = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                isFakeBoldText = true
            }
            val paintTexto = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f }

            fun layout(t: String, p: TextPaint) =
                StaticLayout.Builder.obtain(t, 0, t.length, p, larguraTexto)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1f)
                    .build()

            val corpo = layout(texto, paintTexto)
            val cabecalho = layout(titulo, paintTitulo)
            val pdf = PdfDocument()
            var linha = 0
            var numeroPagina = 1
            while (linha < corpo.lineCount || numeroPagina == 1) {
                val pagina = pdf.startPage(PdfDocument.PageInfo.Builder(largura, altura, numeroPagina).create())
                val canvas = pagina.canvas
                var y = margem
                if (numeroPagina == 1) {
                    canvas.save()
                    canvas.translate(margem, y)
                    cabecalho.draw(canvas)
                    canvas.restore()
                    y += cabecalho.height + 18f
                }
                // Desenha as linhas que cabem nesta página.
                val inicio = linha
                val topoInicio = corpo.getLineTop(inicio)
                var fim = inicio
                while (fim < corpo.lineCount && corpo.getLineBottom(fim) - topoInicio <= altura - margem - y) fim++
                if (fim > inicio) {
                    canvas.save()
                    canvas.translate(margem, y - topoInicio)
                    canvas.clipRect(0f, topoInicio.toFloat(), larguraTexto.toFloat(), corpo.getLineBottom(fim - 1).toFloat())
                    corpo.draw(canvas)
                    canvas.restore()
                }
                pdf.finishPage(pagina)
                linha = fim
                numeroPagina++
                if (fim == inicio) break // segurança: evita ciclo infinito
            }

            val pasta = File(context.cacheDir, "cartas").apply { mkdirs() }
            val ficheiro = File(pasta, nomeFicheiro)
            ficheiro.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            ficheiro
        }

    fun nomePdf(nomeUtente: String): String {
        val base = pt.avus.app.data.normalizar(nomeUtente)
            .replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "utente" }
        return "Carta_pre-inscricao_$base.pdf"
    }

    /**
     * Abre a app de email com tudo preenchido. O utilizador revê e carrega em "Enviar"
     * — a app nunca envia emails sem o utilizador ver.
     */
    fun abrirEmail(
        context: Context,
        para: List<String>,
        bcc: List<String>,
        assunto: String,
        corpo: String,
        anexos: List<Uri>,
    ): Boolean {
        val mailto = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        val intent = if (anexos.isEmpty()) {
            mailto
        } else {
            Intent(if (anexos.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type = "message/rfc822"
                if (anexos.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, anexos[0])
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(anexos))
                }
                clipData = ClipData.newRawUri("", anexos[0]).apply {
                    anexos.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        intent.apply {
            if (para.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, para.toTypedArray())
            if (bcc.isNotEmpty()) putExtra(Intent.EXTRA_BCC, bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, assunto)
            putExtra(Intent.EXTRA_TEXT, corpo)
        }
        return try {
            context.startActivity(if (anexos.isEmpty()) intent else escolhaSoEmail(context, intent, mailto))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Com anexos é preciso ACTION_SEND, que também é aceite por WhatsApp, Drive, etc.
     * Limitamos a escolha às apps que tratam "mailto:" (apps de email).
     */
    private fun escolhaSoEmail(context: Context, envio: Intent, mailto: Intent): Intent {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(mailto, 0).map { it.activityInfo.packageName }.toSet()
        if (apps.isEmpty()) return Intent.createChooser(envio, "Enviar com…")
        if (apps.size == 1) return Intent(envio).setPackage(apps.first())
        val excluir = pm.queryIntentActivities(envio, 0)
            .map { it.activityInfo }
            .filter { it.packageName !in apps }
            .map { ComponentName(it.packageName, it.name) }
        return Intent.createChooser(envio, "Enviar com…")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluir.toTypedArray())
    }

    fun ligar(context: Context, telefone: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${telefone.filter { it.isDigit() || it == '+' }}".toUri()))
        }
    }

    fun abrirMapa(context: Context, morada: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(morada)}".toUri()))
        }
    }

    fun abrirUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
}
