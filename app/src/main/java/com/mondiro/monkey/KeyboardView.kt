package com.mondiro.monkey

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ShiftState { OFF, ONCE, LOCK }

@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onKey(key: Key)
        /** dir: -1 vuốt trái, 1 vuốt phải, 2 vuốt lên. Trả về false nếu không xử lý (sẽ gõ dấu cách). */
        fun onSpaceSwipe(dir: Int): Boolean
        /** Gõ nguyên văn (phím phụ khi giữ). */
        fun onText(s: String)
        /** Giữ phím xoá lâu: xoá cả từ. */
        fun onDeleteWord()
    }

    companion object {
        /** Ký hiệu đặt theo vị trí phím chữ (dùng cho bàn phím vòng cung / một tay). */
        fun symbolFor(page: Int, ch: Char): Char? {
            val pg = Layouts.SYM_PAGES[page - 1]
            "qwertyuiop".indexOf(ch).let { if (it >= 0) return pg[0][it] }
            "asdfghjkl".indexOf(ch).let { if (it >= 0) return pg[1][it] }
            "zxcvbnm".indexOf(ch).let { if (it >= 0) return pg[2][it] }
            return null
        }
        /** Phím phụ khi giữ (giống Laban Key). */
        val SUBS = mapOf(
            'a' to '@', 's' to '#', 'd' to '$', 'f' to '%', 'g' to '_', 'h' to '-', 'j' to '+', 'k' to '(', 'l' to ')',
            'z' to '*', 'x' to '"', 'c' to '\'', 'v' to ':', 'b' to ';', 'n' to '!', 'm' to '?',
        )
        /** Phím phụ dạng chức năng khi giữ: ',' -> cài đặt, '.' -> dán. */
        val ACTION_SUBS = mapOf(',' to KeyKind.SETTINGS, '.' to KeyKind.PASTE)
        private const val ANIM_MS = 450L
        private const val HOLD_MS = 280L
    }

    private val dp = resources.displayMetrics.density

    var mode = Mode.NORMAL
        private set
    var config = LayoutConfig(0.05f, 40 * dp, 0f, 0f, 0.85f, true, true, 0f, dp)
        private set
    var shift = ShiftState.OFF
        set(v) { field = v; invalidate() }
    /** 0 = bàn phím chữ, 1 / 2 = trang ký hiệu. */
    var symPage = 0
        set(v) {
            if (field == v) return
            field = v
            // Bàn phím thường có bố cục ký hiệu riêng (hàng 2 có 10 phím) nên phải dựng lại
            if (mode == Mode.NORMAL && width > 0) rebuild(false) else invalidate()
        }
    val symbols get() = symPage > 0
    /** Độ mạnh rung 0..100 (0 = tắt). */
    var vibrateStrength = 50

    private var keys: List<Key> = emptyList()

    // ---- animation ----
    private var fromKeys: List<Key> = emptyList()
    private var toKeys: List<Key> = emptyList()
    private var animT = 1f
    private var animator: ValueAnimator? = null
    private val animating get() = animator?.isRunning == true

    // ---- paints ----
    // Tông màu giống Laban Key
    private val cBg = Color.parseColor("#161F2E")
    private val cKey = Color.parseColor("#4F616D")
    private val cKeyFn = Color.parseColor("#374147")
    private val cKeyDown = Color.parseColor("#6B7F8C")
    private val cText = Color.parseColor("#FFFFFF")
    private val cHint = Color.parseColor("#92A3AB")
    private val cDim = Color.parseColor("#C9D2D8")
    private val cAccent = Color.parseColor("#E5383B")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = cText
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.FILL_AND_STROKE // viền chữ để nét dày hơn
        strokeJoin = Paint.Join.ROUND
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = cHint
        typeface = Typeface.DEFAULT_BOLD
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    // Icon vẽ trong hệ toạ độ đơn vị (-1..1)
    private val shiftPath = Path().apply {
        moveTo(0f, -0.95f); lineTo(0.9f, 0.05f); lineTo(0.42f, 0.05f); lineTo(0.42f, 0.85f)
        lineTo(-0.42f, 0.85f); lineTo(-0.42f, 0.05f); lineTo(-0.9f, 0.05f); close()
    }
    private val backPath = Path().apply {
        moveTo(-1f, 0f); lineTo(-0.45f, -0.7f); lineTo(1f, -0.7f); lineTo(1f, 0.7f); lineTo(-0.45f, 0.7f); close()
    }
    private val backX = Path().apply {
        moveTo(0.05f, -0.3f); lineTo(0.6f, 0.3f); moveTo(0.6f, -0.3f); lineTo(0.05f, 0.3f)
    }
    private val hidePath = Path().apply {
        addRoundRect(RectF(-0.95f, -0.8f, 0.95f, 0.3f), 0.15f, 0.15f, Path.Direction.CW)
        moveTo(-0.55f, -0.45f); lineTo(-0.5f, -0.45f); moveTo(-0.05f, -0.45f); lineTo(0.05f, -0.45f)
        moveTo(0.5f, -0.45f); lineTo(0.55f, -0.45f); moveTo(-0.45f, -0.05f); lineTo(0.45f, -0.05f)
        moveTo(-0.3f, 0.55f); lineTo(0f, 0.85f); lineTo(0.3f, 0.55f)
    }
    private val pastePath = Path().apply {
        // bảng kẹp giấy
        addRoundRect(RectF(-0.7f, -0.75f, 0.7f, 0.95f), 0.15f, 0.15f, Path.Direction.CW)
        addRect(RectF(-0.3f, -0.95f, 0.3f, -0.6f), Path.Direction.CW)
        moveTo(-0.35f, 0f); lineTo(0.35f, 0f)
        moveTo(-0.35f, 0.4f); lineTo(0.2f, 0.4f)
    }
    private val chevronPath = Path().apply {
        moveTo(-0.7f, -0.35f); lineTo(0f, 0.35f); lineTo(0.7f, -0.35f)
    }
    private val enterPath = Path().apply {
        moveTo(0.85f, -0.75f); lineTo(0.85f, 0.2f); lineTo(-0.8f, 0.2f)
        moveTo(-0.35f, -0.25f); lineTo(-0.8f, 0.2f); lineTo(-0.35f, 0.65f)
    }

    // ---- touch ----
    private class Ptr(val key: Key?, val downX: Float, val downY: Float, var done: Boolean = false, var sub: Boolean = false)
    private val ptrs = HashMap<Int, Ptr>()
    private val handler = Handler(Looper.getMainLooper())
    private var repeatPtr = -1
    private var repeatCount = 0
    /** Giữ phím xoá: càng giữ lâu càng xoá nhanh, sau ~2 giây thì xoá từng từ. */
    private val repeat = object : Runnable {
        override fun run() {
            val p = ptrs[repeatPtr] ?: return
            val k = p.key ?: return
            repeatCount++
            if (repeatCount > 24) {
                listener.onDeleteWord()
                handler.postDelayed(this, 140)
            } else {
                listener.onKey(k)
                handler.postDelayed(this, maxOf(25L, 110L - repeatCount * 6L))
            }
        }
    }
    private var holdPtr = -1
    private val hold = Runnable {
        val p = ptrs[holdPtr] ?: return@Runnable
        if (!p.done) { p.sub = true; buzz(); invalidate() }
    }

    // ================= Layout =================

    /** Đổi chế độ/thông số. animate = true để trượt phím mượt (~0.45s). */
    fun configure(newMode: Mode, newConfig: LayoutConfig, animate: Boolean) {
        val oldMode = mode
        mode = newMode
        config = newConfig
        if (width == 0) { requestLayout(); return }
        val needH = Layouts.height(mode, config).roundToInt()
        if (needH != height) {
            requestLayout()
            return // onSizeChanged sẽ dựng lại
        }
        rebuild(animate && oldMode != newMode)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, Layouts.height(mode, config).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuild(false)
    }

    private fun rebuild(animate: Boolean) {
        val newKeys = Layouts.build(mode, width.toFloat(), height.toFloat(), config, symPage)
        animator?.cancel()
        cancelTouches()
        if (!animate || keys.isEmpty()) {
            keys = newKeys
            invalidate()
            return
        }
        fromKeys = keys
        toKeys = newKeys
        keys = newKeys
        animT = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { animT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Ghép phím cũ -> mới theo id để trượt; phím chỉ có một bên thì mờ dần. */
    private fun frameKeys(): List<Key> {
        if (!animating) return keys
        val t = animT
        val from = fromKeys.associateBy { it.id }
        val to = toKeys.associateBy { it.id }
        val out = ArrayList<Key>(from.size + to.size)
        for (a in fromKeys) {
            val b = to[a.id]
            if (b == null) out += a.copy(alpha = 1 - t)
        }
        for (b in toKeys) {
            val a = from[b.id]
            out += if (a == null) b.copy(alpha = t) else Key(
                b.id, b.kind, b.ch,
                cx = lerp(a.cx, b.cx, t), cy = lerp(a.cy, b.cy, t),
                w = lerp(a.w, b.w, t), h = lerp(a.h, b.h, t),
                angle = lerp(a.angle, b.angle, t),
                accent = b.accent, round = b.round,
            )
        }
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    // ================= Draw =================

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBg)
        val pressed = ptrs.values.mapNotNull { it.key?.id }.toHashSet()
        for (k in frameKeys()) drawKey(canvas, k, k.id in pressed)
        for (p in ptrs.values) if (p.sub) p.key?.let { drawPopup(canvas, it) }
    }

    private fun drawKey(cv: Canvas, k: Key, down: Boolean) {
        val a = (k.alpha.coerceIn(0f, 1f) * 255).roundToInt()
        if (a <= 0) return
        cv.save()
        cv.translate(k.cx, k.cy)
        cv.rotate(k.angle)
        if (k.plain) {
            // Nút thu gọn ở dải dưới: chỉ vẽ mũi tên, không nền
            drawIcon(cv, chevronPath, k.h * 0.32f, if (down) cText else cHint, a, width = 2.4f)
            cv.restore()
            return
        }

        val fn = k.kind != KeyKind.CHAR && k.kind != KeyKind.SPACE
        fill.color = if (down) cKeyDown else if (fn) cKeyFn else cKey
        fill.alpha = a
        rect.set(-k.w / 2, -k.h / 2, k.w / 2, k.h / 2)
        val r = 7 * dp
        if (k.round) cv.drawOval(rect, fill) else cv.drawRoundRect(rect, r, r, fill)
        if (k.accent) {
            stroke.color = cAccent; stroke.strokeWidth = 1.6f * dp; stroke.alpha = a
            if (k.round) cv.drawOval(rect, stroke) else cv.drawRoundRect(rect, r, r, stroke)
        }

        val s = minOf(k.w, k.h)
        when (k.kind) {
            KeyKind.CHAR -> {
                label(cv, labelOf(k), s * 0.6f, cText, a)
                val hx = k.w / 2 - s * 0.2f
                val hy = -k.h / 2 + s * 0.24f
                val sub = subOf(k)
                if (sub?.action != null) {
                    cv.save(); cv.translate(hx, hy)
                    drawActionIcon(cv, sub.action, s * 0.12f, cHint, a)
                    cv.restore()
                } else if (sub?.text != null) {
                    hint.textSize = s * 0.27f
                    hint.alpha = a
                    cv.drawText(sub.text, hx, hy + s * 0.09f, hint)
                }
            }
            KeyKind.SPACE -> label(cv, spaceLabel(), s * 0.36f, cDim, a)
            KeyKind.SYMBOLS -> label(cv, if (symbols) "ABC" else "?123", s * 0.36f, cText, a)
            KeyKind.SHIFT -> if (symbols) {
                // Ở trang ký hiệu, phím shift dùng để lật trang
                label(cv, if (symPage == 1) "=\\<" else "?123", s * 0.36f, cText, a)
            } else {
                val on = shift != ShiftState.OFF
                drawIcon(cv, shiftPath, s * 0.28f, if (on) cAccent else cText, a, filled = true)
                if (shift == ShiftState.LOCK) {
                    fill.color = cAccent; fill.alpha = a
                    cv.drawRect(-s * 0.22f, s * 0.34f, s * 0.22f, s * 0.4f, fill)
                }
            }
            KeyKind.BACKSPACE -> {
                drawIcon(cv, backPath, s * 0.3f, cText, a, filled = true)
                drawIcon(cv, backX, s * 0.3f, if (down) cKeyDown else cKeyFn, a, width = 2.6f)
            }
            KeyKind.ENTER -> drawIcon(cv, enterPath, s * 0.3f, cText, a, width = 2.6f)
            KeyKind.SETTINGS, KeyKind.PASTE, KeyKind.HIDE -> drawActionIcon(cv, k.kind, s * 0.27f, cText, a)
            KeyKind.ROUND_SWAP -> label(cv, "Đổi bên", s * 0.22f, cText, a)
            KeyKind.ROUND_TWO -> label(cv, "Hai tay", s * 0.22f, cText, a)
        }
        cv.restore()
    }

    /** Bong bóng hiện phím phụ phía trên phím đang giữ. */
    private fun drawActionIcon(cv: Canvas, kind: KeyKind, size: Float, color: Int, a: Int) {
        when (kind) {
            KeyKind.SETTINGS -> drawGear(cv, size, a, color)
            KeyKind.PASTE -> drawIcon(cv, pastePath, size * 0.9f, color, a)
            KeyKind.HIDE -> drawIcon(cv, hidePath, size, color, a)
            else -> {}
        }
    }

    private fun drawPopup(cv: Canvas, k: Key) {
        val sub = subOf(k) ?: return
        val w = maxOf(k.w, k.h) * 1.1f
        val h = k.h * 1.2f
        val cx = k.cx.coerceIn(w / 2, width - w / 2)
        val cy = (k.cy - k.h * 1.25f).coerceAtLeast(h / 2)
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        fill.color = cKeyDown; fill.alpha = 255
        cv.drawRoundRect(rect, 10 * dp, 10 * dp, fill)
        cv.save(); cv.translate(cx, cy)
        if (sub.action != null) drawActionIcon(cv, sub.action, h * 0.28f, cText, 255)
        else label(cv, sub.text ?: "", h * 0.55f, cText, 255)
        cv.restore()
    }

    /** Rung nhẹ khi chạm phím (dùng bộ rung trực tiếp, không phụ thuộc cài đặt hệ thống). */
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun buzz() {
        val st = vibrateStrength
        if (st <= 0) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            val effect = if (v.hasAmplitudeControl())
                VibrationEffect.createOneShot(14, (st * 255 / 100).coerceIn(1, 255))
            else
                VibrationEffect.createOneShot((6 + st / 6).toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
            v.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    /** Ký tự thật mà phím chữ này gõ ra (tính cả trang ký hiệu). */
    fun outputOf(k: Key): Char =
        if (symbols && mode != Mode.NORMAL) symbolFor(symPage, k.ch) ?: k.ch else k.ch

    private fun labelOf(k: Key): String {
        val o = outputOf(k)
        return if (!symbols && shift != ShiftState.OFF) o.uppercaseChar().toString() else o.toString()
    }

    private class Sub(val text: String? = null, val action: KeyKind? = null)

    /** Phím phụ khi giữ của một phím (ký tự hoặc chức năng), null nếu không có. */
    private fun subOf(k: Key): Sub? {
        if (k.kind != KeyKind.CHAR) return null
        if (symbols && mode != Mode.NORMAL) {
            // Vòng cung / một tay thiếu chỗ: ký hiệu dư của trang được đặt làm phím phụ
            val pg = Layouts.SYM_PAGES[symPage - 1]
            val t = when (k.ch) { 'l' -> pg[1][9]; ',' -> pg[3][0]; '.' -> pg[3][1]; else -> null }
            if (t != null && t != k.ch) return Sub(text = t.toString())
        }
        ACTION_SUBS[k.ch]?.let { return Sub(action = it) }
        if (!symbols) SUBS[k.ch]?.let { return Sub(text = it.toString()) }
        return null
    }

    private fun spaceLabel() = when (mode) {
        Mode.ONE_LEFT, Mode.ONE_RIGHT -> "← vuốt →"
        Mode.ARC -> "VNI"
        Mode.NORMAL -> "MonKey VNI"
    }

    private fun label(cv: Canvas, s: String, size: Float, color: Int, a: Int) {
        text.textSize = size
        text.color = color
        text.alpha = a
        text.strokeWidth = size * 0.035f
        val fm = text.fontMetrics
        cv.drawText(s, 0f, -(fm.ascent + fm.descent) / 2, text)
    }

    private fun drawIcon(cv: Canvas, p: Path, scale: Float, color: Int, a: Int, filled: Boolean = false, width: Float = 2f) {
        cv.save()
        cv.scale(scale, scale)
        icon.color = color
        icon.alpha = a
        icon.strokeWidth = width * dp / scale
        icon.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        cv.drawPath(p, icon)
        cv.restore()
    }

    private fun drawGear(cv: Canvas, r: Float, a: Int, color: Int = cText) {
        icon.color = color
        icon.alpha = a
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = (r * 0.18f).coerceIn(1.2f * dp, 2.2f * dp)
        cv.drawCircle(0f, 0f, r * 0.6f, icon)
        cv.drawCircle(0f, 0f, r * 0.22f, icon)
        for (i in 0 until 8) {
            val t = Math.toRadians(i * 45.0)
            val c = cos(t).toFloat(); val sn = sin(t).toFloat()
            cv.drawLine(c * r * 0.6f, sn * r * 0.6f, c * r, sn * r, icon)
        }
    }

    // ================= Touch =================

    private fun hit(x: Float, y: Float): Key? {
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (k in keys) {
            // Đưa điểm chạm về hệ toạ độ của phím (bỏ góc xoay) rồi kiểm tra trong khung
            val a = Math.toRadians(-k.angle.toDouble())
            val dx = x - k.cx; val dy = y - k.cy
            val lx = (dx * cos(a) - dy * sin(a)).toFloat()
            val ly = (dx * sin(a) + dy * cos(a)).toFloat()
            val slack = 0.08f * minOf(k.w, k.h)
            val inside = if (k.round) hypot(lx, ly) <= k.w / 2 + slack
            else abs(lx) <= k.w / 2 + slack && abs(ly) <= k.h / 2 + slack
            val d = hypot(dx, dy)
            if (inside && d < bestD) { best = k; bestD = d }
        }
        if (best != null) return best
        // Không trúng phím nào: lấy phím gần nhất nếu đủ gần
        for (k in keys) {
            val d = hypot(x - k.cx, y - k.cy)
            if (d < bestD) { best = k; bestD = d }
        }
        return if (best != null && bestD < config.keySize * 0.9f) best else null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (animating) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Ngón khác vừa chạm: gõ ngay phím chữ đang giữ để gõ nhanh hai ngón không mất chữ
                for (p in ptrs.values) {
                    val held = p.key
                    if (!p.done && held != null && held.kind == KeyKind.CHAR) { commitChar(p, held); p.done = true }
                }
                handler.removeCallbacks(hold)
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val k = hit(e.getX(i), e.getY(i))
                ptrs[id] = Ptr(k, e.getX(i), e.getY(i))
                if (k != null) {
                    buzz()
                    if (k.kind == KeyKind.BACKSPACE) {
                        listener.onKey(k)
                        ptrs[id]?.done = true
                        repeatPtr = id
                        repeatCount = 0
                        handler.removeCallbacks(repeat)
                        handler.postDelayed(repeat, 380)
                    } else if (hasSub(k)) {
                        holdPtr = id
                        handler.postDelayed(hold, HOLD_MS)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val p = ptrs.remove(id)
                if (id == repeatPtr) { handler.removeCallbacks(repeat); repeatPtr = -1 }
                if (id == holdPtr) { handler.removeCallbacks(hold); holdPtr = -1 }
                val k = p?.key
                if (p != null && k != null && !p.done) {
                    when (k.kind) {
                        KeyKind.SPACE -> space(k, e.getX(i) - p.downX, e.getY(i) - p.downY)
                        KeyKind.CHAR -> commitChar(p, k)
                        else -> listener.onKey(k)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { cancelTouches(); invalidate() }
        }
        return true
    }

    private fun hasSub(k: Key) = subOf(k) != null

    private fun commitChar(p: Ptr, k: Key) {
        val sub = if (p.sub) subOf(k) else null
        when {
            sub?.action != null -> listener.onKey(k.copy(kind = sub.action))
            sub?.text != null -> listener.onText(sub.text)
            else -> listener.onKey(k)
        }
    }

    private fun space(k: Key, dx: Float, dy: Float) {
        val th = 36 * dp
        // Phím cách ở bàn phím vòng cung bị nghiêng nên vuốt ngang thường lệch chéo:
        // chỉ cần đi ngang đủ xa là tính vuốt trái/phải.
        val dir = when {
            abs(dx) > th * 0.8f && abs(dx) > abs(dy) * 0.5f -> if (dx < 0) -1 else 1
            dy < -th && abs(dy) > abs(dx) -> 2
            else -> 0
        }
        if (dir == 0 || !listener.onSpaceSwipe(dir)) listener.onKey(k)
    }

    private fun cancelTouches() {
        ptrs.clear()
        handler.removeCallbacks(repeat)
        handler.removeCallbacks(hold)
        repeatPtr = -1
        holdPtr = -1
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        cancelTouches()
    }
}
