package com.example.poolaimassist

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "pool_aim_assist_channel"
        private const val NOTIFICATION_ID = 1
    }
    private var projection: MediaProjection? = null
    private var engine: CaptureEngine? = null
    private var overlay: OverlayView? = null
    private var panel: ControlPanelView? = null
    private var calibration: CalibrationOverlay? = null
    private var wm: WindowManager? = null
    private val detector = BallDetector()
    private var calibrating = false
    private var frameCount = 0L

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (code != Activity.RESULT_OK || resultData == null) {
            stopSelf(); return START_NOT_STICKY
        }
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = pm.getMediaProjection(code, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlay()
        addPanel()
        if (!CalibrationStore.hasCalibration(this)) startCalibration()
        try {
            projection?.let { mp ->
                engine = CaptureEngine(this, mp) { bmp -> processFrame(bmp) }
                engine?.start()
            }
        } catch (e: Exception) {
            Log.e("PoolAimAssist", "Capture could not start", e)
            overlay?.update(null, emptyList(), CalibrationStore.loadPockets(this), "Capture error: ${e.javaClass.simpleName}")
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun windowType(): Int = if (Build.VERSION.SDK_INT >= 26)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else WindowManager.LayoutParams.TYPE_PHONE

    private fun addOverlay() {
        val manager = wm ?: return
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            windowType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        overlay = OverlayView(this).also { manager.addView(it, p) }
    }
    private fun addPanel() {
        val manager = wm ?: return
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 16; p.y = 100
        panel = ControlPanelView(this, manager, p, { startCalibration() }, { stopSelf() })
        manager.addView(panel, p)
    }
    private fun startCalibration() {
        if (calibrating) return
        val manager = wm ?: return
        calibrating = true
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            windowType(), WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        calibration = CalibrationOverlay(this) { points ->
            CalibrationStore.savePockets(this, points)
            calibration?.let { runCatching { manager.removeView(it) } }
            calibration = null
            calibrating = false
        }
        manager.addView(calibration, p)
    }

    private fun processFrame(bitmap: Bitmap) {
        try {
            if (calibrating) return
            frameCount++
            val view = overlay ?: return
            val vw = view.width.takeIf { it > 0 } ?: bitmap.width
            val vh = view.height.takeIf { it > 0 } ?: bitmap.height
            val sx = bitmap.width.toFloat() / vw
            val sy = bitmap.height.toFloat() / vh
            fun toCapture(p: Pt) = Pt(p.x * sx, p.y * sy)
            fun toView(p: Pt) = Pt(p.x / sx, p.y / sy)
            val viewPockets = CalibrationStore.loadPockets(this)
            val capturePockets = viewPockets.map(::toCapture)
            val balls = detector.detectBalls(bitmap, capturePockets)
            val cue = balls.firstOrNull { it.isCueBall }
            val objects = balls.filterNot { it.isCueBall }
            val shots = if (cue != null && capturePockets.size == 6)
                TrajectoryCalculator.computeShotGuides(cue, objects, capturePockets)
                else emptyList()
            fun mapBall(ball: Ball): Ball = ball.copy(
                center = toView(ball.center),
                radiusPx = ball.radiusPx / ((sx + sy) / 2f)
            )
            val mappedShots = shots.map { g ->
                ShotGuide(mapBall(g.objectBall), toView(g.pocket), toView(g.ghostBallCenter), g.cutAngleDeg)
            }
            val diag = "F:$frameCount  Cue:${if (cue != null) "YES" else "NO"}  Balls:${objects.size}  Lines:${shots.size}"
            view.update(cue?.let(::mapBall), mappedShots, viewPockets, diag)
        } catch (e: Exception) {
            Log.e("PoolAimAssist", "Detection failed", e)
            overlay?.update(null, emptyList(), CalibrationStore.loadPockets(this), "Detection error: ${e.javaClass.simpleName}")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Pool Aim Assist", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pool Aim Assist is running")
            .setContentText("Screen capture and shot guide")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build()
    }
    override fun onDestroy() {
        engine?.stop()
        calibration?.let { runCatching { wm?.removeView(it) } }
        panel?.let { runCatching { wm?.removeView(it) } }
        overlay?.let { runCatching { wm?.removeView(it) } }
        projection?.stop()
        super.onDestroy()
    }
}
