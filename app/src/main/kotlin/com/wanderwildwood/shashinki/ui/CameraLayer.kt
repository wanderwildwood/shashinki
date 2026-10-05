package com.wanderwildwood.shashinki.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.text.TextMMD
import net.sourceforge.opencamera.MainActivity
import net.sourceforge.opencamera.MyApplicationInterface
import net.sourceforge.opencamera.PreferenceKeys
import net.sourceforge.opencamera.R
import java.lang.ref.WeakReference

/**
 * This app's whole interface: a Compose layer laid over Open Camera's own activity.
 *
 * Open Camera keeps doing everything it does — opening the camera, focusing, saving, recording
 * — and its own buttons are still there, in a container that is never shown (see
 * activity_main.xml). Every press here calls the same method of Open Camera's that its own
 * button would have, so the camera behaves exactly as upstream's does and only the face changes.
 *
 * The layout is Mudita's own camera's: the viewfinder above a white bar of three circles —
 * the last picture, the shutter, the flash. Above that bar one line more than Mudita's, to switch
 * between photo and video, to turn on HDR or Night, and to reach the settings.
 */
object CameraLayer {

    /** What the bar shows, read from the camera when something changes and never on a timer. */
    data class Snapshot(
        val flash: String? = null,
        val hasFlash: Boolean = false,
        val video: Boolean = false,
        val recording: Boolean = false,
        val onTimer: Boolean = false,
        /** The camera could not be opened — on a Kompakt, most often the side switch. */
        val failed: Boolean = false,
        /** Open Camera's photo mode: standard, HDR or noise reduction (shown as Night). */
        val mode: MyApplicationInterface.PhotoMode = MyApplicationInterface.PhotoMode.Standard,
        val canHdr: Boolean = false,
        val canNight: Boolean = false,
    )

    internal var snapshot by mutableStateOf(Snapshot())
        private set

    /**
     * Something that went wrong, said in the viewfinder until the camera is next ready. Open
     * Camera says these in toasts, which this app turns off; a failure has to be said somewhere.
     */
    internal var trouble by mutableStateOf<Int?>(null)

    /**
     * The newest photo, as Open Camera hands its own gallery button a thumbnail of it. Shown in
     * the gallery circle: the one sign, besides the shutter's sound, that a photo was taken, and
     * a change in one small circle rather than an animation across the screen.
     */
    internal var newest by mutableStateOf<Bitmap?>(null)

    @JvmStatic
    fun thumbnail(bitmap: Bitmap?) {
        Handler(Looper.getMainLooper()).post { newest = bitmap }
    }

    /**
     * True for a moment as a photo is taken: the viewfinder goes white, like a shutter, and comes
     * back. On this panel that is one quick repaint — the plainest way to say "got it" — where
     * Open Camera's own flash of the frame is drawn on the overlay this app leaves empty.
     */
    internal var blinking by mutableStateOf(false)

    @JvmStatic
    fun blink() {
        val main = Handler(Looper.getMainLooper())
        main.post { blinking = true }
        main.postDelayed({ blinking = false }, BLINK_MS)
    }

    private const val BLINK_MS = 180L

    /** Called by Open Camera's error hooks: the camera stopped, or a photo failed. */
    @JvmStatic
    fun trouble(message: Int) {
        Handler(Looper.getMainLooper()).post { trouble = message }
    }

    /** Called when the camera has (re)opened: any earlier trouble is over. */
    @JvmStatic
    fun cameraReady() {
        Handler(Looper.getMainLooper()).post {
            trouble = null
            refresh()
        }
    }

    private var host = WeakReference<MainActivity>(null)

