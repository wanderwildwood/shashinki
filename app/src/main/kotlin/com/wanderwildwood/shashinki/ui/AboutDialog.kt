package com.wanderwildwood.shashinki.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.sourceforge.opencamera.BuildConfig
import net.sourceforge.opencamera.R

/**
 * This app's About: only the words and the address. The dialog, its one Close button, the
 * Licences row, the links and the llama are the shared HouseAbout.kt, the same in every app.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data = AboutData(
        title = stringResource(R.string.shashinki_about_title, BuildConfig.VERSION_NAME),
        sections = listOf(
            AboutSection(stringResource(R.string.shashinki_about_privacy_title), stringResource(R.string.shashinki_about_privacy)),
            AboutSection(null, stringResource(R.string.shashinki_about_privacy_more)),
            AboutSection(stringResource(R.string.privacy_who_title), stringResource(R.string.privacy_who)),
            AboutSection(stringResource(R.string.privacy_contact_title), stringResource(R.string.privacy_contact)),
            AboutSection(stringResource(R.string.privacy_rights_title), stringResource(R.string.privacy_rights)),
            AboutSection(stringResource(R.string.shashinki_about_terms_title), stringResource(R.string.shashinki_about_terms)),
            AboutSection(null, stringResource(R.string.shashinki_about_after)),
        ),
        sourceUrl = SOURCE_URL,
    )
    val labels = AboutLabels(
        close = stringResource(R.string.shashinki_about_close),
        licences = stringResource(R.string.licences_title),
        feedTheLlamas = stringResource(R.string.shashinki_about_feed_the_llamas),
        noBrowser = { address -> context.getString(R.string.about_no_browser_address, address) },
        writeTo = { address -> context.getString(R.string.about_write_to, address) },
        noMailApp = { address -> context.getString(R.string.about_no_mail_app, address) },
    )
    HouseAboutDialog(data, labels, R.drawable.llama, onDismiss) { modifier, back ->
        LicencesScreen(rememberLicencesData(), rememberLicencesLabels(), onBack = back, modifier = modifier)
    }
}
