package com.mpazpro3.app.data

import com.mpazpro3.app.model.Resultado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorConMockKTest {

    @Test
    fun enModoServidor_delegaAprobacionALaFuenteRemota() = runTest {
        val pref = PreferenciaModoMemoria(local = false)
        val remota = mockk<FuenteDatos>(relaxed = true)
        val local = mockk<FuenteDatos>(relaxed = true)
        val lecturas = mockk<LecturasObservables>(relaxed = true)

        coEvery { remota.aprobarContenido("C1", "OK") } returns Resultado.ok("Aprobado en servidor")

        val selector = SelectorFuente(pref, remota, local, lecturas, lecturas)
        val resultado = selector.aprobarContenido("C1", "OK")

        assertTrue(resultado.exito)
        coVerify(exactly = 1) { remota.aprobarContenido("C1", "OK") }
        coVerify(exactly = 0) { local.aprobarContenido(any(), any()) }
    }
}