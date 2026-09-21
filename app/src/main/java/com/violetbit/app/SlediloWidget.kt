package com.violetbit.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

class SlediloWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Log.d("SlediloWidget", "onUpdate: ${ids.size} widgets")
        for (id in ids) {
            try { updateWidget(context, mgr, id) }
            catch (e: Exception) { Log.e("SlediloWidget", "update: ${e.message}", e) }
        }
    }

    override fun onEnabled(context: Context) {
        Log.d("SlediloWidget", "onEnabled")
    }

    companion object {
        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SlediloWidget::class.java))
                for (id in ids) {
                    try { updateWidget(context, mgr, id) } catch (e: Exception) { }
                }
            } catch (e: Exception) { }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val v = RemoteViews(context.packageName, R.layout.widget_layout)

            if (!UsageTracker.hasPermission(context)) {
                v.setTextViewText(R.id.widget_total, "нет доступа")
                v.setTextViewText(R.id.widget_name1, "Открой приложение")
                v.setTextViewText(R.id.widget_name2, "и дай разрешение")
                v.setTextViewText(R.id.widget_name3, "")
                attachClick(context, v)
                mgr.updateAppWidget(widgetId, v)
                return
            }

            val apps = UsageTracker.getTodayUsage(context)
            val settings = SettingsStorage(context).load()
            val threshold = settings.thresholdHours * 60L * 60L * 1000L
            val total = apps.sumOf { it.usageMillis }
            val th = total / 3_600_000
            val tm = (total % 3_600_000) / 60_000
            v.setTextViewText(R.id.widget_total, "Всего: ${th}ч ${tm}м")

            val top3 = apps.take(3)
            val nameIds = listOf(R.id.widget_name1, R.id.widget_name2, R.id.widget_name3)
            nameIds.forEachIndexed { i, id ->
                if (i < top3.size) {
                    val a = top3[i]
                    val h = a.usageMillis / 3_600_000
                    val m = (a.usageMillis % 3_600_000) / 60_000
                    val timeStr = if (h > 0) "${h}ч ${m}м" else "${m}м"
                    v.setTextViewText(id, "${a.appName} — $timeStr")
                    val isAbuser = a.usageMillis >= threshold
                    v.setTextColor(id, if (isAbuser) 0xFFFF5555.toInt() else 0xFFE0B0FF.toInt())
                } else {
                    v.setTextViewText(id, "")
                }
            }

            attachClick(context, v)
            mgr.updateAppWidget(widgetId, v)
        }

        private fun attachClick(context: Context, v: RemoteViews) {
            val intent = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val p = PendingIntent.getActivity(context, 0, intent, flags)
            v.setOnClickPendingIntent(R.id.widget_title, p)
            v.setOnClickPendingIntent(R.id.widget_total, p)
        }
    }
}
