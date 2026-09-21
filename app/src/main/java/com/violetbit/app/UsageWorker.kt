package com.violetbit.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar

class UsageWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): WorkResult {
        val ctx = applicationContext
        if (!UsageTracker.hasPermission(ctx)) return WorkResult.success()

        val settings = SettingsStorage(ctx).load()
        val thresholdMillis = settings.thresholdHours * 60L * 60L * 1000L
        val apps = UsageTracker.getTodayUsage(ctx)
        val abusers = apps.filter { it.usageMillis >= thresholdMillis }
        if (abusers.isEmpty()) return WorkResult.success()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("usage_monitor", "Следило", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Слежу за экранным временем"
            nm.createNotificationChannel(ch)
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        abusers.forEachIndexed { i, app ->
            val hours = app.usageMillis / 3_600_000
            val text = pickMessage(app.appName, hours, hour, settings)
            val titlePrefix = if (settings.noProfanity) "Внимание" else "Пиздец"
            val notif = NotificationCompat.Builder(ctx, "usage_monitor")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$titlePrefix: ${app.appName} — ${hours} ч")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            nm.notify(2000 + i, notif)
        }

        return WorkResult.success()
    }

    private fun pickMessage(app: String, hours: Long, hour: Int, settings: AppSettings): String {
        if (settings.useCustomPhrases && settings.customPhrases.isNotEmpty()) {
            return settings.customPhrases.random()
                .replace("{app}", app)
                .replace("{hours}", hours.toString())
        }

        return if (settings.noProfanity) {
            pickSoft(app, hours, hour)
        } else {
            pickHard(app, hours, hour)
        }
    }

    private fun pickHard(app: String, hours: Long, hour: Int): String {
        val day = listOf(
            "Ты в \"$app\" уже $hours часов, братан. Может хватит?",
            "\"$app\" — $hours ч. Ты там живёшь, что ли?",
            "$hours часов в \"$app\". У тебя вообще жизнь есть?",
            "Э, лошара, закрой \"$app\", там нихуя нового не появилось",
            "Столько времени в \"$app\" — а толку ноль. Иди делом займись",
            "\"$app\" $hours ч. Руки мыл сегодня? Ну тогда ладно",
            "Хуя ты залип в \"$app\". Сходи прогуляйся, воздухом подыши",
            "Уже $hours ч в \"$app\". Телефон сам себя не разрядит, отдохни"
        )
        val evening = listOf(
            "\"$app\" $hours ч. Вечер, а ты всё там же. Помойся уже",
            "Снова \"$app\"? $hours ч, братан. Совесть есть?",
            "Поздний вечер, а ты в \"$app\" $hours ч. Спать не?",
            "\"$app\" $hours ч. Тебя там медом намазано?",
            "$hours часов в \"$app\". Иди поспи, чудо"
        )
        val night = listOf(
            "Ночь, а ты в \"$app\" $hours ч. Ты вообще нормальный?",
            "\"$app\" в 3 ночи. $hours ч. Ложись, еб твою мать",
            "Полночь прошла, а ты всё \"$app\" терроризируешь. $hours ч",
            "Ты не спишь и $hours ч в \"$app\". Завтра хуёво будет"
        )
        return when {
            hour in 0..5 -> night.random()
            hour in 20..23 -> evening.random()
            else -> day.random()
        }
    }

    private fun pickSoft(app: String, hours: Long, hour: Int): String {
        val day = listOf(
            "Ты в \"$app\" уже $hours часов. Может, перерыв?",
            "\"$app\" — $hours ч. Стоит отдохнуть.",
            "$hours часов в \"$app\". Не забывай про другие дела.",
            "Закрой \"$app\", там ничего нового. Прогуляйся.",
            "Столько времени в \"$app\". Сделай паузу.",
            "\"$app\" $hours ч. Вода и разминка не помешают.",
            "Ты залип в \"$app\" на $hours ч. Проветрись.",
            "Уже $hours ч в \"$app\". Дай телефону отдохнуть."
        )
        val evening = listOf(
            "\"$app\" $hours ч. Вечер, отдохни от экрана.",
            "Снова \"$app\"? $hours ч. Пора расслабиться.",
            "Поздний вечер, а ты в \"$app\" $hours ч. Готовься ко сну.",
            "\"$app\" $hours ч. Не забудь поужинать и отдохнуть.",
            "$hours часов в \"$app\". Лучше поспать."
        )
        val night = listOf(
            "Ночь, а ты в \"$app\" $hours ч. Пора спать.",
            "\"$app\" ночью. $hours ч. Отдых важен.",
            "Полночь прошла, а ты всё в \"$app\". $hours ч.",
            "Ты не спишь и $hours ч в \"$app\". Завтра будет тяжело."
        )
        return when {
            hour in 0..5 -> night.random()
            hour in 20..23 -> evening.random()
            else -> day.random()
        }
    }
}
