package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRequestDTO;
import com.medichain.modules.bulto.BultoService;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.modules.unidadtrazable.VerificacionPublicaResponseDTO;
import com.medichain.modules.unidadtrazable.VerificacionPublicaService;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración RestriccionesBaseIT en MediChain.
 * Lo que garantiza la BASE (secuencias, índices únicos, índices parciales)
 * y que el Service lo traduce a su regla cuando dos operaciones chocan:
 * <ul>
 *   <li>Las CARRERAS se provocan de forma determinista: la primera operación
 *       queda con su transacción ABIERTA (fila sin confirmar, invisible para la
 *       otra) hasta que la segunda, que ya pasó los controles del Service, queda
 *       bloqueada en PostgreSQL esperando el índice único; recién entonces se
 *       confirma la primera y la segunda recibe la violación del índice.</li>
 *   <li>R5 (ux_circuito_par_vigente, parcial: RECHAZADO no cuenta),
 *       LOTE_DUPLICADO (ux_lote_laboratorio_codigo), R3 por GTIN
 *       (ux_unidad_gtin_serie) con su INTENTO_SERIE_INVALIDA en transacción
 *       aparte, un solo SERIE_INEXISTENTE por día (ux_intento_verificacion_dia)
 *       y códigos de secuencia sin repetir.</li>
 * </ul>
 */
class RestriccionesBaseIT extends IntegracionBase {

    @Autowired
    private EjecutorComoUsuario ejecutor;

    @Autowired
    private EnlaceCuitService enlaceCuitService;

    @Autowired
    private BultoService bultoService;

    @Autowired
    private VerificacionPublicaService verificacionPublicaService;

    @Autowired
    private VerificadorCadena verificadorCadena;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private EscenarioIntegracion.Actores actores;
    private Medicamento medicamento;

    @BeforeEach
    void escenario() {
        actores = escenario.actores(Provincia.SANTA_FE);
        medicamento = escenario.medicamento(actores);
    }

    // ---------- R5: un solo circuito vigente por par laboratorio–farmacia ----------

