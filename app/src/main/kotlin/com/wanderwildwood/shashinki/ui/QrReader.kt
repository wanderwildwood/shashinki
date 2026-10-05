package com.wanderwildwood.shashinki.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.result.ParsedResult
import com.google.zxing.client.result.ResultParser
import com.google.zxing.common.HybridBinarizer

/**
 * Reads a QR code out of a frame of the viewfinder, if there is one in it.
 *
 * ZXing (Apache-2.0), the reader most QR apps are built on, and its parser, which already knows
 * what a Wi-Fi code, a contact card, a web address and the rest look like. Only QR: a camera
 * pointed at a shelf would otherwise find barcodes on everything in it.
 */
object QrReader {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                // Slower, and finds a code that is small or at an angle in the frame — which,
                // read twice a second, is time there is to spend.
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    /** The code in [frame], parsed into what it says, or null when there is none. */
    @Synchronized
    fun read(frame: Bitmap): ParsedResult? {
        val w = frame.width
        val h = frame.height
        val pixels = IntArray(w * h)
        frame.getPixels(pixels, 0, w, 0, 0, w, h)
        return try {
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels))))
            ResultParser.parseResult(result)
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            // A half-read code, a checksum that does not add up: no code, rather than a wrong one.
            null
        } finally {
            reader.reset()
        }
    }
}
