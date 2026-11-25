package com.mpazpro3.app.domain

import com.mpazpro3.app.model.Actividad
import com.mpazpro3.app.model.Alternativa
import com.mpazpro3.app.model.Dificultad
import com.mpazpro3.app.model.RespuestaEstudiante
import com.mpazpro3.app.model.SeguimientoAvance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasProgresoTest {

    private fun actividad(id: String, dificultad: Dificultad, orden: Int, esFinal: Boolean = false) = Actividad(
        id = id,
        contenidoId = "C",
        enunciado = "Pregunta $id",
        alternativas = listOf(Alternativa("a", true), Alternativa("b", false)),
        dificultad = dificultad,
        orden = orden,
        esEvaluacionFinal = esFinal
    )

    private fun respuesta(id: String, correcta: Boolean, intento: Int) =
        RespuestaEstudiante(id, if (correcta) 0 else 1, correcta, intento)

    private val actividades = listOf(
        actividad("D1", Dificultad.FACIL, 1),
        actividad("D2", Dificultad.MEDIO, 2),
        actividad("E1", Dificultad.MEDIO, 1, esFinal = true),
        actividad("E2", Dificultad.MEDIO, 2, esFinal = true)
    )

    @Test
    fun progresoContenido_sinSeguimientoEsCero() {
        assertEquals(0f, ReglasProgreso.progresoContenido(null, actividades), 0.0001f)
    }

    @Test
    fun progresoContenido(seguimiento: SeguimientoAvance?, actividadesDelContenido: List<Actividad>): Float {
        if (seguimiento == null || actividadesDelContenido.isEmpty()) return 0f

        val desafios = actividadesDelContenido.filter { !it.esEvaluacionFinal }
        val evalFinal = actividadesDelContenido.filter { it.esEvaluacionFinal }
        val totalAsignadas = desafios.size + evalFinal.size
        if (totalAsignadas == 0) return 0f

        val idsRespondidos = seguimiento.respuestas.map { it.actividadId }.toSet()
        val desafiosRespondidos = desafios.count { it.id in idsRespondidos }
        val evalRespondidas = if (seguimiento.intentosEvaluacionUsados > 0) {
            evalFinal.count { it.id in idsRespondidos }
        } else {
            0
        }

        return ((desafiosRespondidos + evalRespondidas).toFloat() / totalAsignadas).coerceIn(0f, 1f)
    }

    @Test
    fun progresoContenido_repetirUnDesafioNoSumaDeNuevo() {
        val seguimiento = SeguimientoAvance(
            estudianteId = "E", contenidoId = "C",
            respuestas = listOf(respuesta("D1", false, 1), respuesta("D1", true, 2))
        )
        assertEquals(1f / 4f, ReglasProgreso.progresoContenido(seguimiento, actividades), 0.0001f)
    }

    @Test
    fun progresoGeneral_esElPromedio() {
        assertEquals(0.5f, ReglasProgreso.progresoGeneral(listOf(1f, 0f)), 0.0001f)
        assertEquals(0f, ReglasProgreso.progresoGeneral(emptyList()), 0.0001f)
    }

    @Test
    fun porcentaje_redondeaYLimita() {
        assertEquals(60, ReglasProgreso.porcentaje(0.6f))
        assertEquals(100, ReglasProgreso.porcentaje(1.7f))
        assertEquals(0, ReglasProgreso.porcentaje(-0.2f))
    }

    @Test
    fun respuestasVigentes_ultimoIntentoDeCadaDesafioYUltimaEvaluacion() {
        val todas = listOf(
            respuesta("D1", false, 1), respuesta("D1", true, 2),
            respuesta("D2", false, 1),
            respuesta("E1", false, 1), respuesta("E2", false, 1),
            respuesta("E1", true, 2), respuesta("E2", true, 2)
        )
        val vigentes = ReglasProgreso.respuestasVigentes(todas, actividades)
        assertEquals(listOf("D1", "D2", "E1", "E2"), vigentes.map { it.actividadId })
        assertEquals(listOf(2, 1, 2, 2), vigentes.map { it.numeroIntento })
        assertEquals(3, ReglasProgreso.contarCorrectas(vigentes))
    }

    @Test
    fun respuestasVigentes_ignoraActividadesDeOtroContenido() {
        val vigentes = ReglasProgreso.respuestasVigentes(listOf(respuesta("OTRA", true, 1)), actividades)
        assertTrue(vigentes.isEmpty())
    }
}
