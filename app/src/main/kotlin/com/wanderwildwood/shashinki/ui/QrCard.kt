package com.wanderwildwood.shashinki.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.provider.ContactsContract
import android.provider.Settings
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.client.result.AddressBookParsedResult
import com.google.zxing.client.result.EmailAddressParsedResult
import com.google.zxing.client.result.GeoParsedResult
import com.google.zxing.client.result.ParsedResult
import com.google.zxing.client.result.SMSParsedResult
import com.google.zxing.client.result.TelParsedResult
import com.google.zxing.client.result.URIParsedResult
import com.google.zxing.client.result.WifiParsedResult
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import net.sourceforge.opencamera.MainActivity
import net.sourceforge.opencamera.R

/**
 * What a code says, put the way a person would say it, and the one thing to do with it.
 *
 * The doing is handed to whichever app on the phone answers it: a web address to the browser,
 * an email to Email, a text to Messaging, a contact to Contacts, a place to a map, a sign-in
 * code to the authenticator. Nothing is opened until the button is pressed.
 */
data class Found(
    val raw: String,
    /** "Web address", "Wi-Fi network" — what kind of thing this is. */
    val kind: Int,
    /** The part worth reading: the address, the network's name, the person. */
    val shown: String,
    /** The button's word, and what it does; null where there is nothing to open, only to copy. */
    val action: Int?,
    val intent: Intent?,
)

internal fun describe(context: Context, parsed: ParsedResult): Found {
    val raw = parsed.displayResult.orEmpty()
    fun ifAnyone(intent: Intent): Intent? = intent.takeIf { it.resolveActivity(context.packageManager) != null }
    return when (parsed) {
        is URIParsedResult -> {
            val uri = Uri.parse(parsed.uri)
            val scheme = uri.scheme.orEmpty().lowercase()
            val kind = when (scheme) {
                "http", "https" -> R.string.qr_kind_web
                "otpauth" -> R.string.qr_kind_sign_in
                else -> R.string.qr_kind_link
            }
            val shown = when (scheme) {
                "http", "https" -> parsed.uri
                // otpauth://totp/Issuer:account?secret=… — the issuer and account, never the secret.
                "otpauth" -> uri.path.orEmpty().trimStart('/').ifEmpty { uri.getQueryParameter("issuer").orEmpty() }
                else -> parsed.uri
            }
            Found(raw, kind, shown, R.string.qr_open, ifAnyone(Intent(Intent.ACTION_VIEW, uri)))
        }
        is WifiParsedResult -> {
            val suggestion = WifiNetworkSuggestion.Builder().setSsid(parsed.ssid).apply {
                val pass = parsed.password
                when (parsed.networkEncryption?.uppercase()) {
                    "WPA", "WPA2" -> if (!pass.isNullOrEmpty()) setWpa2Passphrase(pass)
                    "SAE", "WPA3" -> if (!pass.isNullOrEmpty()) setWpa3Passphrase(pass)
                    else -> Unit
                }
            }.build()
            // Android's own "save this network?" question, which needs no permission of this app's.
            val join = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
            val wep = parsed.networkEncryption.equals("WEP", ignoreCase = true)
            Found(raw, R.string.qr_kind_wifi, parsed.ssid, if (wep) null else R.string.qr_join, if (wep) null else ifAnyone(join))
        }
        is AddressBookParsedResult -> {
            val name = parsed.names?.firstOrNull().orEmpty()
            val add = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE).apply {
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                parsed.phoneNumbers?.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                parsed.emails?.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                parsed.org?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            }
            Found(raw, R.string.qr_kind_contact, name.ifEmpty { parsed.phoneNumbers?.firstOrNull().orEmpty() }, R.string.qr_add, ifAnyone(add))
        }
        is EmailAddressParsedResult -> {
            val to = parsed.tos?.joinToString(",").orEmpty()
            val uri = Uri.Builder().scheme("mailto").opaquePart(to).build()
            val write = Intent(Intent.ACTION_SENDTO, uri).apply {
                parsed.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                parsed.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
            Found(raw, R.string.qr_kind_email, to, R.string.qr_write, ifAnyone(write))
        }
        is SMSParsedResult -> {
            val number = parsed.numbers?.firstOrNull().orEmpty()
            val write = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                parsed.body?.let { putExtra("sms_body", it) }
            }
            Found(raw, R.string.qr_kind_text, number, R.string.qr_write, ifAnyone(write))
        }
        is TelParsedResult -> Found(
            raw, R.string.qr_kind_phone, parsed.number, R.string.qr_call,
            // The dialler with the number in it, not a call: the press still goes through a person.
            ifAnyone(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parsed.number}"))),
        )
        is GeoParsedResult -> Found(
            raw, R.string.qr_kind_place, "%.5f, %.5f".format(parsed.latitude, parsed.longitude), R.string.qr_show,
            ifAnyone(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.geoURI))),
        )
        else -> Found(raw, R.string.qr_kind_text_plain, raw, null, null)
    }
}

