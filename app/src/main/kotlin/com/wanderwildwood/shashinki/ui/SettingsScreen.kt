package com.wanderwildwood.shashinki.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import net.sourceforge.opencamera.MainActivity
import net.sourceforge.opencamera.PreferenceKeys
import net.sourceforge.opencamera.R

/**
 * Eight of Open Camera's 177 settings, and reading QR codes: the ones that change what a photo or a video is, or what
 * taking one is like. The rest keep Open Camera's own defaults, which are good ones.
 *
 * Each row writes the same preference Open Camera's own settings screen wrote, under the same
 * key, so the camera reads it as it always has. Closing this screen asks Open Camera to reread
 * them all, as its own settings did on the way out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(activity: MainActivity, onClose: () -> Unit) {
    val prefs = remember { Defaults.prefs(activity) }
    // Bumped after every write, so the rows read the preferences again.
    var version by remember { mutableIntStateOf(0) }
    fun changed() { version++ }

    var aboutOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Pick?>(null) }
    var folderOpen by remember { mutableStateOf(false) }

    val preview = activity.preview
    val app = activity.applicationInterface
    val cameraId = app.cameraIdPref
    val physical = app.cameraIdSPhysicalPref

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.shashinki_settings)) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.shashinki_cd_close), onClose) },
                actions = { BarButton(Icons.Info, stringResource(R.string.shashinki_cd_about)) { aboutOpen = true } },
            )
        },
    ) { padding ->
        // Every value is read here, outside the list, after [version]. A row's lambda that read
        // the preferences itself captured nothing that changed, so Compose skipped it and the
        // row went on showing the value from before the press until the screen was reopened.
        @Suppress("UNUSED_EXPRESSION") version
        // A size chosen here is used once the screen closes, so until then the row shows the
        // choice, not the size the camera is still running at.
        val pictureSize = prefs.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId, physical), null)
            ?.split(' ')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
            ?.let { (w, h) -> w to h }
            ?: preview?.currentPictureSize?.let { it.width to it.height }
        val videoQuality = prefs.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId, physical, false), null)
            ?.takeIf { it in preview?.videoQualityHander?.supportedVideoQuality.orEmpty() }
            ?: preview?.videoQualityHander?.currentVideoQuality
        val steady = prefs.getBoolean(PreferenceKeys.VideoStabilizationPreferenceKey, true)
        val quality = prefs.getString(PreferenceKeys.QualityPreferenceKey, "90") ?: "90"
        val timer = prefs.getString(PreferenceKeys.TimerPreferenceKey, "0") ?: "0"
        val shutterSound = prefs.getBoolean(PreferenceKeys.ShutterSoundPreferenceKey, true)
        val location = prefs.getBoolean(PreferenceKeys.LocationPreferenceKey, false)
        val qr = prefs.getBoolean(Defaults.QR, true)
        val grid = prefs.getBoolean(Defaults.GRID, false)
        val folder = prefs.getString(PreferenceKeys.SaveLocationPreferenceKey, "Camera") ?: "Camera"
        LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), isScrollbarVisible = false) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Setting(
                    title = stringResource(R.string.shashinki_picture_size),
                    value = pictureSize?.let { (w, h) -> sizeLabel(w, h) } ?: stringResource(R.string.shashinki_unknown),
                    onClick = { picking = Pick.PICTURE },
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.shashinki_video_size),
                    value = videoQuality?.let { preview.getCamcorderProfileDescriptionShort(it) } ?: stringResource(R.string.shashinki_unknown),
                    onClick = { picking = Pick.VIDEO },
                )
            }
            if (preview?.supportsVideoStabilization() == true) {
                item {
                    Toggle(
                        title = stringResource(R.string.shashinki_steady_video),
                        note = stringResource(R.string.shashinki_steady_video_note),
                        on = steady,
                    ) {
                        prefs.edit().putBoolean(PreferenceKeys.VideoStabilizationPreferenceKey, it).apply()
                        changed()
                    }
                }
            }
            item {
                Setting(
                    title = stringResource(R.string.shashinki_quality),
                    value = stringResource(R.string.shashinki_quality_value, quality),
                    onClick = {
                        val next = QUALITIES[(QUALITIES.indexOf(quality) + 1) % QUALITIES.size]
                        prefs.edit().putString(PreferenceKeys.QualityPreferenceKey, next).apply()
                        changed()
                    },
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.shashinki_timer),
                    value = if (timer == "0") stringResource(R.string.shashinki_off) else stringResource(R.string.shashinki_seconds, timer),
                    onClick = {
                        val next = TIMERS[(TIMERS.indexOf(timer).coerceAtLeast(0) + 1) % TIMERS.size]
                        prefs.edit().putString(PreferenceKeys.TimerPreferenceKey, next).apply()
                        changed()
                    },
                )
            }
            item {
                Toggle(
                    title = stringResource(R.string.shashinki_shutter_sound),
                    on = shutterSound,
                ) {
                    prefs.edit().putBoolean(PreferenceKeys.ShutterSoundPreferenceKey, it).apply()
                    changed()
                }
            }
            item {
                Toggle(
                    title = stringResource(R.string.shashinki_location),
                    note = stringResource(R.string.shashinki_location_note),
                    on = location,
                ) { on ->
                    prefs.edit().putBoolean(PreferenceKeys.LocationPreferenceKey, on).apply()
                    // Android asks once; Open Camera starts reading the position when this screen
                    // closes and it rereads its settings.
                    if (on && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            LOCATION_REQUEST,
                        )
                    }
                    changed()
                }
            }
            item {
                Toggle(
                    title = stringResource(R.string.shashinki_qr),
                    note = stringResource(R.string.shashinki_qr_note),
                    on = qr,
                ) {
                    prefs.edit().putBoolean(Defaults.QR, it).apply()
                    changed()
                }
            }
            item {
                Toggle(
                    title = stringResource(R.string.shashinki_grid),
                    on = grid,
                ) {
                    prefs.edit().putBoolean(Defaults.GRID, it).apply()
                    changed()
                }
            }
            item {
                Setting(
                    title = stringResource(R.string.shashinki_folder),
                    value = "DCIM/$folder",
                    onClick = { folderOpen = true },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })

    when (picking) {
        Pick.PICTURE -> {
            val sizes = preview?.getSupportedPictureSizes(true).orEmpty()
            val current = prefs.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId, physical), null)
                ?.split(' ')?.mapNotNull { it.toIntOrNull() }
                ?: preview?.currentPictureSize?.let { listOf(it.width, it.height) }
            PickDialog(
                title = stringResource(R.string.shashinki_picture_size),
                options = sizes.map { sizeLabel(it.width, it.height) },
                chosen = sizes.indexOfFirst { listOf(it.width, it.height) == current },
                onChoose = { i ->
                    val s = sizes[i]
                    prefs.edit().putString(PreferenceKeys.getResolutionPreferenceKey(cameraId, physical), "${s.width} ${s.height}").apply()
                    // Open Camera applies a new size on reopening; the settings closing does that.
                    picking = null
                    changed()
                },
                onDismiss = { picking = null },
            )
        }
        Pick.VIDEO -> {
            val handler = preview?.videoQualityHander
            val qualities = handler?.supportedVideoQuality.orEmpty()
            PickDialog(
                title = stringResource(R.string.shashinki_video_size),
                options = qualities.map { preview!!.getCamcorderProfileDescriptionShort(it) },
                chosen = qualities.indexOf(
                    prefs.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId, physical, false), null)
                        ?.takeIf { it in qualities } ?: handler?.currentVideoQuality,
                ),
                onChoose = { i ->
                    prefs.edit().putString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId, physical, false), qualities[i]).apply()
                    picking = null
                    changed()
                },
                onDismiss = { picking = null },
            )
        }
        null -> Unit
    }

    if (folderOpen) {
        FolderDialog(
            initial = prefs.getString(PreferenceKeys.SaveLocationPreferenceKey, "Camera") ?: "Camera",
            onSave = {
                prefs.edit().putString(PreferenceKeys.SaveLocationPreferenceKey, it).apply()
                changed()
            },
            onDismiss = { folderOpen = false },
        )
    }
}

private enum class Pick { PICTURE, VIDEO }

private val QUALITIES = listOf("85", "90", "95", "100")
private val TIMERS = listOf("0", "3", "5", "10")
private const val LOCATION_REQUEST = 7301

/** "3264 × 2448 · 8 MP", the shape Open Camera's own list uses, said plainly. Under one
 *  megapixel it keeps a decimal, and under a tenth two, so a small size does not read as
 *  nothing at all ("176 × 144 · 0.03 MP", not "0.0"). */
