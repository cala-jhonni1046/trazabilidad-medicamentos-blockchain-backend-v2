package com.medichain.integracion;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración BloqueoIT en MediChain (paso B11b, punto E, R10).
 * Las respuestas de lotes, bultos y cajas traen bloqueado + motivoBloqueo +
 * mensajeBloqueo, calculados por EvaluadorBloqueo (el único lugar de R10):
 * <ul>
 *   <li>cada causa vista por la API: LOTE_VENCIDO, LOTE_EN_CUARENTENA,
 *       LOTE_EN_RECALL y BULTO_CON_MEDIDA_VIGENTE (ruptura de frío: el bulto y
 *       sus cajas, el lote no);</li>
 *   <li>sin N+1: listar 50 cajas cuesta las mismas consultas que listar 5
 *       (estadísticas de Hibernate).</li>
 * </ul>
 */
class BloqueoIT extends IntegracionBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private EscenarioIntegracion.Actores actores;
    private Medicamento medicamento;
    private EnlaceCuit circuito;
    private String sede;

    @BeforeEach
    void escenario() throws Exception {
        actores = escenario.actores(Provincia.TIERRA_DEL_FUEGO);
        medicamento = escenario.medicamento(actores);
        circuito = escenario.circuitoAprobado(actores);
        sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
    }

    @Test
    @DisplayName("Sin medidas: lote, bulto y cajas con bloqueado=false y sin motivo")
    void sinBloqueo() throws Exception {
        Lote lote = escenario.loteLiberado(actores, medicamento, "BQ-0001", 3);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);

        assertNoBloqueado(obtener("/api/lotes/" + lote.getId()));
        assertNoBloqueado(obtener("/api/bultos/" + bulto.getId()));
        obtener("/api/lotes/" + lote.getId() + "/unidades").path("content").forEach(this::assertNoBloqueado);
    }

    @Test
    @DisplayName("Lote vencido: LOTE_VENCIDO en el lote, en su bulto y en sus cajas")
    void loteVencido() throws Exception {
        Lote lote = escenario.loteLiberado(actores, medicamento, "BQ-0002", 3);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        jdbc.update("UPDATE lotes SET fecha_vencimiento = current_date - 30, fecha_fabricacion = current_date - 400 "
                + "WHERE id = ?", lote.getId());

        assertBloqueado(obtener("/api/lotes/" + lote.getId()), "LOTE_VENCIDO");
        assertBloqueado(obtener("/api/bultos/" + bulto.getId()), "LOTE_VENCIDO");
        obtener("/api/lotes/" + lote.getId() + "/unidades").path("content")
                .forEach(caja -> assertBloqueado(caja, "LOTE_VENCIDO"));
    }

    @Test
    @DisplayName("Cuarentena de LOTE → LOTE_EN_CUARENTENA; convertida en recall → LOTE_EN_RECALL (en las cajas del listado)")
    void cuarentenaYRecall() throws Exception {
        Lote lote = escenario.loteLiberado(actores, medicamento, "BQ-0003", 2);
        String inspector = bearer(actores.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        String cuarentena = json(mockMvc.perform(post("/api/cuarentenas").header("Authorization", inspector)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "loteId", lote.getId().toString(), "motivo", "DEFECTO_CALIDAD", "descripcion", "Envase"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asString();

        obtener("/api/unidades-trazables?size=100").path("content").forEach(caja -> assertBloqueado(caja, "LOTE_EN_CUARENTENA"));

        // Quien abre la cuarentena manual ya queda como su revisor (R12): dictamina sin tomarla.
        mockMvc.perform(post("/api/cuarentenas/" + cuarentena + "/recall").header("Authorization", inspector)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("fundamento", "Defecto confirmado"))))
                .andExpect(status().isOk());
        assertBloqueado(obtener("/api/lotes/" + lote.getId()), "LOTE_EN_RECALL");
        obtener("/api/unidades-trazables?size=100").path("content").forEach(caja -> assertBloqueado(caja, "LOTE_EN_RECALL"));
    }

    @Test
    @DisplayName("Ruptura de frío (medida DESPACHO): BULTO_CON_MEDIDA_VIGENTE en el bulto y sus cajas; el lote y la caja suelta, no")
    void rupturaDeFrio() throws Exception {
        Lote lote = escenario.loteLiberado(actores, medicamento, "BQ-0004", 3);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        DespachoLogistico viaje = escenario.viajeEnTransito(actores.adminLaboratorio(), bulto);
        mockMvc.perform(post("/api/telemetria-temperatura")
                        .header("Authorization", bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "sensorId", "SENSOR-IT", "temperatura", "40", "despachoId", viaje.getId().toString(),
                                "fechaHora", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()))))
                .andExpect(status().isCreated());

        assertBloqueado(obtener("/api/bultos/" + bulto.getId()), "BULTO_CON_MEDIDA_VIGENTE");
        assertNoBloqueado(obtener("/api/lotes/" + lote.getId()));
        int enElBulto = 0;
        for (JsonNode caja : obtener("/api/lotes/" + lote.getId() + "/unidades").path("content")) {
            if (bulto.getId().toString().equals(caja.path("bultoId").asString())) {
                assertBloqueado(caja, "BULTO_CON_MEDIDA_VIGENTE");
                enElBulto++;
            } else {
                assertNoBloqueado(caja);
            }
        }
        assertEquals(2, enElBulto);
    }

    @Test
    @DisplayName("Sin N+1: listar 60 cajas (3 lotes, 6 bultos y cajas sueltas) cuesta las mismas consultas que listar 20 (1 lote)")
    void listadoSinNMasUno() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Lote lote = escenario.loteLiberado(actores, medicamento, "BQ-N" + i, 20);
            escenario.bultoArmado(actores, circuito, lote, 5);
            escenario.bultoArmado(actores, circuito, lote, 5);
        }
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // Ordenadas por serie: las 20 primeras son el primer lote entero (10 en sus 2 bultos y 10 sueltas);
        // las 60 son los 3 lotes. Las dos páginas mezclan cajas en bultos y sueltas: mismas clases de consulta.
        estadisticas.clear();
        JsonNode veinte = obtener("/api/unidades-trazables?size=20&sort=serie");
        long consultasVeinte = estadisticas.getPrepareStatementCount();
        estadisticas.clear();
        JsonNode sesenta = obtener("/api/unidades-trazables?size=60&sort=serie");
        long consultasSesenta = estadisticas.getPrepareStatementCount();

        assertEquals(composicion(veinte), Map.of("lotes", 1, "bultos", 2, "sueltas", 10));
        assertEquals(composicion(sesenta), Map.of("lotes", 3, "bultos", 6, "sueltas", 30));
        assertEquals(consultasVeinte, consultasSesenta,
                "las consultas no crecen con el tamaño de la página (" + consultasVeinte + " vs " + consultasSesenta + ")");
        assertTrue(consultasSesenta <= 10, "un listado con su indicador R10 cuesta pocas consultas: " + consultasSesenta);
    }

    /** Cuántos lotes y bultos distintos, y cuántas cajas sueltas, tiene una página de cajas. */
    private static Map<String, Integer> composicion(JsonNode pagina) {
        Set<String> lotes = new HashSet<>();
        Set<String> bultos = new HashSet<>();
        int sueltas = 0;
        for (JsonNode caja : pagina.path("content")) {
            lotes.add(caja.path("loteId").asString());
            if (caja.path("bultoId").isNull()) {
                sueltas++;
            } else {
                bultos.add(caja.path("bultoId").asString());
            }
        }
        return Map.of("lotes", lotes.size(), "bultos", bultos.size(), "sueltas", sueltas);
    }

    // ---------- Utilidades ----------

    private JsonNode obtener(String url) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", sede)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void assertBloqueado(JsonNode respuesta, String causa) {
        assertTrue(respuesta.path("bloqueado").asBoolean(), "debe estar bloqueado: " + respuesta);
        assertEquals(causa, respuesta.path("motivoBloqueo").asString());
        assertFalse(respuesta.path("mensajeBloqueo").asString().isBlank());
    }

    private void assertNoBloqueado(JsonNode respuesta) {
        assertFalse(respuesta.path("bloqueado").asBoolean(), "no debe estar bloqueado: " + respuesta);
        assertTrue(respuesta.path("motivoBloqueo").isNull());
        assertTrue(respuesta.path("mensajeBloqueo").isNull());
    }
}
