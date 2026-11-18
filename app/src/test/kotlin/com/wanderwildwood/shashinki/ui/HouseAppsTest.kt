package com.wanderwildwood.shashinki.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseAppsTest {

    private val other = "00".repeat(32)

    @Test fun eachAppOnlyByItsOwnReleaseCertificate() {
        for (pkg in listOf(HouseApps.PASSWORDS, HouseApps.WALLET, HouseApps.PICTURES)) {
            val release = HouseApps.RELEASE_CERTS_SHA256.getValue(pkg)
            assertTrue(pkg, HouseApps.trusted(pkg, listOf(release), debuggable = false))
            // An app that took the name with another key, or none we could read.
            assertFalse(pkg, HouseApps.trusted(pkg, listOf(other), debuggable = false))
            assertFalse(pkg, HouseApps.trusted(pkg, emptyList(), debuggable = false))
            assertFalse(pkg, HouseApps.trusted(pkg, listOf(release, other), debuggable = false))
        }
    }

    @Test fun oneAppsCertificateDoesNotVouchForAnother() {
        val wallet = HouseApps.RELEASE_CERTS_SHA256.getValue(HouseApps.WALLET)
        assertFalse(HouseApps.trusted(HouseApps.PASSWORDS, listOf(wallet), debuggable = false))
        assertFalse(HouseApps.trusted("com.example.notes", listOf(wallet), debuggable = true))
    }

    @Test fun debugKeysOnlyInADebugBuild() {
        val debug = FilesPicker.DEBUG_CERTS_SHA256.first()
        assertFalse(HouseApps.trusted(HouseApps.PASSWORDS, listOf(debug), debuggable = false))
        assertTrue(HouseApps.trusted(HouseApps.PASSWORDS, listOf(debug), debuggable = true))
    }
}
