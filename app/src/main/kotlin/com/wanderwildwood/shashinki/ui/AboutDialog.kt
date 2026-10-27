package com.wanderwildwood.shashinki.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import net.sourceforge.opencamera.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.sourceforge.opencamera.R

/**
 * What this is, what it does with what it can see, and whose work it started from.
 *
 * The line about location is here because a camera that can write where a photo was taken into
 * the photo is the kind a stranger should ask about before trusting it.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = stringResource(R.string.shashinki_about_title, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))

        // Paged on whole paragraphs: the text outgrows the panel, and the Close button below stays put.
        PagedColumn(modifier = Modifier.fillMaxWidth().height(380.dp)) {
            // A heading stays with its paragraph: one row each, so a page never ends on a heading.
            item {
                Column {
                    Heading(stringResource(R.string.shashinki_about_privacy_title))
                    Body(stringResource(R.string.shashinki_about_privacy))
                }
            }
            item {
                Column {
                    Heading(stringResource(R.string.shashinki_about_terms_title))
                    Body(stringResource(R.string.shashinki_about_terms))
                }
            }
            item {
                Column {
                    Body(stringResource(R.string.shashinki_about_after))
                    Body(stringResource(R.string.shashinki_about_icons))
                    Body(stringResource(R.string.shashinki_about_zxing))
                }
            }
            item {
                // The source, as the licence asks of anyone who hands the program on.
                Body(
                    stringResource(R.string.shashinki_about_source),
                    Modifier.clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE)))
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.shashinki_about_no_browser), Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            item { Llama() }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.shashinki_about_close), style = MaterialTheme.typography.bodySmall) }
    }
}

private const val SOURCE = "https://github.com/wanderwildwood/shashinki"

@Composable
private fun Heading(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    TextMMD(text = text, style = MaterialTheme.typography.bodySmall, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp))
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * The Kompakt may have nothing registered for a web address, so the intent is allowed to fail
 * quietly rather than take the dialog down with it.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    // Straight to the checkout. The Donate button on the site only leads
                    // here anyway, so the page in between is a press the reader does not need.
                    // The short square.link form, not the long checkout.square.site address it
                    // redirects to -- the short one is what the site itself links to, so a
                    // regenerated checkout follows it and a published app does not break.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.shashinki_about_no_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.shashinki_about_feed_the_llamas), style = MaterialTheme.typography.bodySmall)
        }
    }
}
