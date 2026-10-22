package com.mondiro.monkey

import org.junit.Assert.assertEquals
import org.junit.Test

class VniEngineTest {

    /** Gõ lần lượt từng ký tự như trên bàn phím. */
    private fun type(keys: String): String {
        val sb = StringBuilder()
        for (c in keys) {
            if (c.isDigit()) {
                var i = sb.length
                while (i > 0 && sb[i - 1].isLetter()) i--
                val r = VniEngine.apply(sb.substring(i), c)
                if (r == null) sb.append(c) else sb.replace(i, sb.length, r)
            } else sb.append(c)
        }
        return sb.toString()
    }

    @Test fun tones() {
        assertEquals("toán", type("toan1"))
        assertEquals("hoà", type("hoa2"))
        assertEquals("hòa", type("ho2a"))
        assertEquals("của", type("cu3a"))
        assertEquals("cuả", type("cua3"))
        assertEquals("Việt", type("Viet65"))
        assertEquals("Nam", type("Nam"))
    }

    @Test fun shapes() {
        assertEquals("ngươi", type("nguoi7"))
        assertEquals("quơ", type("quo7"))
        assertEquals("tương", type("tuong7"))
        assertEquals("ăn", type("a8n"))
        assertEquals("đi", type("di9"))
        assertEquals("Đ", type("D9"))
        assertEquals("Â", type("A6"))
        assertEquals("tư", type("tu7"))
    }

    @Test fun toggleAndDigits() {
        assertEquals("a1", type("a11"))
        assertEquals("o66", type("o666"))
        assertEquals("di9", type("di99"))
        assertEquals("123", type("123"))
        assertEquals("abc 5", type("abc 5"))
        assertEquals("tôi", type("to6i"))
        assertEquals("ta", type("ta10"))
    }

    /** Gõ kiểu tự bỏ dấu đúng chỗ, giống MonKeyIME. */
    private fun smart(keys: String, oldStyle: Boolean = true): String {
        val sb = StringBuilder()
        for (c in keys) {
            var i = sb.length
            while (i > 0 && sb[i - 1].isLetter()) i--
            if (c.isDigit()) {
                val r = VniEngine.applySmart(sb.substring(i), c, oldStyle)
                if (r == null) sb.append(c) else sb.replace(i, sb.length, r)
            } else {
                sb.append(c)
                if (c.isLetter()) sb.replace(i, sb.length, VniEngine.reposition(sb.substring(i), oldStyle))
            }
        }
        return sb.toString()
    }

    @Test fun smartTones() {
        assertEquals("hòa", smart("hoa2"))
        assertEquals("hoà", smart("hoa2", oldStyle = false))
        assertEquals("hoàn", smart("hoan2"))
        assertEquals("hoàn", smart("hoa2n"))
        assertEquals("toán", smart("toan1"))
        assertEquals("mùa", smart("mua2"))
        assertEquals("người", smart("nguoi72"))
        assertEquals("người", smart("nguoi27"))
        assertEquals("tiếng", smart("tieng61"))
        assertEquals("Việt", smart("Viet65"))
        assertEquals("quý", smart("quy1"))
        assertEquals("quốc", smart("quoc61"))
        assertEquals("thúy", smart("thuy1"))
        assertEquals("thuý", smart("thuy1", oldStyle = false))
        assertEquals("khuỷu", smart("khuyu3"))
        assertEquals("già", smart("gia2"))
        assertEquals("gì", smart("gi2"))
        assertEquals("giường", smart("giuong72"))
        assertEquals("mứa", smart("mua71"))
        assertEquals("khỏe", smart("khoe3"))
        assertEquals("hoa2", smart("hoa22"))
        assertEquals("máy", smart("may1"))
    }
}
