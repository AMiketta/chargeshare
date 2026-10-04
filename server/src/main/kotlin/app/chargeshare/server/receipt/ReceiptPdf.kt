package app.chargeshare.server.receipt

import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Zeichnet die Quittung als schlichtes A4-PDF (siehe [SimplePdf]). */
object ReceiptPdf {
    private val dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)
    private val date = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    private const val LEFT = 50.0
    private const val VALUE_X = 200.0
    private const val RIGHT = 545.0
    private const val ROW_HEIGHT = 18.0

    fun render(data: ReceiptData): ByteArray {
        val pdf = SimplePdf()
        header(pdf, data)
        var y = 700.0
        y = section(pdf, y, "Buchung", listOf(
            "Quittungsnummer" to data.bookingId.take(8).uppercase(),
            "Ausgestellt am" to data.issuedAt.format(date),
            "Zahlungsreferenz" to (data.paymentReference ?: "–"),
        ))
        y = section(pdf, y, "Ladevorgang", listOf(
            "Wallbox" to data.wallboxName,
            "Standort" to data.wallboxAddress,
            "Host" to (data.hostName ?: "–"),
            "Beginn" to data.start.format(dateTime),
            "Ende" to data.end.format(dateTime),
        ))
        y = section(pdf, y, "Fahrer", listOf("Name" to data.driverName, "E-Mail" to data.driverEmail))
        y = amounts(pdf, y, data)
        notes(pdf, y - 10.0)
        return pdf.toByteArray()
    }

    private fun header(pdf: SimplePdf, data: ReceiptData) {
        pdf.rect(0.0, 760.0, SimplePdf.PAGE_WIDTH.toDouble(), 82.0, 0.043, 0.431, 0.310)
        pdf.text(LEFT, 805.0, "ChargeShare", size = 22.0, font = PdfFont.BOLD, gray = 1.0)
        pdf.text(LEFT, 780.0, "Quittung über ein Ladeentgelt", size = 12.0, gray = 1.0)
        pdf.text(380.0, 805.0, data.start.format(date), size = 11.0, gray = 1.0)
    }

    private fun section(pdf: SimplePdf, top: Double, title: String, rows: List<Pair<String, String>>): Double {
        var y = top
        pdf.text(LEFT, y, title, size = 12.0, font = PdfFont.BOLD)
        pdf.line(LEFT, RIGHT, y - 5.0)
        y -= ROW_HEIGHT + 4.0
        rows.forEach { (label, value) ->
            pdf.text(LEFT, y, label, gray = 0.35)
            pdf.text(VALUE_X, y, value)
            y -= ROW_HEIGHT
        }
        return y - 10.0
    }

    private fun amounts(pdf: SimplePdf, top: Double, data: ReceiptData): Double {
        var y = top
        pdf.text(LEFT, y, "Abrechnung", size = 12.0, font = PdfFont.BOLD)
        pdf.line(LEFT, RIGHT, y - 5.0)
        y -= ROW_HEIGHT + 4.0
        val kwhLabel = if (data.kwhIsEstimate) "Energie (gebucht, geschätzt)" else "Energie (gemessen)"
        val rows = listOf(
            kwhLabel to (data.kwh?.let { "${decimal(it, 2)} kWh" } ?: "–"),
            "Preis pro kWh" to euro(data.pricePerKwh, 4),
            "Plattformgebühr (${data.platformFeePercent} %, enthalten)" to euro(data.platformFeeAmount, 2),
        )
        rows.forEach { (label, value) ->
            pdf.text(LEFT, y, label, gray = 0.35)
            pdf.text(VALUE_X + 60.0, y, value)
            y -= ROW_HEIGHT
        }
        pdf.line(LEFT, RIGHT, y + 8.0, gray = 0.2)
        y -= 6.0
        pdf.text(LEFT, y, "Gesamtbetrag (bezahlt)", size = 12.0, font = PdfFont.BOLD)
        pdf.text(VALUE_X + 60.0, y, euro(data.totalAmount, 2), size = 12.0, font = PdfFont.BOLD)
        return y - ROW_HEIGHT - 10.0
    }

    private fun notes(pdf: SimplePdf, top: Double) {
        val lines = listOf(
            "Diese Quittung bestätigt die Zahlung des Ladeentgelts über ChargeShare.",
            "Umsatzsteuer ist nicht ausgewiesen. Eine Rechnung mit Steuerangaben des Hosts ist nicht Teil dieser Quittung.",
            "Die Plattformgebühr ist im Gesamtbetrag enthalten und wird dem Host abgezogen.",
        )
        lines.forEachIndexed { index, line -> pdf.text(LEFT, top - index * 13.0, line, size = 8.5, gray = 0.4) }
    }

    private fun decimal(value: BigDecimal, scale: Int): String =
        String.format(Locale.GERMANY, "%,.${scale}f", value.setScale(scale, java.math.RoundingMode.HALF_UP))

    private fun euro(value: BigDecimal, scale: Int): String = "${decimal(value, scale)} €"
}
