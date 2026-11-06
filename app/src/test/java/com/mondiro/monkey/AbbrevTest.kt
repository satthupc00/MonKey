package com.mondiro.monkey

import org.junit.Assert.assertEquals
import org.junit.Test

class AbbrevTest {
    private val rules = Abbrev.parse(Abbrev.DEFAULT)
    private fun ab(s: String) = Abbrev.apply(s, rules)

    @Test fun basic() {
        assertEquals("ko bít a có đi ko", ab("không biết anh có đi không"))
        assertEquals("Tui vs e", ab("Tôi với em"))
        assertEquals("bh tui đi", ab("bây giờ tôi đi"))
        assertEquals("ăn kem", ab("ăn kem"))          // không đụng giữa chữ
        assertEquals("a ấy", ab("anh ấy"))
        assertEquals("Ko dc r", ab("Không được rồi"))
    }
}
