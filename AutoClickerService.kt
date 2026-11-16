package com.polas.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AutoClickerService : AccessibilityService() {

    companion object {
        var instance: AutoClickerService? = null
        var isOverlayShowing: Boolean = false
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controlPanelView: LinearLayout? = null
    private val targetViews = mutableListOf<TextView>()
    private val targetPositions = mutableListOf<TargetPoint>()

    private var currentConfig: ClickConfig = ClickConfig()
    private var isRunning: Boolean = false
    private var executedCycles: Int = 0
    private var multiTargetIndex: Int = 0
    private var startTimeMs: Long = 0L

    private var btnPlayPause: TextView? = null
    private var btnDirectionOrAdd: TextView? = null
    private var statusBadge: TextView? = null

    private val automationRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            // ১. স্টপ লজিক যাচাই (Timer অথবা Number of Cycles)
            if (shouldStopNow()) {
                stopAutomation("স্টপ কন্ডিশন পূর্ণ হয়েছে")
                return
            }

            // ২. মোড অনুযায়ী ট্যাপ বা অটো-স্ক্রল এক্সিকিউট করা
            when (currentConfig.mode) {
                ModeType.SINGLE_TARGET -> {
                    val pt = targetPositions.firstOrNull() ?: TargetPoint(540, 1100)
                    performTap(pt.x.toFloat(), pt.y.toFloat())
                    executedCycles++
                }
                ModeType.MULTI_TARGET -> {
                    if (targetPositions.isNotEmpty()) {
                        val pt = targetPositions[multiTargetIndex % targetPositions.size]
                        performTap(pt.x.toFloat(), pt.y.toFloat())
                        multiTargetIndex++
                        if (multiTargetIndex % targetPositions.size == 0) {
                            executedCycles++
                        }
                    }
                }
                ModeType.AUTO_SCROLL -> {
                    performAutoScroll(currentConfig.scrollDirection, currentConfig.swipeDurationMs)
                    executedCycles++
                }
            }

            updateStatusBadge()

            // নিরাপদ ইন্টারভাল (৪০ মিলিসেকেন্ডের নিচে গেলে ডিভাইস ফ্রিজ হতে পারে)
            val safeDelay = currentConfig.intervalMs.coerceAtLeast(40L)
            mainHandler.postDelayed(this, safeDelay)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        currentConfig = ConfigStorage.loadActiveConfig(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // আলাদা ইভেন্ট প্রসেসিংয়ের প্রয়োজন নেই
    }

    override fun onInterrupt() {
        stopAutomation()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingOverlay()
        instance = null
    }

    /**
     * ফ্লোটিং ওভারলে প্যানেল এবং টার্গেট পয়েন্ট স্ক্রিনে দেখানো
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingOverlay(config: ClickConfig) {
        hideFloatingOverlay()
        currentConfig = config
        ConfigStorage.saveActiveConfig(this, config)

        val overlayFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // কন্ট্রোল প্যানেল তৈরি
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61E293B"))
                cornerRadius = 36f
                setStroke(3, Color.parseColor("#2ECC71"))
            }
        }

        // স্ট্যাটাস ব্যাজ (মোড ও সাইকেল সংখ্যা দেখায়)
        statusBadge = TextView(this).apply {
            text = getModeLabel()
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 14)
        }
        panel.addView(statusBadge)

        // ১. স্টার্ট / পজ বাটন (▶ / ⏸)
        btnPlayPause = createCircleButton("▶", "#2ECC71") {
            if (isRunning) {
                stopAutomation()
            } else {
                startAutomation()
            }
        }
        panel.addView(btnPlayPause)

        // ২. মাল্টি-টার্গেট মোডে (+) পয়েন্ট যোগ বা অটো-স্ক্রল মোডে ডিরেকশন পরিবর্তন বাটন
        val secondBtnSymbol = when (currentConfig.mode) {
            ModeType.MULTI_TARGET -> "＋"
            ModeType.AUTO_SCROLL -> getDirectionArrow(currentConfig.scrollDirection)
            ModeType.SINGLE_TARGET -> "🎯"
        }
        btnDirectionOrAdd = createCircleButton(secondBtnSymbol, "#2563EB") {
            when (currentConfig.mode) {
                ModeType.MULTI_TARGET -> {
                    addTargetCrosshair(540 + (targetViews.size * 50), 900 + (targetViews.size * 50))
                }
                ModeType.AUTO_SCROLL -> {
                    cycleScrollDirection()
                }
                ModeType.SINGLE_TARGET -> {
                    Toast.makeText(
                        this,
                        "ইন্টারভাল: ${currentConfig.intervalMs}ms",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        panel.addView(btnDirectionOrAdd)

        // ৩. মাল্টি-টার্গেট মোডে পয়েন্ট কমানোর বাটন (－)
        if (currentConfig.mode == ModeType.MULTI_TARGET) {
            val btnRemove = createCircleButton("－", "#F59E0B") {
                removeLastTargetCrosshair()
            }
            panel.addView(btnRemove)
        }

        // ৪. ওভারলে বন্ধ করার বাটন (✕)
        val btnClose = createCircleButton("✕", "#EF4444") {
            hideFloatingOverlay()
        }
        panel.addView(btnClose)

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }

        // প্যানেলটি ড্র্যাগ করে স্ক্রিনের যেকোনো জায়গায় সরানোর লজিক
        makeViewDraggable(panel, panelParams) { _, _ -> }

        windowManager.addView(panel, panelParams)
        controlPanelView = panel
        isOverlayShowing = true

        // অটো-স্ক্রল ছাড়া সিঙ্গেল বা মাল্টি মোডে টার্গেট পয়েন্ট দেখানো
        if (currentConfig.mode != ModeType.AUTO_SCROLL) {
            val initialPoints = if (currentConfig.targets.isNotEmpty()) {
                currentConfig.targets
            } else {
                mutableListOf(TargetPoint(540, 1100))
            }
            initialPoints.forEach { pt ->
                addTargetCrosshair(pt.x, pt.y)
            }
        }
    }

    /**
     * স্ক্রিনে টার্গেট পয়েন্ট (Crosshair) যোগ করা এবং ড্র্যাগ করার সুবিধা
     */
    private fun addTargetCrosshair(initX: Int, initY: Int) {
        val overlayFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val index = targetViews.size + 1
        val point = TargetPoint(initX, initY)
        targetPositions.add(point)

        val targetCircle = TextView(this).apply {
            text = index.toString()
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            width = 110
            height = 110
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#880D7377"))
                setStroke(6, Color.parseColor("#2ECC71"))
            }
        }

        val params = WindowManager.LayoutParams(
            110,
            110,
            overlayFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initX - 55
            y = initY - 55
        }

        makeViewDraggable(targetCircle, params) { newX, newY ->
            point.x = newX + 55
            point.y = newY + 55
            currentConfig.targets = targetPositions.toMutableList()
            ConfigStorage.saveActiveConfig(this, currentConfig)
        }

        windowManager.addView(targetCircle, params)
        targetViews.add(targetCircle)
    }

    private fun removeLastTargetCrosshair() {
        if (targetViews.size <= 1) {
            Toast.makeText(this, "কমপক্ষে ১টি টার্গেট পয়েন্ট থাকতে হবে", Toast.LENGTH_SHORT).show()
            return
        }
        val lastView = targetViews.removeAt(targetViews.lastIndex)
        targetPositions.removeAt(targetPositions.lastIndex)
        try {
            windowManager.removeView(lastView)
        } catch (_: Exception) {
        }
    }

    /**
     * সিঙ্গেল ও মাল্টি ট্যাপ এক্সিকিউশন (GestureDescription API)
     */
    private fun performTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 45L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * অটো-স্ক্রল / সোয়াইপ এক্সিকিউশন (Reels, Shorts, Facebook বা পেজ স্ক্রলিংয়ের জন্য)
     */
    private fun performAutoScroll(direction: ScrollDirection, durationMs: Long) {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()

        val path = Path()
        when (direction) {
            ScrollDirection.DOWN_TO_UP -> {
                // নিচ থেকে উপরে সোয়াইপ (পরবর্তী ভিডিও বা নিচে স্ক্রল)
                path.moveTo(w * 0.5f, h * 0.78f)
                path.lineTo(w * 0.5f, h * 0.22f)
            }
            ScrollDirection.UP_TO_DOWN -> {
                // উপর থেকে নিচে সোয়াইপ (আগের ভিডিও বা উপরে স্ক্রল)
                path.moveTo(w * 0.5f, h * 0.22f)
                path.lineTo(w * 0.5f, h * 0.78f)
            }
            ScrollDirection.RIGHT_TO_LEFT -> {
                path.moveTo(w * 0.85f, h * 0.5f)
                path.lineTo(w * 0.15f, h * 0.5f)
            }
            ScrollDirection.LEFT_TO_RIGHT -> {
                path.moveTo(w * 0.15f, h * 0.5f)
                path.lineTo(w * 0.85f, h * 0.5f)
            }
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs.coerceIn(100L, 3000L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun startAutomation() {
        if (isRunning) return
        isRunning = true
        executedCycles = 0
        multiTargetIndex = 0
        startTimeMs = System.currentTimeMillis()
        btnPlayPause?.text = "⏸"
        updateStatusBadge()
        mainHandler.post(automationRunnable)
    }

    fun stopAutomation(reason: String? = null) {
        isRunning = false
        mainHandler.removeCallbacks(automationRunnable)
        btnPlayPause?.text = "▶"
        updateStatusBadge()
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldStopNow(): Boolean {
        return when (currentConfig.stopCondition) {
            StopCondition.INFINITY -> false
            StopCondition.CYCLE_COUNT -> executedCycles >= currentConfig.maxCycles
            StopCondition.TIMER_SECONDS -> {
                val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000L
                elapsedSec >= currentConfig.timerSeconds
            }
        }
    }

    private fun cycleScrollDirection() {
        val values = ScrollDirection.values()
        val nextIndex = (currentConfig.scrollDirection.ordinal + 1) % values.size
        currentConfig.scrollDirection = values[nextIndex]
        btnDirectionOrAdd?.text = getDirectionArrow(currentConfig.scrollDirection)
        ConfigStorage.saveActiveConfig(this, currentConfig)
        Toast.makeText(
            this,
            "স্ক্রল ডিরেকশন: ${currentConfig.scrollDirection.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getDirectionArrow(dir: ScrollDirection): String {
        return when (dir) {
            ScrollDirection.DOWN_TO_UP -> "⬆"
            ScrollDirection.UP_TO_DOWN -> "⬇"
            ScrollDirection.RIGHT_TO_LEFT -> "⬅"
            ScrollDirection.LEFT_TO_RIGHT -> "➡"
        }
    }

    private fun getModeLabel(): String {
        return when (currentConfig.mode) {
            ModeType.SINGLE_TARGET -> "1P"
            ModeType.MULTI_TARGET -> "MP"
            ModeType.AUTO_SCROLL -> "SCR"
        }
    }

    private fun updateStatusBadge() {
        val state = if (isRunning) "${executedCycles}" else getModeLabel()
        statusBadge?.text = state
    }

    fun hideFloatingOverlay() {
        stopAutomation()
        controlPanelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        controlPanelView = null

        targetViews.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        targetViews.clear()
        targetPositions.clear()
        isOverlayShowing = false
    }

    private fun createCircleButton(
        symbol: String,
        hexColor: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = symbol
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            width = 105
            height = 105
            val params = LinearLayout.LayoutParams(105, 105).apply {
                setMargins(0, 8, 0, 8)
            }
            layoutParams = params
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hexColor))
            }
            setOnClickListener { onClick() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeViewDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        onMoved: (Int, Int) -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDragged = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) {
                        isDragged = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(view, params)
                        onMoved(params.x, params.y)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragged) view.performClick()
                    true
                }
                else -> false
            }
        }
    }
}
