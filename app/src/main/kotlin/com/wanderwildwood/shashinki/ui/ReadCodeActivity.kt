package com.wanderwildwood.shashinki.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sourceforge.opencamera.R

/**
 * "Read code" in the share sheet: a picture with a code in it — a screenshot of a ticket, a
 * photo of a poster — read as the viewfinder would, and shown on the same screen. Any kind of
 * code, at any size, since the picture was chosen for it.
 */
class ReadCodeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val picture = picture(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
              // A blinking cursor in the page-number dialog repaints the panel twice a second.
              CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                // null while reading; then what was found, or why nothing was.
                var state by remember { mutableStateOf<Read?>(null) }
                LaunchedEffect(picture) {
                    state = withContext(Dispatchers.Default) {
                        when (val result = picture?.let { read(it) }) {
                            null, Unopened -> Read(null, R.string.qr_picture_unopened)
                            NoCode -> Read(null, R.string.qr_none_in_picture)
                            is Code -> Read(describe(this@ReadCodeActivity, result.result), 0)
                        }
                    }
                }
                val found = state?.found
                when {
                    found != null -> QrScreen(found, onClose = { finish() })
                    else -> Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TextMMD(
                                text = stringResource(state?.why ?: R.string.qr_reading),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            if (state != null) {
                                OutlinedButtonMMD(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    TextMMD(text = stringResource(R.string.shashinki_close), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
              }
            }
        }
    }

    private fun picture(intent: Intent?): Uri? = when {
        intent == null -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
    } ?: intent?.data

    private class Read(val found: Found?, val why: Int)

    private sealed interface Outcome
    private object Unopened : Outcome
    private object NoCode : Outcome
    private class Code(val result: com.google.zxing.Result) : Outcome

    /**
     * The code in the picture, tried at up to [LARGE] on its long side and then smaller: a
     * photo's grain can hide a code that reads once the picture is shrunk. A picture that
     * cannot be opened is said so, not passed off as one without a code.
     */
    private fun read(uri: Uri): Outcome {
        for (limit in listOf(LARGE, SMALL)) {
            val bitmap = load(uri, limit) ?: return Unopened
            try {
                QrReader.readPicture(bitmap)?.let { return Code(it) }
            } finally {
                bitmap.recycle()
            }
        }
        return NoCode
    }

    private fun load(uri: Uri, limit: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= limit) sample *= 2
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) {
        android.util.Log.w("shashinki", "picture not opened: $uri", e)
        null
    }

    private companion object {
        const val LARGE = 2048
        const val SMALL = 800
    }
}
