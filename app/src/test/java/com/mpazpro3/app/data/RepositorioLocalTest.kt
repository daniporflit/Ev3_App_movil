package com.mpazpro3.app.data

import com.mpazpro3.app.data.remote.ApiResult
import com.mpazpro3.app.domain.ReglasAcceso
import com.mpazpro3.app.model.EstadoAprobacion
import com.mpazpro3.app.model.EstadoAvance
import com.mpazpro3.app.model.EstadoValidacion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.mpazpro3.app.domain.GeneradorInforme
import org.junit.Before
import org.junit.Test

class RepositorioLocalLogicaTest {

    private lateinit var almacen: AlmacenLocalMemoria
    private lateinit var repo: RepositorioLocal

    @Before
    fun setUp() {
        almacen = AlmacenLocalMemoria()
        repo = RepositorioLocal(
            almacen = almacen,
            ahora = { 1_700_000_000_000L },
            fechaDeHoy = { "24-11-2026" }
        )
    }

    @Test
    fun iniciarSesion_credencialesValidasEInvalidas() = runTest {
        val docente = DatosIniciales.usuarios.first { it.usuario.isNotBlank() }
        val ok = repo.iniciarSesion(docente.usuario.uppercase(), "clave_invalida_para_forzar_rechazo")
        assertTrue(ok is ResultadoLogin.Rechazado)

        // Usuario con hash coincidente
        val usuarioConHash = docente.copy(claveHash = ReglasAcceso.hashClave("1234"))
        almacen = AlmacenLocalMemoria(usuarios = listOf(usuarioConHash))
        repo = RepositorioLocal(almacen)

        val exito = repo.iniciarSesion("  ${docente.usuario}  ", "1234")
        assertTrue(exito is ResultadoLogin.Exito)
        exito as ResultadoLogin.Exito
        assertEquals(docente.id, exito.usuario.id)
        assertTrue(exito.modoLocal)
        assertFalse(exito.enLinea)
    }

    @Test
    fun aprobarYRechazarContenido_respetanReglasYObservacion() = runTest {
        val pendiente = almacen.datos.contenidos.firstOrNull { it.estado == EstadoAprobacion.PENDIENTE }
            ?: return@runTest

        // Rechazar sin observación debe fallar
        val rechazoVacio = repo.rechazarContenido(pendiente.id, "   ")
        assertFalse(rechazoVacio.exito)

        // Rechazar con motivo válido actualiza el estado
        val rechazoOk = repo.rechazarContenido(pendiente.id, "Falta ajustar actividad")
        assertTrue(rechazoOk.exito)
        assertEquals(
            EstadoAprobacion.RECHAZADO,
            almacen.datos.contenidos.first { it.id == pendiente.id }.estado
        )
    }

    @Test
    fun asignarAlCurso_bloqueaCursoAjenoSiSesionEsDocenteDeOtroCurso() = runTest {
        val docente = DatosIniciales.usuarios.first { it.perfil == com.mpazpro3.app.model.Perfil.DOCENTE }
            .copy(curso = "3° básico A", claveHash = ReglasAcceso.hashClave("1234"))
        almacen = AlmacenLocalMemoria(usuarios = listOf(docente))
        repo = RepositorioLocal(almacen)
        repo.iniciarSesion(docente.usuario, "1234")

        val aprobadoId = almacen.datos.contenidos.first { it.estado == EstadoAprobacion.APROBADO }.id
        val resAjeno = repo.asignarAlCurso("3° básico B", listOf(aprobadoId), 2)
        assertFalse(resAjeno.exito)
        assertEquals(RepositorioLocal.MENSAJE_CURSO_AJENO, resAjeno.mensaje)

        val resPropio = repo.asignarAlCurso("3° básico A", listOf(aprobadoId), 2)
        assertTrue(resPropio.exito)
    }

    @Test
    fun confirmarMaterialYResponderDesafio_actualizanSeguimiento() = runTest {
        val asignacion = almacen.datos.asignaciones.first()
        val estudiante = almacen.datos.estudiantes.first { it.curso == asignacion.curso }
        val desafio = almacen.datos.actividades.first {
            it.contenidoId == asignacion.contenidoId && !it.esEvaluacionFinal
        }
        val indiceCorrecto = desafio.alternativas.indexOfFirst { it.esCorrecta }

        val matRes = repo.confirmarMaterialRevisado(estudiante.id, asignacion.contenidoId)
        assertTrue(matRes.exito)

        val respDesafio = repo.responderDesafio(estudiante.id, desafio.id, indiceCorrecto)
        assertEquals(true, respDesafio.esCorrecta)

        val seg = almacen.datos.seguimientos.first {
            it.estudianteId == estudiante.id && it.contenidoId == asignacion.contenidoId
        }
        assertTrue(seg.materialRevisado)
        assertEquals(EstadoAvance.EN_PROGRESO, seg.estado)
        assertTrue(seg.respuestas.any { it.actividadId == desafio.id && it.esCorrecta })
    }

    @Test
    fun descargarResultadosCsv_sinEstudiantesDevuelveError404_yConDatosGeneraCsv() = runTest {
        val vacio = repo.descargarResultadosCsv("Curso Inexistente 9°")
        assertTrue(vacio is ApiResult.Error)
        assertEquals(404, (vacio as ApiResult.Error).codigo)

        val cursoConDatos = almacen.datos.estudiantes.first().curso
        val csvRes = repo.descargarResultadosCsv(cursoConDatos)
        assertTrue(csvRes is ApiResult.Exito)
        val csv = (csvRes as ApiResult.Exito).datos
        assertTrue(csv.contains("Curso;Estudiante ID;Estudiante"))
    }

    @Test
    fun generarYValidarInforme_cambiaEstadoABorradorYLuegoAValidado() = runTest {
        val seguimientoConRespuestas = almacen.datos.seguimientos.firstOrNull { it.respuestas.isNotEmpty() }
            ?: return@runTest
        val estId = seguimientoConRespuestas.estudianteId

        val genRes = repo.generarInforme(estId)
        assertTrue(genRes.exito)

        val informe = almacen.datos.informes.firstOrNull { it.estudianteId == estId }
        assertNotNull(informe)
        val informeNoNulo = requireNotNull(informe)
        assertEquals(EstadoValidacion.BORRADOR, informeNoNulo.estado)
        assertEquals("24-11-2026", informeNoNulo.fecha)

        val valRes = repo.validarInforme(informeNoNulo.id)
        assertTrue(valRes.exito)
        assertEquals(
            EstadoValidacion.VALIDADO,
            almacen.datos.informes.first { it.id == informeNoNulo.id }.estado
        )

        val regenRes = repo.generarInforme(estId)
        assertFalse(regenRes.exito)
        assertEquals(GeneradorInforme.MENSAJE_YA_VALIDADO, regenRes.mensaje)
    }

    @Test
    fun restablecerDatos_invocaAlmacenRestablecerConMockK() = runTest {
        val almacenMock = mockk<AlmacenLocal>(relaxed = true)
        coEvery { almacenMock.restablecer() } returns Unit
        val repoMock = RepositorioLocal(almacenMock)

        val res = repoMock.restablecerDatos()

        assertTrue(res.exito)
        coVerify(exactly = 1) { almacenMock.restablecer() }
    }
}