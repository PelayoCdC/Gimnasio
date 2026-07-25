package com.pelayo.gimnasio.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Lanza la notificación del recordatorio; WorkManager decide cuándo. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    companion object {
        private const val CHANNEL_ID = "recordatorios"
    }

    override fun doWork(): Result {
        val title = inputData.getString(ReminderScheduler.KEY_TITLE) ?: return Result.success()
        val text = inputData.getString(ReminderScheduler.KEY_TEXT).orEmpty()

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Recordatorios",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Avisos de entrenamiento y pesaje" },
        )

        if (!manager.areNotificationsEnabled()) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            manager.notify(id.hashCode(), notification)
            Result.success()
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: no es un fallo que merezca reintento.
            Result.success()
        }
    }
}
