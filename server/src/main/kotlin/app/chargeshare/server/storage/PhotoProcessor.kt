package app.chargeshare.server.storage

import app.chargeshare.server.util.validationError
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

/** Ergebnis von [PhotoProcessor.normalize]: große und kleine Fassung als JPEG. */
class ProcessedPhoto(val full: ByteArray, val thumbnail: ByteArray)

/**
 * Prüft hochgeladene Bilder und macht daraus ein einheitliches JPEG.
 *
 * Wir speichern nie die Originalbytes: Das Neuberechnen beweist, dass es ein echtes Bild ist, begrenzt die Größe
 * und entfernt EXIF-Daten – vor allem den **GPS-Standort** des Handys, der sonst öffentlich ausgeliefert würde.
 * Erlaubt sind JPEG und PNG (an den Magic Bytes erkannt, nicht am Dateinamen). Die EXIF-Ausrichtung wird nicht
 * ausgewertet: Die Android-App dreht das Bild vor dem Hochladen selbst.
 */
object PhotoProcessor {
    /** Längste Kante nach dem Verkleinern. */
    const val MAX_EDGE_PX = 1600

    /** Längste Kante der kleinen Fassung für Liste, Karte und Vorschau. */
    const val THUMBNAIL_EDGE_PX = 480

    /** Schutz vor „Dekompressionsbomben“: größere Bilder werden gar nicht erst dekodiert. */
    private const val MAX_SOURCE_PIXELS = 40_000_000L
    private const val JPEG_QUALITY = 0.85f

    init {
        // Auf dem Server gibt es keine Oberfläche; ohne das Flag kann AWT in Containern fehlschlagen.
        System.setProperty("java.awt.headless", "true")
    }

    /** @throws app.chargeshare.server.util.AppException (422), wenn die Datei kein erlaubtes Bild ist */
    fun normalize(bytes: ByteArray): ProcessedPhoto {
        if (!bytes.isJpeg() && !bytes.isPng()) throw validationError("photo: only JPEG and PNG images are supported")
        val source = decode(bytes)
        return ProcessedPhoto(
            full = source.scaledToFit(MAX_EDGE_PX).toJpeg(),
            thumbnail = source.scaledToFit(THUMBNAIL_EDGE_PX).toJpeg(),
        )
    }

    private fun decode(bytes: ByteArray): BufferedImage {
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                    ?: throw validationError("photo: not a readable image")
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width < 1 || height < 1 || width.toLong() * height > MAX_SOURCE_PIXELS) {
                        throw validationError("photo: image dimensions are too large")
                    }
                    return reader.read(0)
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: IOException) {
            throw validationError("photo: not a readable image")
        } catch (e: RuntimeException) {
            if (e is app.chargeshare.server.util.AppException) throw e
            throw validationError("photo: not a readable image")
        }
    }

    private fun BufferedImage.scaledToFit(maxEdge: Int): BufferedImage {
        val longest = maxOf(width, height)
        val factor = if (longest > maxEdge) maxEdge.toDouble() / longest else 1.0
        val targetWidth = maxOf(1, (width * factor).toInt())
        val targetHeight = maxOf(1, (height * factor).toInt())
        // Immer in RGB mit weißem Grund neu zeichnen: JPEG kennt keine Transparenz, und so entfallen auch Farbprofil-Sonderfälle.
        val result = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = result.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, targetWidth, targetHeight)
            graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun BufferedImage.toJpeg(): ByteArray {
        val writer: ImageWriter = ImageIO.getImageWritersByFormatName("jpeg").asSequence().first()
        try {
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JPEG_QUALITY
            }
            val out = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(this, null, null), params)
            }
            return out.toByteArray()
        } finally {
            writer.dispose()
        }
    }

    private fun ByteArray.isJpeg(): Boolean =
        size > 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

    private fun ByteArray.isPng(): Boolean =
        size > 8 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() && this[2] == 'N'.code.toByte() &&
            this[3] == 'G'.code.toByte() && this[4] == 0x0D.toByte() && this[5] == 0x0A.toByte() &&
            this[6] == 0x1A.toByte() && this[7] == 0x0A.toByte()
}
