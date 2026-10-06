package com.medichain.modules.empresa;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test unitario EmpresaTransicionesTest en MediChain.
 * Métodos de dominio de Empresa sin Spring: transiciones permitidas y
 * TRANSICION_INVALIDA en las demás.
 */
class EmpresaTransicionesTest {

    private final InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);

    /** Verifica que la acción falle con TRANSICION_INVALIDA. */
    private void invalida(Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("PENDIENTE → tomar → habilitar → suspender → rehabilitar")
    void recorridoFeliz() {
        Empresa empresa = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        empresa.tomar(inspector);
        empresa.habilitar(inspector);
        assertEquals(EstadoHabilitacion.HABILITADA, empresa.getEstado());
        empresa.suspender("motivo");
        assertEquals(EstadoHabilitacion.SUSPENDIDA, empresa.getEstado());
        empresa.rehabilitar();
        assertEquals(EstadoHabilitacion.HABILITADA, empresa.getEstado());
    }

    @Test
    @DisplayName("Habilitar o rechazar sin ser el revisor → inválida")
    void sinRevisorInvalida() {
        Empresa empresa = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        invalida(() -> empresa.habilitar(inspector));
        empresa.tomar(DatosDePrueba.inspector(Provincia.CORDOBA));
        invalida(() -> empresa.rechazar(inspector, "motivo"));
    }

    @Test
    @DisplayName("RECHAZADA es final: no se habilita, no se toma, no se suspende")
    void rechazadaEsFinal() {
        Empresa empresa = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        empresa.tomar(inspector);
        empresa.rechazar(inspector, "motivo");
        invalida(() -> empresa.habilitar(inspector));
        invalida(() -> empresa.tomar(inspector));
        invalida(() -> empresa.suspender("motivo"));
        invalida(empresa::rehabilitar);
    }

    @Test
    @DisplayName("HABILITADA no se rehabilita ni se vuelve a tomar")
    void habilitadaNoSeRehabilita() {
        Empresa empresa = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        invalida(empresa::rehabilitar);
        invalida(() -> empresa.tomar(inspector));
        invalida(empresa::liberarRevisor);
    }
}
