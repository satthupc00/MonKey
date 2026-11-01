package com.mondiro.monkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View

/** Hàng gợi ý chữ phía trên bàn phím. Bấm vào chữ nào thì gọi [onPick]. */
@SuppressLint("ViewConstructor")
class SuggestionBar(context: Context, private val onPick: (String) -> Unit) : View(context) {

    private val dp = resources.displayMetrics.density
    private val font: Typeface = try { resources.getFont(R.font.roboto_regular) } catch (_: Exception) { Typeface.DEFAULT }

    var words: List<String> = emptyList()
        set(v) { if (field != v) { field = v; invalidate() } }

    private var pressed = -1

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = font; textSize = 17 * dp
    }
    private val divider = Paint().apply { color = Color.parseColor("#374147"); strokeWidth = 1 * dp }
    private val press = Paint().apply { color = Color.parseColor("#2B3644") }

    /** Màn rộng (màn trong) hiện 5 gợi ý, màn ngoài hiện 3. */
    private fun slots() = if (width / dp >= 600) 5 else 3

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (42 * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#161F2E"))
        val n = slots()
        val sw = width / n.toFloat()
        val fm = text.fontMetrics
        val baseline = height / 2f - (fm.ascent + fm.descent) / 2
        for (i in 0 until n) {
            if (i == pressed && i < words.size) canvas.drawRect(i * sw, 0f, (i + 1) * sw, height.toFloat(), press)
            if (i > 0) canvas.drawLine(i * sw, height * 0.25f, i * sw, height * 0.75f, divider)
            val w = words.getOrNull(i) ?: continue
            val shown = TextUtils.ellipsize(w, text, sw - 12 * dp, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, i * sw + sw / 2, baseline, text)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = (e.x / (width / slots().toFloat())).toInt()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = i; invalidate() }
            MotionEvent.ACTION_UP -> {
                words.getOrNull(pressed)?.let { if (pressed == i) onPick(it) }
                pressed = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}
