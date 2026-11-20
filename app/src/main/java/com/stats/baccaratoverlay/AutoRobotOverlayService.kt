package com.stats.baccaratoverlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.ImageReader
import android.media.ToneGenerator
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.math.roundToInt

class AutoRobotOverlayService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_V7_OVERLAY"
        const val ACTION_STOP = "ACTION_STOP_V7_OVERLAY"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        private const val CHANNEL_ID = "tuannanhh_bcr_v9_channel"
        private const val NOTIF_ID = 9001

        private const val GRID_ROWS = 6
        private const val SCAN_INTERVAL_MS = 350L
        private const val STABILITY_REQUIRED = 2
    }

    private lateinit var windowManager: WindowManager
    private lateinit var coinManager: AccountCoinManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var toneGenerator: ToneGenerator? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 720
    private var screenHeight = 1600
    private var screenDensity = 320

    private var robotView: View? = null
    private var targetView: View? = null
    private lateinit var robotParams: WindowManager.LayoutParams
    private lateinit var targetParams: WindowManager.LayoutParams

    private lateinit var ivOverlayRobot: ImageView
    private lateinit var viewOverlayRobotAura: View
    private lateinit var boxSquarePrediction: LinearLayout
    private lateinit var tvSquareTopTag: TextView
    private lateinit var tvSquareSideLabel: TextView
    private lateinit var tvSquareProbValue: TextView

    private lateinit var layoutExpandableBody: LinearLayout
    private lateinit var layoutRobotBubble: LinearLayout
    private lateinit var tvOverlayCoinBadge: TextView
    private lateinit var tvTimingBadge: TextView
    private lateinit var tvHitMissStats: TextView
    private lateinit var tvResultAnnouncement: TextView
    private lateinit var tvRobotStatus: TextView
    private lateinit var tvMainRoadStatus: TextView
    private lateinit var tvMarkovProb: TextView
    private lateinit var tvRecentBeads: TextView
    private lateinit var btnToggleMinimize: Button
    private lateinit var btnToggleScan: Button
    private lateinit var btnToggleTarget: Button
    private lateinit var btnResetRoad: Button

    private var isAutoScanning = false
    private var isTargetLocked = false
    private var isMinimized = false
    private var isOcrBusy = false

    private var stableGridSignature = ""
    private var stableGridHits = 0

    private val capturedHistory = mutableListOf<Char>()
    private var lastGridBeadCount = 0
    private var lastAlertedHandIndex = -1
    private var isBettingWindowOpen = true
    private var lastBannerResultToken = ""
    private var lastScoreboardTotal = -1

    // Live hit/miss verification tracker for previous actionable prediction
    private var pendingPredictedSide: Char? = null
    private var pendingPredictionHandCount: Int = -1
    private var hitCount = 0
    private var missCount = 0

    private var overlayRobotAnim: AnimatorSet? = null

    private val scanLoopRunnable = object : Runnable {
        override fun run() {
            if (isAutoScanning) {
                performLockedRoiAndTimingScan()
                mainHandler.postDelayed(this, SCAN_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        coinManager = AccountCoinManager(this)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (_: Exception) {
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        startAndroid14ProjectionForeground()
        setupOverlayWindows()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (resultCode == Activity.RESULT_OK && data != null) {
                    initMediaProjection(resultCode, data)
                }
            }
        }
        return START_STICKY
    }

    private fun startAndroid14ProjectionForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TuannAnhh BCR V9 Scanner",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TuannAnhh BCR V9")
            .setContentText("Robot đang phân tích xác suất & bảng vuông dự đoán...")
            .setSmallIcon(R.drawable.ic_robot_cutout)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        releaseProjection()
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                isAutoScanning = false
                mainHandler.removeCallbacks(scanLoopRunnable)
            }
        }, mainHandler)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TuannAnhhBcrV9Capture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )
    }

    private fun setupOverlayWindows() {
        val inflater = LayoutInflater.from(this)

        val defaultBoxW = (screenWidth * 0.70f).toInt().coerceAtLeast(dpToPx(160))
        val defaultBoxH = (screenHeight * 0.112f).toInt().coerceAtLeast(dpToPx(75))
        targetView = inflater.inflate(R.layout.scan_target_layout, null)
        targetParams = WindowManager.LayoutParams(
            defaultBoxW,
            defaultBoxH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth * 0.29f).toInt()
            y = (screenHeight * 0.673f).toInt()
        }
        windowManager.addView(targetView, targetParams)
        setupTargetDragAndResize()

        robotView = inflater.inflate(R.layout.robot_overlay_layout, null)
        robotParams = WindowManager.LayoutParams(
            dpToPx(276),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(10)
            y = dpToPx(30)
        }
        windowManager.addView(robotView, robotParams)

        bindOverlayViews()
        startOverlayRobotAnimations()
    }

    private fun bindOverlayViews() {
        val root = robotView ?: return
        ivOverlayRobot = root.findViewById(R.id.ivOverlayRobot)
        viewOverlayRobotAura = root.findViewById(R.id.viewOverlayRobotAura)
        boxSquarePrediction = root.findViewById(R.id.boxSquarePrediction)
        tvSquareTopTag = root.findViewById(R.id.tvSquareTopTag)
        tvSquareSideLabel = root.findViewById(R.id.tvSquareSideLabel)
        tvSquareProbValue = root.findViewById(R.id.tvSquareProbValue)

        layoutExpandableBody = root.findViewById(R.id.layoutExpandableBody)
        layoutRobotBubble = root.findViewById(R.id.layoutRobotBubble)
        tvOverlayCoinBadge = root.findViewById(R.id.tvOverlayCoinBadge)
        tvTimingBadge = root.findViewById(R.id.tvTimingBadge)
        tvHitMissStats = root.findViewById(R.id.tvHitMissStats)
        tvResultAnnouncement = root.findViewById(R.id.tvResultAnnouncement)
        tvRobotStatus = root.findViewById(R.id.tvRobotStatus)
        tvMainRoadStatus = root.findViewById(R.id.tvMainRoadStatus)
        tvMarkovProb = root.findViewById(R.id.tvMarkovProb)
        tvRecentBeads = root.findViewById(R.id.tvRecentBeads)
        btnToggleMinimize = root.findViewById(R.id.btnToggleMinimize)
        btnToggleScan = root.findViewById(R.id.btnToggleScan)
        btnToggleTarget = root.findViewById(R.id.btnToggleTarget)
        btnResetRoad = root.findViewById(R.id.btnResetRoad)

        val user = coinManager.getCurrentUser()
        val coins = coinManager.getCoins(user)
        tvOverlayCoinBadge.text = if (coinManager.isAdmin(user)) "ADMIN ∞" else "$coins Xu"

        val header = root.findViewById<View>(R.id.robotHeader)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = robotParams.x
                    initialY = robotParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    robotParams.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, screenWidth - dpToPx(100))
                    robotParams.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn(0, screenHeight - dpToPx(80))
                    windowManager.updateViewLayout(robotView, robotParams)
                    true
                }
                else -> false
            }
        }

        // Minimize toggles ONLY the detail panel — Robot + Compact Square Prediction Box remain visible!
        btnToggleMinimize.setOnClickListener {
            isMinimized = !isMinimized
            if (isMinimized) {
                layoutExpandableBody.visibility = View.GONE
                btnToggleMinimize.text = "+"
            } else {
                layoutExpandableBody.visibility = View.VISIBLE
                btnToggleMinimize.text = "—"
            }
        }

        root.findViewById<Button>(R.id.btnCloseOverlay).setOnClickListener {
            stopSelf()
        }

        btnToggleScan.setOnClickListener {
            isAutoScanning = !isAutoScanning
            if (isAutoScanning) {
                btnToggleScan.text = "TẠM DỪNG"
                btnToggleScan.setBackgroundResource(R.drawable.bg_v9_btn_neon_gold)
                btnToggleScan.setTextColor(Color.parseColor("#090D16"))
                tvTimingBadge.text = "🟢 ĐANG QUÉT CẦU"
                mainHandler.removeCallbacks(scanLoopRunnable)
                mainHandler.post(scanLoopRunnable)
            } else {
                btnToggleScan.text = "BẮT ĐẦU"
                btnToggleScan.setBackgroundResource(R.drawable.bg_v9_btn_rainbow)
                btnToggleScan.setTextColor(Color.WHITE)
                tvTimingBadge.text = "⏸ ĐÃ TẠM DỪNG"
                mainHandler.removeCallbacks(scanLoopRunnable)
            }
        }

        btnToggleTarget.setOnClickListener {
            isTargetLocked = !isTargetLocked
            val tView = targetView ?: return@setOnClickListener
            val resizeBtn = tView.findViewById<View>(R.id.btnResizeHandle)

            if (isTargetLocked) {
                btnToggleTarget.text = "MỞ KHUNG"
                tView.setBackgroundResource(R.drawable.bg_scan_target_locked)
                resizeBtn.visibility = View.GONE
                targetParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                btnToggleTarget.text = "KHÓA KHUNG"
                tView.setBackgroundResource(R.drawable.bg_scan_target)
                resizeBtn.visibility = View.VISIBLE
                targetParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            windowManager.updateViewLayout(tView, targetParams)
        }

        btnResetRoad.setOnClickListener {
            capturedHistory.clear()
            lastGridBeadCount = 0
            lastAlertedHandIndex = -1
            stableGridSignature = ""
            stableGridHits = 0
            lastBannerResultToken = ""
            lastScoreboardTotal = -1
            pendingPredictedSide = null
            pendingPredictionHandCount = -1
            hitCount = 0
            missCount = 0

            boxSquarePrediction.setBackgroundResource(R.drawable.bg_v9_square_wait)
            tvSquareTopTag.text = "TUANNANHH"
            tvSquareSideLabel.text = "CHỜ"
            tvSquareProbValue.text = "50.7%"
            tvHitMissStats.text = "Chuẩn: 0 | Gãy: 0"
            tvResultAnnouncement.setBackgroundResource(R.drawable.bg_result_banner)
            tvResultAnnouncement.text = "ĐÃ LÀM MỚI DỮ LIỆU BÀN MỚI"
            tvRobotStatus.text = "Sẵn sàng tính xác suất bàn mới!"
            tvMainRoadStatus.text = "Nhịp cột: Đang chờ dữ liệu..."
            tvMarkovProb.text = "CÁI: 50.7%\nCON: 49.3%"
            tvRecentBeads.text = "Chuỗi bắt được: --"
            playChimeAndVibrate(isAlert = false)
        }
    }

    private fun setupTargetDragAndResize() {
        val tView = targetView ?: return
        val resizeHandle = tView.findViewById<View>(R.id.btnResizeHandle)

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        tView.setOnTouchListener { _, event ->
            if (isTargetLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = targetParams.x
                    startY = targetParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    targetParams.x = (startX + (event.rawX - touchX).toInt())
                        .coerceIn(0, (screenWidth - targetParams.width).coerceAtLeast(0))
                    targetParams.y = (startY + (event.rawY - touchY).toInt())
                        .coerceIn(0, (screenHeight - targetParams.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(tView, targetParams)
                    true
                }
                else -> false
            }
        }

        var startW = 0
        var startH = 0
        resizeHandle.setOnTouchListener { _, event ->
            if (isTargetLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = targetParams.width
                    startH = targetParams.height
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    targetParams.width = (startW + (event.rawX - touchX).toInt())
                        .coerceIn(dpToPx(90), screenWidth - targetParams.x)
                    targetParams.height = (startH + (event.rawY - touchY).toInt())
                        .coerceIn(dpToPx(60), screenHeight - targetParams.y)
                    windowManager.updateViewLayout(tView, targetParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun startOverlayRobotAnimations() {
        val floatY = ObjectAnimator.ofFloat(ivOverlayRobot, View.TRANSLATION_Y, -5f, 5f).apply {
            duration = 1300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val auraScaleX = ObjectAnimator.ofFloat(viewOverlayRobotAura, View.SCALE_X, 0.85f, 1.18f).apply {
            duration = 1050L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val auraScaleY = ObjectAnimator.ofFloat(viewOverlayRobotAura, View.SCALE_Y, 0.85f, 1.18f).apply {
            duration = 1050L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        overlayRobotAnim = AnimatorSet().apply {
            playTogether(floatY, auraScaleX, auraScaleY)
            start()
        }
    }

    private fun triggerRobotAndSquareAlert() {
        ivOverlayRobot.animate()
            .scaleX(1.26f)
            .scaleY(1.26f)
            .rotationBy(18f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                ivOverlayRobot.animate().scaleX(1.0f).scaleY(1.0f).rotation(0f).setDuration(200L).start()
            }
            .start()

        boxSquarePrediction.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
        if (!isMinimized) {
            layoutRobotBubble.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pop_in))
        }
    }

    private fun performLockedRoiAndTimingScan() {
        val reader = imageReader ?: return
        val tView = targetView ?: return
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val fullBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            val inset = dpToPx(4)
            val loc = IntArray(2)
            tView.getLocationOnScreen(loc)
            val roiLeft = (loc[0] + inset).coerceIn(0, fullBitmap.width - 12)
            val roiTop = (loc[1] + inset).coerceIn(0, fullBitmap.height - 12)
            val roiWidth = (tView.width - inset * 2).coerceIn(12, fullBitmap.width - roiLeft)
            val roiHeight = (tView.height - inset * 2).coerceIn(12, fullBitmap.height - roiTop)

            val roiBitmap = Bitmap.createBitmap(fullBitmap, roiLeft, roiTop, roiWidth, roiHeight)
            val gridBeads = extractOrderedBeadsAdaptive(roiBitmap)
            roiBitmap.recycle()

            val signature = gridBeads.joinToString("")
            if (signature == stableGridSignature) {
                stableGridHits++
            } else {
                stableGridSignature = signature
                stableGridHits = 1
            }

            if (stableGridHits == STABILITY_REQUIRED && gridBeads.isNotEmpty()) {
                processStableRoadmapSnapshot(gridBeads)
            }

            if (!isOcrBusy) {
                isOcrBusy = true
                val ocrTop = (screenHeight * 0.36f).toInt().coerceIn(0, fullBitmap.height - 20)
                val ocrBottom = (screenHeight * 0.68f).toInt().coerceIn(ocrTop + 20, fullBitmap.height)
                val ocrLeft = (screenWidth * 0.02f).toInt().coerceIn(0, fullBitmap.width - 20)
                val ocrWidth = (screenWidth * 0.96f).toInt().coerceIn(20, fullBitmap.width - ocrLeft)

                val ocrBitmap = Bitmap.createBitmap(fullBitmap, ocrLeft, ocrTop, ocrWidth, ocrBottom - ocrTop)
                runTableAndCounterOcr(ocrBitmap)
            }

            fullBitmap.recycle()
        } catch (_: Exception) {
            isOcrBusy = false
        } finally {
            image.close()
        }
    }

    private fun extractOrderedBeadsAdaptive(roi: Bitmap): List<Char> {
        val aspectCols = ((roi.width.toFloat() / roi.height.toFloat()) * GRID_ROWS).roundToInt().coerceIn(4, 24)
        val cellW = roi.width.toFloat() / aspectCols
        val cellH = roi.height.toFloat() / GRID_ROWS
        val orderedBeads = mutableListOf<Char>()

        for (col in 0 until aspectCols) {
            for (row in 0 until GRID_ROWS) {
                val startX = (col * cellW + cellW * 0.14f).toInt().coerceIn(0, roi.width - 1)
                val endX = ((col + 1) * cellW - cellW * 0.14f).toInt().coerceIn(startX + 1, roi.width)
                val startY = (row * cellH + cellH * 0.14f).toInt().coerceIn(0, roi.height - 1)
                val endY = ((row + 1) * cellH - cellH * 0.14f).toInt().coerceIn(startY + 1, roi.height)

                var redHits = 0
                var blueHits = 0
                var greenHits = 0
                var totalSamples = 0

                var y = startY
                while (y < endY) {
                    var x = startX
                    while (x < endX) {
                        val px = roi.getPixel(x, y)
                        val r = Color.red(px)
                        val g = Color.green(px)
                        val b = Color.blue(px)
                        totalSamples++

                        if (r > 130 && (r - g) > 35 && (r - b) > 35) {
                            redHits++
                        } else if (b > 130 && (b - r) > 35 && (b - g) > 15) {
                            blueHits++
                        } else if (g > 125 && (g - r) > 40 && (g - b) > 40) {
                            greenHits++
                        }
                        x++
                    }
                    y++
                }

                if (totalSamples > 0) {
                    val redRatio = redHits.toFloat() / totalSamples
                    val blueRatio = blueHits.toFloat() / totalSamples
                    val greenRatio = greenHits.toFloat() / totalSamples

                    val bead = when {
                        redRatio >= 0.028f && redRatio >= blueRatio -> 'B'
                        blueRatio >= 0.028f && blueRatio > redRatio -> 'P'
                        greenRatio >= 0.14f && greenRatio > redRatio && greenRatio > blueRatio -> 'T'
                        else -> null
                    }
                    if (bead != null) {
                        orderedBeads.add(bead)
                    }
                }
            }
        }
        return orderedBeads
    }

    private fun processStableRoadmapSnapshot(gridBeads: List<Char>) {
        val isNewHandAdded = gridBeads.size > lastGridBeadCount && lastGridBeadCount > 0
        val isInitialLoad = lastGridBeadCount == 0 && gridBeads.isNotEmpty()

        // Verify previous actionable prediction against newly arrived outcome (B or P)
        if (isNewHandAdded && pendingPredictedSide != null && gridBeads.size > pendingPredictionHandCount) {
            val actualOutcome = gridBeads.last()
            if (actualOutcome == 'B' || actualOutcome == 'P') {
                if (actualOutcome == pendingPredictedSide) {
                    hitCount++
                } else {
                    missCount++
                }
                pendingPredictedSide = null
            }
        }

        capturedHistory.clear()
        capturedHistory.addAll(gridBeads)
        lastGridBeadCount = gridBeads.size

        val report = TuannAnhhBcrV9Engine.evaluate(capturedHistory)

        // Record current prediction for next-hand accuracy tracking
        if (report.recommendedSide == "BANKER") {
            pendingPredictedSide = 'B'
            pendingPredictionHandCount = capturedHistory.size
        } else if (report.recommendedSide == "PLAYER") {
            pendingPredictedSide = 'P'
            pendingPredictionHandCount = capturedHistory.size
        } else {
            pendingPredictedSide = null
        }

        updateHudWithReport(report)

        if (isNewHandAdded) {
            val newestBead = capturedHistory.last()
            announceRoundResult(newestBead, report)
        } else if (isInitialLoad) {
            tvResultAnnouncement.setBackgroundResource(R.drawable.bg_result_banner)
            tvResultAnnouncement.text = "ĐÃ ĐỒNG BỘ ${capturedHistory.size} TAY CẦU TRÊN BẢNG"
            if (report.recommendedSide != "WAIT" && lastAlertedHandIndex != capturedHistory.size) {
                lastAlertedHandIndex = capturedHistory.size
                playChimeAndVibrate(isAlert = true)
                triggerRobotAndSquareAlert()
            }
        }
    }

    private fun runTableAndCounterOcr(ocrBitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(ocrBitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.uppercase()

                val bettingOpenKeywords = listOf(
                    "BẮT ĐẦU ĐẶT CƯỢC", "BAT DAU DAT CUOC", "PLACE YOUR BET", "ĐẶT CƯỢC", "DAT CUOC"
                )
                val bettingClosedKeywords = listOf(
                    "NGỪNG ĐẶT CƯỢC", "NGUNG DAT CUOC", "NGỪNG CƯỢC", "NO MORE BET", "CHIA BÀI", "DEALING"
                )

                if (bettingOpenKeywords.any { rawText.contains(it) }) {
                    if (!isBettingWindowOpen) {
                        isBettingWindowOpen = true
                        tvTimingBadge.text = "🟢 MỞ CƯỢC!"
                        tvTimingBadge.setTextColor(Color.parseColor("#10B981"))

                        val report = TuannAnhhBcrV9Engine.evaluate(capturedHistory)
                        if (report.recommendedSide != "WAIT" && lastAlertedHandIndex != capturedHistory.size) {
                            lastAlertedHandIndex = capturedHistory.size
                            playChimeAndVibrate(isAlert = true)
                            triggerRobotAndSquareAlert()
                        }
                    }
                } else if (bettingClosedKeywords.any { rawText.contains(it) }) {
                    isBettingWindowOpen = false
                    tvTimingBadge.text = "🔴 ĐÓNG CƯỢC"
                    tvTimingBadge.setTextColor(Color.parseColor("#FF2A55"))
                }

                val explicitOutcome: Char? = when {
                    rawText.contains("BANKER WIN") || rawText.contains("NHÀ CÁI THẮNG") ||
                            rawText.contains("NHA CAI THANG") || rawText.contains("CÁI THẮNG") -> 'B'
                    rawText.contains("PLAYER WIN") || rawText.contains("NHÀ CON THẮNG") ||
                            rawText.contains("NHA CON THANG") || rawText.contains("CON THẮNG") -> 'P'
                    rawText.contains("TIE WIN") || rawText.contains("VÁN HÒA") ||
                            rawText.contains("VAN HOA") || rawText.contains("KẾT QUẢ HÒA") -> 'T'
                    else -> null
                }

                if (explicitOutcome != null) {
                    val token = "${capturedHistory.size}_$explicitOutcome"
                    if (token != lastBannerResultToken) {
                        lastBannerResultToken = token
                        val report = TuannAnhhBcrV9Engine.evaluate(capturedHistory)
                        announceRoundResult(explicitOutcome, report)
                    }
                }

                val totalMatch = Regex("TOTAL\\s*[:\\-]?\\s*(\\d{1,3})").find(rawText)
                if (totalMatch != null) {
                    val totalVal = totalMatch.groupValues[1].toIntOrNull() ?: -1
                    if (totalVal > 0) {
                        if (lastScoreboardTotal > 0 && totalVal == lastScoreboardTotal + 1 &&
                            capturedHistory.size == lastGridBeadCount && lastGridBeadCount > 0
                        ) {
                            val token = "${totalVal}_T"
                            if (token != lastBannerResultToken) {
                                lastBannerResultToken = token
                                val report = TuannAnhhBcrV9Engine.evaluate(capturedHistory)
                                announceRoundResult('T', report)
                            }
                        }
                        lastScoreboardTotal = totalVal
                    }
                }
            }
            .addOnCompleteListener {
                ocrBitmap.recycle()
                isOcrBusy = false
            }
    }

    private fun announceRoundResult(outcome: Char, report: V9CalibratedReport) {
        when (outcome) {
            'B' -> {
                tvResultAnnouncement.setBackgroundResource(R.drawable.bg_result_banker)
                tvResultAnnouncement.text = "🔴 VÁN VỪA RỒI: BANKER (CÁI) THẮNG!"
            }
            'P' -> {
                tvResultAnnouncement.setBackgroundResource(R.drawable.bg_result_player)
                tvResultAnnouncement.text = "🔵 VÁN VỪA RỒI: PLAYER (CON) THẮNG!"
            }
            'T' -> {
                tvResultAnnouncement.setBackgroundResource(R.drawable.bg_result_tie)
                tvResultAnnouncement.text = "🟢 VÁN VỪA RỒI: TIE (HÒA)!"
            }
        }
        tvTimingBadge.text = "🟢 VÀO LỆNH MỚI"
        tvTimingBadge.setTextColor(Color.parseColor("#FEE440"))

        updateHudWithReport(report)
        playChimeAndVibrate(isAlert = true)
        triggerRobotAndSquareAlert()
    }

    private fun updateHudWithReport(report: V9CalibratedReport) {
        // Update Compact Square Prediction Box (CÁI / CON / CHỜ + Probability %)
        tvSquareTopTag.text = report.squareTopTag
        tvSquareSideLabel.text = report.squareBigLabel
        tvSquareProbValue.text = report.squareProbText

        when (report.recommendedSide) {
            "BANKER" -> {
                boxSquarePrediction.setBackgroundResource(R.drawable.bg_v9_square_banker)
                tvSquareProbValue.setTextColor(Color.parseColor("#FEE440"))
            }
            "PLAYER" -> {
                boxSquarePrediction.setBackgroundResource(R.drawable.bg_v9_square_player)
                tvSquareProbValue.setTextColor(Color.parseColor("#00F5D4"))
            }
            else -> {
                boxSquarePrediction.setBackgroundResource(R.drawable.bg_v9_square_wait)
                tvSquareProbValue.setTextColor(Color.parseColor("#B8C1EC"))
            }
        }

        val bStr = String.format(Locale.US, "%.1f", report.probBPercent)
        val pStr = String.format(Locale.US, "%.1f", report.probPPercent)
        tvMarkovProb.text = "CÁI: $bStr%\nCON: $pStr%"
        tvHitMissStats.text = "Chuẩn: $hitCount | Gãy: $missCount"

        tvRobotStatus.text = report.robotNoteText
        tvMainRoadStatus.text = "${report.columnVectorText}\n${report.derivedRoadStatusText}"

        val recentStr = if (capturedHistory.isEmpty()) {
            "--"
        } else {
            capturedHistory.takeLast(12).joinToString(" → ")
        }
        tvRecentBeads.text = "12 tay gần nhất (${capturedHistory.size}): $recentStr"
    }

    private fun playChimeAndVibrate(isAlert: Boolean) {
        try {
            if (isAlert) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 260)
            } else {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            }
        } catch (_: Exception) {
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(
                VibrationEffect.createOneShot(if (isAlert) 240L else 90L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {
        }
    }

    private fun releaseProjection() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        isAutoScanning = false
        mainHandler.removeCallbacks(scanLoopRunnable)
        overlayRobotAnim?.cancel()
        toneGenerator?.release()
        releaseProjection()
        robotView?.let { windowManager.removeView(it) }
        targetView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }
}
