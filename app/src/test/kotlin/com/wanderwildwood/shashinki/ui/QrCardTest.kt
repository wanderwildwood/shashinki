package com.wanderwildwood.shashinki.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCardTest {
    @Test fun aSignInCodeIsNeitherDrawnAgainNorCopied() {
        val signIn = Found("otpauth://totp/Example:ada@example.org?secret=JBSWY3DPEHPK3PXP", 0, "Example:ada@example.org", null, null, signIn = true)
        assertFalse(signIn.showsCode)
    }

    @Test fun anyOtherCodeIs() {
        val web = Found("https://example.org", 0, "example.org", null, null)
        assertTrue(web.showsCode)
    }
}