private fun sizeLabel(w: Int, h: Int): String {
    val pixels = w.toLong() * h
    val mp = when {
        pixels < 100_000 -> "%.2f".format(pixels / 1_000_000.0)
        pixels < 1_000_000 -> "%.1f".format(pixels / 1_000_000.0)
        else -> ((pixels + 500_000) / 1_000_000).toString()
    }
    return "$w × $h · $mp MP"
}

@Composable
private fun Setting(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
        TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

/** A row and a switch; the row takes the press, the switch only draws the state. */
@Composable
private fun Toggle(title: String, on: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(checked = on, onCheckedChange = null)
    }
}

@Composable
internal fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** One of a list, the chosen one marked in its own words rather than by a tick. */
@Composable
private fun PickDialog(title: String, options: List<String>, chosen: Int, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumnMMD(modifier = Modifier.fillMaxWidth().height(300.dp), isScrollbarVisible = false) {
            items(options.size) { i ->
                TextMMD(
                    text = if (i == chosen) stringResource(R.string.shashinki_in_use, options[i]) else options[i],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { onChoose(i) }.padding(vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            TextMMD(text = stringResource(R.string.shashinki_close), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The folder under DCIM the pictures go to. Done on the keyboard saves it. */
@Composable
private fun FolderDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    // A name only: no slashes, nothing that leaves DCIM.
    val name = value.text.trim()
    val ok = name.isNotEmpty() && name.none { it == '/' || it == '\\' } && name != "." && name != ".."
    val save = {
        if (ok) onSave(name)
        onDismiss()
    }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.shashinki_folder), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        TextMMD(text = stringResource(R.string.shashinki_folder_note), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        TextFieldMMD(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButtonMMD(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                TextMMD(text = stringResource(R.string.shashinki_cancel), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButtonMMD(onClick = save, enabled = ok, modifier = Modifier.weight(1f).height(48.dp)) {
                TextMMD(text = stringResource(R.string.shashinki_save), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
