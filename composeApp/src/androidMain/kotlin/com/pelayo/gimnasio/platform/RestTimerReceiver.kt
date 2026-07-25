package com.pelayo.gimnasio.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Lanza el aviso de fin de descanso. Al vivir fuera del proceso de la app, suena
 * aunque Android la haya matado mientras descansabas.
 */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        vibrate(context)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val exerciseName = intent.getStringExtra(RestTimerNotifier.EXTRA_EXERCISE)
        val notification = NotificationCompat.Builder(context, RestTimerNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("¡Descanso terminado!")
            .setContentText(exerciseName?.let { "Siguiente serie: $it" } ?: "A por la siguiente serie")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(RestTimerNotifier.NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones queda la vibración, que ya ha sonado.
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 150, 200), -1))
    }
}