    @Test
    @DisplayName("Carrera R5: dos propuestas del mismo par a la vez → una se crea, la otra 409 R5 (no un 409 genérico)")
    void carreraDelParVigente() throws Exception {
        Usuario dt = actores.adminLaboratorio();
        Supplier<EnlaceCuit> proponer = () -> ejecutor.ejecutarComo(dt, () -> enlaceCuitService.proponer(
                actores.distribuidora().getCuit(), actores.farmacia().getCuit()));

        Throwable error = carrera(proponer, proponer);

        assertRegla("R5", error);
        assertEquals(1, contar("SELECT count(*) FROM enlaces_cuit"));
        assertEquals(1, contar("SELECT count(*) FROM eventos_trazabilidad WHERE tipo = 'CIRCUITO_PROPUESTO'"));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @DisplayName("R5 con el índice PARCIAL: un circuito RECHAZADO no cuenta; se vuelve a proponer el mismo par")
    void rechazadoNoCuenta() {
        Usuario dt = actores.adminLaboratorio();
        EnlaceCuit primero = ejecutor.ejecutarComo(dt, () -> enlaceCuitService.proponer(
                actores.distribuidora().getCuit(), actores.farmacia().getCuit()));
        assertRegla("R5", assertThrows(ReglaNegocioException.class, () -> ejecutor.ejecutarComo(dt,
                () -> enlaceCuitService.proponer(actores.distribuidora().getCuit(), actores.farmacia().getCuit()))));

        ejecutor.ejecutarComo(actores.adminFarmacia(),
                () -> enlaceCuitService.rechazarPorEmpresa(primero.getId(), "No trabajamos con esa distribuidora"));
        EnlaceCuit segundo = ejecutor.ejecutarComo(dt, () -> enlaceCuitService.proponer(
                actores.distribuidora().getCuit(), actores.farmacia().getCuit()));

        assertNotNull(segundo.getId());
        assertEquals(2, contar("SELECT count(*) FROM enlaces_cuit"));
        assertEquals("CIR-0002", segundo.getCodigo());
    }

    // ---------- Código de lote: único POR laboratorio ----------

    @Test
    @DisplayName("Código de lote: el mismo código en OTRO laboratorio se acepta; en el mismo → LOTE_DUPLICADO")
    void codigoDeLotePorLaboratorio() {
        escenario.lote(actores, medicamento, "MISMO-01", 2);
        EscenarioIntegracion.Actores otros = escenario.actores(Provincia.SANTA_FE);
        escenario.lote(otros, escenario.medicamento(otros), "MISMO-01", 2);

        assertRegla("LOTE_DUPLICADO", assertThrows(ReglaNegocioException.class,
                () -> escenario.lote(actores, medicamento, "MISMO-01", 2)));
        assertEquals(2, contar("SELECT count(*) FROM lotes"));
    }

    @Test
    @DisplayName("Carrera de código de lote: dos altas del mismo código a la vez → una se crea, la otra 409 LOTE_DUPLICADO")
    void carreraDeCodigoDeLote() throws Exception {
        Supplier<Lote> registrar = () -> escenario.lote(actores, medicamento, "CARRERA-1", 3);

        Throwable error = carrera(registrar, registrar);

        assertRegla("LOTE_DUPLICADO", error);
        assertEquals(1, contar("SELECT count(*) FROM lotes"));
        assertEquals(3, contar("SELECT count(*) FROM unidades_trazables"));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    // ---------- R3: serie única POR GTIN ----------

    @Test
    @DisplayName("R3 por GTIN: la misma serie en otro medicamento se acepta; en el mismo GTIN → 409 R3")
    void seriePorGtin() {
        escenario.lote(actores, medicamento, "G-0001", 0, List.of("COMPARTIDA1", "PROPIA1"));
        Medicamento otro = escenario.medicamento(actores);
        escenario.lote(actores, otro, "G-0002", 0, List.of("COMPARTIDA1"));

        assertRegla("R3", assertThrows(ReglaNegocioException.class,
                () -> escenario.lote(actores, medicamento, "G-0003", 0, List.of("COMPARTIDA1"))));
        assertEquals(2, contar("SELECT count(*) FROM unidades_trazables WHERE serie = 'COMPARTIDA1'"));
    }

    @Test
    @DisplayName("Carrera de series: dos lotes del mismo GTIN con una serie en común → uno se crea; el otro 409 R3 + INTENTO_SERIE_INVALIDA")
    void carreraDeSeries() throws Exception {
        Supplier<Lote> primero = () -> escenario.lote(actores, medicamento, "S-0001", 0, List.of("X1", "X2", "X3"));
        Supplier<Lote> segundo = () -> escenario.lote(actores, medicamento, "S-0002", 0, List.of("X3", "X4"));

        Throwable error = carrera(primero, segundo);

        assertRegla("R3", error);
        assertTrue(error.getMessage().contains("[X3]"), error.getMessage());
        assertEquals(List.of("S-0001"), jdbc.queryForList("SELECT codigo FROM lotes", String.class));
        assertEquals(3, contar("SELECT count(*) FROM unidades_trazables"));
        String datos = jdbc.queryForObject("SELECT datos_json FROM eventos_trazabilidad ORDER BY numero DESC LIMIT 1",
                String.class);
        assertEquals("INTENTO_SERIE_INVALIDA", jdbc.queryForObject(
                "SELECT tipo FROM eventos_trazabilidad ORDER BY numero DESC LIMIT 1", String.class));
        assertTrue(datos.contains("\"yaExistentes\":1") && datos.contains("\"codigoLote\":\"S-0002\""), datos);
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    // ---------- Secuencias e intentos de verificación ----------

    @Test
    @DisplayName("Secuencias: 6 bultos armados a la vez → BUL-0001..BUL-0006, sin códigos repetidos")
    void codigosDeBultoConcurrentes() throws Exception {
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "SEQ-0001", 6);
        List<String> series = jdbc.queryForList("SELECT serie FROM unidades_trazables WHERE lote_id = ? ORDER BY serie",
                String.class, lote.getId());
        List<Callable<?>> tareas = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            String serie = series.get(i);
            String precinto = "PRE-SEQ-" + i;
            tareas.add(() -> ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> {
                BultoRequestDTO dto = new BultoRequestDTO();
                dto.setCircuitoId(circuito.getId());
                dto.setLoteId(lote.getId());
                dto.setPrecinto(precinto);
                dto.setSeries(List.of(serie));
                return bultoService.armar(dto);
            }));
        }

        List<EnParalelo.Resultado> resultados = EnParalelo.correr(tareas);

        assertEquals(List.of(), EnParalelo.errores(resultados));
        List<String> codigos = resultados.stream().map(r -> ((Bulto) r.getValor()).getCodigo()).sorted().toList();
        assertEquals(List.of("BUL-0001", "BUL-0002", "BUL-0003", "BUL-0004", "BUL-0005", "BUL-0006"), codigos);
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @DisplayName("5 verificaciones públicas simultáneas de una serie falsa → todas NO_EXISTE y UN solo SERIE_INEXISTENTE")
    void unSoloSerieInexistentePorDia() throws Exception {
        List<EnParalelo.Resultado> resultados = EnParalelo.correr(5,
                () -> verificacionPublicaService.verificar(medicamento.getGtin(), "FALSA0001"));

        assertEquals(List.of(), EnParalelo.errores(resultados));
        for (EnParalelo.Resultado resultado : resultados) {
            assertEquals("NO_EXISTE", ((VerificacionPublicaResponseDTO) resultado.getValor()).getEstado().name());
        }
        // Una sexta, por HTTP y sin token (endpoint público): 200 y sigue habiendo un solo evento.
        mockMvc.perform(get("/api/verificacion").param("gtin", medicamento.getGtin()).param("serie", "FALSA0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_EXISTE"));
        assertEquals(1, contar("SELECT count(*) FROM eventos_trazabilidad WHERE tipo = 'SERIE_INEXISTENTE'"));
        assertEquals(1, contar("SELECT count(*) FROM intento_verificacion"));
    }

    // ---------- Utilidades ----------

    /**
     * Carrera determinista: corre "primera" en una transacción que queda
     * ABIERTA; lanza "segunda" en otro hilo, espera a que PostgreSQL la
     * bloquee (pg_stat_activity: espera de tipo Lock) y recién entonces
     * confirma la primera. La primera tiene que salir bien; devuelve la
     * excepción de la segunda (falla si la segunda no lanzó ninguna).
     */
    private Throwable carrera(Supplier<?> primera, Supplier<?> segunda) throws Exception {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        CountDownLatch primeraEscribio = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> futuroPrimera = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                primera.get();
                primeraEscribio.countDown();
                esperar(confirmar);
            }));
            assertTrue(primeraEscribio.await(30, TimeUnit.SECONDS), "la primera operación no terminó de escribir");
            Future<?> futuroSegunda = hilos.submit(segunda::get);
            esperarBloqueoEnLaBase();
            confirmar.countDown();
            futuroPrimera.get(30, TimeUnit.SECONDS);
            try {
                futuroSegunda.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                return e.getCause();
            }
            throw new AssertionError("La segunda operación de la carrera no falló");
        } finally {
            confirmar.countDown();
            hilos.shutdownNow();
        }
    }

    /** Espera (hasta 10 s) a que una sesión de la base quede bloqueada esperando un lock. */
    private void esperarBloqueoEnLaBase() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Integer bloqueadas = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (bloqueadas != null && bloqueadas > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("La segunda operación nunca quedó bloqueada en el índice único: no hubo carrera");
    }

    /** Espera la señal, sin colgar el test si nunca llega. */
    private static void esperar(CountDownLatch senal) {
        try {
            if (!senal.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No llegó la señal para confirmar la primera transacción");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void assertRegla(String codigo, Throwable error) {
        ReglaNegocioException regla = assertInstanceOf(ReglaNegocioException.class, error,
                () -> "se esperaba 409 " + codigo + " y llegó: " + error);
        assertEquals(codigo, regla.getCodigoRegla(), regla.getMessage());
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
