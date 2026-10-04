package app.chargeshare.server.receipt

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale

/** Schriftarten des PDF-Standards (müssen nicht eingebettet werden). */
enum class PdfFont(internal val resourceName: String, internal val baseFont: String) {
    REGULAR("F1", "Helvetica"),
    BOLD("F2", "Helvetica-Bold"),
}

/**
 * Minimaler PDF-Schreiber für schlichte, einseitige A4-Dokumente aus Text, Linien und Flächen – ohne Fremdbibliothek.
 *
 * Text wird in WinAnsi (Windows-1252) kodiert; damit sind deutsche Umlaute, ß und € darstellbar. Zeichen außerhalb davon
 * erscheinen als `?`. Koordinaten sind Punkte (1/72 Zoll) mit Ursprung unten links.
 */
class SimplePdf {
    private val content = StringBuilder()

    /** Schreibt [text] mit der Grundlinie bei ([x], [y]). [gray] 0 = schwarz, 1 = weiß. */
    fun text(x: Double, y: Double, text: String, size: Double = 10.0, font: PdfFont = PdfFont.REGULAR, gray: Double = 0.0) {
        content.append("BT ${num(gray)} g /${font.resourceName} ${num(size)} Tf ${num(x)} ${num(y)} Td (${escape(text)}) Tj ET\n")
    }

    /** Waagerechte Linie von [x1] bis [x2] auf Höhe [y]. */
    fun line(x1: Double, x2: Double, y: Double, gray: Double = 0.6) {
        content.append("${num(gray)} G 0.5 w ${num(x1)} ${num(y)} m ${num(x2)} ${num(y)} l S\n")
    }

    /** Gefülltes Rechteck in RGB (0..1). */
    fun rect(x: Double, y: Double, width: Double, height: Double, red: Double, green: Double, blue: Double) {
        content.append("${num(red)} ${num(green)} ${num(blue)} rg ${num(x)} ${num(y)} ${num(width)} ${num(height)} re f\n")
    }

    /** Serialisiert das Dokument (PDF 1.4) mit Kreuzverweistabelle. */
    fun toByteArray(): ByteArray {
        val stream = content.toString().toByteArray(WIN_ANSI)
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Contents 4 0 R " +
                "/Resources << /Font << ${PdfFont.entries.joinToString(" ") { "/${it.resourceName} ${5 + it.ordinal} 0 R" }} >> >> >>",
        ).map { it.toByteArray(WIN_ANSI) } +
            listOf("<< /Length ${stream.size} >>\nstream\n".toByteArray(WIN_ANSI) + stream + "\nendstream".toByteArray(WIN_ANSI)) +
            PdfFont.entries.map { "<< /Type /Font /Subtype /Type1 /BaseFont /${it.baseFont} /Encoding /WinAnsiEncoding >>".toByteArray(WIN_ANSI) }

        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n".toByteArray(WIN_ANSI))
        val offsets = objects.mapIndexed { index, body ->
            val offset = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(WIN_ANSI))
            out.write(body)
            out.write("\nendobj\n".toByteArray(WIN_ANSI))
            offset
        }
        val xrefOffset = out.size()
        out.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray(WIN_ANSI))
        offsets.forEach { out.write("%010d 00000 n \n".format(it).toByteArray(WIN_ANSI)) }
        out.write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n".toByteArray(WIN_ANSI))
        return out.toByteArray()
    }

    private fun num(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun escape(text: String): String {
        val encoder = WIN_ANSI.newEncoder()
        return buildString {
            text.forEach { char ->
                when {
                    char == '\\' || char == '(' || char == ')' -> append('\\').append(char)
                    char < ' ' -> append(' ')
                    encoder.canEncode(char) -> append(char)
                    else -> append('?')
                }
            }
        }
    }

    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        private val WIN_ANSI: Charset = Charset.forName("windows-1252")
    }
}
