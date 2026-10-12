package com.wanderwildwood.shashinki.ui

import android.app.Activity
import android.content.Intent
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType

/**
 * Another app asking for a code to be scanned and handed back: the request ZXing's Barcode
 * Scanner made the custom, which authenticators, wallets and the rest already send. The camera
 * opens with the square to aim with, and the first code read goes straight back as text.
 */
object ScanRequest {

    const val ACTION = "com.google.zxing.client.android.SCAN"

    fun asked(intent: Intent?): Boolean = intent?.action == ACTION

    /** The kinds of code asked for, by name or by ZXing's modes; any, if it did not say. */
    fun formats(intent: Intent?): List<BarcodeFormat> {
        val named = intent?.getStringExtra("SCAN_FORMATS")
            ?.split(',')
            ?.mapNotNull { name -> runCatching { BarcodeFormat.valueOf(name.trim()) }.getOrNull() }
            ?.filter { it in QrReader.ALL }
        if (!named.isNullOrEmpty()) return named
        return when (intent?.getStringExtra("SCAN_MODE")) {
            "QR_CODE_MODE" -> listOf(BarcodeFormat.QR_CODE)
            "PRODUCT_MODE" -> listOf(BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8)
            "ONE_D_MODE" -> QrReader.BARS
            "DATA_MATRIX_MODE" -> listOf(BarcodeFormat.DATA_MATRIX)
            "AZTEC_MODE" -> listOf(BarcodeFormat.AZTEC)
            "PDF417_MODE" -> listOf(BarcodeFormat.PDF_417)
            else -> QrReader.ALL
        }
    }

    /** What the asking app wants said over the viewfinder, if anything. */
    fun prompt(intent: Intent?): String? = intent?.getStringExtra("PROMPT_MESSAGE")?.takeIf { it.isNotBlank() }

    /** The code back to the app that asked, under the names it reads them by. */
    fun answer(activity: Activity, result: Result) {
        val back = Intent()
            .putExtra("SCAN_RESULT", result.text)
            .putExtra("SCAN_RESULT_FORMAT", result.barcodeFormat.toString())
        result.rawBytes?.takeIf { it.isNotEmpty() }?.let { back.putExtra("SCAN_RESULT_BYTES", it) }
        result.resultMetadata?.get(ResultMetadataType.ERROR_CORRECTION_LEVEL)?.let {
            back.putExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL", it.toString())
        }
        result.resultMetadata?.get(ResultMetadataType.UPC_EAN_EXTENSION)?.let {
            back.putExtra("SCAN_RESULT_UPC_EAN_EXTENSION", it.toString())
        }
        activity.setResult(Activity.RESULT_OK, back)
        activity.finish()
    }
}
