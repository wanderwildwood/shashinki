package com.wanderwildwood.shashinki.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * The other apps of this set that Lens hands things to by name: Passwords (a sign-in code's
 * secret), Wallet (a code kept as a card) and Pictures (a photo to show). Each is used only
 * when the app holding its name is that app, signed with its release certificate and nothing
 * else: on a phone without it any app could take the name. Otherwise Lens acts as if it were
 * not installed. A debuggable build of Lens also takes one signed with a debug key.
 */
object HouseApps {

    const val PASSWORDS = "com.wanderwildwood.aikotoba"
    const val WALLET = "com.wanderwildwood.satsuire"
    const val PICTURES = "com.wanderwildwood.garo"

    /** SHA-256 of the certificate each app's releases are signed with, lower-case hex. */
    val RELEASE_CERTS_SHA256 = mapOf(
        PASSWORDS to "9b9fbd10920e2c8a94609f3aa9a1bb11213e36d5d21b53c973de11033fba25e1",
        WALLET to "e8dd0e8acf311e519c336dc5898acb0eba0eb627b51306cf3cdb594c3ffdd637",
        PICTURES to "fb01c054c1e349eb5df5937c7e6c4968824dd719ada7566342dfcb1a562d96c5",
    )

    /** Whether the app holding [pkg]'s name is that app; see the class comment. */
    @JvmStatic
    fun isReal(context: Context, pkg: String): Boolean {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return trusted(pkg, signers(context.packageManager, pkg), debuggable)
    }

    /** Whether certificates [signers] (SHA-256, lower-case hex) are [pkg]'s alone. */
    fun trusted(pkg: String, signers: List<String>, debuggable: Boolean): Boolean {
        val release = RELEASE_CERTS_SHA256[pkg] ?: return false
        val certs = if (debuggable) FilesPicker.DEBUG_CERTS_SHA256 + release else setOf(release)
        return signers.isNotEmpty() && signers.all { it in certs }
    }

    /** The SHA-256 of each certificate [pkg] is signed with, lower-case hex; none when it cannot be read. */
    private fun signers(pm: PackageManager, pkg: String): List<String> = runCatching {
        pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            .orEmpty()
            .map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
    }.getOrDefault(emptyList())
}
