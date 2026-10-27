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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
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
import kotlinx.coroutines.delay

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
        /**
         * Open Camera's photo mode: standard, DRO (shown as HDR), HDR (shown as HDR+) or noise
         * reduction (shown as Night).
         */
        val mode: MyApplicationInterface.PhotoMode = MyApplicationInterface.PhotoMode.Standard,
        /** One-shot HDR: Open Camera's DRO, which brightens the shadows of a single frame. */
        val canDro: Boolean = false,
        /** Three-shot HDR: three exposures joined, which needs the phone held still. */
        val canHdr: Boolean = false,
        val canNight: Boolean = false,
        /**
         * Megapixels the photo will have in Night mode, which joins a burst and so cannot use
         * the biggest sizes; 0 when Night is not on.
         */
        val nightMp: Int = 0,
        /** The camera has not been allowed, and the question has been answered no. */
        val noPermission: Boolean = false,
        /** Android will ask again; false once it has been refused for good. */
        val canAsk: Boolean = true,
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

    /**
     * True from the moment a photo starts until Open Camera has its frames. Only said for the
     * modes that take several frames (HDR+ and Night); a one-frame photo is over before the
     * words would be drawn.
     */
    internal var capturing by mutableStateOf(false)

    /** Called by Open Camera as a capture starts and once it is over, or the camera stops. */
    @JvmStatic
    fun capturing(on: Boolean) {
        Handler(Looper.getMainLooper()).post { capturing = on }
    }

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
        Handler(Looper.getMainLooper()).post {
            trouble = message
            capturing = false
        }
    }

    /**
     * Called by MainActivity as it resumes, before the camera opens: the camera is asked for a
     * copy of its frames for the code reader while reading codes is on or a scan was asked for.
     */
    @JvmStatic
    fun resume(activity: MainActivity) {
        if (Defaults.qr(activity) || ScanRequest.asked(activity.intent)) CodeFrames.start() else CodeFrames.stop()
        refreshSoon()
    }

    /** What the sizes and qualities were as the camera closed; the settings list reads these. */
    class PanelInfo(
        val pictureSizes: List<Pair<Int, Int>>,
        val currentPicture: Pair<Int, Int>?,
        val videoQualities: List<String>,
        val videoNames: Map<String, String>,
        val currentVideo: String?,
        val steady: Boolean,
        /** The largest picture Night mode can take, in megapixels. */
        val nightMaxMp: Int,
    )

    internal var panelInfo by mutableStateOf<PanelInfo?>(null)
        private set

    private fun megapixels(w: Int, h: Int) = ((w.toLong() * h + 500_000) / 1_000_000).toInt()

    private fun nightMegapixels(activity: MainActivity): Int {
        val preview = activity.preview ?: return 0
        if (activity.applicationInterface.photoMode != MyApplicationInterface.PhotoMode.NoiseReduction || preview.isVideo) return 0
        val size = preview.currentPictureSize ?: return 0
        return megapixels(size.width, size.height)
    }

    /**
     * A full-screen panel is about to cover the viewfinder: note what the settings will want to
     * list, then close the camera. Open, it would go on drawing frames nobody can see.
     */
    internal fun panelOpened(activity: MainActivity) {
        val preview = activity.preview ?: return
        if (!activity.isCameraInBackground && preview.cameraController != null) {
            val handler = preview.videoQualityHander
            val qualities = handler?.supportedVideoQuality.orEmpty()
            val sizes = preview.getSupportedPictureSizes(true).orEmpty()
            panelInfo = PanelInfo(
                pictureSizes = sizes.map { it.width to it.height },
                currentPicture = preview.currentPictureSize?.let { it.width to it.height },
                videoQualities = qualities,
                videoNames = qualities.associateWith { preview.getCamcorderProfileDescriptionShort(it) },
                currentVideo = handler?.currentVideoQuality,
                steady = preview.supportsVideoStabilization(),
                // Only worth saying when Night really is smaller than the largest picture.
                nightMaxMp = sizes.filter { it.supports_burst }.maxOfOrNull { megapixels(it.width, it.height) }
                    ?.takeIf { it < (sizes.maxOfOrNull { s -> megapixels(s.width, s.height) } ?: 0) } ?: 0,
            )
        }
        activity.closeCameraForPanel()
    }

    /** The panel is gone: open the camera again, on whatever the panel changed. */
    internal fun panelClosed(activity: MainActivity) {
        activity.reopenCameraAfterPanel()
        refresh()
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
                    // A cursor that blinks repaints the panel twice a second for as long as a
                    // text field has focus.
                    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                        Camera(activity)
                    }
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
        // Posted, not called: this runs inside Open Camera's own camera-opening code.
        if (!preview.isVideoRecording && Defaults.fullFrameOnce(activity)) {
            Handler(Looper.getMainLooper()).post { activity.updateForSettings(true) }
        }
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
            canDro = modes && activity.supportsDRO(),
            canHdr = modes && activity.supportsHDR(),
            canNight = modes && activity.supportsNoiseReduction(),
            nightMp = nightMegapixels(activity),
            noPermission = activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                activity.permissionHandler.cameraDenied(),
            canAsk = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
        )
    }

    /**
     * The HDR press: off → HDR (one shot) → HDR+ (three shots) → off. One shot first, since it
     * is the one that works handheld; a camera without exposure bracketing goes straight back
     * to off.
     */
    internal fun cycleHdr(activity: MainActivity) {
        val shot = snapshot
        val next = when (shot.mode) {
            MyApplicationInterface.PhotoMode.DRO ->
                if (shot.canHdr) MyApplicationInterface.PhotoMode.HDR else MyApplicationInterface.PhotoMode.Standard
            MyApplicationInterface.PhotoMode.HDR -> MyApplicationInterface.PhotoMode.Standard
            else ->
                if (shot.canDro) MyApplicationInterface.PhotoMode.DRO else MyApplicationInterface.PhotoMode.HDR
        }
        choose(activity, next)
    }

    /** Night on, or back to a plain photo. */
    internal fun toggle(activity: MainActivity, mode: MyApplicationInterface.PhotoMode) {
        val now = activity.applicationInterface.photoMode
        choose(activity, if (now == mode) MyApplicationInterface.PhotoMode.Standard else mode)
    }

    /**
     * A photo mode, written where Open Camera's own photo-mode menu writes it, then the camera
     * set up again as that menu does, since the modes differ in how many frames they take.
     */
    private fun choose(activity: MainActivity, next: MyApplicationInterface.PhotoMode) {
        val value = when (next) {
            MyApplicationInterface.PhotoMode.DRO -> "preference_photo_mode_dro"
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

    internal val STRIP: Dp = 48.dp
    internal val BAR: Dp = 96.dp
}

@Composable
private fun Camera(activity: MainActivity) {
    var settingsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val shot = CameraLayer.snapshot

    if (settingsOpen) {
        // The camera is closed while this covers it, and opened again, on the settings as they
        // now are, when it goes: Open Camera does the same when its own settings open and close.
        // Back and the close button leave the same way.
        DisposableEffect(Unit) {
            CameraLayer.panelOpened(activity)
            onDispose { CameraLayer.panelClosed(activity) }
        }
        val close = { settingsOpen = false }
        BackHandler(onBack = close)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            SettingsScreen(activity, onClose = close)
        }
        return
    }

    // A code the viewfinder has read, shown on its own screen; and the last one closed, which
    // is not offered again while it is still in front of the lens.
    var found by remember { mutableStateOf<Found?>(null) }
    var closed by remember { mutableStateOf<Pair<String, Long>?>(null) }
    // Another app asked for a scan: the square stays up, and the code goes straight back.
    val scan = remember(activity.intent) { ScanRequest.asked(activity.intent) }
    val formats = remember(activity.intent) { ScanRequest.formats(activity.intent) }
    // The square to aim with comes up once a code is seen, and goes a while after it is not.
    var spottedAt by remember { mutableStateOf(0L) }
    var aiming by remember { mutableStateOf(false) }
    LaunchedEffect(spottedAt) {
        if (spottedAt == 0L) return@LaunchedEffect
        aiming = true
        delay(AIM_SHOWN_MS)
        aiming = false
    }
    QrWatch(
        activity,
        enabled = found == null && !shot.video && !shot.failed && (scan || Defaults.qr(activity)),
        formats = if (scan) formats else QrReader.ALL,
        anywhere = scan,
        onSpotted = { if (!aiming || System.currentTimeMillis() - spottedAt > AIM_SHOWN_MS / 2) spottedAt = System.currentTimeMillis() },
    ) { f, result ->
        if (scan) {
            ScanRequest.answer(activity, result)
            return@QrWatch
        }
        val (raw, at) = closed ?: ("" to 0L)
        if (f.raw != raw || System.currentTimeMillis() - at > QR_QUIET_MS) found = f
    }
    found?.let { f ->
        DisposableEffect(Unit) {
            CameraLayer.panelOpened(activity)
            onDispose { CameraLayer.panelClosed(activity) }
        }
        val close = {
            closed = f.raw to System.currentTimeMillis()
            found = null
        }
        BackHandler(onBack = close)
        QrScreen(f, onClose = close)
        return
    }

    Column(Modifier.fillMaxSize()) {
        // The viewfinder shows through here. Nothing in this box takes a touch, so a tap on the
        // picture still reaches Open Camera's preview, which focuses where it was tapped.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (Defaults.grid(activity)) Grid()
            if ((scan || aiming) && !shot.video) Aim(activity)
            if (scan) ScanRequest.prompt(activity.intent)?.let { prompt ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) {
                    TextMMD(text = prompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            if (CameraLayer.blinking) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
            val message = when {
                shot.noPermission -> null
                shot.failed -> R.string.shashinki_no_camera
                else -> CameraLayer.trouble
            }
            if (shot.noPermission) {
                // The camera was refused: a black viewfinder and a shutter that does nothing is
                // all there is to see, so say what is wrong and how to put it right.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        TextMMD(text = stringResource(R.string.shashinki_permission_camera), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButtonMMD(
                            onClick = {
                                if (shot.canAsk) {
                                    activity.permissionHandler.askForCamera()
                                } else {
                                    runCatching {
                                        activity.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            TextMMD(
                                text = stringResource(if (shot.canAsk) R.string.shashinki_allow_camera else R.string.shashinki_open_app_settings),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
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
                .padding(start = 11.dp),
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
            // Not while the timer counts down: the room is wanted for saying so on one line.
            if (!shot.video && !shot.recording && !shot.onTimer) {
                if (shot.canDro || shot.canHdr) {
                    // One word that says which HDR is on: plain when off, bold "HDR" for one
                    // shot, bold "HDR+" for three.
                    val three = shot.mode == MyApplicationInterface.PhotoMode.HDR
                    Mode(
                        stringResource(if (three) R.string.shashinki_hdr_plus else R.string.shashinki_hdr),
                        chosen = three || shot.mode == MyApplicationInterface.PhotoMode.DRO,
                        enabled = !CameraLayer.capturing,
                        toggle = true,
                    ) {
                        CameraLayer.cycleHdr(activity)
                    }
                }
                if (shot.canNight) {
                    val night = stringResource(R.string.shashinki_night)
                    Mode(if (shot.nightMp > 0) stringResource(R.string.shashinki_night_size, night, shot.nightMp) else night, chosen = shot.mode == MyApplicationInterface.PhotoMode.NoiseReduction, enabled = !CameraLayer.capturing, toggle = true) {
                        CameraLayer.toggle(activity, MyApplicationInterface.PhotoMode.NoiseReduction)
                    }
                }
            }
            // Said, not animated: a running clock would repaint this strip every second.
            val status = when {
                shot.recording -> stringResource(R.string.shashinki_recording)
                shot.onTimer -> stringResource(R.string.shashinki_timer_running)
                CameraLayer.capturing && (
                    shot.mode == MyApplicationInterface.PhotoMode.HDR ||
                        shot.mode == MyApplicationInterface.PhotoMode.NoiseReduction
                    ) -> stringResource(R.string.shashinki_hold_still)
                else -> null
            }
            if (status != null) {
                TextMMD(text = status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
            }
            if (!shot.recording) {
                BarIcon(Icons.Settings, stringResource(R.string.shashinki_cd_settings)) {
                    CameraLayer.panelOpened(activity)
                    settingsOpen = true
                }
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
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(enabled = enabled && (toggle || !chosen), onClick = onClick)
            .padding(horizontal = 5.dp)
            .wrapContentHeight(Alignment.CenterVertically),
        maxLines = 1,
        softWrap = false,
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

/**
 * The square a code is read in, over the middle of the picture: corners only, black edged in
 * white so they show over anything. The same square [QrReader] reads first.
 */
@Composable
private fun Aim(activity: MainActivity) {
    val view = activity.preview?.view
    // Over the picture itself, which need not sit in the middle of this box.
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        var cx = size.width / 2
        var cy = size.height / 2
        var w = size.width
        var h = size.height
        if (view != null && view.width > 0 && view.height > 0) {
            val at = IntArray(2).also { view.getLocationInWindow(it) }
            cx = at[0] - origin.x + view.width / 2f
            cy = at[1] - origin.y + view.height / 2f
            w = view.width.toFloat()
            h = view.height.toFloat()
        }
        val side = QrReader.aim(w.toInt(), h.toInt()).toFloat()
        val left = cx - side / 2
        val top = cy - side / 2
        val arm = side / 6
        val corners = listOf(
            Offset(left, top) to Offset(1f, 1f),
            Offset(left + side, top) to Offset(-1f, 1f),
            Offset(left, top + side) to Offset(1f, -1f),
            Offset(left + side, top + side) to Offset(-1f, -1f),
        )
        for ((width, ink) in listOf(14f to Color.White, 8f to Color.Black)) {
            for ((c, d) in corners) {
                drawLine(ink, c, Offset(c.x + d.x * arm, c.y), strokeWidth = width)
                drawLine(ink, c, Offset(c.x, c.y + d.y * arm), strokeWidth = width)
            }
        }
    }
}

/** How long the square stays up after a code was last seen. */
private const val AIM_SHOWN_MS = 3_000L

/** How long a closed code stays closed while it is still in the frame. */
private const val QR_QUIET_MS = 8_000L

private val RING = 56.dp
private val SHUTTER = 72.dp
