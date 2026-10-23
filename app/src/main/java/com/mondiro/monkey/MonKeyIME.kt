package com.mondiro.monkey

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MonKeyIME : InputMethodService(), KeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private var kv: KeyboardView? = null
    private val scope: CoroutineScope = MainScope()

    private var mode = Mode.NORMAL
    private var halfOpened = false
    private var lastShiftTap = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        // Nhận biết gập nửa (Flex mode) qua Jetpack WindowManager
        scope.launch {
            try {
                WindowInfoTracker.getOrCreate(this@MonKeyIME)
                    .windowLayoutInfo(this@MonKeyIME)
                    .collect { info ->
                        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                        val half = fold?.state == FoldingFeature.State.HALF_OPENED
                        if (half != halfOpened) {
                            halfOpened = half
                            updatePosture(animate = false)
                        }
                    }
            } catch (_: Throwable) {
                // Máy không hỗ trợ: chỉ dựa vào kích thước màn hình
            }
        }
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    /** Tô thanh điều hướng dưới bàn phím cùng màu nền (mặc định hệ thống để trắng) + icon sáng. */
    @Suppress("DEPRECATION")
    private fun tintNavigationBar() {
        val w = window?.window ?: return
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        w.navigationBarColor = Color.parseColor("#161F2E")
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 30) {
            w.insetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        } else {
            w.decorView.systemUiVisibility =
                w.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        kv = v
        mode = targetMode(Mode.NORMAL)
        apply(animate = false)
        return v
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        tintNavigationBar()
        kv?.symPage = 0
        kv?.shift = ShiftState.OFF
        updatePosture(animate = false)
        apply(animate = false) // luôn áp cài đặt mới nhất mỗi lần bàn phím hiện
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePosture(animate = false)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == "one_left") return
        updatePosture(animate = false)
        apply(animate = false)
    }

    // ================= Tư thế máy =================

    /** Màn trong (mở) có chiều rộng nhỏ nhất >= 600dp; màn ngoài (gập) nhỏ hơn. */
    private fun unfolded() = resources.configuration.smallestScreenWidthDp >= 600

    /**
     * Gập (màn ngoài) -> thường · Gập nửa -> thường full · Mở phẳng -> vòng cung hai tay
     * (nếu đang dùng một tay thì giữ nguyên một tay).
     */
    private fun targetMode(current: Mode): Mode {
        if (!unfolded()) return Mode.NORMAL
        if (prefs.autoPosture && halfOpened) return Mode.NORMAL
        return if (current == Mode.NORMAL) Mode.ARC else current
    }

    private fun updatePosture(animate: Boolean) {
        val m = targetMode(mode)
        if (m != mode) {
            mode = m
            apply(animate)
        }
    }

    private fun apply(animate: Boolean) {
        val v = kv ?: return
        v.vibrateStrength = prefs.vibrateStrength
        v.configure(mode, prefs.layoutConfig(resources.displayMetrics.density), animate)
    }

    private fun switchTo(m: Mode) {
        if (m == mode) return
        if (m == Mode.ONE_LEFT) prefs.oneLeft = true
        if (m == Mode.ONE_RIGHT) prefs.oneLeft = false
        mode = m
        apply(animate = true)
    }

    // ================= Phím =================

    override fun onKey(key: Key) {
        val v = kv ?: return
        when (key.kind) {
            KeyKind.CHAR -> if (v.symbols) commit(v.outputOf(key).toString()) else typeChar(key.ch)
            KeyKind.SPACE -> commit(" ")
            KeyKind.BACKSPACE -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            KeyKind.ENTER -> enter()
            KeyKind.SHIFT -> if (v.symbols) {
                v.symPage = if (v.symPage == 1) 2 else 1 // lật trang ký hiệu
            } else {
                val now = SystemClock.uptimeMillis()
                v.shift = when {
                    v.shift == ShiftState.OFF -> ShiftState.ONCE
                    v.shift == ShiftState.ONCE && now - lastShiftTap < 350 -> ShiftState.LOCK
                    else -> ShiftState.OFF
                }
                lastShiftTap = now
            }
            KeyKind.SYMBOLS -> v.symPage = if (v.symbols) 0 else 1
            KeyKind.PASTE -> paste()
            KeyKind.HIDE -> requestHideSelf(0)
            KeyKind.SETTINGS -> startActivity(
                Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            KeyKind.ROUND_SWAP -> switchTo(if (mode == Mode.ONE_LEFT) Mode.ONE_RIGHT else Mode.ONE_LEFT)
            KeyKind.ROUND_TWO -> switchTo(Mode.ARC)
        }
    }

    override fun onSpaceSwipe(dir: Int): Boolean {
        when (mode) {
            Mode.ARC -> when (dir) {
                -1 -> switchTo(Mode.ONE_LEFT)
                1 -> switchTo(Mode.ONE_RIGHT)
                else -> return false
            }
            Mode.ONE_LEFT -> when (dir) {
                1 -> switchTo(Mode.ONE_RIGHT)
                2 -> switchTo(Mode.ARC)
                else -> return false
            }
            Mode.ONE_RIGHT -> when (dir) {
                -1 -> switchTo(Mode.ONE_LEFT)
                2 -> switchTo(Mode.ARC)
                else -> return false
            }
            Mode.NORMAL -> return false
        }
        return true
    }

    override fun onText(s: String) = commit(s)

    /** Xoá cả từ (và khoảng trắng) ngay trước con trỏ. */
    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && before[i - 1].isLetterOrDigit()) i--
        if (i == before.length) i-- // ký tự đặc biệt: xoá 1 ký tự
        ic.deleteSurroundingText(before.length - i, 0)
    }

    private fun paste() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isNotEmpty()) commit(text)
    }

    private fun typeChar(ch: Char) {
        val v = kv ?: return
        if (ch.isDigit()) {
            typeVniDigit(ch)
            return
        }
        commit((if (v.shift != ShiftState.OFF) ch.uppercaseChar() else ch).toString())
        if (v.shift == ShiftState.ONCE) v.shift = ShiftState.OFF
        if (prefs.smartTone) fixTonePosition()
    }

    /** Sau khi gõ thêm chữ: dời dấu thanh về đúng chỗ (vd "hòa" + n -> "hoàn"). */
    private fun fixTonePosition() {
        val ic = currentInputConnection ?: return
        val word = wordBeforeCursor(ic.getTextBeforeCursor(32, 0)?.toString() ?: return)
        if (word.isEmpty()) return
        val fixed = VniEngine.reposition(word, prefs.oldToneStyle)
        if (fixed == word) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(fixed, 1)
        ic.endBatchEdit()
    }

    private fun wordBeforeCursor(before: String): String {
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        return before.substring(i)
    }

    /** Phím số: thử bỏ dấu VNI cho chữ ngay trước con trỏ, không được thì gõ số. */
    private fun typeVniDigit(d: Char) {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty() || isNumberField()) {
            ic.commitText(d.toString(), 1)
            return
        }
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        val word = before.substring(i)
        val result = if (prefs.smartTone) VniEngine.applySmart(word, d, prefs.oldToneStyle)
        else VniEngine.apply(word, d)
        if (result == null) {
            ic.commitText(d.toString(), 1)
        } else {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(result, 1)
            ic.endBatchEdit()
        }
    }

    private fun isNumberField(): Boolean {
        val cls = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
    }

    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

}
