package com.noctra.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latest: String,
    val url: String,
    val newer: Boolean
)

object UpdateChecker {
    const val REPO = "buzzyfluxon/noctra"

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split(".").map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val code = conn.responseCode
                if (code == 404) throw IllegalStateException("No release published yet.")
                if (code != 200) throw IllegalStateException("GitHub answered $code.")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.getString("tag_name")
                val assets = json.optJSONArray("assets")
                var url = json.optString("html_url", "")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) {
                            url = a.getString("browser_download_url")
                            break
                        }
                    }
                }
                UpdateInfo(tag.removePrefix("v"), url, isNewer(tag, currentVersion))
            } finally {
                conn.disconnect()
            }
        }
    }
}
