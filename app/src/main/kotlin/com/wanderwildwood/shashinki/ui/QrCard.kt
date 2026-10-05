package com.wanderwildwood.shashinki.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.provider.ContactsContract
import android.provider.Settings
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            delay(INTERVAL_MS)
            val view = activity.preview?.view as? TextureView ?: continue
            if (!view.isAvailable || view.width == 0) continue
            val frame = view.getBitmap(FRAME_WIDTH, FRAME_WIDTH * view.height / view.width) ?: continue
            val parsed = withContext(Dispatchers.Default) {
                try { QrReader.read(frame) } finally { frame.recycle() }
            }
            if (parsed != null) report(describe(activity, parsed))
        }
    }
}

/** The card above the bar: what the code is, the button that acts on it, Copy, and Close. */
@Composable
internal fun QrCard(found: Found, onClose: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(text = stringResource(found.kind), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                BarButton(Icons.Close, stringResource(R.string.shashinki_close), onClose)
            }
            TextMMD(
                text = found.shown,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (found.action != null && found.intent == null) {
                // Say it, rather than offer a button that does nothing.
                TextMMD(text = stringResource(R.string.qr_no_app), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                if (found.action != null && found.intent != null) {
                    OutlinedButtonMMD(
                        onClick = {
                            runCatching { context.startActivity(found.intent) }
                            onClose()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { TextMMD(text = stringResource(found.action), style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.width(10.dp))
                }
                OutlinedButtonMMD(
                    onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java)
                        clip?.setPrimaryClip(ClipData.newPlainText(null, found.raw))
                        Toast.makeText(context, R.string.qr_copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { TextMMD(text = stringResource(R.string.qr_copy), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private const val INTERVAL_MS = 500L
private const val FRAME_WIDTH = 480
