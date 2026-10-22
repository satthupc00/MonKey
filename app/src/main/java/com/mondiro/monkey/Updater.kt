package com.mondiro.monkey

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Tự cập nhật từ GitHub Releases của repo MonKey. */
object Updater {
    private const val REPO = "satthupc00/MonKey"

    class Release(val version: String, val notes: String, val apkUrl: String?)

    /** Chạy ở luồng nền. Trả về null nếu không kết nối được. */
    fun fetchLatest(): Release? = try {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        if (conn.responseCode != 200) null else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val url = assets.getJSONObject(i).optString("browser_download_url")
                if (url.endsWith(".apk")) { apk = url; break }
            }
            Release(json.getString("tag_name").removePrefix("v"), json.optString("body"), apk)
        }
    } catch (_: Exception) {
        null
    }

    /** "1.2.0" > "1.1.9" ? */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Tải APK về bộ nhớ đệm (luồng nền). */
    fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "MonKey-update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
