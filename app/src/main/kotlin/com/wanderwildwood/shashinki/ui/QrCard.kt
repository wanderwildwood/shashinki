package com.wanderwildwood.shashinki.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.app.SearchManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.provider.ContactsContract
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import com.google.zxing.MultiFormatWriter
import com.google.zxing.Result
import com.google.zxing.client.result.ISBNParsedResult
import com.google.zxing.client.result.ProductParsedResult
import com.google.zxing.client.result.ResultParser
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
    /** Bound for Passwords: a secret, so not offered to Wallet as well. */
    val signIn: Boolean = false,
    /** The kind of code it was read from, so it is drawn again as the same kind. */
    val format: BarcodeFormat = BarcodeFormat.QR_CODE,
)

internal fun describe(context: Context, result: Result): Found =
    describe(context, ResultParser.parseResult(result)).copy(format = result.barcodeFormat)

internal fun describe(context: Context, parsed: ParsedResult): Found {
    val raw = parsed.displayResult.orEmpty()
    fun ifAnyone(intent: Intent): Intent? = intent.takeIf { it.resolveActivity(context.packageManager) != null }
    // Sign-in codes first, whatever ZXing made of them: an account like "ada@example.org" after
    // the issuer's colon looks to its link parser like a login hidden in the address, and it
    // hands those back as plain text.
    val text = parsed.displayResult.orEmpty().trim()
    if (text.startsWith("otpauth://", ignoreCase = true) || text.startsWith("otpauth-migration://", ignoreCase = true)) {
        return signIn(context, text)
    }
    return when (parsed) {
        is URIParsedResult -> {
            val uri = Uri.parse(parsed.uri)
            val scheme = uri.scheme.orEmpty().lowercase()
            val kind = when (scheme) {
                "http", "https" -> R.string.qr_kind_web
                else -> R.string.qr_kind_link
            }
            Found(raw, kind, parsed.uri, R.string.qr_open, ifAnyone(Intent(Intent.ACTION_VIEW, uri)))
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
        // A shop barcode or a book's: the number means nothing read out, so it is looked up.
        is ProductParsedResult -> Found(raw, R.string.qr_kind_product, parsed.normalizedProductID, R.string.qr_look_up, lookUp(context, parsed.normalizedProductID))
        is ISBNParsedResult -> Found(raw, R.string.qr_kind_book, parsed.isbn, R.string.qr_look_up, lookUp(context, "ISBN " + parsed.isbn))
        else -> Found(raw, R.string.qr_kind_text_plain, raw, null, null)
    }
}

/** A web search for [words]: the browser's own if it takes one, otherwise DuckDuckGo in it. */
private fun lookUp(context: Context, words: String): Intent? {
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, words)
    if (search.resolveActivity(context.packageManager) != null) return search
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(words)))
    return view.takeIf { it.resolveActivity(context.packageManager) != null }
}

/**
 * A two-step sign-in code: to Passwords when it is on the phone, which keeps it; otherwise to
 * whichever authenticator answers the link. Never the secret on screen.
 */
private fun signIn(context: Context, text: String): Found {
    val uri = Uri.parse(text)
    val shown = if (uri.scheme.equals("otpauth", ignoreCase = true)) {
        // otpauth://totp/Issuer:account?secret=… — the issuer and account.
        uri.path.orEmpty().trimStart('/').ifEmpty { uri.getQueryParameter("issuer").orEmpty() }
    } else {
        // A Google Authenticator transfer is nothing but secrets.
        ""
    }
    val view = Intent(Intent.ACTION_VIEW, uri)
    val passwords = Intent(view).setPackage(PASSWORDS).takeIf { it.resolveActivity(context.packageManager) != null }
    return if (passwords != null) Found(text, R.string.qr_kind_sign_in, shown, R.string.qr_add_passwords, passwords, signIn = true)
    else Found(text, R.string.qr_kind_sign_in, shown, R.string.qr_open, view.takeIf { it.resolveActivity(context.packageManager) != null })
}

/**
 * Looks at the viewfinder while [enabled], and reports a code when it sees one; there is no
 * photo to take first. Frames come from the camera's own grey copy ([CodeFrames]), one as soon
 * as the last is read, at the camera's sharpness; where the camera gives no copy (RAW on, a
 * vendor mode) the picture on screen is read a few times a second instead. A code seen but not
 * yet readable, or too small in the picture to be what the camera is pointed at, is focused on
 * as a tap on it would be, and [onSpotted] says so, to bring up the square to aim with. A read
 * gives one short haptic tick. Never while a video records.
 *
 * [formats] and [anywhere]: what another app asked for, when it asked for a scan.
 */
