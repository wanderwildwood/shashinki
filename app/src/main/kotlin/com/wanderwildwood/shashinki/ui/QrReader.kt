package com.wanderwildwood.shashinki.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.detector.Detector

/**
 * Reads a code out of a frame of the viewfinder, or out of a picture, if there is one in it.
 *
 * ZXing (Apache-2.0), the reader most code apps are built on. Two looks at each frame, the way
 * a dedicated scanner reads: the square in the middle of the picture, at the camera's full
 * sharpness, for any kind of code it is aimed at — QR, the square and stacked codes, and the
 * barcodes on things in shops — and the whole picture, lighter, for a QR code anywhere in it,
 * so the square can be shown to aim with. Barcodes only in the square: a camera pointed at a
 * shelf would otherwise find them on everything in it.
 */
object QrReader {

    /** What a frame held: a code that reads and is plainly what the camera is pointed at, or
     *  only where one is ([x], [y], in the frame's pixels), to focus on and aim at. */
    sealed class Seen {
        class Code(val result: Result) : Seen()
        class Place(val x: Float, val y: Float) : Seen()
    }

    val SQUARE = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417)
    val BARS = listOf(
        BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_93, BarcodeFormat.CODE_39, BarcodeFormat.CODABAR, BarcodeFormat.ITF,
    )
    val ALL = SQUARE + BARS

    private fun hints(formats: List<BarcodeFormat>) = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to formats,
        // Slower, and finds a code that is small or at an angle in the frame — which, read as
        // often as the reader is free, is time there is to spend.
        DecodeHintType.TRY_HARDER to true,
    )
    private val qrHints = hints(listOf(BarcodeFormat.QR_CODE))
    private val qrReader = MultiFormatReader().apply { setHints(qrHints) }
    private val anyReader = MultiFormatReader()

    /**
     * The code in [frame], or where one is, or null. In the middle square any of [formats]
     * reads once it spans a tenth of the square; elsewhere a QR code only when it is big in the
     * picture, so a photo of a room with a poster on the far wall is not taken over. With
     * [anywhere] (another app asked for a scan), any of [formats] anywhere, at any size.
     */
    @Synchronized
    fun read(frame: CodeFrames.Frame, formats: List<BarcodeFormat>, anywhere: Boolean): Seen? {
        val w = frame.width
        val h = frame.height
        if (anywhere) {
            decode(PlanarYUVLuminanceSource(frame.luma, w, h, 0, 0, w, h, false), formats)?.let { return Seen.Code(it) }
        }
        val side = aim(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        val square = PlanarYUVLuminanceSource(frame.luma, w, h, left, top, side, side, false)
        decode(square, formats)?.let { result ->
            if (anywhere || span(result) >= MIN_SPAN_AIMED * side) return Seen.Code(result)
        }
        // The whole picture at half the size: enough to find a QR code, and light.
        val half = halve(frame)
        return spot(PlanarYUVLuminanceSource(half.luma, half.width, half.height, 0, 0, half.width, half.height, false))
            ?.let { seen -> if (seen is Seen.Place) Seen.Place(seen.x * 2, seen.y * 2) else seen }
    }

    /** The same from a picture off the screen, when the camera gives no copy of its own. */
    @Synchronized
    fun read(frame: Bitmap): Seen? = spot(luminance(frame))

    /** Any code at all in a picture someone chose to read, at any size, or null. */
    @Synchronized
    fun readPicture(picture: Bitmap): Result? = decode(luminance(picture), ALL)

    /** The side of the middle square, in a [w] by [h] frame: three-fifths of its narrow side. */
    fun aim(w: Int, h: Int): Int = (AIM * minOf(w, h)).toInt()

    private fun spot(source: LuminanceSource): Seen? {
        val w = source.width
        val h = source.height
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = qrReader.decodeWithState(bitmap)
            val points = result.resultPoints.orEmpty().filterNotNull()
            when {
                span(result) >= MIN_SPAN * minOf(w, h) -> Seen.Code(result)
                points.isNotEmpty() -> Seen.Place(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
                else -> null
            }
        } catch (e: Exception) {
            // Not readable (often not yet in focus), or a half-read code, a checksum that does
            // not add up: no code, rather than a wrong one. Where one is, if it can be found.
            try {
                val points = Detector(bitmap.blackMatrix).detect(qrHints).points.filterNotNull()
                if (points.isEmpty()) null
                else Seen.Place(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
            } catch (e: Exception) {
                null
            }
        } finally {
            qrReader.reset()
        }
    }

    private fun decode(source: LuminanceSource, formats: List<BarcodeFormat>): Result? = try {
        anyReader.decode(BinaryBitmap(HybridBinarizer(source)), hints(formats))
    } catch (e: Exception) {
        null
    } finally {
        anyReader.reset()
    }

    private fun span(result: Result): Float {
        val points = result.resultPoints.orEmpty().filterNotNull()
        if (points.size < 2) return 0f
        return maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
    }

    private fun luminance(picture: Bitmap): LuminanceSource {
        val w = picture.width
        val h = picture.height
        val pixels = IntArray(w * h)
        picture.getPixels(pixels, 0, w, 0, 0, w, h)
        return RGBLuminanceSource(w, h, pixels)
    }

    private fun halve(frame: CodeFrames.Frame): CodeFrames.Frame {
        val w = frame.width / 2
        val h = frame.height / 2
        val out = ByteArray(w * h)
        val src = frame.luma
        for (y in 0 until h) {
            val row = 2 * y * frame.width
            for (x in 0 until w) out[y * w + x] = src[row + 2 * x]
        }
        return CodeFrames.Frame(out, w, h, frame.front)
    }

    /** Of the frame's narrow side: about a fifth, a code held up to the lens or close to it. */
    private const val MIN_SPAN = 0.18f
    /** The middle square, of the frame's narrow side. */
    private const val AIM = 0.6f
    /** Of the square's side, between a code's outer marks: a code aimed at reads from about a
     *  tenth of the square up, where the whole picture wants nearly a fifth. */
    private const val MIN_SPAN_AIMED = 0.1f
}
