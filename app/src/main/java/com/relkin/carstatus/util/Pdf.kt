package com.relkin.carstatus.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Документ отчёта: заголовок и блоки. Рисуется в PDF через [Pdf]. */
class Doc(val title: String, val subtitle: String, val footer: String, val blocks: List<Block>)

/** Тон строки таблицы: выделяет просрочку, итоги, закрытые пункты. */
enum class Tone { NORMAL, STRONG, BAD, WARN, MUTED }

sealed interface Block {
    data class Heading(val text: String) : Block
    data class Para(val text: String, val muted: Boolean = false) : Block
    /** Плитки «значение + подпись». */
    data class Stats(val items: List<Pair<String, String>>) : Block
    data class Col(val title: String, val weight: Float = 1f, val right: Boolean = false)
    data class Row(val cells: List<String>, val tone: Tone = Tone.NORMAL)
    /** Таблица; если у всех колонок пустые заголовки — без строки заголовка. */
    data class Table(val cols: List<Col>, val rows: List<Row>, val empty: String = "Записей нет") : Block
}

/** A4 в пунктах, без сторонних библиотек: android.graphics.pdf.PdfDocument + StaticLayout. */
object Pdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val CW = W - 2 * M
    private const val BOTTOM = H - 44f

    private val ACCENT = Color.rgb(0x1F, 0x4E, 0x8C)
    private val ACCENT_LIGHT = Color.rgb(0xE6, 0xEE, 0xF8)
    private val TEXT = Color.rgb(0x1E, 0x23, 0x2B)
    private val MUTED = Color.rgb(0x6B, 0x74, 0x80)
    private val LINE = Color.rgb(0xDA, 0xE0, 0xE7)
    private val ZEBRA = Color.rgb(0xF6, 0xF8, 0xFA)
    private val TILE = Color.rgb(0xF1, 0xF4, 0xF8)
    private val BAD = Color.rgb(0xC6, 0x28, 0x28)
    private val WARN = Color.rgb(0xB2, 0x6A, 0x00)

    private fun paint(size: Float, color: Int = TEXT, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun layout(text: String, p: TextPaint, width: Float, right: Boolean = false): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(if (right) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).build()

    private fun Canvas.draw(l: StaticLayout, x: Float, y: Float) { save(); translate(x, y); l.draw(this); restore() }

    fun write(doc: Doc, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            Writer(pdf, doc).run { doc.blocks.forEach(::block); finish() }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private class Writer(val pdf: PdfDocument, val doc: Doc) {
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = 0f
        var n = 0

        init { newPage() }

        fun newPage() {
            page?.let { footer(); pdf.finishPage(it) }
            n++
            val p = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            page = p; c = p.canvas
            if (n == 1) {
                c.drawRect(0f, 0f, W.toFloat(), 96f, Paint().apply { color = ACCENT })
                val t = layout(doc.title, paint(19f, Color.WHITE, true), CW)
                c.draw(t, M, 26f)
                val sub = layout(doc.subtitle, paint(9.5f, Color.argb(220, 255, 255, 255)), CW)
                c.draw(sub, M, 26f + t.height + 6f)
                y = 96f + 22f
            } else {
                c.draw(layout(doc.title, paint(8.5f, MUTED), CW), M, 22f)
                c.drawLine(M, 36f, W - M, 36f, Paint().apply { color = LINE; strokeWidth = 0.6f })
                y = 50f
            }
        }

        fun footer() {
            val p = paint(7.5f, MUTED)
            c.drawLine(M, H - 32f, W - M, H - 32f, Paint().apply { color = LINE; strokeWidth = 0.6f })
            c.draw(layout(doc.footer, p, CW - 60), M, H - 26f)
            c.draw(layout("стр. $n", p, CW, right = true), M, H - 26f)
        }

        fun finish() { page?.let { footer(); pdf.finishPage(it) }; page = null }

        fun ensure(h: Float) { if (y + h > BOTTOM && y > 60f) newPage() }

        fun block(b: Block) = when (b) {
            is Block.Heading -> heading(b.text)
            is Block.Para -> {
                val l = layout(b.text, paint(9.5f, if (b.muted) MUTED else TEXT), CW)
                ensure(l.height.toFloat()); c.draw(l, M, y); y += l.height + 8f
            }
            is Block.Stats -> stats(b.items)
            is Block.Table -> table(b)
        }

        fun heading(text: String) {
            val l = layout(text, paint(12.5f, ACCENT, true), CW)
            // заголовок не оставляем последней строкой страницы
            ensure(l.height + 50f)
            y += 6f
            c.draw(l, M, y); y += l.height + 4f
            c.drawLine(M, y, W - M, y, Paint().apply { color = ACCENT; strokeWidth = 1.2f })
            y += 8f
        }

        fun stats(items: List<Pair<String, String>>) {
            val perRow = 3
            val gap = 8f
            val w = (CW - gap * (perRow - 1)) / perRow
            for (row in items.chunked(perRow)) {
                val ls = row.map { (v, label) -> layout(v, paint(13f, TEXT, true), w - 16) to layout(label, paint(8f, MUTED), w - 16) }
                val h = ls.maxOf { (v, l) -> v.height + l.height } + 20f
                ensure(h)
                ls.forEachIndexed { i, (v, l) ->
                    val x = M + i * (w + gap)
                    c.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE })
                    c.drawRect(x, y + 6f, x + 3f, y + h - 6f, Paint().apply { color = ACCENT })
                    c.draw(v, x + 10f, y + 8f)
                    c.draw(l, x + 10f, y + 10f + v.height)
                }
                y += h + gap
            }
            y += 4f
        }

        fun table(t: Block.Table) {
            val total = t.cols.sumOf { it.weight.toDouble() }.toFloat()
            val widths = t.cols.map { CW * it.weight / total }
            val pad = 4f
            val hasHeader = t.cols.any { it.title.isNotBlank() }
            val linePaint = Paint().apply { color = LINE; strokeWidth = 0.5f }

            fun drawRow(cells: List<String>, p: (Int) -> TextPaint, bg: Int?): Float {
                val ls = cells.mapIndexed { i, s -> layout(s.ifBlank { "—" }, p(i), widths[i] - 2 * pad, t.cols[i].right) }
                val h = ls.maxOf { it.height } + 2 * pad + 1f
                return h.also {
                    if (bg != null) c.drawRect(M, y, W - M, y + h, Paint().apply { color = bg })
                    var x = M
                    ls.forEachIndexed { i, l -> c.draw(l, x + pad, y + pad); x += widths[i] }
                    c.drawLine(M, y + h, W - M, y + h, linePaint)
                }
            }

            fun measure(cells: List<String>, p: TextPaint) =
                cells.mapIndexed { i, s -> layout(s.ifBlank { "—" }, p, widths[i] - 2 * pad).height }.max() + 2 * pad + 1f

            val headPaint = paint(8f, ACCENT, true)
            fun header() { if (hasHeader) y += drawRow(t.cols.map { it.title }, { headPaint }, ACCENT_LIGHT) }

            val bodyPaint = paint(8.5f)
            ensure((if (hasHeader) measure(t.cols.map { it.title }, headPaint) else 0f) + 20f)
            header()
            if (t.rows.isEmpty()) {
                val l = layout(t.empty, paint(8.5f, MUTED), CW - 2 * pad)
                c.draw(l, M + pad, y + pad); y += l.height + 2 * pad
            }
            t.rows.forEachIndexed { idx, r ->
                val p = when (r.tone) {
                    Tone.NORMAL -> bodyPaint
                    Tone.STRONG -> paint(8.5f, TEXT, true)
                    Tone.BAD -> paint(8.5f, BAD)
                    Tone.WARN -> paint(8.5f, WARN)
                    Tone.MUTED -> paint(8.5f, MUTED)
                }
                if (y + measure(r.cells, p) > BOTTOM) { newPage(); header() }
                // в таблице «параметр — значение» (без заголовка) названия параметров приглушены
                val first = if (r.tone == Tone.NORMAL && !hasHeader) paint(8.5f, MUTED) else p
                y += drawRow(r.cells, { i -> if (i == 0) first else p }, if (r.tone == Tone.STRONG) ACCENT_LIGHT else if (idx % 2 == 1) ZEBRA else null)
            }
            y += 12f
        }
    }
}
