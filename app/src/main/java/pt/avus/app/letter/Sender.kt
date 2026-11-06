package pt.avus.app.letter

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

object Sender {

    private fun authority(context: Context) = "${context.packageName}.files"

    fun shareableUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /** Renders the letter as an A4 PDF, to attach or print. */
    suspend fun generatePdf(context: Context, title: String, text: String, fileName: String): File =
        withContext(Dispatchers.IO) {
            val pageWidth = 595 // A4 in points (1/72")
            val pageHeight = 842
            val margin = 56f
            val textWidth = (pageWidth - 2 * margin).toInt()

            val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                isFakeBoldText = true
            }
            val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f }

            fun layout(t: String, p: TextPaint) =
                StaticLayout.Builder.obtain(t, 0, t.length, p, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1f)
                    .build()

            val body = layout(text, bodyPaint)
            val header = layout(title, titlePaint)
            val pdf = PdfDocument()
            var line = 0
            var pageNumber = 1
            while (line < body.lineCount || pageNumber == 1) {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                val canvas = page.canvas
                var y = margin
                if (pageNumber == 1) {
                    canvas.save()
                    canvas.translate(margin, y)
                    header.draw(canvas)
                    canvas.restore()
                    y += header.height + 18f
                }
                // Draw the lines that fit on this page.
                val startLine = line
                val startTop = body.getLineTop(startLine)
                var end = startLine
                while (end < body.lineCount && body.getLineBottom(end) - startTop <= pageHeight - margin - y) end++
                if (end > startLine) {
                    canvas.save()
                    canvas.translate(margin, y - startTop)
                    canvas.clipRect(0f, startTop.toFloat(), textWidth.toFloat(), body.getLineBottom(end - 1).toFloat())
                    body.draw(canvas)
                    canvas.restore()
                }
                pdf.finishPage(page)
                line = end
                pageNumber++
                if (end == startLine) break // safety: avoid an infinite loop
            }

            val folder = File(context.cacheDir, "letters").apply { mkdirs() }
            val file = File(folder, fileName)
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            file
        }

    fun pdfFileName(residentName: String): String {
        val base = pt.avus.app.data.normalizeForSearch(residentName)
            .replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "utente" }
        return "Carta_pre-inscricao_$base.pdf"
    }

    /**
     * Opens the email app with everything filled in. The user reviews it and taps "Send"
     * — the app never sends email without the user seeing it.
     */
    fun openEmail(
        context: Context,
        recipients: List<String>,
        bcc: List<String>,
        subject: String,
        body: String,
        attachments: List<Uri>,
    ): Boolean {
        val mailto = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        val intent = if (attachments.isEmpty()) {
            mailto
        } else {
            Intent(if (attachments.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type = "message/rfc822"
                if (attachments.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, attachments[0])
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
                }
                clipData = ClipData.newRawUri("", attachments[0]).apply {
                    attachments.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        intent.apply {
            if (recipients.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
            if (bcc.isNotEmpty()) putExtra(Intent.EXTRA_BCC, bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(if (attachments.isEmpty()) intent else emailAppsOnlyChooser(context, intent, mailto))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Attachments need ACTION_SEND, which WhatsApp, Drive, etc. also accept.
     * Restrict the chooser to apps that handle "mailto:" (email apps).
     */
    private fun emailAppsOnlyChooser(context: Context, sendIntent: Intent, mailto: Intent): Intent {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(mailto, 0).map { it.activityInfo.packageName }.toSet()
        if (apps.isEmpty()) return Intent.createChooser(sendIntent, "Enviar com…")
        if (apps.size == 1) return Intent(sendIntent).setPackage(apps.first())
        val excluded = pm.queryIntentActivities(sendIntent, 0)
            .map { it.activityInfo }
            .filter { it.packageName !in apps }
            .map { ComponentName(it.packageName, it.name) }
        return Intent.createChooser(sendIntent, "Enviar com…")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
    }

    fun dial(context: Context, phone: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${phone.filter { it.isDigit() || it == '+' }}".toUri()))
        }
    }

    fun openMap(context: Context, address: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri()))
        }
    }

    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
}
