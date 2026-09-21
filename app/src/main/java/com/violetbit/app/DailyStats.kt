package com.violetbit.app

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailyStats {

    private const val PREFS = "sledilo_daily"
    private const val KEY = "history"
    private const val MAX_DAYS = 60

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun saveToday(context: Context, totalMillis: Long, topApps: List<AppUsage>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val history = load(context).toMutableMap()

        val appsJson = JSONObject()
        topApps.take(5).forEach { a ->
            appsJson.put(a.appName, a.usageMillis)
        }

        history[today()] = DayEntry(totalMillis, topApps.take(5))

        // Обрезаем до MAX_DAYS самых новых
        val trimmed = history.entries
            .sortedByDescending { it.key }
            .take(MAX_DAYS)
            .associate { it.key to it.value }

        val json = JSONObject()
        trimmed.forEach { (date, entry) ->
            val obj = JSONObject()
            obj.put("total", entry.totalMillis)
            val apps = JSONObject()
            entry.topApps.forEach { apps.put(it.appName, it.usageMillis) }
            obj.put("apps", apps)
            json.put(date, obj)
        }

        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun load(context: Context): Map<String, DayEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY, null) ?: return emptyMap()
        return try {
            val json = JSONObject(str)
            val result = mutableMapOf<String, DayEntry>()
            json.keys().forEach { date ->
                val obj = json.getJSONObject(date)
                val total = obj.optLong("total", 0L)
                val appsArr = mutableListOf<AppUsage>()
                obj.optJSONObject("apps")?.let { apps ->
                    apps.keys().forEach { name ->
                        appsArr.add(AppUsage(name, name, apps.getLong(name)))
                    }
                }
                result[date] = DayEntry(total, appsArr.sortedByDescending { it.usageMillis })
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getLastDays(context: Context, count: Int): List<Pair<String, Long>> {
        val history = load(context)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val result = mutableListOf<Pair<String, Long>>()
        for (i in 0 until count) {
            val dateStr = fmt.format(cal.time)
            val entry = history[dateStr]
            result.add(dateStr to (entry?.totalMillis ?: 0L))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result.reversed()
    }

    data class DayEntry(
        val totalMillis: Long,
        val topApps: List<AppUsage>
    )
}