@Composable
internal fun QrWatch(
    activity: MainActivity,
    enabled: Boolean,
    formats: List<BarcodeFormat> = QrReader.ALL,
    anywhere: Boolean = false,
    onSpotted: () -> Unit = {},
    onFound: (Found, Result) -> Unit,
) {
    val report by rememberUpdatedState(onFound)
    val spotted by rememberUpdatedState(onSpotted)
    // Only while the camera is in front and running. Another screen over it — Android's own
    // "save this network?" — pauses the activity and Open Camera lets the camera go and takes it
    // back; reading frames off the view through that is how the viewfinder came back frozen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(enabled, resumed.isAtLeast(Lifecycle.State.RESUMED), formats, anywhere) {
        if (!enabled || !resumed.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        var focusedAt = 0L
        var screenUntil = 0L
        while (isActive) {
            val preview = activity.preview
            val view = preview?.view as? TextureView
            if (preview == null || view == null || !preview.isPreviewStarted || preview.isOpeningCamera ||
                preview.isTakingPhotoOrOnTimer || preview.isVideoRecording || !view.isAvailable || view.width == 0
            ) {
                delay(INTERVAL_MS)
                continue
            }
            // The camera's copy, if this session has one; else the screen's, more slowly.
            val frame = if (System.currentTimeMillis() < screenUntil) null else CodeFrames.next(FRAME_WAIT_MS)
            if (frame == null && System.currentTimeMillis() >= screenUntil) screenUntil = System.currentTimeMillis() + SCREEN_FOR_MS
            val seen: QrReader.Seen?
            val scaleX: Float
            val scaleY: Float
            var mirror = false
            if (frame != null) {
                seen = withContext(Dispatchers.Default) { QrReader.read(frame, formats, anywhere) }
                scaleX = view.width.toFloat() / frame.width
                scaleY = view.height.toFloat() / frame.height
                mirror = frame.front
                delay(GAP_MS)
            } else {
                delay(INTERVAL_MS)
                val shot = view.getBitmap(FRAME_WIDTH, FRAME_WIDTH * view.height / view.width) ?: continue
                scaleX = view.width.toFloat() / shot.width
                scaleY = scaleX
                seen = withContext(Dispatchers.Default) {
                    try { QrReader.read(shot) } finally { shot.recycle() }
                }
            }
            when (seen) {
                is QrReader.Seen.Code -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    report(describe(activity, seen.result), seen.result)
                }
                is QrReader.Seen.Place -> {
                    spotted()
                    val now = System.currentTimeMillis()
                    if (now - focusedAt > FOCUS_EVERY_MS) {
                        focusedAt = now
                        val x = seen.x * scaleX
                        preview.focusAt(if (mirror) view.width - x else x, seen.y * scaleY)
                    }
                }
                null -> Unit
            }
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
    val drawn = remember(found.raw) { draw(found.raw, found.format) }
    val wallet = remember(found.raw) { if (found.signIn) null else walletIntent(context) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(found.kind), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.shashinki_close), onClose) },
            )
        },
    ) { padding ->
        // Paged on whole rows, like every list in the shop; nothing scrolls.
        PagedColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (drawn != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = drawn.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            // Whole pixels, so every module stays black or white on the panel.
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.size(QR_SIZE),
                        )
                    }
                }
            }
            item {
                TextMMD(
                    text = found.shown,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (found.action != null && found.intent == null) {
                // Say it, rather than offer a button that does nothing.
                item {
                    TextMMD(
                        text = stringResource(R.string.qr_no_app),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (found.action != null && found.intent != null) {
                item {
                    Choice(stringResource(found.action)) {
                        val data = found.intent.data
                        if (data != null && (data.scheme == "http" || data.scheme == "https")) {
                            // A web address goes through the guarded opener: on the Kompakt the only
                            // thing that answers it can be the WebView test shell, which crashes.
                            openLink(context, data.toString(), context.getString(R.string.about_no_browser_address, data.toString()))
                        } else if (runCatching { context.startActivity(found.intent) }.isFailure) {
                            Toast.makeText(context, R.string.qr_no_app, Toast.LENGTH_LONG).show()
                        }
                        onClose()
                    }
                }
            }
            if (wallet != null && drawn != null) {
                item {
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
            }
            item {
                Choice(stringResource(R.string.qr_copy)) {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                    clip?.setPrimaryClip(ClipData.newPlainText(null, found.raw))
                    Toast.makeText(context, R.string.qr_copied, Toast.LENGTH_SHORT).show()
                }
            }
            item { Choice(stringResource(R.string.shashinki_close), onClose) }
        }
    }
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    OutlinedButtonMMD(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The code drawn again from its text as the kind it was read from, one pixel a module plus the
 * quiet zone a reader needs; the screen scales it up without smoothing. A barcode's one row is
 * stood a third as tall as it is wide. Null for text too long to encode, or a kind ZXing cannot
 * draw — then the text alone is shown.
 */
internal fun draw(raw: String, format: BarcodeFormat = BarcodeFormat.QR_CODE): Bitmap? = try {
    val hints = buildMap<EncodeHintType, Any> {
        put(EncodeHintType.MARGIN, if (format in QrReader.BARS) 10 else 4)
        if (format == BarcodeFormat.QR_CODE) put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
    }
    val matrix = MultiFormatWriter().encode(raw, format, 0, 0, hints)
    val w = matrix.width
    val h = if (format in QrReader.BARS) maxOf(1, w / 3) else matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, if (format in QrReader.BARS) 0 else i / w)) Color.BLACK else Color.WHITE }
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

/** Off the screen, about three frames a second: quick to notice a code, light on the battery. */
private const val INTERVAL_MS = 330L
/** Between the camera's frames: as fast as they read, with a breath for the rest of the phone. */
private const val GAP_MS = 60L
/** How long to wait for the camera's copy before reading the screen instead. */
private const val FRAME_WAIT_MS = 600L
/** Once the camera gave no copy, the screen is read this long before the camera is asked again. */
private const val SCREEN_FOR_MS = 5_000L
/** How often a code not yet readable is focused on again. */
private const val FOCUS_EVERY_MS = 2000L
private const val FRAME_WIDTH = 480
/** Small enough that the four buttons under it fit the panel without scrolling. */
private val QR_SIZE = 208.dp
/** About this wide when shared: ten pixels a module for the usual code, plenty for a reader. */
private const val SHARED_PX = 600
private const val WALLET = "com.wanderwildwood.satsuire"
private const val PASSWORDS = "com.wanderwildwood.aikotoba"
