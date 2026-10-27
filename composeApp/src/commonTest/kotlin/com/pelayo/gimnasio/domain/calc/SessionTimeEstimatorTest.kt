package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.TrainingPrescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionTimeEstimatorTest {

    private val hipertrofia = TrainingPrescription.forGoal(RoutineGoal.HIPERTROFIA)

    @Test
    fun el_coste_de_un_ejercicio_suma_series_descansos_y_transicion() {
        val expected = hipertrofia.sets * SessionTimeEstimator.SECONDS_PER_SET +
            (hipertrofia.sets - 1) * hipertrofia.restSeconds +
            SessionTimeEstimator.TRANSITION_SECONDS
        assertEquals(expected, SessionTimeEstimator.secondsPerExercise(hipertrofia))
    }

    @Test
    fun no_cuenta_descanso_despues_de_la_ultima_serie() {
        val single = hipertrofia.copy(sets = 1)
        assertEquals(
            SessionTimeEstimator.SECONDS_PER_SET + SessionTimeEstimator.TRANSITION_SECONDS,
            SessionTimeEstimator.secondsPerExercise(single),
        )
    }

    @Test
    fun con_mas_minutos_caben_mas_ejercicios() {
        val short = SessionTimeEstimator.exercisesThatFit(30, hipertrofia, 3..8)
        val long = SessionTimeEstimator.exercisesThatFit(90, hipertrofia, 3..8)
        assertTrue(long > short, "90 min deben dar para más que 30 ($long vs $short)")
    }

    @Test
    fun el_resultado_respeta_el_rango_permitido() {
        assertEquals(3, SessionTimeEstimator.exercisesThatFit(5, hipertrofia, 3..8))
        assertEquals(8, SessionTimeEstimator.exercisesThatFit(600, hipertrofia, 3..8))
    }

    @Test
    fun ida_y_vuelta_coherente() {
        val count = SessionTimeEstimator.exercisesThatFit(60, hipertrofia, 3..8)
        val minutes = SessionTimeEstimator.minutesFor(count, hipertrofia)
        // La estimación de vuelta debe quedar razonablemente cerca de lo pedido.
        assertTrue(minutes in 40..80, "60 min pedidos → $count ejercicios → $minutes min")
    }
}
