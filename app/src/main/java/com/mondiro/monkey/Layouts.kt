package com.mondiro.monkey

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class Mode { NORMAL, ARC, ONE_LEFT, ONE_RIGHT }

enum class KeyKind { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, SETTINGS, PASTE, HIDE, ROUND_SWAP, ROUND_TWO }

/** Một phím đã đặt chỗ: tâm (cx, cy), kích thước, góc xoay (độ, theo chiều kim đồng hồ). */
data class Key(
    val id: String,
    val kind: KeyKind,
    val ch: Char = ' ',
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val angle: Float = 0f,
    val accent: Boolean = false,
    val round: Boolean = false,
    val plain: Boolean = false,   // vẽ chỉ icon, không nền phím (vd nút thu gọn ở dải dưới)
    val alpha: Float = 1f,
)

/** Thông số người dùng chỉnh trong Cài đặt, đã đổi sang pixel. */
data class LayoutConfig(
    val curvature: Float,   // 0 = tách đôi thẳng, 1 = cong hoàn toàn
    val keySize: Float,     // px
    val lift: Float,        // px, nâng bàn phím hai tay
    val inset: Float,       // px, nhích hai nửa bàn phím vào trong
    val dupMiddle: Boolean,
    val oneCurved: Boolean,
    val oneLift: Float,     // px, nâng bàn phím một tay
    val dp: Float,
)

private class Spec(val id: String, val kind: KeyKind, val ch: Char, val units: Float, val accent: Boolean)

private fun c(ch: Char) = Spec(ch.toString(), KeyKind.CHAR, ch, 1f, false)
private fun dup(ch: Char, side: String) = Spec("$ch#$side", KeyKind.CHAR, ch, 1f, true)
private fun k(id: String, kind: KeyKind, units: Float) = Spec(id, kind, ' ', units, false)
private fun chars(s: String) = s.map { c(it) }

object Layouts {

    // Mỗi nửa bàn phím vòng cung, từ dưới lên; mỗi hàng liệt kê từ mép màn hình vào giữa.
    private val ARC_LEFT = listOf(
        listOf(k("sym_L", KeyKind.SYMBOLS, 1.2f), c(','), k("space_L", KeyKind.SPACE, 2.4f)),
        listOf(k("shift", KeyKind.SHIFT, 1.3f)) + chars("zxcv") + dup('b', "L"),
        chars("asdfg") + dup('h', "L"),
        chars("qwert") + dup('y', "L"),
        chars("12345") + dup('6', "L"),
    )
    private val ARC_RIGHT = listOf(
        listOf(k("enter", KeyKind.ENTER, 1.2f), c('.'), k("space_R", KeyKind.SPACE, 2.4f)),
        listOf(k("back", KeyKind.BACKSPACE, 1.3f)) + chars("mnb") + dup('v', "R"),
        chars("lkjh") + dup('g', "R"),
        chars("poiuy") + dup('t', "R"),
        chars("09876") + dup('5', "R"),
    )

    private fun pad(c: LayoutConfig) = 6 * c.dp
    private fun normalRow(c: LayoutConfig) = 50 * c.dp
    private fun normalStrip(c: LayoutConfig) = 30 * c.dp

    /** Trang ký hiệu giống Laban Key: hàng 1, hàng 2 (10 phím), hàng 3 (7 phím), 2 phím cạnh phím cách. */
    val SYM_PAGES = arrayOf(
        arrayOf("~`|•√π÷×¶Δ", "@#$%&-+()/", "*\"':;!?", "_."),
        arrayOf("~`|•√π÷×¶Δ", "£¢€¥^°={}…", "\\©®™%[]", "<>"),
    )

    fun build(mode: Mode, w: Float, h: Float, c: LayoutConfig, symPage: Int = 0): List<Key> = when (mode) {
        Mode.NORMAL -> normal(w, h, c, symPage)
        Mode.ARC -> arc(w, h, c)
        Mode.ONE_LEFT -> oneHand(w, h, c, true)
        Mode.ONE_RIGHT -> oneHand(w, h, c, false)
    }

    /** Chiều cao cần cho bàn phím. Hai tay & một tay dùng chung chiều cao để chuyển qua lại không bị giật. */
    fun height(mode: Mode, c: LayoutConfig): Float {
        if (mode == Mode.NORMAL) return 5 * normalRow(c) + normalStrip(c) + 2 * pad(c)
        val arcH = max(bbox(fan(ARC_LEFT, c)).height(), bbox(fan(ARC_RIGHT, c)).height()) + c.lift
        val oneH = bbox(oneRaw(c, false)).height() + c.oneLift
        return max(arcH, oneH) + 2 * pad(c)
    }

    // ---------------- Bàn phím thường (màn ngoài / gập nửa) ----------------

