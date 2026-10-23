package com.mondiro.monkey

import android.content.Context
import android.content.SharedPreferences

/** Cài đặt người dùng (lưu trong máy). */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("monkey", Context.MODE_PRIVATE)

    init {
        // v1.4.0: áp bộ mặc định mới (độ cong 5, cỡ phím 40, nhích vào trong 39, rung 88) một lần
        if (sp.getInt("defaults_version", 0) < 2) {
            sp.edit()
                .remove("curvature").remove("key_size").remove("inset").remove("vibrate_strength")
                .putInt("defaults_version", 2)
                .apply()
        }
    }

    var curvature: Int            // 0..100
        get() = sp.getInt("curvature", 5)
        set(v) = sp.edit().putInt("curvature", v).apply()
    var keySize: Int              // dp
        get() = sp.getInt("key_size", 40)
        set(v) = sp.edit().putInt("key_size", v).apply()
    var lift: Int                 // dp
        get() = sp.getInt("lift", 0)
        set(v) = sp.edit().putInt("lift", v).apply()
    var inset: Int                // dp, nhích hai nửa vào trong
        get() = sp.getInt("inset", 39)
        set(v) = sp.edit().putInt("inset", v).apply()
    var keyWidth: Int             // %, bề rộng phím khi tách đôi (thu hẹp mỗi nửa)
        get() = sp.getInt("key_width", 85)
        set(v) = sp.edit().putInt("key_width", v).apply()
    var oneKeyWidth: Int          // %, bề rộng phím ở bàn phím một tay
        get() = sp.getInt("one_key_width", 100)
        set(v) = sp.edit().putInt("one_key_width", v).apply()
    var smartTone: Boolean        // tự bỏ dấu đúng chỗ
        get() = sp.getBoolean("smart_tone", true)
        set(v) = sp.edit().putBoolean("smart_tone", v).apply()
    var oldToneStyle: Boolean     // true: hòa, thúy · false: hoà, thuý
        get() = sp.getBoolean("old_tone_style", true)
        set(v) = sp.edit().putBoolean("old_tone_style", v).apply()
    var dupMiddle: Boolean
        get() = sp.getBoolean("dup_middle", true)
        set(v) = sp.edit().putBoolean("dup_middle", v).apply()
    var oneCurved: Boolean
        get() = sp.getBoolean("one_curved", true)
        set(v) = sp.edit().putBoolean("one_curved", v).apply()
    var oneLift: Int              // dp
        get() = sp.getInt("one_lift", 0)
        set(v) = sp.edit().putInt("one_lift", v).apply()
    var oneLeft: Boolean          // nhớ bên của lần dùng một tay trước
        get() = sp.getBoolean("one_left", false)
        set(v) = sp.edit().putBoolean("one_left", v).apply()
    var vibrateStrength: Int      // 0..100, 0 = tắt rung
        get() = sp.getInt("vibrate_strength", 88)
        set(v) = sp.edit().putInt("vibrate_strength", v).apply()
    var autoPosture: Boolean      // tự đổi bàn phím theo tư thế máy
        get() = sp.getBoolean("auto_posture", true)
        set(v) = sp.edit().putBoolean("auto_posture", v).apply()

    /** Xoá mọi tuỳ chỉnh để dùng lại giá trị mặc định (giữ bên một tay đã nhớ). */
    fun resetDefaults() {
        val side = oneLeft
        sp.edit().clear().putInt("defaults_version", 2).apply()
        oneLeft = side
    }

    fun layoutConfig(dp: Float) = LayoutConfig(
        curvature = curvature / 100f,
        keySize = keySize * dp,
        lift = lift * dp,
        inset = inset * dp,
        keyWidth = keyWidth / 100f,
        oneKeyWidth = oneKeyWidth / 100f,
        dupMiddle = dupMiddle,
        oneCurved = oneCurved,
        oneLift = oneLift * dp,
        dp = dp,
    )
}
