package com.mondiro.monkey

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Gõ bằng giọng nói tiếng Việt qua bộ nhận diện của Android (ưu tiên Google).
 * Bấm 1 lần là nghe liên tục; người dùng chạm bàn phím để dừng ([stop]).
 * Mỗi khi bộ nhận diện trả kết quả một đoạn, đoạn đó được gõ ra rồi tự nghe tiếp.
 */
class VoiceInput(private val context: Context, private val cb: Callback) : RecognitionListener {

    interface Callback {
        /** Chữ tạm thời của đoạn đang nói (hiện dần trong ô gõ). */
        fun onPartial(text: String)
        /** Chữ chốt của một đoạn. */
        fun onFinal(text: String)
        /** Thông báo trạng thái cho bàn phím; null = đã dừng nghe. */
        fun onStatus(message: String?)
    }

    companion object {
        /** Từ tiếng Anh thông dụng hay xen trong câu tiếng Việt, giúp nhận đúng khi nói giọng Việt. */
        private val ENGLISH_HINTS = arrayListOf(
            "online", "offline", "off", "on", "website", "web", "email", "app", "file", "link",
            "game", "update", "download", "upload", "OK", "check", "meeting", "deadline", "team",
            "project", "feedback", "test", "design", "animation", "Spine", "Facebook", "Zalo",
            "YouTube", "Google", "iPhone", "laptop", "wifi", "pass", "password", "share", "like",
        )
        private const val MAX_SILENT_RESTARTS = 2
    }

    var active = false
        private set
    private var stopping = false
    private var silentRestarts = 0
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (active) return
        active = true
        stopping = false
        silentRestarts = 0
        cb.onStatus("Đang nghe… chạm để dừng")
        listen()
    }

    /** Dừng nghe: đoạn đang nói vẫn được gõ ra. */
    fun stop() {
        if (!active) return
        stopping = true
        cb.onStatus("Đang xử lý…")
        recognizer?.stopListening()
    }

    /** Huỷ ngay (vd khi bàn phím bị ẩn). */
    fun cancel() {
        if (!active) return
        recognizer?.cancel()
        finish()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer {
        // Ưu tiên bộ nhận diện của Google (máy Samsung có thể đặt mặc định là bộ khác không hỗ trợ tiếng Việt)
        val services = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        val google = services.firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
            ?: services.firstOrNull { it.serviceInfo.packageName.startsWith("com.google.") }
        return if (google != null)
            SpeechRecognizer.createSpeechRecognizer(context, ComponentName(google.serviceInfo.packageName, google.serviceInfo.name))
        else SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun listen() {
        val sr = recognizer ?: createRecognizer().also { recognizer = it }
        sr.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (Build.VERSION.SDK_INT >= 33) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ENGLISH_HINTS)
        }
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            cb.onStatus(null)
            active = false
        }
    }

    private fun finish() {
        active = false
        stopping = false
        cb.onStatus(null)
    }

    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    override fun onPartialResults(partialResults: Bundle?) {
        val t = first(partialResults)
        if (t.isNotEmpty()) cb.onPartial(t)
    }

    override fun onResults(results: Bundle?) {
        val t = first(results)
        cb.onFinal(t)
        if (t.isNotEmpty()) silentRestarts = 0
        if (active && !stopping) listen() else finish()
    }

    override fun onError(error: Int) {
        val silent = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        cb.onFinal("") // chốt phần chữ tạm (nếu có)
        if (active && !stopping && silent && ++silentRestarts <= MAX_SILENT_RESTARTS) {
            listen() // im lặng một lúc: nghe tiếp
            return
        }
        if (!silent && !stopping) {
            cb.onStatus(
                when (error) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lỗi mạng, chưa nhận được giọng nói"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cho phép micro"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận giọng nói đang bận, thử lại"
                    else -> "Không nhận được giọng nói (mã $error)"
                }
            )
            active = false
            stopping = false
            return
        }
        finish()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
