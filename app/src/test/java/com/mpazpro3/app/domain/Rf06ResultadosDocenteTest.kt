package com.mpazpro3.app.domain

import com.mpazpro3.app.model.Actividad
import com.mpazpro3.app.model.Alternativa
import com.mpazpro3.app.model.Asignacion
import com.mpazpro3.app.model.Asignatura
import com.mpazpro3.app.model.Contenido
import com.mpazpro3.app.model.Dificultad
import com.mpazpro3.app.model.EstadoAprobacion
import com.mpazpro3.app.model.EstadoAvance
import com.mpazpro3.app.model.Estudiante
import com.mpazpro3.app.model.RespuestaEstudiante
import com.mpazpro3.app.model.SeguimientoAvance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Rf06ResultadosDocenteTest {

    private val curso = "3° básico A"

    private val estudiantes = listOf(
        Estudiante(id = "EST-01", nombreFicticio = "Alumna Uno", curso = curso),
        Estudiante(id = "EST-02", nombreFicticio = "Alumno Dos", curso = curso),
        Estudiante(id = "EST-99", nombreFicticio = "Otro Curso", curso = "3° básico B")
    )

    private val contenidos = listOf(
        Contenido(
            id = "C1",
            asignatura = Asignatura.MATEMATICA,
            unidad = "Unidad 1: Números",
            objetivoAprendizaje = "OA 1",
            ejeCurricular = "Números y operaciones",
            recursoReferencia = "res_c1",
            estado = EstadoAprobacion.APROBADO
        ),
        Contenido(
            id = "C2",
            asignatura = Asignatura.LENGUAJE,
            unidad = "Unidad 2: Lectura",
            objetivoAprendizaje = "OA 2",
            ejeCurricular = "Lectura",
            recursoReferencia = "res_c2",
            estado = EstadoAprobacion.APROBADO
        )
    )

    private val asignaciones = listOf(
        Asignacion(id = "A1", curso = curso, contenidoId = "C1", maxIntentosEvaluacion = 2),
        Asignacion(id = "A2", curso = curso, contenidoId = "C2", maxIntentosEvaluacion = 3)
    )

    private val actividades = listOf(
        Actividad(
            id = "D1",
            contenidoId = "C1",
            enunciado = "Desafío 1",
            alternativas = listOf(Alternativa("A", true), Alternativa("B", false)),
            dificultad = Dificultad.FACIL,
            orden = 1,
            esEvaluacionFinal = false
        ),
        Actividad(
            id = "E1",
            contenidoId = "C1",
            enunciado = "Evaluación 1",
            alternativas = listOf(Alternativa("A", true), Alternativa("B", false)),
            dificultad = Dificultad.MEDIO,
            orden = 1,
            esEvaluacionFinal = true
        ),
        Actividad(
            id = "D2",
            contenidoId = "C2",
            enunciado = "Desafío 2",
            alternativas = listOf(Alternativa("A", true), Alternativa("B", false)),
            dificultad = Dificultad.FACIL,
            orden = 1,
            esEvaluacionFinal = false
        )
    )

    @Test
    fun rf06_revisaAvanceGeneralYFilasDelCursoConDatosParcialesYSinInicio() {
        val seguimientos = listOf(
            // En C1 hay 3 pasos (material + D1 + E1): material (1) + D1 (1) = 2/3
            SeguimientoAvance(
                estudianteId = "EST-01",
                contenidoId = "C1",
                materialRevisado = true,
                respuestas = listOf(RespuestaEstudiante("D1", 0, esCorrecta = true, numeroIntento = 1)),
                intentosEvaluacionUsados = 0,
                estado = EstadoAvance.EN_PROGRESO
            ),
            // En C2 hay 2 pasos (material + D2): material (1) + D2 (1) = 2/2 = 1.0 -> promedio = (2/3 + 1) / 2 = 5/6 (83%)
            SeguimientoAvance(
                estudianteId = "EST-01",
                contenidoId = "C2",
                materialRevisado = true,
                respuestas = listOf(RespuestaEstudiante("D2", 1, esCorrecta = false, numeroIntento = 1)),
                intentosEvaluacionUsados = 0,
                estado = EstadoAvance.FINALIZADO
            )
        )

        val avancesEst01 = contenidos.map { contenido ->
            val seg = seguimientos.find { it.estudianteId == "EST-01" && it.contenidoId == contenido.id }
            val acts = actividades.filter { it.contenidoId == contenido.id }
            ReglasProgreso.progresoContenido(seg, acts)
        }
        val progresoGeneralEst01 = ReglasProgreso.progresoGeneral(avancesEst01)
        assertEquals(
            "Avance esperado (2/3 en C1 y 2/2 en C2 -> promedio 5/6)",
            75,
            ReglasProgreso.porcentaje(progresoGeneralEst01)
        )

        val filas = ExportadorResultados.construirFilas(
            curso = curso,
            estudiantes = estudiantes,
            contenidosAsignados = contenidos,
            asignaciones = asignaciones,
            actividades = actividades,
            seguimientos = seguimientos
        )

        // Solo los 2 estudiantes del curso x 2 contenidos = 4 filas
        assertEquals(4, filas.size)

        val filaEst02C1 = filas.first { it.estudianteId == "EST-02" && it.contenido == "Unidad 1: Números" }
        assertEquals(0, filaEst02C1.avancePorcentaje)
        assertEquals("No iniciado", filaEst02C1.estado)
        assertEquals(2, filaEst02C1.maxIntentos)
    }

    @Test
    fun rf06_descargaValidaCsvConNombreLimpioEncabezadosYFilasEsperadas() {
        val filas = ExportadorResultados.construirFilas(
            curso = curso,
            estudiantes = estudiantes.filter { it.id == "EST-01" },
            contenidosAsignados = listOf(contenidos.first()),
            asignaciones = asignaciones,
            actividades = actividades,
            seguimientos = listOf(
                SeguimientoAvance(
                    estudianteId = "EST-01",
                    contenidoId = "C1",
                    materialRevisado = true,
                    respuestas = listOf(
                        RespuestaEstudiante("D1", 0, esCorrecta = true, numeroIntento = 1),
                        RespuestaEstudiante("E1", 1, esCorrecta = false, numeroIntento = 1)
                    ),
                    intentosEvaluacionUsados = 1,
                    estado = EstadoAvance.FINALIZADO
                )
            )
        )

        val csv = ExportadorResultados.generarCsv(filas)
        val nombre = ExportadorResultados.nombreArchivo(curso)
        val lineas = csv.split("\r\n")

        assertEquals("resultados_3basicoA.csv", nombre)
        assertEquals(3, lineas.size) // encabezado + 1 fila + línea vacía final por \r\n
        assertTrue(lineas.first().startsWith("Curso;Estudiante ID;Estudiante;Asignatura;Contenido;Avance (%)"))
        assertEquals(
            "3° básico A;EST-01;Alumna Uno;Matemática;Unidad 1: Números;100;1;1;1;2;Finalizado",
            lineas[1]
        )
    }
}