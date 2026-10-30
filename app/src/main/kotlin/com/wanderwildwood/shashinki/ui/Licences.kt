package com.wanderwildwood.shashinki.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import net.sourceforge.opencamera.R

/** One piece of other people's work that ships inside the app, and the licence it comes under. */
data class Library(val name: String, val by: String, val licence: String, val link: String)

/** A licence text bundled as a raw resource, with the heading it is shown under. */
data class LicenceText(@StringRes val title: Int, @RawRes val raw: Int)

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/shashinki"

/**
 * Everything the APK carries that is not this app's own code: the work it started from, the
 * libraries and the typeface. Licences are the ones each project declares itself.
 */
val LIBRARIES: List<Library> = listOf(
    Library("Open Camera", "Mark Harman", "GPL-3.0-or-later", "https://opencamera.org.uk"),
    Library("Open Camera sounds", "Mark Harman", "CC0-1.0", "https://opencamera.org.uk"),
    Library("ZXing core", "ZXing authors", "Apache-2.0", "https://github.com/zxing/zxing"),
    Library("AppCompat, ExifInterface and Legacy Support", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Library("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0", "https://github.com/mudita/MMD"),
    Library("Lato typeface, supplied with MMD", "Łukasz Dziedzic", "OFL-1.1", "https://www.latofonts.com"),
    Library("AndroidX and Jetpack Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
    Library("Kotlin and kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    Library("Material Symbols icons", "Google", "Apache-2.0", "https://fonts.google.com/icons"),
)

/** Each distinct licence text once, in the order the names above first use them. */
val LICENCE_TEXTS: List<LicenceText> = listOf(
    LicenceText(R.string.licences_gpl, R.raw.gpl_3_0),
    LicenceText(R.string.licences_apache, R.raw.apache_2_0),
    LicenceText(R.string.licences_ofl, R.raw.ofl_1_1),
)

/**
 * A licence text as the paragraphs a reader turns pages by: blocks split at blank lines, each
 * joined into one row. No word is dropped or repeated by it.
 */
fun licenceParagraphs(text: String): List<String> =
    text
        .replace("\r\n", "\n")
        .split(Regex("\n[ \t]*\n"))
        .map { block -> block.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }

/** The Licences page of the About dialog: source, this app's licence, what it is built with, the texts. */
@Composable
fun ColumnScope.LicencesPage(onBack: () -> Unit, room: Dp) {
    val context = LocalContext.current
    val texts = remember { LICENCE_TEXTS.map { it to readParagraphs(context, it.raw) } }
    TextMMD(
        text = stringResource(R.string.licences_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(10.dp))
    PagedColumn(modifier = Modifier.fillMaxWidth().height(room)) {
        item(key = "source") {
            Column {
                LicenceHeading(stringResource(R.string.licences_source_head))
                LicenceLink(SOURCE_URL.removePrefix("https://"), SOURCE_URL)
            }
        }
        item(key = "licence") {
            Column {
                LicenceHeading(stringResource(R.string.licences_licence_head))
                LicenceBody(stringResource(R.string.licences_licence))
            }
        }
        item(key = "built") { LicenceHeading(stringResource(R.string.licences_built_head)) }
        LIBRARIES.forEach { library ->
            item(key = "lib-${library.name}") {
                Column(Modifier.padding(bottom = 6.dp)) {
                    LicenceBody("${library.name}, ${library.by}: ${library.licence}")
                    LicenceLink(library.link.removePrefix("https://"), library.link)
                }
            }
        }
        texts.forEach { (text, paragraphs) ->
            item(key = "head-${text.raw}") { LicenceHeading(stringResource(text.title)) }
            paragraphs.forEachIndexed { index, paragraph ->
                item(key = "${text.raw}-$index") { LicenceBody(paragraph) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButtonMMD(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { TextMMD(text = stringResource(R.string.licences_back), style = MaterialTheme.typography.bodyMedium) }
}

private fun readParagraphs(context: Context, @RawRes raw: Int): List<String> =
    runCatching {
        licenceParagraphs(context.resources.openRawResource(raw).bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList())

@Composable
private fun LicenceHeading(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun LicenceBody(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** An address that opens in a browser, on a 48 dp row; a phone with none says so plainly. */
@Composable
private fun LicenceLink(label: String, address: String) {
    val context = LocalContext.current
    TextMMD(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) }
                    .onFailure {
                        Toast.makeText(context, context.getString(R.string.licences_no_browser), Toast.LENGTH_SHORT).show()
                    }
            }
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
