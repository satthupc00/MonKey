package com.mondiro.monkey

import android.content.Context
import android.content.SharedPreferences

/** Cài đặt người dùng (lưu trong máy). */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("monkey", Context.MODE_PRIVATE)

    var curvature: Int            // 0..100
        get() = sp.getInt("curvature", 70)
        set(v) = sp.edit().putInt("curvature", v).apply()
    var keySize: Int              // dp
        get() = sp.getInt("key_size", 40)
        set(v) = sp.edit().putInt("key_size", v).apply()
    var lift: Int                 // dp
        get() = sp.getInt("lift", 0)
        set(v) = sp.edit().putInt("lift", v).apply()
    var inset: Int                // dp, nhích hai nửa vào trong
        get() = sp.getInt("inset", 16)
        set(v) = sp.edit().putInt("inset", v).apply()
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
        get() = sp.getInt("vibrate_strength", 50)
        set(v) = sp.edit().putInt("vibrate_strength", v).apply()
    var autoPosture: Boolean      // tự đổi bàn phím theo tư thế máy
        get() = sp.getBoolean("auto_posture", true)
        set(v) = sp.edit().putBoolean("auto_posture", v).apply()

    fun layoutConfig(dp: Float) = LayoutConfig(
        curvature = curvature / 100f,
        keySize = keySize * dp,
        lift = lift * dp,
        inset = inset * dp,
        dupMiddle = dupMiddle,
        oneCurved = oneCurved,
        oneLift = oneLift * dp,
        dp = dp,
    )
}
