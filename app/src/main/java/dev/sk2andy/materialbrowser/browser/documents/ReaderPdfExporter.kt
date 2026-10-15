package dev.sk2andy.materialbrowser.browser.documents

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import java.io.IOException
import java.io.OutputStream

/** Text-only, offline PDF export: no browser, scripts, cookies or remote resources. */
internal object ReaderPdfExporter {
    fun write(document: ReaderDocument, output: OutputStream, signal: CancellationSignal?) {
        signal?.throwIfCanceled()
        val text = SpannableStringBuilder().apply {
            append(document.title)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
            setSpan(RelativeSizeSpan(1.5f), 0, length, 0)
            append("\n\n")
            append(document.sourceUrl)
            document.blocks.forEach { block ->
                append("\n\n")
                append(block.text)
            }
        }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(3f, 1f)
            .build()
        val pageOffsets = mutableListOf<Pair<Int, Int>>()
        var line = 0
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line + 1
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= CONTENT_HEIGHT) end++
            pageOffsets += top to (layout.getLineBottom(end - 1) - top)
            if (pageOffsets.size > MAX_PAGES) throw IOException("Article exceeds PDF page limit")
            line = end
        }
        val pdf = PdfDocument()
        try {
            pageOffsets.forEachIndexed { index, (top, height) ->
                signal?.throwIfCanceled()
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                page.canvas.apply {
                    drawColor(Color.WHITE)
                    save()
                    clipRect(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, MARGIN + height)
                    translate(MARGIN.toFloat(), MARGIN.toFloat() - top)
                    layout.draw(this)
                    restore()
                }
                pdf.finishPage(page)
            }
            signal?.throwIfCanceled()
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
    private const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN * 2
    private const val MAX_PAGES = 200
}
