package com.violetbit.app

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/averinger/sledilo/releases/latest"

    // ТЕКУЩАЯ ВЕРСИЯ - обновляй при каждом релизе!
    const val CURRENT_VERSION = "0.9"

    data class UpdateInfo(
        val available: Boolean,
        val latestVersion: String = "",
        val releaseUrl: String = "",
        val releaseNotes: String = ""
    )

    fun check(): UpdateInfo {
        return try {
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Sledilo-App")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                Log.w("UpdateChecker", "HTTP ${conn.responseCode}")
                return UpdateInfo(false)
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(text)
            val tag = json.optString("tag_name", "").removePrefix("v")
            val htmlUrl = json.optString("html_url", "")
            val body = json.optString("body", "")

            val newer = isNewer(tag, CURRENT_VERSION)
            Log.d("UpdateChecker", "latest=$tag current=$CURRENT_VERSION newer=$newer")

            UpdateInfo(newer, tag, htmlUrl, body)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "err: ${e.message}")
            UpdateInfo(false)
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val a = latest.split(".").mapNotNull { it.toIntOrNull() }
        val b = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x > y) return true
            if (x < y) return false
        }
        return false
    }
}
