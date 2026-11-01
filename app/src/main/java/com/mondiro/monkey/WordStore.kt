package com.mondiro.monkey

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.Normalizer

/**
 * Bộ nhớ chữ đã gõ để gợi ý (chỉ lưu trong máy).
 * - words: chữ -> số lần gõ
 * - bigrams: chữ trước -> (chữ sau -> số lần), để gợi ý chữ tiếp theo
 */
class WordStore private constructor(context: Context) {

    companion object {
        private var instance: WordStore? = null
        fun get(context: Context): WordStore =
            instance ?: WordStore(context.applicationContext).also { instance = it }

        private const val MAX_WORDS = 5000
        private const val MAX_NEXT = 12

        /** Bỏ dấu để so khớp: "Việt" -> "viet". */
        fun plain(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace('đ', 'd')
    }

    private val wordsFile = File(context.filesDir, "words.tsv")
    private val bigramFile = File(context.filesDir, "bigrams.tsv")
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val handler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { save() }

    init { load() }

    fun learn(word: String, prev: String?) {
        val w = word.lowercase()
        if (w.length < 2 || w.length > 30 || !w.all { it.isLetter() }) return
        words[w] = (words[w] ?: 0) + 1
        val p = prev?.lowercase()
        if (p != null && p.length >= 1 && p.all { it.isLetter() }) {
            val next = bigrams.getOrPut(p) { HashMap() }
            next[w] = (next[w] ?: 0) + 1
            if (next.size > MAX_NEXT) next.remove(next.minByOrNull { it.value }!!.key)
        }
        if (words.size > MAX_WORDS) trim()
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 3000)
    }

    /** Gợi ý tối đa n chữ cho chữ đang gõ dở [prefix] (rỗng = gợi ý chữ tiếp theo sau [prev]). */
    fun suggest(prefix: String, prev: String?, n: Int): List<String> {
        val p = prefix.lowercase()
        val next = prev?.let { bigrams[it.lowercase()] }
        val list: List<String> = if (p.isEmpty()) {
            val fromPrev = next?.entries?.sortedByDescending { it.value }?.map { it.key } ?: emptyList()
            if (fromPrev.isNotEmpty()) fromPrev
            else words.entries.sortedByDescending { it.value }.map { it.key }
        } else {
            val pp = plain(p)
            words.entries
                .filter { it.key != p && (it.key.startsWith(p) || plain(it.key).startsWith(pp)) }
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { if (it.key.startsWith(p)) 1 else 0 }
                        .thenByDescending { (next?.get(it.key) ?: 0) * 5 + it.value }
                )
                .map { it.key }
        }
        val capital = prefix.firstOrNull()?.isUpperCase() == true
        return list.take(n).map { if (capital) it.replaceFirstChar { c -> c.uppercaseChar() } else it }
    }

    fun clear() {
        words.clear()
        bigrams.clear()
        handler.removeCallbacks(saveTask)
        wordsFile.delete()
        bigramFile.delete()
    }

    private fun trim() {
        // Bỏ bớt các chữ ít dùng nhất
        words.entries.sortedBy { it.value }.take(words.size - MAX_WORDS * 9 / 10)
            .map { it.key }.forEach { words.remove(it) }
    }

    private fun load() {
        try {
            if (wordsFile.exists()) wordsFile.forEachLine { line ->
                val t = line.split('\t')
                if (t.size == 2) t[1].toIntOrNull()?.let { words[t[0]] = it }
            }
            if (bigramFile.exists()) bigramFile.forEachLine { line ->
                val t = line.split('\t')
                if (t.size == 3) t[2].toIntOrNull()?.let { bigrams.getOrPut(t[0]) { HashMap() }[t[1]] = it }
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            wordsFile.writeText(words.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            bigramFile.writeText(bigrams.entries.flatMap { (p, m) -> m.map { "$p\t${it.key}\t${it.value}" } }
                .joinToString("\n"))
        } catch (_: Exception) {
        }
    }
}
