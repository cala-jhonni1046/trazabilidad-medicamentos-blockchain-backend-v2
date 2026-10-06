package com.medichain.modules.enlacecuit;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario EnlaceCuitTransicionesTest en MediChain.
 * Métodos de dominio de EnlaceCuit sin Spring: recorrido feliz y
 * TRANSICION_INVALIDA en las transiciones no permitidas.
 */
class EnlaceCuitTransicionesTest {

    private final Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
    private final Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
    private final Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
    private final InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);

    /** Verifica que la acción falle con TRANSICION_INVALIDA. */
    private void invalida(Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("PENDIENTE_EMPRESAS → aceptan las dos → tomar → aprobar → suspender → rehabilitar")
    void recorridoFeliz() {
        EnlaceCuit circuito = DatosDePrueba.circuito(laboratorio, distribuidora, farmacia);
        assertFalse(circuito.aceptar(distribuidora));
        assertTrue(circuito.aceptar(farmacia));
        assertEquals(EstadoEnlaceCuit.PENDIENTE_INSPECTOR, circuito.getEstado());
        circuito.tomar(inspector);
        circuito.aprobar(inspector);
        assertEquals(EstadoEnlaceCuit.APROBADO, circuito.getEstado());
        circuito.suspenderManual("motivo");
        assertFalse(circuito.getSuspendidoPorEmpresa());
        circuito.rehabilitarManual();
        assertEquals(EstadoEnlaceCuit.APROBADO, circuito.getEstado());
    }

    @Test
    @DisplayName("RECHAZADO es final: no se acepta, no se toma, no se aprueba")
    void rechazadoEsFinal() {
        EnlaceCuit circuito = DatosDePrueba.circuito(laboratorio, distribuidora, farmacia);
        circuito.rechazarPorEmpresa(distribuidora, "motivo");
        assertEquals(OrigenRechazo.DISTRIBUIDOR, circuito.getRechazadoPor());
        invalida(() -> circuito.aceptar(farmacia));
        invalida(() -> circuito.tomar(inspector));
        invalida(() -> circuito.aprobar(inspector));
        invalida(() -> circuito.suspenderManual("x"));
    }

    @Test
    @DisplayName("Tomar dos veces o aprobar sin ser el revisor → inválida")
    void revisor() {
        EnlaceCuit circuito = DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia);
        invalida(() -> circuito.aprobar(inspector));
        circuito.tomar(DatosDePrueba.inspector(Provincia.CORDOBA));
        invalida(() -> circuito.tomar(inspector));
        invalida(() -> circuito.asignar(inspector));
        invalida(() -> circuito.rechazarPorInspector(inspector, "x"));
    }

    @Test
    @DisplayName("Cascada y manual no se pisan: la cascada solo toca APROBADO y el manual no rehabilita cascada")
    void cascadaYManual() {
        EnlaceCuit circuito = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia);
        circuito.suspenderManual("x");
        invalida(circuito::suspenderPorEmpresa);
        invalida(circuito::rehabilitarPorEmpresa);

        EnlaceCuit otro = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia);
        otro.suspenderPorEmpresa();
        invalida(otro::rehabilitarManual);
        otro.rehabilitarPorEmpresa();
        assertEquals(EstadoEnlaceCuit.APROBADO, otro.getEstado());
    }
}
