package com.stats.baccaratoverlay

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var coinManager: AccountCoinManager

    private lateinit var tvTopLivePill: TextView
    private lateinit var heroRobotContainer: View
    private lateinit var viewRobotOuterRing: View
    private lateinit var ivLoginRobot: ImageView
    private lateinit var viewLoginRobotAura: View
    private lateinit var tvAppTitle: TextView
    private lateinit var tvVersionBadge: TextView
    private lateinit var tvAppSubtitle: TextView

    private lateinit var cardLoginSection: LinearLayout
    private lateinit var etLoginUser: EditText
    private lateinit var etLoginPass: EditText
    private lateinit var btnLoginSubmit: Button
    private lateinit var btnQuickDemo: Button
    private lateinit var tvLoginFeedback: TextView

    private lateinit var cardDashboardSection: LinearLayout
    private lateinit var tvWelcomeUser: TextView
    private lateinit var tvCoinBalance: TextView
    private lateinit var etRedeemKey: EditText
    private lateinit var btnRedeemKey: Button
    private lateinit var btnLaunchRobot: Button
    private lateinit var btnLogout: Button

    private lateinit var cardAdminSection: LinearLayout
    private lateinit var etAdminTargetUser: EditText
    private lateinit var etAdminTargetPass: EditText
    private lateinit var etAdminTargetCoins: EditText
    private lateinit var btnAdminGrant: Button

    private var robotFloatAnimator: AnimatorSet? = null
    private var auraPulseAnimator: AnimatorSet? = null
    private var ringSpinAnimator: ObjectAnimator? = null
    private var titleRgbAnimator: ValueAnimator? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (!coinManager.consumeOneCoinForTable()) {
                Toast.makeText(this, "Hết Xu! Vui lòng nạp thêm Key hoặc liên hệ Admin.", Toast.LENGTH_LONG).show()
                refreshAccountState(animateReveal = false)
                return@registerForActivityResult
            }
            refreshAccountState(animateReveal = false)

            val serviceIntent = Intent(this, AutoRobotOverlayService::class.java).apply {
                action = AutoRobotOverlayService.ACTION_START
                putExtra(AutoRobotOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(AutoRobotOverlayService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Đã bật TuannAnhh BCR V9! Robot + Bảng vuông xác suất đã sẵn sàng.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Bạn cần cấp quyền quay màn hình để Robot đọc bảng cầu!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coinManager = AccountCoinManager(this)
        bindViews()
        setupColorfulAnimations()
        bindListeners()
        requestNotificationPermissionAndroid14()
        refreshAccountState(animateReveal = false)
    }

    private fun bindViews() {
        tvTopLivePill = findViewById(R.id.tvTopLivePill)
        heroRobotContainer = findViewById(R.id.heroRobotContainer)
        viewRobotOuterRing = findViewById(R.id.viewRobotOuterRing)
        ivLoginRobot = findViewById(R.id.ivLoginRobot)
        viewLoginRobotAura = findViewById(R.id.viewLoginRobotAura)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvVersionBadge = findViewById(R.id.tvVersionBadge)
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle)

        cardLoginSection = findViewById(R.id.cardLoginSection)
        etLoginUser = findViewById(R.id.etLoginUser)
        etLoginPass = findViewById(R.id.etLoginPass)
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit)
        btnQuickDemo = findViewById(R.id.btnQuickDemo)
        tvLoginFeedback = findViewById(R.id.tvLoginFeedback)

        cardDashboardSection = findViewById(R.id.cardDashboardSection)
        tvWelcomeUser = findViewById(R.id.tvWelcomeUser)
        tvCoinBalance = findViewById(R.id.tvCoinBalance)
        etRedeemKey = findViewById(R.id.etRedeemKey)
        btnRedeemKey = findViewById(R.id.btnRedeemKey)
        btnLaunchRobot = findViewById(R.id.btnLaunchRobot)
        btnLogout = findViewById(R.id.btnLogout)

        cardAdminSection = findViewById(R.id.cardAdminSection)
        etAdminTargetUser = findViewById(R.id.etAdminTargetUser)
        etAdminTargetPass = findViewById(R.id.etAdminTargetPass)
        etAdminTargetCoins = findViewById(R.id.etAdminTargetCoins)
        btnAdminGrant = findViewById(R.id.btnAdminGrant)
    }

    private fun setupColorfulAnimations() {
        val viewsToReveal = listOf(
            tvTopLivePill, heroRobotContainer, tvAppTitle, tvVersionBadge, tvAppSubtitle, cardLoginSection
        )
        viewsToReveal.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 38f
            view.scaleX = 0.94f
            view.scaleY = 0.94f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 75L)
                .setDuration(460L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Continuous 360-degree outer neon ring rotation
        ringSpinAnimator = ObjectAnimator.ofFloat(viewRobotOuterRing, View.ROTATION, 0f, 360f).apply {
            duration = 6500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Floating, breathing & playful tilt animation on Robot Mascot
        val floatY = ObjectAnimator.ofFloat(ivLoginRobot, View.TRANSLATION_Y, -10f, 10f).apply {
            duration = 1500L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val tiltRot = ObjectAnimator.ofFloat(ivLoginRobot, View.ROTATION, -5f, 5f).apply {
            duration = 2100L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val breatheX = ObjectAnimator.ofFloat(ivLoginRobot, View.SCALE_X, 0.96f, 1.05f).apply {
            duration = 1500L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val breatheY = ObjectAnimator.ofFloat(ivLoginRobot, View.SCALE_Y, 0.96f, 1.05f).apply {
            duration = 1500L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        robotFloatAnimator = AnimatorSet().apply {
            playTogether(floatY, tiltRot, breatheX, breatheY)
            start()
        }

        // RGB Aura pulse behind Robot
        val scaleX = ObjectAnimator.ofFloat(viewLoginRobotAura, View.SCALE_X, 0.88f, 1.20f).apply {
            duration = 1200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(viewLoginRobotAura, View.SCALE_Y, 0.88f, 1.20f).apply {
            duration = 1200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val alphaPulse = ObjectAnimator.ofFloat(viewLoginRobotAura, View.ALPHA, 0.50f, 1.0f).apply {
            duration = 1200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        auraPulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alphaPulse)
            start()
        }

        // Neon Rainbow Shimmer on "TUANNANHH BCR" Title
        titleRgbAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.parseColor("#FEE440"),
            Color.parseColor("#00F5D4"),
            Color.parseColor("#FF007F"),
            Color.parseColor("#00BBF9"),
            Color.parseColor("#FEE440")
        ).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                tvAppTitle.setTextColor(anim.animatedValue as Int)
            }
            start()
        }

        // Interactive Robot tap celebration
        ivLoginRobot.setOnClickListener {
            triggerRobotCelebration()
            Toast.makeText(this, "🤖 TuannAnhh BCR V9 sẵn sàng soi cầu!", Toast.LENGTH_SHORT).show()
        }

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) 1.025f else 1.0f)
                .scaleY(if (hasFocus) 1.025f else 1.0f)
                .setDuration(180L)
                .start()
        }
        etLoginUser.onFocusChangeListener = focusListener
        etLoginPass.onFocusChangeListener = focusListener

        listOf(btnLoginSubmit, btnQuickDemo, btnRedeemKey, btnLaunchRobot, btnAdminGrant).forEach { btn ->
            btn.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(85L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
                false
            }
        }
    }

    private fun bindListeners() {
        btnLoginSubmit.setOnClickListener {
            val (ok, msg) = coinManager.login(etLoginUser.text.toString(), etLoginPass.text.toString())
            tvLoginFeedback.text = msg
            if (ok) {
                triggerRobotCelebration()
                refreshAccountState(animateReveal = true)
            } else {
                cardLoginSection.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_error))
                tvLoginFeedback.setTextColor(ContextCompat.getColor(this, R.color.accent_crimson))
            }
        }

        btnQuickDemo.setOnClickListener {
            etLoginUser.setText("vip01")
            etLoginPass.setText("123456")
            btnLoginSubmit.performClick()
        }

        btnRedeemKey.setOnClickListener {
            val (ok, msg) = coinManager.redeemKey(etRedeemKey.text.toString())
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            if (ok) {
                etRedeemKey.text.clear()
                triggerRobotCelebration()
                refreshAccountState(animateReveal = false)
            } else {
                etRedeemKey.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_error))
            }
        }

        btnLaunchRobot.setOnClickListener {
            startAutoRobotFlow()
        }

        btnLogout.setOnClickListener {
            coinManager.logout()
            refreshAccountState(animateReveal = false)
            Toast.makeText(this, "Đã đăng xuất!", Toast.LENGTH_SHORT).show()
        }

        btnAdminGrant.setOnClickListener {
            val coins = etAdminTargetCoins.text.toString().toIntOrNull() ?: 0
            val (ok, msg) = coinManager.createOrAddCoinsToUser(
                etAdminTargetUser.text.toString(),
                etAdminTargetPass.text.toString(),
                coins
            )
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            if (ok) {
                etAdminTargetUser.text.clear()
                etAdminTargetPass.text.clear()
                etAdminTargetCoins.text.clear()
            }
        }
    }

    private fun triggerRobotCelebration() {
        ivLoginRobot.animate()
            .scaleX(1.26f)
            .scaleY(1.26f)
            .rotationBy(360f)
            .setDuration(520L)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                ivLoginRobot.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200L).start()
            }
            .start()
    }

    private fun refreshAccountState(animateReveal: Boolean) {
        val currentUser = coinManager.getCurrentUser()
        if (currentUser.isNullOrBlank()) {
            cardLoginSection.visibility = View.VISIBLE
            cardDashboardSection.visibility = View.GONE
            cardAdminSection.visibility = View.GONE
            tvLoginFeedback.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
            tvLoginFeedback.text = "Trạng thái: Chưa đăng nhập"
        } else {
            cardLoginSection.visibility = View.GONE
            cardDashboardSection.visibility = View.VISIBLE

            val coins = coinManager.getCoins(currentUser)
            tvWelcomeUser.text = "Xin chào, ${currentUser.uppercase()}"
            tvCoinBalance.text = if (coinManager.isAdmin(currentUser)) "ADMIN ∞ Xu" else "$coins Xu"

            if (animateReveal) {
                cardDashboardSection.alpha = 0f
                cardDashboardSection.scaleX = 0.88f
                cardDashboardSection.scaleY = 0.88f
                cardDashboardSection.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(380L)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }

            cardAdminSection.visibility = if (coinManager.isAdmin(currentUser)) View.VISIBLE else View.GONE
        }
    }

    private fun startAutoRobotFlow() {
        val currentUser = coinManager.getCurrentUser()
        if (currentUser.isNullOrBlank()) {
            Toast.makeText(this, "Vui lòng đăng nhập trước!", Toast.LENGTH_SHORT).show()
            return
        }
        if (coinManager.getCoins(currentUser) <= 0 && !coinManager.isAdmin(currentUser)) {
            Toast.makeText(this, "Tài khoản hết Xu! Vui lòng nạp Key.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Cấp quyền 'Hiển thị trên ứng dụng khác' cho TuannAnhh BCR!", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun requestNotificationPermissionAndroid14() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onDestroy() {
        robotFloatAnimator?.cancel()
        auraPulseAnimator?.cancel()
        ringSpinAnimator?.cancel()
        titleRgbAnimator?.cancel()
        super.onDestroy()
    }
}
