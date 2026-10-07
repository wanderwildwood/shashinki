package com.wanderwildwood.shashinki.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.result.ParsedResult
import com.google.zxing.client.result.ResultParser
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.detector.Detector

/**
 * Reads a QR code out of a frame of the viewfinder, if there is one in it.
 *
 * ZXing (Apache-2.0), the reader most QR apps are built on, and its parser, which already knows
 * what a Wi-Fi code, a contact card, a web address and the rest look like. Only QR: a camera
 * pointed at a shelf would otherwise find barcodes on everything in it.
 */
object QrReader {

    /** What a frame held: a code that reads and is plainly what the camera is pointed at, or
     *  only where one is ([x], [y], in the frame's pixels), to focus on. */
    sealed class Seen {
        class Code(val parsed: ParsedResult) : Seen()
        class Place(val x: Float, val y: Float) : Seen()
    }

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        // Slower, and finds a code that is small or at an angle in the frame — which, read a
        // few times a second, is time there is to spend.
        DecodeHintType.TRY_HARDER to true,
    )
    private val reader = MultiFormatReader().apply { setHints(hints) }

    /**
     * The code in [frame], or where one is, or null. A code is only reported when it is big in
     * the picture, its corners at least [MIN_SPAN] of the frame's narrow side apart, so a photo
     * of a room with a poster on the far wall is not taken over; a smaller one is focused on
     * instead, and reads once the camera is brought to it.
     */
    @Synchronized
    fun read(frame: Bitmap): Seen? {
        val w = frame.width
        val h = frame.height
        val pixels = IntArray(w * h)
        frame.getPixels(pixels, 0, w, 0, 0, w, h)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))
        return try {
            val result = reader.decodeWithState(bitmap)
            val points = result.resultPoints.orEmpty().filterNotNull()
            val span = if (points.size < 2) 0f else maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
            when {
                span >= MIN_SPAN * minOf(w, h) -> Seen.Code(ResultParser.parseResult(result))
                points.isNotEmpty() -> Seen.Place(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
                else -> null
            }
        } catch (e: Exception) {
            // Not readable (often not yet in focus), or a half-read code, a checksum that does
            // not add up: no code, rather than a wrong one. Where one is, if it can be found.
            try {
                val points = Detector(bitmap.blackMatrix).detect(hints).points.filterNotNull()
                if (points.isEmpty()) null
                else Seen.Place(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
            } catch (e: Exception) {
                null
            }
        } finally {
            reader.reset()
        }
    }

    /** Of the frame's narrow side: about a fifth, a code held up to the lens or close to it. */
    private const val MIN_SPAN = 0.18f
}
