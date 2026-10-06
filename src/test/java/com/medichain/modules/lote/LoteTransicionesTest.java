package com.medichain.modules.lote;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test unitario LoteTransicionesTest en MediChain.
 * LIBERADO ↔ CUARENTENA → RECALL; sin LIBERADO → RECALL directo; un lote
 * pendiente no entra en cuarentena.
 */
class LoteTransicionesTest {

    /** Verifica que la acción falle con TRANSICION_INVALIDA. */
    private void invalida(Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("LIBERADO → CUARENTENA → LIBERADO (levantada) y CUARENTENA → RECALL")
    void recorrido() {
        Lote lote = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        lote.liberar(null);
        lote.entrarEnCuarentena();
        assertEquals(EstadoLote.CUARENTENA, lote.getEstado());
        lote.levantarCuarentena();
        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
        lote.entrarEnCuarentena();
        lote.pasarARecall();
        assertEquals(EstadoLote.RECALL, lote.getEstado());
        invalida(lote::levantarCuarentena);
        invalida(lote::entrarEnCuarentena);
    }

    @Test
    @DisplayName("Sin LIBERADO → RECALL directo; un lote PENDIENTE_LIBERACION no entra en cuarentena")
    void transicionesInvalidas() {
        Lote pendiente = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        invalida(pendiente::entrarEnCuarentena);
        pendiente.liberar(null);
        invalida(pendiente::pasarARecall);
    }
}
