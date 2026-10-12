package com.wanderwildwood.shashinki.ui

import android.media.Image
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import net.sourceforge.opencamera.cameracontroller.CameraController2
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Frames for the code reader, straight from the camera rather than off the screen: the camera
 * hands a grey copy of what the viewfinder shows, about 1280 wide, where the screen of this
 * phone is 480. Only the brightness is kept; a code needs nothing else.
 *
 * One frame at a time, and only when the reader asks for the next, so it reads as fast as it
 * can and no faster. Frames are turned the right way up here, as the viewfinder shows them.
 */
object CodeFrames {

    /** Brightness, one byte a pixel, upright, [width] by [height]. */
    class Frame(val luma: ByteArray, val width: Int, val height: Int, val front: Boolean)

    private val wanted = AtomicBoolean(false)
    private val frames = Channel<Frame>(Channel.CONFLATED)

    private val sink = CameraController2.CodeFrameSink { image, orientation, front ->
        if (wanted.compareAndSet(true, false)) frames.trySend(copy(image, orientation, front))
    }

    /**
     * Ask the camera for its copy. Takes effect when the camera next starts its preview, which
     * Open Camera does on resume and after its settings; until then [next] gives nothing.
     */
    fun start() {
        CameraController2.codeFrameSink = sink
    }

    fun stop() {
        CameraController2.codeFrameSink = null
        wanted.set(false)
        frames.tryReceive()
    }

    /** The next frame, or null when none came in [waitMs]: no copy set up for this session. */
    suspend fun next(waitMs: Long): Frame? {
        frames.tryReceive()
        wanted.set(true)
        return withTimeoutOrNull(waitMs) { frames.receive() }.also { if (it == null) wanted.set(false) }
    }

    private fun copy(image: Image, orientation: Int, front: Boolean): Frame {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val row = ByteArray(rowStride)
        // The sensor sits turned in the phone; this turns its picture the way the screen shows it.
        val turned = (orientation % 360 + 360) % 360
        val outW = if (turned == 90 || turned == 270) h else w
        val outH = if (turned == 90 || turned == 270) w else h
        val out = ByteArray(outW * outH)
        for (y in 0 until h) {
            buffer.position(y * rowStride)
            buffer.get(row, 0, minOf(rowStride, buffer.remaining()))
            for (x in 0 until w) {
                val v = row[x * pixelStride]
                val i = when (turned) {
                    90 -> x * outW + (h - 1 - y)
                    180 -> (h - 1 - y) * w + (w - 1 - x)
                    270 -> (w - 1 - x) * outW + y
                    else -> y * w + x
                }
                out[i] = v
            }
        }
        return Frame(out, outW, outH, front)
    }
}
