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
        assertEquals("của", type("cua3"))
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
}
