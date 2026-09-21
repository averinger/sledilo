package com.violetbit.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

object UsageTracker {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getTodayUsage(context: Context): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val start = startOfDay()
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return emptyList()

        val agg = mutableMapOf<String, Long>()
        for (s in stats) {
            if (s.totalTimeInForeground > 0) {
                agg[s.packageName] = (agg[s.packageName] ?: 0L) + s.totalTimeInForeground
            }
        }

        val result = mutableListOf<AppUsage>()
        for ((pkg, time) in agg) {
            if (time < 60_000) continue
            if (pkg == context.packageName) continue
            if (pkg == "com.android.systemui") continue
            if (pkg.startsWith("com.android.launcher")) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                result.add(AppUsage(pkg, label, time))
            } catch (e: PackageManager.NameNotFoundException) {
                // skip
            }
        }

        return result.sortedByDescending { it.usageMillis }
    }

    /**
     * Почасовая статистика использования приложения (24 значения — минуты в каждом часе).
     */
    fun getHourlyUsage(context: Context, packageName: String): LongArray {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfDay()
        val now = System.currentTimeMillis()

        val hourly = LongArray(24)
        val events = usm.queryEvents(start, now) ?: return hourly
        val event = UsageEvents.Event()

        var lastResume = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResume = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastResume > 0 && event.timeStamp > lastResume) {
                        var remaining = event.timeStamp - lastResume
                        var cursor = lastResume
                        while (remaining > 0) {
                            val cal = Calendar.getInstance().apply { timeInMillis = cursor }
                            val hour = cal.get(Calendar.HOUR_OF_DAY)
                            val nextHour = Calendar.getInstance().apply {
                                timeInMillis = cursor
                                set(Calendar.MINUTE, 60)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            val chunk = minOf(remaining, nextHour - cursor)
                            hourly[hour] += chunk
                            remaining -= chunk
                            cursor += chunk
                        }
                        lastResume = 0
                    }
                }
            }
        }

        return hourly
    }

    fun formatDuration(millis: Long): String {
        val totalMin = millis / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h} ч ${m} мин"
            h > 0 -> "${h} ч"
            else -> "${m} мин"
        }
    }
}
