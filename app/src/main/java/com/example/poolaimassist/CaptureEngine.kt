package com.example.poolaimassist

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Wraps MediaProjection + ImageReader to periodically grab a Bitmap of the screen.
 * Runs on its own background thread; capture is polled at [intervalMs] rather than
 * continuously, which is enough for a guideline overlay and much easier on battery/CPU
 * than processing every frame.
 */
class CaptureEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val intervalMs: Long = 200L, // ~5 fps
    private val onFrame: (Bitmap) -> Unit
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val thread = HandlerThread("CaptureEngineThread").apply { start() }
    private val handler = Handler(thread.looper)
    private var running = false

    private val width: Int
    private val height: Int
    private val density: Int

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
    }

    fun start() {
        if (running) return
        running = true

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "PoolAimAssistCapture",
            width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        scheduleNextGrab()
    }

    private fun scheduleNextGrab() {
        if (!running) return
        handler.postDelayed({
            grabFrame()
            scheduleNextGrab()
        }, intervalMs)
    }

    private fun grabFrame() {
        val reader = imageReader ?: return
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            onFrame(cropped)
        } catch (e: Exception) {
            // Swallow transient capture errors (e.g. during rotation) - next frame will retry.
        } finally {
            image.close()
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        thread.quitSafely()
    }
}
