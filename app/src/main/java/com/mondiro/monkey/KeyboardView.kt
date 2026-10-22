package com.mondiro.monkey

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
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
    }

    companion object {
        val SYMBOLS = mapOf(
            'q' to '!', 'w' to '@', 'e' to '#', 'r' to '$', 't' to '%', 'y' to '^', 'u' to '&', 'i' to '*', 'o' to '(', 'p' to ')',
            'a' to '-', 's' to '_', 'd' to '=', 'f' to '+', 'g' to '/', 'h' to '\\', 'j' to ':', 'k' to ';', 'l' to '\'',
            'z' to '"', 'x' to '[', 'c' to ']', 'v' to '?', 'b' to '~', 'n' to '<', 'm' to '>', ',' to '{', '.' to '}',
        )
        private const val ANIM_MS = 450L
    }

    private val dp = resources.displayMetrics.density

    var mode = Mode.NORMAL
        private set
    var config = LayoutConfig(0.7f, 40 * dp, 0f, true, true, 0f, dp)
        private set
    var shift = ShiftState.OFF
        set(v) { field = v; invalidate() }
    var symbols = false
        set(v) { field = v; invalidate() }
    var vibrate = true

    private var keys: List<Key> = emptyList()

    // ---- animation ----
    private var fromKeys: List<Key> = emptyList()
    private var toKeys: List<Key> = emptyList()
    private var animT = 1f
    private var animator: ValueAnimator? = null
    private val animating get() = animator?.isRunning == true

    // ---- paints ----
    private val cBg = Color.parseColor("#1F2126")
    private val cKey = Color.parseColor("#2F333A")
    private val cKeyFn = Color.parseColor("#272A30")
    private val cKeyDown = Color.parseColor("#454A54")
    private val cBorder = Color.parseColor("#363A42")
    private val cText = Color.parseColor("#E8E9EC")
    private val cDim = Color.parseColor("#8C9099")
    private val cAccent = Color.parseColor("#E5383B")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = cText }
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
        moveTo(0.05f, -0.3f); lineTo(0.6f, 0.3f); moveTo(0.6f, -0.3f); lineTo(0.05f, 0.3f)
    }
    private val enterPath = Path().apply {
        moveTo(0.85f, -0.75f); lineTo(0.85f, 0.2f); lineTo(-0.8f, 0.2f)
        moveTo(-0.35f, -0.25f); lineTo(-0.8f, 0.2f); lineTo(-0.35f, 0.65f)
    }

    // ---- touch ----
    private class Ptr(val key: Key?, val downX: Float, val downY: Float, var done: Boolean = false)
    private val ptrs = HashMap<Int, Ptr>()
    private val handler = Handler(Looper.getMainLooper())
    private var repeatPtr = -1
    private val repeat = object : Runnable {
        override fun run() {
            val p = ptrs[repeatPtr] ?: return
            p.key?.let { listener.onKey(it) }
            handler.postDelayed(this, 55)
        }
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
        val newKeys = Layouts.build(mode, width.toFloat(), height.toFloat(), config)
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
    }

    private fun drawKey(cv: Canvas, k: Key, down: Boolean) {
        val a = (k.alpha.coerceIn(0f, 1f) * 255).roundToInt()
        if (a <= 0) return
        cv.save()
        cv.translate(k.cx, k.cy)
        cv.rotate(k.angle)

        val fn = k.kind != KeyKind.CHAR && k.kind != KeyKind.SPACE
        fill.color = if (down) cKeyDown else if (fn) cKeyFn else cKey
        fill.alpha = a
        stroke.color = if (k.accent) cAccent else cBorder
        stroke.strokeWidth = if (k.accent) 1.6f * dp else 1f * dp
        stroke.alpha = a
        rect.set(-k.w / 2, -k.h / 2, k.w / 2, k.h / 2)
        if (k.round) {
            cv.drawOval(rect, fill); cv.drawOval(rect, stroke)
        } else {
            val r = 8 * dp
            cv.drawRoundRect(rect, r, r, fill); cv.drawRoundRect(rect, r, r, stroke)
        }

        val s = minOf(k.w, k.h)
        when (k.kind) {
            KeyKind.CHAR -> label(cv, charLabel(k.ch), s * if (k.ch.isDigit()) 0.38f else 0.44f, cText, a)
            KeyKind.SPACE -> label(cv, spaceLabel(), s * 0.30f, cDim, a)
            KeyKind.SYMBOLS -> label(cv, if (symbols) "ABC" else "123", s * 0.32f, cText, a)
            KeyKind.SHIFT -> {
                val on = shift != ShiftState.OFF
                drawIcon(cv, shiftPath, s * 0.26f, if (on) cAccent else cText, a, filled = shift == ShiftState.LOCK)
            }
            KeyKind.BACKSPACE -> drawIcon(cv, backPath, s * 0.28f, cText, a)
            KeyKind.ENTER -> drawIcon(cv, enterPath, s * 0.28f, cText, a)
            KeyKind.SETTINGS -> drawGear(cv, s * 0.26f, a)
            KeyKind.ROUND_SWAP -> label(cv, "Đổi bên", s * 0.2f, cText, a)
            KeyKind.ROUND_TWO -> label(cv, "Hai tay", s * 0.2f, cText, a)
        }
        cv.restore()
    }

    private fun charLabel(ch: Char): String {
        if (symbols) return (SYMBOLS[ch] ?: ch).toString()
        return if (shift != ShiftState.OFF) ch.uppercaseChar().toString() else ch.toString()
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
        val fm = text.fontMetrics
        cv.drawText(s, 0f, -(fm.ascent + fm.descent) / 2, text)
    }

    private fun drawIcon(cv: Canvas, p: Path, scale: Float, color: Int, a: Int, filled: Boolean = false) {
        cv.save()
        cv.scale(scale, scale)
        icon.color = color
        icon.alpha = a
        icon.strokeWidth = 1.8f * dp / scale
        icon.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        cv.drawPath(p, icon)
        cv.restore()
    }

    private fun drawGear(cv: Canvas, r: Float, a: Int) {
        icon.color = cText
        icon.alpha = a
        icon.style = Paint.Style.STROKE
        icon.strokeWidth = 1.8f * dp
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
                    if (!p.done && held != null && held.kind == KeyKind.CHAR) { listener.onKey(held); p.done = true }
                }
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val k = hit(e.getX(i), e.getY(i))
                ptrs[id] = Ptr(k, e.getX(i), e.getY(i))
                if (k != null) {
                    if (vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (k.kind == KeyKind.BACKSPACE) {
                        listener.onKey(k)
                        ptrs[id]?.done = true
                        repeatPtr = id
                        handler.removeCallbacks(repeat)
                        handler.postDelayed(repeat, 420)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val p = ptrs.remove(id)
                if (id == repeatPtr) { handler.removeCallbacks(repeat); repeatPtr = -1 }
                val k = p?.key
                if (p != null && k != null && !p.done) {
                    if (k.kind == KeyKind.SPACE) space(k, e.getX(i) - p.downX, e.getY(i) - p.downY)
                    else listener.onKey(k)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { cancelTouches(); invalidate() }
        }
        return true
    }

    private fun space(k: Key, dx: Float, dy: Float) {
        val th = 36 * dp
        val dir = when {
            abs(dx) > th && abs(dx) > abs(dy) -> if (dx < 0) -1 else 1
            dy < -th && abs(dy) > abs(dx) -> 2
            else -> 0
        }
        if (dir == 0 || !listener.onSpaceSwipe(dir)) listener.onKey(k)
    }

    private fun cancelTouches() {
        ptrs.clear()
        handler.removeCallbacks(repeat)
        repeatPtr = -1
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        cancelTouches()
    }
}