    /**
     * Called by MainActivity straight after super.onCreate, before Open Camera reads anything:
     * this app's defaults go in first, and Open Camera's first-run welcome dialog is marked as
     * seen — a dialog over the viewfinder before the camera has even opened is the wrong
     * introduction, and its "online help" leads to a site about a different app.
     */
    @JvmStatic
    fun prepare(activity: MainActivity) {
        Defaults.apply(activity)
        val prefs = Defaults.prefs(activity)
        if (!prefs.contains(PreferenceKeys.FirstTimePreferenceKey)) {
            prefs.edit().putBoolean(PreferenceKeys.FirstTimePreferenceKey, true).apply()
        }
        // Open Camera explains HDR in a dialog the first time it is chosen; the strip says
        // enough, and a dialog over the viewfinder is a second press to get back to it.
        if (!prefs.contains(PreferenceKeys.HDRInfoPreferenceKey)) {
            prefs.edit().putBoolean(PreferenceKeys.HDRInfoPreferenceKey, true).apply()
        }
    }

    /**
     * [refresh], once Open Camera has finished what it is doing. Its hooks fire at the start of
     * a change — the camera setting up, the shutter icon about to change — so reading the state
     * there would read the state before.
     */
    @JvmStatic
    fun refreshSoon() {
        Handler(Looper.getMainLooper()).post { refresh() }
    }

