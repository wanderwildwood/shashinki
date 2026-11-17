package net.sourceforge.opencamera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lens writes a picture taken for another app only to an address that app gave out. */
class CaptureOutputTest {

    private val own = "com.wanderwildwood.shashinki"

    @Test fun anotherAppsContentAddressIsTaken() {
        assertTrue(CaptureOutput.acceptable("content", "com.example.notes.fileprovider", own))
        assertTrue(CaptureOutput.acceptable("content", "media", own))
    }

    @Test fun aFilePathIsNeverTaken() {
        assertFalse(CaptureOutput.acceptable("file", "", own))
        assertFalse(CaptureOutput.acceptable("file", null, own))
        assertFalse(CaptureOutput.acceptable(null, null, own))
    }

    @Test fun lensOwnProvidersAreNeverTaken() {
        assertFalse(CaptureOutput.acceptable("content", "$own.qr", own))
        assertFalse(CaptureOutput.acceptable("content", "$own.darkmodeinit", own))
        assertFalse(CaptureOutput.acceptable("CONTENT", "Com.WanderWildwood.Shashinki.QR", own))
        assertFalse(CaptureOutput.acceptable("content", "0@$own.qr", own))
        assertFalse(CaptureOutput.acceptable("content", own, own))
    }

    @Test fun aLookalikeNameIsAnotherApp() {
        assertTrue(CaptureOutput.acceptable("content", "${own}2.provider", own))
    }
}
