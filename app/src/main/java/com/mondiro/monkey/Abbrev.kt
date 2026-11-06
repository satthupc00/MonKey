package com.mondiro.monkey

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Chuyển chữ khi gõ bằng giọng nói sang kiểu viết tắt ("không" -> "ko").
 * Danh sách dạng mỗi dòng "từ gốc=viết tắt", người dùng sửa được trong Cài đặt.
 * Chỉ thay nguyên chữ (không đụng giữa chữ khác, vd "kem" giữ nguyên), giữ viết hoa đầu chữ.
 */
object Abbrev {

    val DEFAULT = """
        không=ko
        tôi=tui
        anh=a
        em=e
        được=dc
        với=vs
        vậy=v
        rồi=r
        gì=j
        bây giờ=bh
        hôm nay=hnay
        người=ng
        trước=trc
        thôi=thui
        luôn=lun
        biết=bít
        nhiều=nhìu
        chị=c
        bạn=b
        vâng=vg
        tin nhắn=tn
        điện thoại=đt
        trả lời=tl
        facebook=fb
    """.trimIndent()

    class Rule(val pattern: Pattern, val to: String)

    fun parse(spec: String): List<Rule> = spec.lines()
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val from = line.substring(0, i).trim()
            val to = line.substring(i + 1).trim()
            if (from.isEmpty()) null else from to to
        }
        .sortedByDescending { it.first.length } // cụm dài trước ("bây giờ" trước "giờ")
        .map { (from, to) ->
            Rule(
                Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(from) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
                ),
                to,
            )
        }

    fun apply(text: String, rules: List<Rule>): String {
        var out = text
        for (r in rules) {
            val m = r.pattern.matcher(out)
            val sb = StringBuffer()
            while (m.find()) {
                val src = m.group()
                val rep = when {
                    src.length > 1 && src == src.uppercase() && r.to.length > 1 -> r.to.uppercase()
                    src[0].isUpperCase() -> r.to.replaceFirstChar { it.uppercaseChar() }
                    else -> r.to
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep))
            }
            m.appendTail(sb)
            out = sb.toString()
        }
        return out
    }
}