    /** Called by MainActivity at the end of its onCreate. */
    @JvmStatic
    fun attach(activity: MainActivity) {
        host = WeakReference(activity)
        // The viewfinder sits above the bars rather than under them, so the whole frame the
        // camera will save is the frame on screen.
        val preview = activity.findViewById<ViewGroup>(R.id.preview)
        (preview.parent as? ViewGroup)?.let { root ->
            val bars = ((BAR + STRIP).value * activity.resources.displayMetrics.density).toInt()
            root.setPadding(0, 0, 0, bars)
        }

        val layer = ComposeView(activity).apply {
            setContent {
                ThemeMMD(colorScheme = monochrome) {
                    Camera(activity)
                }
            }
        }
        activity.addContentView(
            layer,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        refresh()
    }

    /**
     * Read the camera's state again. Called by Open Camera wherever its own shutter icon would
     * have changed — the camera opening, video starting or stopping, the mode switching.
     */
    @JvmStatic
    fun refresh() {
        val activity = host.get() ?: return
        val preview = activity.preview ?: return
        // Neither mode for a photo another app asked for: it wants one picture back, quickly.
        val modes = activity.intent?.action !in listOf(
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_IMAGE_CAPTURE_SECURE,
        )
        snapshot = Snapshot(
            flash = preview.currentFlashValue,
            hasFlash = preview.supportsFlash(),
            video = preview.isVideo,
            recording = preview.isVideoRecording,
            onTimer = preview.isOnTimer,
            failed = preview.openCameraFailed(),
            mode = activity.applicationInterface.photoMode,
            canHdr = modes && activity.supportsHDR(),
            canNight = modes && activity.supportsNoiseReduction(),
        )
    }

    /**
     * HDR or Night on, or back to a plain photo — written where Open Camera's own photo-mode
     * menu writes it, then the camera set up again as that menu does, since both modes take a
     * burst of frames rather than one.
     */
    internal fun choose(activity: MainActivity, mode: MyApplicationInterface.PhotoMode) {
        val now = activity.applicationInterface.photoMode
        val next = if (now == mode) MyApplicationInterface.PhotoMode.Standard else mode
        val value = when (next) {
            MyApplicationInterface.PhotoMode.HDR -> "preference_photo_mode_hdr"
            MyApplicationInterface.PhotoMode.NoiseReduction -> "preference_photo_mode_noise_reduction"
            else -> "preference_photo_mode_std"
        }
        Defaults.prefs(activity).edit().putString(PreferenceKeys.PhotoModePreferenceKey, value).apply()
        activity.applicationInterface.drawPreview.updateSettings()
        activity.updateForSettings(true, null, false, true)
        refresh()
    }

    /**
     * Called by Open Camera once a photo is saved. Gallery (garō), when it is on the phone, is
     * told directly, so its backup starts within its own limits rather than waiting on Android
     * to notice the new picture — which on the Kompakt it sometimes does not.
     */
    @JvmStatic
    fun pictureSaved(context: Context, isPicture: Boolean) {
        if (!isPicture) return
        runCatching {
            context.sendBroadcast(
                Intent(GALLERY_NEW_PICTURE).setPackage(GALLERY),
            )
        }
    }

    private const val GALLERY = "com.wanderwildwood.garo"
    private const val GALLERY_NEW_PICTURE = "com.wanderwildwood.garo.action.NEW_PICTURE"

    /**
     * The three flash states Mudita's camera offers; Open Camera's others are skipped over.
     * "On" is Open Camera's torch: the light comes on when it is chosen and stays on, so the
     * picture can be framed by it, rather than firing only as the photo is taken.
     */
    private val FLASHES = listOf("flash_auto", "flash_off", "flash_torch")

    internal fun cycleFlash(activity: MainActivity) {
        val preview = activity.preview ?: return
        repeat(preview.supportedFlashValues?.size ?: 0) {
            preview.cycleFlash(false, true)
            if (preview.currentFlashValue in FLASHES) {
                refresh()
                return
            }
        }
        refresh()
    }

    internal val STRIP: Dp = 44.dp
    internal val BAR: Dp = 96.dp
}

@Composable
private fun Camera(activity: MainActivity) {
    var settingsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val shot = CameraLayer.snapshot

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            SettingsScreen(activity, onClose = {
                settingsOpen = false
                // Open Camera rereads everything and reopens the camera if it has to, as it
                // does when its own settings close.
                activity.updateForSettings(true)
                CameraLayer.refresh()
            })
        }
        return
    }

    // A code the viewfinder has read, waiting on the card; and the last one closed, which is
    // not offered again while it is still in front of the lens.
    var found by remember { mutableStateOf<Found?>(null) }
    var closed by remember { mutableStateOf<Pair<String, Long>?>(null) }
    QrWatch(activity, enabled = found == null && !shot.video && !shot.failed && Defaults.qr(activity)) { f ->
        val (raw, at) = closed ?: ("" to 0L)
        if (f.raw != raw || System.currentTimeMillis() - at > QR_QUIET_MS) found = f
    }

    Column(Modifier.fillMaxSize()) {
        // The viewfinder shows through here. Nothing in this box takes a touch, so a tap on the
        // picture still reaches Open Camera's preview, which focuses where it was tapped.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (Defaults.grid(activity)) Grid()
            if (CameraLayer.blinking) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
            found?.let { f ->
                Box(Modifier.align(Alignment.BottomCenter)) {
                    QrCard(f) {
                        closed = f.raw to System.currentTimeMillis()
                        found = null
                    }
                }
            }
            val message = when {
                shot.failed -> R.string.shashinki_no_camera
                else -> CameraLayer.trouble
            }
            if (message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                ) {
                    TextMMD(
                        text = stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }

        // Photo or video, what is happening, and the way into settings.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(CameraLayer.STRIP)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 16.dp),
        ) {
            Mode(stringResource(R.string.shashinki_photo), chosen = !shot.video, enabled = !shot.recording) {
                if (shot.video) {
                    activity.clickedSwitchVideo(null)
                    CameraLayer.refresh()
                }
            }
            TextMMD(text = "·", style = MaterialTheme.typography.bodyMedium)
            Mode(stringResource(R.string.shashinki_video), chosen = shot.video, enabled = !shot.recording) {
                if (!shot.video) {
                    activity.clickedSwitchVideo(null)
                    CameraLayer.refresh()
                }
            }
            Spacer(Modifier.weight(1f))
            if (!shot.video && !shot.recording) {
                if (shot.canHdr) {
                    Mode(stringResource(R.string.shashinki_hdr), chosen = shot.mode == MyApplicationInterface.PhotoMode.HDR, enabled = true, toggle = true) {
                        CameraLayer.choose(activity, MyApplicationInterface.PhotoMode.HDR)
                    }
                }
                if (shot.canNight) {
                    Mode(stringResource(R.string.shashinki_night), chosen = shot.mode == MyApplicationInterface.PhotoMode.NoiseReduction, enabled = true, toggle = true) {
                        CameraLayer.choose(activity, MyApplicationInterface.PhotoMode.NoiseReduction)
                    }
                }
            }
            // Said, not animated: a running clock would repaint this strip every second.
            val status = when {
                shot.recording -> stringResource(R.string.shashinki_recording)
                shot.onTimer -> stringResource(R.string.shashinki_timer_running)
                else -> null
            }
            if (status != null) {
                TextMMD(text = status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
            }
            if (!shot.recording) {
                BarIcon(Icons.Settings, stringResource(R.string.shashinki_cd_settings)) { settingsOpen = true }
            }
        }

        // Mudita's bar: the last picture, the shutter, the flash.
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(CameraLayer.BAR)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Ring(
                Icons.Picture,
                stringResource(R.string.shashinki_cd_gallery),
                enabled = !shot.recording,
                picture = CameraLayer.newest,
            ) {
                activity.clickedGallery(null)
            }
            Shutter(recording = shot.recording) {
                CameraLayer.trouble = null
                activity.clickedTakePhoto(null)
                CameraLayer.refresh()
            }
            if (shot.hasFlash && !shot.video) {
                Ring(
                    icon = when (shot.flash) {
                        "flash_torch" -> Icons.FlashOn
                        "flash_off" -> Icons.FlashOff
                        else -> Icons.FlashAuto
                    },
                    description = stringResource(
                        when (shot.flash) {
                            "flash_torch" -> R.string.shashinki_cd_flash_on
                            "flash_off" -> R.string.shashinki_cd_flash_off
                            else -> R.string.shashinki_cd_flash_auto
                        },
                    ),
                    enabled = true,
                ) { CameraLayer.cycleFlash(activity) }
            } else {
                // The same width as a ring, so the shutter stays in the middle.
                Spacer(Modifier.size(RING))
            }
        }
    }
}

