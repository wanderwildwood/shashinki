package com.wanderwildwood.shashinki.ui

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import net.sourceforge.opencamera.PreferenceKeys

/**
 * Open Camera's settings that this app fixes, and the few it adds.
 *
 * The same preferences file Open Camera's engine reads, so every value set here is one it
 * already understands. Set once, the first time this app opens: after that, what the settings
 * screen shows is the whole of what can change.
 */
@Suppress("DEPRECATION") // Open Camera reads the default preferences; so must this.
object Defaults {

    private const val APPLIED = "shashinki_defaults_v1"
    private const val CAMERA2 = "shashinki_camera2"
    private const val CAMERA_FOLDER = "shashinki_camera_folder"
    private const val FLASH_TORCH = "shashinki_flash_torch"
    const val GRID = "shashinki_grid"
    const val QR = "shashinki_qr"

    fun prefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun apply(context: Context) {
        val prefs = prefs(context)
        // The camera API, whatever was set before — so the copy already on a phone gets it too.
        // Open Camera only starts on Camera2 for phones it recognises (Google, Nokia, Samsung,
        // OnePlus) and gives everything else Android's old camera API. On the Kompakt the old
        // API hands back black frames, where Camera2, which its camera supports in full, works.
        if (!prefs.getBoolean(CAMERA2, false)) {
            prefs.edit()
                .putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2")
                .putBoolean(CAMERA2, true)
                .apply()
        }
        // DCIM/Camera, where Android's own camera puts photos, rather than Open Camera's
        // DCIM/OpenCamera. Moved once on the copy already on a phone; a folder of one's own
        // choosing stays. Photos already taken stay where they are.
        if (!prefs.getBoolean(CAMERA_FOLDER, false)) {
            val edit = prefs.edit().putBoolean(CAMERA_FOLDER, true)
            if (prefs.getString(PreferenceKeys.SaveLocationPreferenceKey, "OpenCamera") == "OpenCamera") {
                edit.putString(PreferenceKeys.SaveLocationPreferenceKey, "Camera")
            }
            edit.apply()
        }
        // Flash "on" became the torch, lit from the moment it is chosen. A copy left on the old
        // fire-at-the-shot "on" is moved over once, so the button means the same on every phone.
        if (!prefs.getBoolean(FLASH_TORCH, false)) {
            val edit = prefs.edit().putBoolean(FLASH_TORCH, true)
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("flash_value_") && value == "flash_on") edit.putString(key, "flash_torch")
            }
            edit.apply()
        }
        if (prefs.getBoolean(APPLIED, false)) return
        prefs.edit()
            // Toasts slide in and fade out — a repaint on the way in and another on the way out,
            // for words that say what the button already showed.
            .putBoolean(PreferenceKeys.ShowToastsPreferenceKey, false)
            // The thumbnail flying to the gallery corner is an animation the panel smears.
            .putBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, false)
            // No "what's new" dialog: release notes live on the release, not in the viewfinder.
            .putBoolean(PreferenceKeys.ShowWhatsNewPreferenceKey, false)
            // Taking a photo goes straight back to the viewfinder, as Mudita's camera does.
            .putBoolean(PreferenceKeys.PausePreviewPreferenceKey, false)
            .putBoolean(APPLIED, true)
            .apply()
    }

    fun grid(context: Context): Boolean = prefs(context).getBoolean(GRID, false)

    /** Reading QR codes in the viewfinder: on unless turned off, so a code just works. */
    fun qr(context: Context): Boolean = prefs(context).getBoolean(QR, true)
}