/**
 * Looks at the viewfinder about twice a second while [enabled], and reports a code when it sees
 * one. The frame is taken small — enough to read a code, a fraction of the work of the full
 * picture — and read off the main thread. Nothing on screen changes until a code is found.
 */
@Composable
internal fun QrWatch(activity: MainActivity, enabled: Boolean, onFound: (Found) -> Unit) {
    val report by rememberUpdatedState(onFound)
    // Only while the camera is in front and running. Another screen over it — Android's own
    // "save this network?" — pauses the activity and Open Camera lets the camera go and takes it
    // back; reading frames off the view through that is how the viewfinder came back frozen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(enabled, resumed.isAtLeast(Lifecycle.State.RESUMED)) {
        if (!enabled || !resumed.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        while (isActive) {
            delay(INTERVAL_MS)
            val preview = activity.preview ?: continue
            if (!preview.isPreviewStarted || preview.isOpeningCamera || preview.isTakingPhotoOrOnTimer) continue
            val view = preview.view as? TextureView ?: continue
            if (!view.isAvailable || view.width == 0) continue
            val frame = view.getBitmap(FRAME_WIDTH, FRAME_WIDTH * view.height / view.width) ?: continue
            val parsed = withContext(Dispatchers.Default) {
                try { QrReader.read(frame) } finally { frame.recycle() }
            }
            if (parsed != null) report(describe(activity, parsed))
        }
    }
}

/**
 * The code, on a screen of its own: drawn again clean from what it says, so it reads the same
 * on this panel as it did on the poster, the thing it says, and the ways to act on it. The
 * viewfinder is behind this; the back arrow or Close returns to it.
 *
 * A button goes to the app that answers it — the browser, Contacts, the dialler — and to
 * Wallet, which keeps the code as a card to be shown to a scanner later. Wallet is handed the
 * drawn picture, which it reads as it reads any other, so it learns the kind of code on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrScreen(found: Found, onClose: () -> Unit) {
    val context = LocalContext.current
    val drawn = remember(found.raw) { draw(found.raw) }
    val wallet = remember(found.raw) { walletIntent(context) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(found.kind), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.shashinki_close), onClose) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            if (drawn != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = drawn.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        // Whole pixels, so every module stays black or white on the panel.
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(QR_SIZE),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            TextMMD(
                text = found.shown,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (found.action != null && found.intent == null) {
                // Say it, rather than offer a button that does nothing.
                Spacer(Modifier.height(6.dp))
                TextMMD(text = stringResource(R.string.qr_no_app), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (found.action != null && found.intent != null) {
                    Choice(stringResource(found.action)) {
                        runCatching { context.startActivity(found.intent) }
                        onClose()
                    }
                }
                if (wallet != null && drawn != null) {
                    Choice(stringResource(R.string.qr_save_wallet)) {
                        val picture = runCatching { keep(context, drawn) }.getOrNull()
                        if (picture != null) {
                            wallet.putExtra(Intent.EXTRA_STREAM, picture)
                            wallet.putExtra(Intent.EXTRA_TEXT, found.raw)
                            wallet.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(wallet) }
                            onClose()
                        } else {
                            Toast.makeText(context, R.string.qr_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Choice(stringResource(R.string.qr_copy)) {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                    clip?.setPrimaryClip(ClipData.newPlainText(null, found.raw))
                    Toast.makeText(context, R.string.qr_copied, Toast.LENGTH_SHORT).show()
                }
                Choice(stringResource(R.string.shashinki_close), onClose)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    OutlinedButtonMMD(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The code drawn again from its text, one pixel a module plus the four-module quiet zone a
 * reader needs; the screen scales it up without smoothing. Null for text too long to encode.
 */
internal fun draw(raw: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(
        raw, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4),
    )
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) Color.BLACK else Color.WHITE }
    Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
} catch (e: Exception) {
    null
}

/** Wallet, if it is on the phone, ready to be handed a picture with a code in it. */
private fun walletIntent(context: Context): Intent? {
    val send = Intent(Intent.ACTION_SEND).setType("image/png").setPackage(WALLET)
    return send.takeIf { it.resolveActivity(context.packageManager) != null }
}

/**
 * The drawn code as a picture file another app can read, scaled up so a reader finds it at a
 * glance. In the cache, under one name: the last code shared is the only one kept.
 */
private fun keep(context: Context, drawn: Bitmap): Uri {
    val dir = File(context.cacheDir, "qr").apply { mkdirs() }
    val file = File(dir, "qr.png")
    val scale = (SHARED_PX / drawn.width).coerceAtLeast(1)
    val big = Bitmap.createScaledBitmap(drawn, drawn.width * scale, drawn.height * scale, false)
    try {
        file.outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        big.recycle()
    }
    return FileProvider.getUriForFile(context, context.packageName + ".qr", file)
}

private const val INTERVAL_MS = 500L
private const val FRAME_WIDTH = 480
/** Small enough that the four buttons under it fit the panel without scrolling. */
private val QR_SIZE = 208.dp
/** About this wide when shared: ten pixels a module for the usual code, plenty for a reader. */
private const val SHARED_PX = 600
private const val WALLET = "com.wanderwildwood.satsuire"