    private fun normal(w: Float, h: Float, c: LayoutConfig, symPage: Int): List<Key> {
        val p = 4 * c.dp
        val uw = (w - 2 * p) / 10f
        val rowH = normalRow(c)
        val gap = 5 * c.dp
        val top = h - p - normalStrip(c) - 5 * rowH
        val pg = if (symPage > 0) SYM_PAGES[symPage - 1] else null
        val rows = if (pg != null) listOf(
            chars("1234567890") to 0f,
            chars(pg[0]) to 0f,
            chars(pg[1]) to 0f,
            listOf(k("shift", KeyKind.SHIFT, 1.5f)) + chars(pg[2]) + k("back", KeyKind.BACKSPACE, 1.5f) to 0f,
            listOf(
                k("sym_L", KeyKind.SYMBOLS, 1.5f), c(','), k("space_L", KeyKind.SPACE, 4f),
                c(pg[3][0]), c(pg[3][1]), k("enter", KeyKind.ENTER, 1.5f),
            ) to 0f,
        ) else listOf(
            chars("1234567890") to 0f,
            chars("qwertyuiop") to 0f,
            chars("asdfghjkl") to 0.5f,
            listOf(k("shift", KeyKind.SHIFT, 1.5f)) + chars("zxcvbnm") + k("back", KeyKind.BACKSPACE, 1.5f) to 0f,
            listOf(
                k("sym_L", KeyKind.SYMBOLS, 1.5f), c(','), k("space_L", KeyKind.SPACE, 5f),
                c('.'), k("enter", KeyKind.ENTER, 1.5f),
            ) to 0f,
        )
        val out = ArrayList<Key>()
        rows.forEachIndexed { i, (row, stagger) ->
            var x = p + stagger * uw
            val cy = top + i * rowH + rowH / 2
            for (s in row) {
                val kw = s.units * uw
                out += Key(s.id, s.kind, s.ch, x + kw / 2, cy, kw - gap, rowH - gap)
                x += kw
            }
        }
        // Dải mỏng phía dưới: nút thu gọn bàn phím (tách khỏi các hàng phím)
        val st = normalStrip(c)
        out += Key("hide", KeyKind.HIDE, cx = w - p - 28 * c.dp, cy = h - p - st / 2,
            w = 52 * c.dp, h = st, plain = true)
        return out
    }

    // ---------------- Bàn phím vòng cung hai tay ----------------

    /**
     * Xếp một nửa bàn phím thành hình quạt quanh gốc (0,0) = gốc ngón cái trái.
     * Các hàng là những cung tròn đồng tâm (bán kính R0 + i*bước) nên không bao giờ đè nhau;
     * phím xoay theo góc của cung. Độ cong nhỏ = bán kính lớn hơn = hàng thẳng hơn.
     */
    private fun fan(rows: List<List<Spec>>, c: LayoutConfig): List<Key> {
        val u = c.keySize
        val gap = u * 0.12f
        val pitch = u * 1.12f
        val flat = c.curvature < 0.01f
        val t = c.curvature.coerceAtLeast(0.02f)
        val r0 = u * 3.4f / t
        val mid = Math.toRadians(45.0) * t
        val out = ArrayList<Key>()
        rows.forEachIndexed { i, row ->
            val specs = row.filter { c.dupMiddle || !it.accent }
            val total = specs.sumOf { it.units.toDouble() } * u
            val r = r0 + i * pitch
            var s = 0f
            for (sp in specs) {
                val kw = sp.units * u
                val sc = s + kw / 2
                val phi = if (flat) 0.0 else mid + (sc - total / 2) / r
                out += Key(
                    sp.id, sp.kind, sp.ch,
                    cx = if (flat) sc else (r * sin(phi)).toFloat(),
                    cy = if (flat) -i * pitch else (-r * cos(phi)).toFloat(),
                    w = kw - gap, h = u,
                    angle = Math.toDegrees(phi).toFloat(),
                    accent = sp.accent,
                )
                s += kw
            }
        }
        return out
    }

    private fun arc(w: Float, h: Float, c: LayoutConfig): List<Key> {
        val bottom = h - pad(c) - c.lift
        var left = fan(ARC_LEFT, c)
        val bl = bbox(left)
        left = shift(left, pad(c) + c.inset - bl.left, bottom - bl.bottom)

        var right = fan(ARC_RIGHT, c).map { it.copy(cx = -it.cx, angle = -it.angle) }
        val br = bbox(right)
        right = shift(right, (w - pad(c) - c.inset) - br.right, bottom - br.bottom)

        val g = c.keySize * 1.1f
        val cy = bottom - g / 2
        val gear = Key("gear", KeyKind.SETTINGS, cx = w / 2 - g * 1.25f, cy = cy, w = g, h = g, round = true)
        val hide = Key("hide", KeyKind.HIDE, cx = w / 2, cy = cy, w = g, h = g, round = true)
        val paste = Key("paste", KeyKind.PASTE, cx = w / 2 + g * 1.25f, cy = cy, w = g, h = g, round = true)
        return left + right + gear + hide + paste
    }

