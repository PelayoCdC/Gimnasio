package com.pelayo.gimnasio.platform

/**
 * Avisa del fin del descanso entre series.
 *
 * El aviso final NO lo dispara la app: lo programa el sistema, para que suene
 * aunque Android haya matado el proceso o el usuario se haya ido a otra
 * pantalla. La cuenta atrás que se ve por dentro es solo cosmética.
 */
expect class RestTimerNotifier {
    /** true si el sistema permite mostrar notificaciones. */
    fun canNotify(): Boolean

    /**
     * true si se pueden programar avisos a una hora exacta. Sin este permiso el
     * aviso sigue llegando, pero el sistema puede retrasarlo unos minutos.
     */
    fun canScheduleExactAlarms(): Boolean

    /** Programa el aviso de fin y muestra la cuenta atrás. */
    fun startRest(totalSeconds: Int, exerciseName: String?)

    /** Refresca el tiempo restante en la notificación de cuenta atrás. */
    fun updateCountdown(secondsRemaining: Int)

    /** Cancela el aviso programado y retira la notificación. */
    fun cancelRest()

    /** Abre los ajustes del sistema donde se concede el permiso de alarmas exactas. */
    fun openExactAlarmSettings()
}
