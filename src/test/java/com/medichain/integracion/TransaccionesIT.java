package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.dispensacion.DispensacionRequestDTO;
import com.medichain.modules.dispensacion.DispensacionService;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteRequestDTO;
import com.medichain.modules.lote.LoteService;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.recepcion.RecepcionRequestDTO;
import com.medichain.modules.recepcion.RecepcionService;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración TransaccionesIT en MediChain.
 * Los intentos que deben quedar registrados AUNQUE la operación falle
 * (INTENTO_SERIE_INVALIDA, BULTO_INEXISTENTE, BULTO_DUPLICADO,
 * INTENTO_DUPLICADO) usan una transacción aparte (REQUIRES_NEW) mientras la
 * del negocio hace rollback. Con PostgreSQL real se comprueba:
 * <ul>
 *   <li>el intento queda y el hecho no (ni lote ni cajas, ni segunda dispensación);</li>
 *   <li>la cadena sigue sin huecos (el rollback no se lleva un número ya usado);</li>
 *   <li>no hay deadlock: la transacción externa todavía no tomó el bloqueo de
 *       cadena_estado que pide la interna (@Timeout de 10 s; un deadlock la colgaría).</li>
 * </ul>
 */
class TransaccionesIT extends IntegracionBase {

    @Autowired
    private EjecutorComoUsuario ejecutor;

    @Autowired
    private LoteService loteService;

    @Autowired
    private RecepcionService recepcionService;

    @Autowired
    private DispensacionService dispensacionService;

    @Autowired
    private VerificadorCadena verificadorCadena;

    private EscenarioIntegracion.Actores actores;
    private Medicamento medicamento;

    @BeforeEach
    void escenario() {
        actores = escenario.actores(Provincia.MENDOZA);
        medicamento = escenario.medicamento(actores);
    }

    @Test
    @Timeout(10)
    @DisplayName("R3: lote con una serie inválida → 409, sin lote ni cajas, INTENTO_SERIE_INVALIDA queda; cadena sin huecos")
    void seriesInvalidasDejanSoloElIntento() {
        long antes = cantidadEventos();
        LoteRequestDTO dto = new LoteRequestDTO();
        dto.setCodigo("TX-0001");
        dto.setFechaFabricacion(LocalDate.now().minusMonths(1));
        dto.setFechaVencimiento(LocalDate.now().plusYears(2));
        dto.setSeries(List.of("SERIE001", "779EMPIEZAMAL", "SERIE003"));
        dto.setMedicamentoId(medicamento.getId());

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> loteService.registrar(dto)));

        assertEquals("R3", ex.getCodigoRegla());
        assertEquals(0, contar("SELECT count(*) FROM lotes"));
        assertEquals(0, contar("SELECT count(*) FROM unidades_trazables"));
        assertEquals(antes + 1, cantidadEventos());
        assertEquals("INTENTO_SERIE_INVALIDA", ultimoTipo());
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @Timeout(10)
    @DisplayName("Recepción de un código inexistente → 404 y BULTO_INEXISTENTE queda en la cadena")
    void bultoInexistenteQueda() {
        RecepcionRequestDTO dto = recepcion("BUL-9999", 1);

        assertThrows(ResourceNotFoundException.class,
                () -> ejecutor.ejecutarComo(actores.adminDistribuidora(), () -> recepcionService.recibir(dto)));

        assertEquals("BULTO_INEXISTENTE", ultimoTipo());
        assertEquals(0, contar("SELECT count(*) FROM recepciones"));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @Timeout(20)
    @DisplayName("Bulto ya recibido por la misma empresa → 409 y BULTO_DUPLICADO queda; no hay segunda recepción")
    void bultoDuplicadoQueda() {
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "TX-0002", 3);
        Bulto bulto = escenario.cajasEnFarmacia(actores, circuito, lote, 3);
        int recepciones = contar("SELECT count(*) FROM recepciones");

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> ejecutor.ejecutarComo(
                actores.adminFarmacia(), () -> recepcionService.recibir(recepcion(bulto.getCodigo(), 3))));

        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
        assertEquals("BULTO_DUPLICADO", ultimoTipo());
        assertEquals(recepciones, contar("SELECT count(*) FROM recepciones"));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @Timeout(20)
    @DisplayName("R11: dispensar dos veces la misma caja → 409 R11, INTENTO_DUPLICADO queda, una sola dispensación")
    void intentoDuplicadoQueda() {
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "TX-0003", 2);
        escenario.cajasEnFarmacia(actores, circuito, lote, 2);
        String serie = jdbc.queryForObject("SELECT min(serie) FROM unidades_trazables WHERE lote_id = ?",
                String.class, lote.getId());
        ejecutor.ejecutarComo(actores.adminFarmacia(), () -> dispensacionService.dispensar(dispensacion(serie)));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> ejecutor.ejecutarComo(
                actores.adminFarmacia(), () -> dispensacionService.dispensar(dispensacion(serie))));

        assertEquals("R11", ex.getCodigoRegla());
        assertEquals("INTENTO_DUPLICADO", ultimoTipo());
        assertEquals(1, contar("SELECT count(*) FROM dispensaciones"));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    /** Recepción conforme del bulto. */
    private RecepcionRequestDTO recepcion(String codigoBulto, int cantidad) {
        RecepcionRequestDTO dto = new RecepcionRequestDTO();
        dto.setCodigoBulto(codigoBulto);
        dto.setPrecintoIntacto(true);
        dto.setCantidadVerificada(cantidad);
        dto.setTemperatura(new BigDecimal("20"));
        return dto;
    }

    /** Dispensación particular de una caja del medicamento. */
    private DispensacionRequestDTO dispensacion(String serie) {
        DispensacionRequestDTO dto = new DispensacionRequestDTO();
        dto.setGtin(medicamento.getGtin());
        dto.setSerie(serie);
        dto.setParticular(true);
        dto.setNumeroReceta("REC-IT-1");
        return dto;
    }

    private long cantidadEventos() {
        return jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad", Long.class);
    }

    private String ultimoTipo() {
        return jdbc.queryForObject("SELECT tipo FROM eventos_trazabilidad ORDER BY numero DESC LIMIT 1", String.class);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