@Composable
private fun Mode(label: String, chosen: Boolean, enabled: Boolean, toggle: Boolean = false, onClick: () -> Unit) {
    TextMMD(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (chosen) FontWeight.Bold else null,
        modifier = Modifier
            // Photo and video are one choice, so the chosen one has nothing to do; HDR and Night
            // are each on or off, so pressing the chosen one turns it off.
            .clickable(enabled = enabled && (toggle || !chosen), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** A 48dp press with a 22dp glyph, the size every top bar in this shop uses. */
@Composable
private fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** An outlined circle with a glyph in it, as Mudita draws the gallery and flash buttons. */
@Composable
private fun Ring(icon: ImageVector, description: String, enabled: Boolean, picture: Bitmap? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RING)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (picture != null) {
            Image(
                bitmap = remember(picture) { picture.asImageBitmap() },
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        } else {
            Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Mudita's shutter: a ring with a solid disc inside. While recording, the disc becomes a square,
 * the one shape that says "stop" without a word — and it changes once, not with an animation.
 */
@Composable
private fun Shutter(recording: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SHUTTER)
            .clip(CircleShape)
            .border(3.dp, ink, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (recording) {
            Box(Modifier.size(26.dp).background(ink))
        } else {
            Box(Modifier.size(SHUTTER - 14.dp).clip(CircleShape).background(ink))
        }
    }
}

/** Lines at the thirds, drawn once; they move only if the screen does. */
@Composable
private fun Grid() {
    Canvas(Modifier.fillMaxSize()) {
        val ink = Color.White.copy(alpha = 0.8f)
        for (i in 1..2) {
            val x = size.width * i / 3
            val y = size.height * i / 3
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
    }
}

/** How long a closed code stays closed while it is still in the frame. */
private const val QR_QUIET_MS = 8_000L

private val RING = 56.dp
private val SHUTTER = 72.dp
