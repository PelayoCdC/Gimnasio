package com.pelayo.gimnasio.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * El aviso final se programa con AlarmManager, no con una corrutina: así suena
 * aunque el sistema mate el proceso durante el descanso, que es justo cuando
 * más falta hace. La cuenta atrás en pantalla solo actualiza la notificación.
 */
actual class RestTimerNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "descanso"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_EXERCISE = "ejercicio"
        private const val REQUEST_CODE = 4001
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Descanso entre series",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa cuando termina el descanso"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    actual fun canNotify(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            notificationManager.areNotificationsEnabled()
        }

    actual fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    actual fun startRest(totalSeconds: Int, exerciseName: String?) {
        if (totalSeconds <= 0) return
        val triggerAt = SystemClock.elapsedRealtime() + totalSeconds * 1000L
        val pendingIntent = alarmPendingIntent(exerciseName)

        // setExactAndAllowWhileIdle lanza SecurityException si el permiso de
        // alarmas exactas está revocado; en ese caso vale una inexacta, que puede
        // retrasarse pero acaba llegando.
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }

        updateCountdown(totalSeconds)
    }

    actual fun updateCountdown(secondsRemaining: Int) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Descansando")
            .setContentText("Quedan $secondsRemaining s")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    actual fun cancelRest() {
        alarmManager.cancel(alarmPendingIntent(exerciseName = null))
        notificationManager.cancel(NOTIFICATION_ID)
    }

    actual fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun alarmPendingIntent(exerciseName: String?): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            putExtra(EXTRA_EXERCISE, exerciseName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
