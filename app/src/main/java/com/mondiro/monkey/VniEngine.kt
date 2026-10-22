package com.mondiro.monkey

/**
 * Bộ gõ VNI cơ bản.
 *
 * [applySmart]: tự bỏ dấu đúng chỗ theo quy tắc chính tả (kiểu cũ "hòa" hoặc kiểu mới "hoà"),
 * dấu tự dời chỗ khi gõ tiếp chữ (vd "hòa" + n -> "hoàn") qua [reposition].
 * [apply]: chế độ thủ công, dấu đặt lên nguyên âm gần con trỏ nhất.
 *
 * 1 sắc · 2 huyền · 3 hỏi · 4 ngã · 5 nặng · 6 mũ (â ê ô) · 7 móc (ơ ư) · 8 trăng (ă) · 9 đ · 0 xoá dấu
 * Gõ lại cùng phím lần nữa để bỏ dấu và ra số (vd "a11" -> "a1").
 */
object VniEngine {

    private const val BASES = "aăâeêioôơuưy"
    private val TABLE = arrayOf(
        "aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ",
        "eéèẻẽẹ", "êếềểễệ",
        "iíìỉĩị",
        "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ",
        "uúùủũụ", "ưứừửữự",
        "yýỳỷỹỵ",
    )

    private class V(val base: Char, val tone: Int, val upper: Boolean)

    private val lookup = HashMap<Char, V>().apply {
        TABLE.forEachIndexed { bi, s ->
            s.forEachIndexed { t, ch ->
                put(ch, V(BASES[bi], t, false))
                put(ch.uppercaseChar(), V(BASES[bi], t, true))
            }
        }
    }

    private fun compose(base: Char, tone: Int, upper: Boolean): Char {
        val c = TABLE[BASES.indexOf(base)][tone]
        return if (upper) c.uppercaseChar() else c
    }

    private fun family(base: Char): Char = when (base) {
        'a', 'ă', 'â' -> 'a'
        'e', 'ê' -> 'e'
        'o', 'ô', 'ơ' -> 'o'
        'u', 'ư' -> 'u'
        else -> base
    }

    private fun lastIndex(chars: CharArray, test: (V) -> Boolean): Int {
        for (i in chars.indices.reversed()) {
            val v = lookup[chars[i]] ?: continue
            if (test(v)) return i
        }
        return -1
    }

    private fun setBase(chars: CharArray, i: Int, base: Char) {
        val v = lookup.getValue(chars[i])
        chars[i] = compose(base, v.tone, v.upper)
    }

    /**
     * @param word các chữ cái ngay trước con trỏ
     * @return chữ mới thay cho [word], hoặc null nếu phím số này chỉ là số bình thường.
     */
    fun apply(word: String, digit: Char): String? {
        if (word.isEmpty()) return null
        val chars = word.toCharArray()
        when (digit) {
            in '1'..'5' -> {
                val tone = digit - '0'
                val i = lastIndex(chars) { true }
                if (i < 0) return null
                val v = lookup.getValue(chars[i])
                if (v.tone == tone) {
                    chars[i] = compose(v.base, 0, v.upper)
                    return String(chars) + digit
                }
                chars[i] = compose(v.base, tone, v.upper)
                return String(chars)
            }
            '0' -> {
                val t = lastIndex(chars) { it.tone > 0 }
                if (t >= 0) {
                    val v = lookup.getValue(chars[t])
                    chars[t] = compose(v.base, 0, v.upper)
                    return String(chars)
                }
                val s = lastIndex(chars) { it.base != family(it.base) }
                if (s >= 0) {
                    setBase(chars, s, family(lookup.getValue(chars[s]).base))
                    return String(chars)
                }
                val d = chars.indexOfLast { it == 'đ' || it == 'Đ' }
                if (d >= 0) {
                    chars[d] = if (chars[d] == 'đ') 'd' else 'D'
                    return String(chars)
                }
                return null
            }
            '6' -> {
                val i = lastIndex(chars) { family(it.base) in "aeo" }
                if (i < 0) return null
                val v = lookup.getValue(chars[i])
                val fam = family(v.base)
                val hat = when (fam) { 'a' -> 'â'; 'e' -> 'ê'; else -> 'ô' }
                if (v.base == hat) {
                    setBase(chars, i, fam)
                    return String(chars) + digit
                }
                setBase(chars, i, hat)
                return String(chars)
            }
            '7' -> {
                val i = lastIndex(chars) { family(it.base) in "ou" }
                if (i < 0) return null
                val v = lookup.getValue(chars[i])
                val prev = if (i > 0) lookup[chars[i - 1]] else null
                if (v.base == 'ơ' || v.base == 'ư') {
                    setBase(chars, i, family(v.base))
                    if (v.base == 'ơ' && prev?.base == 'ư') setBase(chars, i - 1, 'u')
                    return String(chars) + digit
                }
                if (family(v.base) == 'o') {
                    setBase(chars, i, 'ơ')
                    // "uo" -> "ươ" (trừ "quo" -> "quơ")
                    val afterQ = i >= 2 && chars[i - 2].lowercaseChar() == 'q'
                    if (prev?.base == 'u' && !afterQ) setBase(chars, i - 1, 'ư')
                } else {
                    setBase(chars, i, 'ư')
                }
                return String(chars)
            }
            '8' -> {
                val i = lastIndex(chars) { family(it.base) == 'a' }
                if (i < 0) return null
                val v = lookup.getValue(chars[i])
                if (v.base == 'ă') {
                    setBase(chars, i, 'a')
                    return String(chars) + digit
                }
                setBase(chars, i, 'ă')
                return String(chars)
            }
            '9' -> {
                val i = chars.indexOfLast { it == 'd' || it == 'D' || it == 'đ' || it == 'Đ' }
                if (i < 0) return null
                when (chars[i]) {
                    'd' -> chars[i] = 'đ'
                    'D' -> chars[i] = 'Đ'
                    'đ' -> { chars[i] = 'd'; return String(chars) + digit }
                    else -> { chars[i] = 'D'; return String(chars) + digit }
                }
                return String(chars)
            }
        }
        return null
    }

