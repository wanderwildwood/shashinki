package com.wanderwildwood.shashinki.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader against frames shaped like the camera's copy (960 by 1280 upright, grey), with a
 * code drawn into them at a size and place: what reads, what is only found, what is let be.
 */
class QrReaderTest {

    private val w = 960
    private val h = 1280

    /** A grey frame with [format] encoding [text] drawn [px] modules wide at ([cx], [cy]). */
    private fun frame(format: BarcodeFormat, text: String, px: Int, cx: Int, cy: Int): CodeFrames.Frame {
        val luma = ByteArray(w * h) { 150.toByte() }
        val bars = format in QrReader.BARS
        val matrix = MultiFormatWriter().encode(text, format, 0, 0)
        val mw = matrix.width
        val mh = if (bars) mw / 3 else matrix.height
        val quiet = 6
        val left = cx - (mw * px) / 2
        val top = cy - (mh * px) / 2
        for (y in -quiet * px until (mh + quiet) * px) for (x in -quiet * px until (mw + quiet) * px) {
            val fx = left + x
            val fy = top + y
            if (fx !in 0 until w || fy !in 0 until h) continue
            val mx = x / px
            val my = y / px
            val black = x >= 0 && y >= 0 && mx < mw && my < mh && matrix.get(mx, if (bars) 0 else my)
            luma[fy * w + fx] = if (black) 20 else 235.toByte()
        }
        return CodeFrames.Frame(luma, w, h, false)
    }

    private val url = "https://example.org/a/fairly/long/path?with=some&query=1"

    @Test fun bigQrInTheMiddleReads() {
        val seen = QrReader.read(frame(BarcodeFormat.QR_CODE, url, 6, w / 2, h / 2), QrReader.ALL, false)
        assertTrue(seen is QrReader.Seen.Code)
        assertEquals(url, (seen as QrReader.Seen.Code).result.text)
    }

    @Test fun smallQrInTheSquareReads() {
        // About a tenth of the narrow side: the old reader let a code this small be.
        val seen = QrReader.read(frame(BarcodeFormat.QR_CODE, url, 3, w / 2, h / 2), QrReader.ALL, false)
        assertTrue("got $seen", seen is QrReader.Seen.Code)
    }

    @Test fun smallQrInACornerIsOnlyFound() {
        val seen = QrReader.read(frame(BarcodeFormat.QR_CODE, url, 4, 150, 170), QrReader.ALL, false)
        assertTrue("got $seen", seen is QrReader.Seen.Place)
    }

    @Test fun barcodeInTheSquareReads() {
        val seen = QrReader.read(frame(BarcodeFormat.EAN_13, "4006381333931", 4, w / 2, h / 2), QrReader.ALL, false)
        assertTrue("got $seen", seen is QrReader.Seen.Code)
        assertEquals(BarcodeFormat.EAN_13, (seen as QrReader.Seen.Code).result.barcodeFormat)
    }

    @Test fun barcodeOutsideTheSquareIsLeftAlone() {
        val seen = QrReader.read(frame(BarcodeFormat.EAN_13, "4006381333931", 3, w / 2, 110), QrReader.ALL, false)
        assertNull(seen)
    }

    @Test fun askedScanReadsAnywhere() {
        val seen = QrReader.read(frame(BarcodeFormat.EAN_13, "4006381333931", 3, w / 2, 110), QrReader.ALL, true)
        assertTrue("got $seen", seen is QrReader.Seen.Code)
    }

    @Test fun dataMatrixReads() {
        val seen = QrReader.read(frame(BarcodeFormat.DATA_MATRIX, "hello there", 10, w / 2, h / 2), QrReader.ALL, false)
        assertTrue("got $seen", seen is QrReader.Seen.Code)
    }
}
