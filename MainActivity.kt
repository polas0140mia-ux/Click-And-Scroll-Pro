package com.polas.autoclicker

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity() {

    companion object {
        // অফিশিয়াল Google AdMob Test Unit IDs (প্লে স্টোরে দেওয়ার আগে আপনার আসল Ad Unit ID বসাবেন)
        private const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val ADMOB_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }

    private lateinit var activeConfig: ClickConfig
    private var interstitialAd: InterstitialAd? = null
    private lateinit var switchSingleMode: Switch
    private lateinit var switchMultiMode: Switch
    private lateinit var switchAutoScrollMode: Switch
    private lateinit var tvPermissionBanner: TextView
    private lateinit var configListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeConfig = ConfigStorage.loadActiveConfig(this)

        // ১. Google AdMob ইনিশিয়ালাইজেশন
        MobileAds.initialize(this) {}
        loadInterstitialAd()

        // ২. মূল ইউজার ইন্টারফেস (Single Mode, Multi Mode, Auto-Scroll, Configs & AdMob Banner)
        setupUserInterface()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusBanner()
        syncSwitchesWithState()
    }

    private fun setupUserInterface() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
        }

        // উপরের অ্যাপ বার
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 52, 48, 36)
            setBackgroundColor(0xFF0F172A.toInt())
        }
        val title = TextView(this).apply {
            text = "Click & Scroll Pro"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "Single/Multi Auto Clicker + Auto-Scroll + AdMob"
            textSize = 13f
            setTextColor(0xFF94A3B8.toInt())
        }
        header.addView(title)
        header.addView(subtitle)
        rootLayout.addView(header)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 48)
        }

        // পারমিশন স্ট্যাটাস কার্ড (Easy Setup)
        tvPermissionBanner = TextView(this).apply {
            textSize = 14f
            setPadding(32, 28, 32, 28)
            setBackgroundColor(0xFFE0F2FE.toInt())
            setTextColor(0xFF0369A1.toInt())
            setOnClickListener { requestRequiredPermissions() }
        }
        content.addView(tvPermissionBanner)

        // ==================== ১. Single Target Mode Card ====================
        switchSingleMode = Switch(this)
        content.addView(
            buildModeSectionCard(
                modeTitle = "Single Target Mode (সিঙ্গেল ট্যাপ)",
                toggleSwitch = switchSingleMode,
                onToggle = { enabled -> handleModeToggle(ModeType.SINGLE_TARGET, enabled) },
                onSettingsClick = { showStopAndIntervalDialog(ModeType.SINGLE_TARGET) },
                onInstructionsClick = { showInstructionsDialog(ModeType.SINGLE_TARGET) }
            )
        )

        // ==================== ২. Multi Target Mode Card ====================
        switchMultiMode = Switch(this)
        content.addView(
            buildModeSectionCard(
                modeTitle = "Multi Target Mode (মাল্টি টার্গেট)",
                toggleSwitch = switchMultiMode,
                onToggle = { enabled -> handleModeToggle(ModeType.MULTI_TARGET, enabled) },
                onSettingsClick = { showStopAndIntervalDialog(ModeType.MULTI_TARGET) },
                onInstructionsClick = { showInstructionsDialog(ModeType.MULTI_TARGET) }
            )
        )

        // ==================== ৩. Auto-Scroll Mode Card (বাড়তি ফিচার) ====================
        switchAutoScrollMode = Switch(this)
        content.addView(
            buildModeSectionCard(
                modeTitle = "Auto-Scroll Mode (অটো-স্ক্রল ও সোয়াইপ)",
                toggleSwitch = switchAutoScrollMode,
                onToggle = { enabled -> handleModeToggle(ModeType.AUTO_SCROLL, enabled) },
                onSettingsClick = { showAutoScrollSettingsDialog() },
                onInstructionsClick = { showInstructionsDialog(ModeType.AUTO_SCROLL) }
            )
        )

        // ==================== ৪. Manage Configuration (Save Configs) ====================
        val configHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 32, 8, 16)
        }
        val configHeaderTitle = TextView(this).apply {
            text = "Manage Configuration (সেভ করা কনফিগ)"
            textSize = 17f
            setTextColor(0xFF0F172A.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnSaveNewConfig = Button(this).apply {
            text = "+ Save Current"
            setBackgroundColor(0xFF2ECC71.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { promptSaveCurrentConfiguration() }
        }
        configHeaderRow.addView(configHeaderTitle)
        configHeaderRow.addView(btnSaveNewConfig)
        content.addView(configHeaderRow)

        configListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(configListContainer)
        refreshSavedConfigsList()

        scrollView.addView(content)
        rootLayout.addView(scrollView)

        // ==================== ৫. নিচে Google AdMob Banner Ad ====================
        val adContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 8)
        }
        val adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = ADMOB_BANNER_UNIT_ID
        }
        adContainer.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
        rootLayout.addView(adContainer)

        setContentView(rootLayout)
        updatePermissionStatusBanner()
        syncSwitchesWithState()
    }

    private fun buildModeSectionCard(
        modeTitle: String,
        toggleSwitch: Switch,
        onToggle: (Boolean) -> Unit,
        onSettingsClick: () -> Unit,
        onInstructionsClick: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 32)
            setBackgroundColor(0xFFFFFFFF.toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 20, 0, 12)
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(this).apply {
            text = modeTitle
            textSize = 16f
            setTextColor(0xFF0F172A.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(isChecked)
        }
        topRow.addView(tvTitle)
        topRow.addView(toggleSwitch)
        card.addView(topRow)

        val btnSettings = Button(this).apply {
            text = "⚙  Settings (ইন্টারভাল ও স্টপ লজিক)   →"
            setOnClickListener { onSettingsClick() }
        }
        val btnInstructions = Button(this).apply {
            text = "ℹ  Instructions (ব্যবহারের নিয়ম)   →"
            setOnClickListener { onInstructionsClick() }
        }
        card.addView(btnSettings)
        card.addView(btnInstructions)

        return card
    }

    private fun handleModeToggle(mode: ModeType, enabled: Boolean) {
        if (!enabled) {
            if (activeConfig.mode == mode && AutoClickerService.isOverlayShowing) {
                AutoClickerService.instance?.hideFloatingOverlay()
            }
            return
        }

        if (!hasAllPermissions()) {
            syncSwitchesWithState()
            requestRequiredPermissions()
            return
        }

        activeConfig.mode = mode
        ConfigStorage.saveActiveConfig(this, activeConfig)
        AutoClickerService.instance?.showFloatingOverlay(activeConfig)
        syncSwitchesWithState()
    }

    private fun syncSwitchesWithState() {
        val showing = AutoClickerService.isOverlayShowing
        switchSingleMode.setOnCheckedChangeListener(null)
        switchMultiMode.setOnCheckedChangeListener(null)
        switchAutoScrollMode.setOnCheckedChangeListener(null)

        switchSingleMode.isChecked = showing && activeConfig.mode == ModeType.SINGLE_TARGET
        switchMultiMode.isChecked = showing && activeConfig.mode == ModeType.MULTI_TARGET
        switchAutoScrollMode.isChecked = showing && activeConfig.mode == ModeType.AUTO_SCROLL

        switchSingleMode.setOnCheckedChangeListener { _, checked ->
            handleModeToggle(ModeType.SINGLE_TARGET, checked)
        }
        switchMultiMode.setOnCheckedChangeListener { _, checked ->
            handleModeToggle(ModeType.MULTI_TARGET, checked)
        }
        switchAutoScrollMode.setOnCheckedChangeListener { _, checked ->
            handleModeToggle(ModeType.AUTO_SCROLL, checked)
        }
    }

    /**
     * ভিডিওর "Stop Logic" স্ক্রিন অনুযায়ী ইন্টারভাল, Run Infinity, Run Timer এবং Number of Cycle ডায়ালগ
     */
    private fun showStopAndIntervalDialog(mode: ModeType) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tvWarn = TextView(this).apply {
            text = "Click Interval (Millisecond)
* 40ms-এর নিচে দিলে ডিভাইস হ্যাং হতে পারে"
            setTextColor(0xFFDC2626.toInt())
            textSize = 12f
        }
        val etInterval = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(activeConfig.intervalMs.toString())
            hint = "1000"
        }
        layout.addView(tvWarn)
        layout.addView(etInterval)

        val radioGroup = RadioGroup(this).apply {
            setPadding(0, 24, 0, 12)
        }
        val rbInfinity = RadioButton(this).apply { text = "Run Infinity (অনির্দিষ্টকাল চলবে)" }
        val rbTimer = RadioButton(this).apply { text = "Run Timer (নির্দিষ্ট সেকেন্ড পর থামবে)" }
        val rbCycles = RadioButton(this).apply { text = "Number of cycle (নির্দিষ্ট বারের পর থামবে)" }

        radioGroup.addView(rbInfinity)
        radioGroup.addView(rbTimer)
        radioGroup.addView(rbCycles)

        when (activeConfig.stopCondition) {
            StopCondition.INFINITY -> rbInfinity.isChecked = true
            StopCondition.TIMER_SECONDS -> rbTimer.isChecked = true
            StopCondition.CYCLE_COUNT -> rbCycles.isChecked = true
        }
        layout.addView(radioGroup)

        val etTimerSeconds = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(activeConfig.timerSeconds.toString())
            hint = "টাইমার সেকেন্ড (যেমন: 300 = ৫ মিনিট)"
        }
        val etCycles = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(activeConfig.maxCycles.toString())
            hint = "সাইকেল সংখ্যা (যেমন: 10)"
        }
        layout.addView(etTimerSeconds)
        layout.addView(etCycles)

        AlertDialog.Builder(this)
            .setTitle("${mode.name} Settings")
            .setView(layout)
            .setNeutralButton("RESET") { _, _ ->
                activeConfig = ClickConfig(mode = mode)
                ConfigStorage.saveActiveConfig(this, activeConfig)
                Toast.makeText(this, "ডিফল্ট সেটিংস রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("SAVE") { _, _ ->
                activeConfig.mode = mode
                activeConfig.intervalMs =
                    (etInterval.text.toString().toLongOrNull() ?: 1000L).coerceAtLeast(40L)
                activeConfig.timerSeconds =
                    (etTimerSeconds.text.toString().toIntOrNull() ?: 300).coerceAtLeast(1)
                activeConfig.maxCycles =
                    (etCycles.text.toString().toIntOrNull() ?: 10).coerceAtLeast(1)
                activeConfig.stopCondition = when {
                    rbInfinity.isChecked -> StopCondition.INFINITY
                    rbTimer.isChecked -> StopCondition.TIMER_SECONDS
                    else -> StopCondition.CYCLE_COUNT
                }
                ConfigStorage.saveActiveConfig(this, activeConfig)
                Toast.makeText(this, "সেটিংস সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                showInterstitialAdIfReady()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    /**
     * অটো-স্ক্রল সেটিংস ডায়ালগ (স্ক্রল ইন্টারভাল, সোয়াইপ স্পিড ও ডিরেকশন)
     */
    private fun showAutoScrollSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tvInterval = TextView(this).apply {
            text = "দুই স্ক্রলের মাঝখানের বিরতি (Millisecond):"
        }
        val etInterval = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(activeConfig.intervalMs.toString())
        }

        val tvDuration = TextView(this).apply {
            text = "সোয়াইপ স্পিড / ডিউরেশন (Millisecond, যেমন 350):"
        }
        val etDuration = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(activeConfig.swipeDurationMs.toString())
        }

        layout.addView(tvInterval)
        layout.addView(etInterval)
        layout.addView(tvDuration)
        layout.addView(etDuration)

        AlertDialog.Builder(this)
            .setTitle("Auto-Scroll Settings")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                activeConfig.mode = ModeType.AUTO_SCROLL
                activeConfig.intervalMs =
                    (etInterval.text.toString().toLongOrNull() ?: 3000L).coerceAtLeast(200L)
                activeConfig.swipeDurationMs =
                    (etDuration.text.toString().toLongOrNull() ?: 350L).coerceIn(100L, 3000L)
                ConfigStorage.saveActiveConfig(this, activeConfig)
                Toast.makeText(this, "অটো-স্ক্রল সেটিংস সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                showInterstitialAdIfReady()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showInstructionsDialog(mode: ModeType) {
        val message = when (mode) {
            ModeType.SINGLE_TARGET ->
                "STEP 1: সুইচ অন করে ফ্লোটিং বার চালু করুন।
" +
                    "STEP 2: গোল টার্গেট আইকনটি টেনে যেখানে ক্লিক করতে চান সেখানে বসান।
" +
                    "STEP 3: ▶ (Play) বাটনে চাপ দিলে অটো-ক্লিক শুরু হবে।"
            ModeType.MULTI_TARGET ->
                "STEP 1: সুইচ অন করে মাল্টি টার্গেট মোড চালু করুন।
" +
                    "STEP 2: ফ্লোটিং বারের (＋) বাটনে ট্যাপ করে একাধিক টার্গেট পয়েন্ট যোগ করুন এবং (－) দিয়ে কমান।
" +
                    "STEP 3: ▶ (Play) বাটনে চাপ দিলে ক্রমানুসারে সব পয়েন্টে ক্লিক হতে থাকবে।"
            ModeType.AUTO_SCROLL ->
                "STEP 1: অটো-স্ক্রল সুইচ অন করুন।
" +
                    "STEP 2: রিলস, শর্টস, টিকটক বা ওয়েবপেজে গিয়ে ▶ (Play) চাপুন।
" +
                    "STEP 3: ডিরেকশন বাটনে (⬆/⬇/⬅/➡) চাপ দিয়ে স্ক্রলের দিক পরিবর্তন করতে পারবেন।"
        }
        AlertDialog.Builder(this)
            .setTitle("Instructions (${mode.name})")
            .setMessage(message)
            .setPositiveButton("বুঝেছি", null)
            .show()
    }

    private fun refreshSavedConfigsList() {
        configListContainer.removeAllViews()
        val configs = ConfigStorage.getSavedConfigs(this)

        configs.forEachIndexed { index, cfg ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(28, 20, 28, 20)
                setBackgroundColor(0xFFFFFFFF.toInt())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 8, 0, 8)
                layoutParams = lp
            }

            val tvName = TextView(this).apply {
                text = "${cfg.name}
(${cfg.mode.name} • ${cfg.intervalMs}ms)"
                textSize = 14f
                setTextColor(0xFF1E293B.toInt())
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnPlay = Button(this).apply {
                text = "▶"
                setBackgroundColor(0xFF0D7377.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    if (!hasAllPermissions()) {
                        requestRequiredPermissions()
                    } else {
                        activeConfig = cfg
                        ConfigStorage.saveActiveConfig(this@MainActivity, activeConfig)
                        AutoClickerService.instance?.showFloatingOverlay(activeConfig)
                        syncSwitchesWithState()
                    }
                }
            }

            val btnDelete = Button(this).apply {
                text = "✕"
                setOnClickListener {
                    configs.removeAt(index)
                    ConfigStorage.saveConfigList(this@MainActivity, configs)
                    refreshSavedConfigsList()
                }
            }

            row.addView(tvName)
            row.addView(btnPlay)
            row.addView(btnDelete)
            configListContainer.addView(row)
        }
    }

    private fun promptSaveCurrentConfiguration() {
        val etName = EditText(this).apply {
            val count = ConfigStorage.getSavedConfigs(this@MainActivity).size + 1
            setText("config $count")
        }
        AlertDialog.Builder(this)
            .setTitle("Save Configuration")
            .setView(etName)
            .setPositiveButton("SAVE") { _, _ ->
                val list = ConfigStorage.getSavedConfigs(this)
                val copy = activeConfig.copy(
                    name = etName.text.toString().ifBlank { "config ${list.size + 1}" }
                )
                list.add(copy)
                ConfigStorage.saveConfigList(this, list)
                refreshSavedConfigsList()
                showInterstitialAdIfReady()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    /**
     * Accessibility এবং Floating Overlay পারমিশন চেক ও রিকোয়েস্ট
     */
    private fun hasAllPermissions(): Boolean {
        val overlayGranted = Settings.canDrawOverlays(this)
        val serviceGranted = AutoClickerService.instance != null
        return overlayGranted && serviceGranted
    }

    private fun updatePermissionStatusBanner() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val serviceGranted = AutoClickerService.instance != null

        tvPermissionBanner.text = when {
            !overlayGranted -> "⚠ ধাপ ১: এখানে ট্যাপ করে 'Display Over Other Apps' পারমিশন চালু করুন"
            !serviceGranted -> "⚠ ধাপ ২: এখানে ট্যাপ করে Accessibility থেকে 'Click & Scroll Pro' অন করুন"
            else -> "✅ সব পারমিশন চালু আছে! যেকোনো মোড অন করে অটো-ক্লিক বা অটো-স্ক্রল শুরু করুন।"
        }
    }

    private fun requestRequiredPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        if (AutoClickerService.instance == null) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Downloaded Apps থেকে 'Click & Scroll Pro' খুঁজে অন করুন",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Google AdMob Interstitial Ad লোড এবং প্রদর্শন
     */
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            ADMOB_INTERSTITIAL_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun showInterstitialAdIfReady() {
        interstitialAd?.show(this)
        loadInterstitialAd()
    }
}
