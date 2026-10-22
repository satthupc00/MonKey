package com.mondiro.monkey

/**
 * Bộ gõ VNI cơ bản.
 *
 * Dấu luôn đặt lên nguyên âm GẦN CON TRỎ NHẤT (không tự đặt dấu thông minh).
 * Muốn "hòa" thay vì "hoà": gõ "ho2a" hoặc đặt con trỏ sau chữ "o" rồi gõ 2.
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
}