    // ---------------- Bàn phím một tay ----------------

    private val ONE_ROWS = listOf(
        listOf(k("sym_L", KeyKind.SYMBOLS, 1.5f), c(','), k("space", KeyKind.SPACE, 5f), c('.'), k("enter", KeyKind.ENTER, 1.5f)) to 0f,
        listOf(k("shift", KeyKind.SHIFT, 1.5f)) + chars("zxcvbnm") + k("back", KeyKind.BACKSPACE, 1.5f) to 0f,
        chars("asdfghjkl") to 0.5f,
        chars("qwertyuiop") to 0f,
        chars("1234567890") to 0f,
    )

    /** QWERTY 1 khối, cong nhẹ (cung đồng tâm bán kính lớn) quanh góc dưới phía bên đang dính. */
    private fun oneRaw(c: LayoutConfig, left: Boolean): List<Key> {
        val u = c.keySize * 0.92f
        val gap = u * 0.12f
        val pitch = u * 1.12f
        val totalW = 10 * u
        val r0 = 16 * u / (if (c.oneCurved) 0.4f else 0.001f)
        val out = ArrayList<Key>()
        ONE_ROWS.forEachIndexed { i, (row, stagger) ->
            var s = stagger * u
            for (sp in row) {
                val kw = sp.units * u
                val fx = s + kw / 2
                val d = if (left) fx else totalW - fx
                val r = r0 + i * pitch
                val phi = d / r
                val deg = Math.toDegrees(phi.toDouble()).toFloat()
                val id = if (sp.kind == KeyKind.SPACE) (if (left) "space_L" else "space_R") else sp.id
                out += Key(
                    id, sp.kind, sp.ch,
                    cx = if (left) r * sin(phi) else totalW - r * sin(phi),
                    cy = r0 - r * cos(phi),
                    w = kw - gap, h = u,
                    angle = if (left) deg else -deg,
                )
                s += kw
            }
        }
        return out
    }

    private fun oneHand(w: Float, h: Float, c: LayoutConfig, left: Boolean): List<Key> {
        var keys = oneRaw(c, left)
        val b = bbox(keys)
        val dx = if (left) pad(c) - b.left else (w - pad(c)) - b.right
        keys = shift(keys, dx, (h - pad(c) - c.oneLift) - b.bottom)
        val bb = bbox(keys)

        // Nút tròn ở khoảng trống bên cạnh
        val rad = 26 * c.dp
        val gapX = 28 * c.dp
        val bx = if (left) bb.right + gapX + rad else bb.left - gapX - rad
        val d = rad * 2
        // 5 nút xếp dọc, co khoảng cách cho vừa chiều cao bàn phím
        val step = minOf(rad * 2.2f, (h - 2 * pad(c) - d) / 4f)
        val my = bb.centerY().coerceIn(pad(c) + rad + step * 2, h - pad(c) - rad - step * 2)
        keys = keys + listOf(
            Key("swap", KeyKind.ROUND_SWAP, cx = bx, cy = my - step * 2, w = d, h = d, round = true),
            Key("two", KeyKind.ROUND_TWO, cx = bx, cy = my - step, w = d, h = d, round = true),
            Key("hide", KeyKind.HIDE, cx = bx, cy = my, w = d, h = d, round = true),
            Key("paste", KeyKind.PASTE, cx = bx, cy = my + step, w = d, h = d, round = true),
            Key("gear", KeyKind.SETTINGS, cx = bx, cy = my + step * 2, w = d, h = d, round = true),
        )
        return keys
    }

    // ---------------- Tiện ích ----------------

    private fun shift(keys: List<Key>, dx: Float, dy: Float) = keys.map { it.copy(cx = it.cx + dx, cy = it.cy + dy) }

    /** Khung bao quanh tất cả phím (đã tính góc xoay). */
    fun bbox(keys: List<Key>): RectF {
        val r = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (k in keys) {
            val a = Math.toRadians(k.angle.toDouble())
            val ca = abs(cos(a)).toFloat()
            val sa = abs(sin(a)).toFloat()
            val ex = k.w / 2 * ca + k.h / 2 * sa
            val ey = k.w / 2 * sa + k.h / 2 * ca
            r.left = minOf(r.left, k.cx - ex)
            r.right = maxOf(r.right, k.cx + ex)
            r.top = minOf(r.top, k.cy - ey)
            r.bottom = maxOf(r.bottom, k.cy + ey)
        }
        return r
    }
}
