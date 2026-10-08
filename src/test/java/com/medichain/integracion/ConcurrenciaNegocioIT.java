package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.bulto.BultoRequestDTO;
import com.medichain.modules.bulto.BultoService;
import com.medichain.modules.dispensacion.DispensacionRequestDTO;
import com.medichain.modules.dispensacion.DispensacionService;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteService;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración ConcurrenciaNegocioIT en MediChain.
 * Dos personas hacen la MISMA acción a la vez sobre la misma entidad. La
 * protegen @Version (BaseEntity: UPDATE … WHERE version = ?) y la transición
 * de estado de la entidad. Pase lo que pase con el orden de los hilos, se
 * comprueba el invariante: la acción ocurre UNA vez, con UN evento, y los
 * demás reciben 409 (conflicto de concurrencia o TRANSICION_INVALIDA / R11),
 * nunca un 500 ni un doble registro.
 */
class ConcurrenciaNegocioIT extends IntegracionBase {

    private static final int INTENTOS = 4;

    @Autowired
    private EjecutorComoUsuario ejecutor;

    @Autowired
    private LoteService loteService;

    @Autowired
    private BultoService bultoService;

    @Autowired
    private DispensacionService dispensacionService;

    @Autowired
    private VerificadorCadena verificadorCadena;

    private EscenarioIntegracion.Actores actores;
    private Medicamento medicamento;

    @BeforeEach
    void escenario() {
        actores = escenario.actores(Provincia.SALTA);
        medicamento = escenario.medicamento(actores);
    }

    @Test
    @DisplayName("R4: 4 liberaciones simultáneas del mismo lote → una sola liberación y un solo LOTE_LIBERADO")
    void liberacionConcurrente() throws Exception {
        Lote lote = escenario.lote(actores, medicamento, "CC-0001", 3);

        List<EnParalelo.Resultado> resultados = EnParalelo.correr(INTENTOS,
                () -> ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> loteService.liberar(lote.getId())));

        assertEquals(1, EnParalelo.exitos(resultados));
        EnParalelo.errores(resultados).forEach(this::assertConflicto);
        assertEquals(1, contar("SELECT count(*) FROM eventos_trazabilidad WHERE tipo = 'LOTE_LIBERADO'"));
        assertEquals("LIBERADO", jdbc.queryForObject("SELECT estado FROM lotes WHERE id = ?", String.class, lote.getId()));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @DisplayName("R11: 4 dispensaciones simultáneas de la misma caja → una sola dispensación y un solo DISPENSACION")
    void dispensacionConcurrente() throws Exception {
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "CC-0002", 2);
        escenario.cajasEnFarmacia(actores, circuito, lote, 2);
        String serie = jdbc.queryForObject("SELECT min(serie) FROM unidades_trazables WHERE lote_id = ?",
                String.class, lote.getId());

        List<EnParalelo.Resultado> resultados = EnParalelo.correr(INTENTOS,
                () -> ejecutor.ejecutarComo(actores.adminFarmacia(), () -> {
                    DispensacionRequestDTO dto = new DispensacionRequestDTO();
                    dto.setGtin(medicamento.getGtin());
                    dto.setSerie(serie);
                    dto.setParticular(true);
                    dto.setNumeroReceta("REC-CC-1");
                    return dispensacionService.dispensar(dto);
                }));

        assertEquals(1, EnParalelo.exitos(resultados));
        EnParalelo.errores(resultados).forEach(this::assertConflicto);
        assertEquals(1, contar("SELECT count(*) FROM dispensaciones"));
        assertEquals(1, contar("SELECT count(*) FROM eventos_trazabilidad WHERE tipo = 'DISPENSACION'"));
        assertEquals("DISPENSADA", jdbc.queryForObject(
                "SELECT estado FROM unidades_trazables WHERE serie = ?", String.class, serie));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @DisplayName("R6: 4 bultos armados a la vez del mismo lote por cantidad → ninguna caja queda en dos bultos")
    void armadoConcurrenteNoRepiteCajas() throws Exception {
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "CC-0003", 10);

        List<EnParalelo.Resultado> resultados = EnParalelo.correr(INTENTOS,
                () -> ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> {
                    BultoRequestDTO dto = new BultoRequestDTO();
                    dto.setCircuitoId(circuito.getId());
                    dto.setLoteId(lote.getId());
                    dto.setPrecinto("PRE-CC-" + Thread.currentThread().threadId());
                    dto.setCantidad(2);
                    return bultoService.armar(dto);
                }));

        long armados = EnParalelo.exitos(resultados);
        assertTrue(armados >= 1, "al menos uno se arma");
        EnParalelo.errores(resultados).forEach(this::assertConflicto);
        assertEquals((int) armados, contar("SELECT count(*) FROM bultos"));
        assertEquals((int) armados * 2, contar("SELECT count(*) FROM unidades_trazables WHERE bulto_id IS NOT NULL"));
        assertEquals(0, contar("SELECT count(*) FROM bultos b WHERE b.cantidad <> "
                + "(SELECT count(*) FROM unidades_trazables u WHERE u.bulto_id = b.id)"),
                "cada bulto tiene exactamente sus cajas");
        assertEquals((int) armados, contar("SELECT count(*) FROM eventos_trazabilidad WHERE tipo = 'BULTO_ARMADO'"));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    /** Un perdedor de la carrera recibe 409: conflicto de @Version o la regla del estado ya cambiado. */
    private void assertConflicto(Throwable error) {
        boolean esperado = error instanceof ObjectOptimisticLockingFailureException
                || (error instanceof ReglaNegocioException regla
                && Set.of("TRANSICION_INVALIDA", "R11").contains(regla.getCodigoRegla()));
        assertTrue(esperado, () -> "se esperaba un 409 de concurrencia y llegó: " + error);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
