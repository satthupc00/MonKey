package com.mondiro.monkey

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class SettingsActivity : Activity() {

    companion object {
        private const val NONE = 0
        private const val TWO = 1
        private const val ONE = 2

        /** Bàn phím mở màn này để xin quyền micro (gõ bằng giọng nói). */
        const val EXTRA_REQUEST_MIC = "request_mic"
    }

    private lateinit var prefs: Prefs
    private lateinit var latest: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        latest = findViewById(R.id.latest)
        findViewById<TextView>(R.id.version).text = "v${BuildConfig.VERSION_NAME}"

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnUpdate).setOnClickListener { checkUpdate(manual = true) }

        setupPreview()
        // Thanh có xem trước: TWO = bàn phím vòng cung, ONE = bàn phím một tay
        slider(R.id.seekCurve, R.id.valCurve, 0, 100, prefs.curvature, "", TWO) { prefs.curvature = it }
        slider(R.id.seekSize, R.id.valSize, 30, 56, prefs.keySize, " dp", TWO) { prefs.keySize = it }
        slider(R.id.seekLift, R.id.valLift, 0, 150, prefs.lift, " dp", TWO) { prefs.lift = it }
        slider(R.id.seekWidth, R.id.valWidth, 60, 100, prefs.keyWidth, "%", TWO) { prefs.keyWidth = it }
        slider(R.id.seekInset, R.id.valInset, 0, 120, prefs.inset, " dp", TWO) { prefs.inset = it }
        slider(R.id.seekOneWidth, R.id.valOneWidth, 60, 100, prefs.oneKeyWidth, "%", ONE) { prefs.oneKeyWidth = it }
        slider(R.id.seekOneInset, R.id.valOneInset, 0, 200, prefs.oneInset, " dp", ONE) { prefs.oneInset = it }
        slider(R.id.seekOneLift, R.id.valOneLift, 0, 200, prefs.oneLift, " dp", ONE) { prefs.oneLift = it }
        toggle(R.id.swDup, prefs.dupMiddle) { prefs.dupMiddle = it }
        toggle(R.id.swSmartTone, prefs.smartTone) { prefs.smartTone = it }
        toggle(R.id.swSuggest, prefs.suggestions) { prefs.suggestions = it }
        toggle(R.id.swVoiceAbbrev, prefs.voiceAbbrev) { prefs.voiceAbbrev = it }
        findViewById<Button>(R.id.btnAbbrevList).setOnClickListener {
            startActivity(android.content.Intent(this, AbbrevActivity::class.java))
        }
        findViewById<Button>(R.id.btnClearWords).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Xoá dữ liệu gợi ý?")
                .setMessage("MonKey sẽ quên hết các chữ bạn đã gõ.")
                .setPositiveButton("Xoá") { _, _ -> WordStore.get(this).clear(); toast("Đã xoá dữ liệu gợi ý") }
                .setNegativeButton("Huỷ", null)
                .show()
        }
        toggle(R.id.swOldStyle, prefs.oldToneStyle) { prefs.oldToneStyle = it }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Đặt lại mặc định?")
                .setMessage("Độ cong 5 · Cỡ phím 40 · Bề rộng phím 85% · Nhích vào trong 39 · Độ rung 88 …")
                .setPositiveButton("Đặt lại") { _, _ -> prefs.resetDefaults(); recreate() }
                .setNegativeButton("Huỷ", null)
                .show()
        }
        toggle(R.id.swOneCurve, prefs.oneCurved) { prefs.oneCurved = it }
        slider(R.id.seekVibrate, R.id.valVibrate, 0, 100, prefs.vibrateStrength, "") { prefs.vibrateStrength = it }
        toggle(R.id.swPosture, prefs.autoPosture) { prefs.autoPosture = it }

        checkUpdate(manual = false)
        if (intent.getBooleanExtra(EXTRA_REQUEST_MIC, false)) askMicPermission()
    }

    private fun slider(
        seekId: Int, valId: Int, min: Int, max: Int, value: Int, unit: String,
        previewOf: Int = NONE, save: (Int) -> Unit,
    ) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(valId)
        seek.min = min
        seek.max = max
        seek.progress = value
        label.text = "$value$unit"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                label.text = "$p$unit"
                // Đang xem trước: lưu ngay để bàn phím cập nhật theo thời gian thực
                if (fromUser && previewCard != null) { save(p); updatePreview() }
            }
            override fun onStartTrackingTouch(s: SeekBar) {
                if (previewOf != NONE) enterPreview(s.parent as View, previewOf)
            }
            override fun onStopTrackingTouch(s: SeekBar) { save(s.progress) }
        })
    }

    // ================= Xem trước bàn phím =================

    private lateinit var preview: KeyboardView
    private var previewCard: View? = null
    private var previewKind = NONE
    private val hiddenForPreview = ArrayList<View>()

    private fun setupPreview() {
        preview = KeyboardView(this, object : KeyboardView.Listener {
            override fun onKey(key: Key) {}
            override fun onSpaceSwipe(dir: Int) = true
            override fun onText(s: String) {}
            override fun onDeleteWord() {}
            override fun onCursor(steps: Int) {}
            override fun onVoiceTap(clear: Boolean) {}
        })
        preview.setOnTouchListener { _, _ -> true } // chỉ để xem, không gõ được
        findViewById<FrameLayout>(R.id.previewHolder).addView(
            preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        findViewById<Button>(R.id.btnPreviewDone).setOnClickListener { exitPreview() }
    }

    /** Ẩn mọi mục khác, chỉ giữ thanh đang chỉnh và hiện bàn phím xem trước bên dưới. */
    private fun enterPreview(card: View, kind: Int) {
        previewKind = kind
        if (previewCard == card) { updatePreview(); return }
        if (previewCard == null) {
            val content = findViewById<LinearLayout>(R.id.content)
            hiddenForPreview.clear()
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i)
                if (child != card && child.visibility == View.VISIBLE) {
                    child.visibility = View.GONE
                    hiddenForPreview += child
                }
            }
            findViewById<View>(R.id.previewBox).visibility = View.VISIBLE
            moveCredit(android.view.Gravity.TOP or android.view.Gravity.END) // tránh đè lên bàn phím xem trước
            findViewById<View>(R.id.scroll).post { findViewById<View>(R.id.scroll).scrollTo(0, 0) }
        }
        previewCard = card
        updatePreview()
    }

    private fun updatePreview() {
        val mode = if (previewKind == ONE) (if (prefs.oneLeft) Mode.ONE_LEFT else Mode.ONE_RIGHT) else Mode.ARC
        preview.configure(mode, prefs.layoutConfig(resources.displayMetrics.density), animate = false)
    }

    private fun exitPreview() {
        if (previewCard == null) return
        hiddenForPreview.forEach { it.visibility = View.VISIBLE }
        hiddenForPreview.clear()
        previewCard = null
        findViewById<View>(R.id.previewBox).visibility = View.GONE
        moveCredit(android.view.Gravity.BOTTOM or android.view.Gravity.END)
    }

    private fun moveCredit(gravity: Int) {
        val credit = findViewById<View>(R.id.credit)
        credit.layoutParams = (credit.layoutParams as FrameLayout.LayoutParams).apply { this.gravity = gravity }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (previewCard != null) exitPreview() else super.onBackPressed()
    }

    private fun toggle(id: Int, value: Boolean, save: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, b -> save(b) }
    }

    // ================= Cập nhật =================

    private fun checkUpdate(manual: Boolean) {
        if (manual) Toast.makeText(this, "Đang kiểm tra…", Toast.LENGTH_SHORT).show()
        thread {
            val r = Updater.fetchLatest()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when {
                    r == null -> if (manual) toast("Không kiểm tra được, hãy thử lại khi có mạng")
                    Updater.isNewer(r.version, BuildConfig.VERSION_NAME) -> {
                        latest.visibility = View.GONE
                        askUpdate(r)
                    }
                    else -> {
                        latest.visibility = View.VISIBLE
                        if (manual) toast("Bạn đang dùng bản mới nhất")
                    }
                }
            }
        }
    }

    private fun askUpdate(r: Updater.Release) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Có bản mới v${r.version}")
            .setMessage(r.notes.ifBlank { "Bản cập nhật mới của MonKey." })
            .setPositiveButton("Cập nhật") { _, _ -> startUpdate(r) }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun startUpdate(r: Updater.Release) {
        val url = r.apkUrl ?: return toast("Bản mới chưa có file APK")
        if (!packageManager.canRequestPackageInstalls()) {
            toast("Hãy cho phép MonKey cài ứng dụng, rồi bấm cập nhật lại")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        toast("Đang tải bản mới…")
        thread {
            try {
                val apk = Updater.download(this, url)
                runOnUiThread { Updater.install(this, apk) }
            } catch (e: Exception) {
                runOnUiThread { toast("Tải thất bại: ${e.message}") }
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun askMicPermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        toast("Cho phép micro để gõ bằng giọng nói (giữ phím 0)")
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toast("Đã cho phép micro. Quay lại ô gõ và giữ phím 0 để nói.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Hiện số từ trong danh sách viết tắt trên nút
        val n = Abbrev.parse(prefs.voiceAbbrevList).size
        findViewById<Button>(R.id.btnAbbrevList).text = "Danh sách viết tắt ($n từ) ›"
    }
}
