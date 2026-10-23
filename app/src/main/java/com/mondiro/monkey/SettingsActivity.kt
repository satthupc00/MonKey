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
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class SettingsActivity : Activity() {

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

        slider(R.id.seekCurve, R.id.valCurve, 0, 100, prefs.curvature, "") { prefs.curvature = it }
        slider(R.id.seekSize, R.id.valSize, 30, 56, prefs.keySize, " dp") { prefs.keySize = it }
        slider(R.id.seekLift, R.id.valLift, 0, 150, prefs.lift, " dp") { prefs.lift = it }
        slider(R.id.seekWidth, R.id.valWidth, 60, 100, prefs.keyWidth, "%") { prefs.keyWidth = it }
        slider(R.id.seekInset, R.id.valInset, 0, 120, prefs.inset, " dp") { prefs.inset = it }
        slider(R.id.seekOneWidth, R.id.valOneWidth, 60, 100, prefs.oneKeyWidth, "%") { prefs.oneKeyWidth = it }
        slider(R.id.seekOneInset, R.id.valOneInset, 0, 200, prefs.oneInset, " dp") { prefs.oneInset = it }
        slider(R.id.seekOneLift, R.id.valOneLift, 0, 200, prefs.oneLift, " dp") { prefs.oneLift = it }
        toggle(R.id.swDup, prefs.dupMiddle) { prefs.dupMiddle = it }
        toggle(R.id.swSmartTone, prefs.smartTone) { prefs.smartTone = it }
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
    }

    private fun slider(seekId: Int, valId: Int, min: Int, max: Int, value: Int, unit: String, save: (Int) -> Unit) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(valId)
        seek.min = min
        seek.max = max
        seek.progress = value
        label.text = "$value$unit"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                label.text = "$p$unit"
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { save(s.progress) }
        })
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
}
