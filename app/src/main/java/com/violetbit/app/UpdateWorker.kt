package com.violetbit.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {

    override fun doWork(): WorkResult {
        val info = UpdateChecker.check()
        if (!info.available) return WorkResult.success()

        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                "update_channel",
                "Обновления",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            ch.description = "Проверка новых версий Следило"
            nm.createNotificationChannel(ch)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
        val pending = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, "update_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Обновление Следило ${info.latestVersion}")
            .setContentText("Тапни чтобы скачать новую версию")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Доступна версия ${info.latestVersion}\n\nТапни, чтобы открыть страницу загрузки."
            ))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        nm.notify(3000, notif)
        return WorkResult.success()
    }
}