    // ================= Tự bỏ dấu đúng chỗ =================

    private fun isVowel(c: Char) = lookup.containsKey(c)
    private fun baseOf(c: Char) = lookup.getValue(c).base

    /**
     * Vị trí nguyên âm mang dấu thanh trong chữ (đã bỏ hết dấu thanh), -1 nếu không có nguyên âm.
     * - "qu", "gi" đầu chữ: u / i thuộc phụ âm đầu.
     * - Có nguyên âm mang mũ/móc/trăng (â ê ô ơ ư ă): dấu đặt lên cái cuối cùng (ươ -> ơ).
     * - 3 nguyên âm: nguyên âm giữa. Có phụ âm cuối: nguyên âm thứ 2 (hoàn, toán).
     * - 2 nguyên âm, không phụ âm cuối: nguyên âm đầu (mùa, hòa); kiểu mới thì oa/oe/uy dấu ở sau (hoà, thuý).
     */
    fun tonePos(c: CharArray, oldStyle: Boolean): Int {
        var start = 0
        while (start < c.size && !isVowel(c[start])) start++
        if (start >= c.size) return -1
        val nextIsVowel = start + 1 < c.size && isVowel(c[start + 1])
        if (start > 0 && c[start - 1].lowercaseChar() == 'q' && family(baseOf(c[start])) == 'u' && nextIsVowel) start++
        else if (start == 1 && c[0].lowercaseChar() == 'g' && baseOf(c[1]) == 'i' && nextIsVowel) start++
        var end = start
        while (end < c.size && isVowel(c[end])) end++
        for (i in end - 1 downTo start) if (baseOf(c[i]) in "ăâêôơư") return i
        val n = end - start
        val hasFinal = end < c.size
        if (n == 1) return start
        if (n >= 3) return start + 1
        if (hasFinal) return start + 1
        if (!oldStyle) {
            val pair = "${baseOf(c[start])}${baseOf(c[start + 1])}"
            if (pair == "oa" || pair == "oe" || pair == "uy") return start + 1
        }
        return start
    }

    /** Bỏ hết dấu thanh, trả về dấu đầu tiên tìm thấy (0 nếu không có). */
    private fun stripTones(chars: CharArray): Int {
        var tone = 0
        for (i in chars.indices) {
            val v = lookup[chars[i]] ?: continue
            if (v.tone > 0) {
                if (tone == 0) tone = v.tone
                chars[i] = compose(v.base, 0, v.upper)
            }
        }
        return tone
    }

    /** Dời dấu thanh về đúng chỗ (dùng sau khi gõ thêm chữ). */
    fun reposition(word: String, oldStyle: Boolean): String {
        val chars = word.toCharArray()
        val tone = stripTones(chars)
        if (tone == 0) return word
        val p = tonePos(chars, oldStyle)
        if (p < 0) return word
        val v = lookup.getValue(chars[p])
        chars[p] = compose(v.base, tone, v.upper)
        return String(chars)
    }

    /** Như [apply] nhưng tự đặt dấu thanh đúng chỗ. */
    fun applySmart(word: String, digit: Char, oldStyle: Boolean): String? {
        if (word.isEmpty()) return null
        if (digit in '1'..'5') {
            val tone = digit - '0'
            val chars = word.toCharArray()
            val existing = stripTones(chars)
            if (existing == tone) return String(chars) + digit // gõ lại: bỏ dấu, ra số
            val p = tonePos(chars, oldStyle)
            if (p < 0) return null
            val v = lookup.getValue(chars[p])
            chars[p] = compose(v.base, tone, v.upper)
            return String(chars)
        }
        val r = apply(word, digit) ?: return null
        if (r.length > word.length) return r // gõ lại mũ/móc/trăng: bỏ và ra số
        return reposition(r, oldStyle)
    }
}
