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

    @Test fun aMediaStoreItemLensMadeIsNeverTaken() {
        assertTrue(CaptureOutput.isMediaStore("media"))
        assertTrue(CaptureOutput.isMediaStore("0@media"))
        assertFalse(CaptureOutput.isMediaStore("com.example.notes.fileprovider"))
        assertTrue(CaptureOutput.ownedBy(own, own))
        assertFalse(CaptureOutput.ownedBy("com.example.notes", own))
        assertFalse(CaptureOutput.ownedBy(null, own))
    }

    @Test fun anAddressInLensOwnPictureFolderIsNeverTaken() {
        val granted = listOf("com.android.externalstorage.documents")
        assertTrue(CaptureOutput.underOwnGrant("com.android.externalstorage.documents", granted))
        assertTrue(CaptureOutput.underOwnGrant("0@com.android.externalstorage.documents", granted))
        assertFalse(CaptureOutput.underOwnGrant("com.example.notes.fileprovider", granted))
        assertFalse(CaptureOutput.underOwnGrant("com.android.externalstorage.documents", emptyList()))
        assertFalse(CaptureOutput.underOwnGrant(null, granted))
    }

    @Test fun aPictureForAnotherAppNeverCarriesLocation() {
        for (action in listOf(
            "android.media.action.IMAGE_CAPTURE",
            "android.media.action.IMAGE_CAPTURE_SECURE",
            "android.media.action.VIDEO_CAPTURE",
        )) {
            assertFalse(action, CaptureOutput.storesLocation(action, true))
            assertFalse(action, CaptureOutput.storesLocation(action, false))
        }
    }

    @Test fun anOrdinaryPictureFollowsTheSetting() {
        for (action in listOf(null, "android.intent.action.MAIN", "android.media.action.STILL_IMAGE_CAMERA")) {
            assertTrue(CaptureOutput.storesLocation(action, true))
            assertFalse(CaptureOutput.storesLocation(action, false))
        }
    }
}
