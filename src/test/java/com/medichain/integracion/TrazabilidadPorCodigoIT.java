package com.medichain.integracion;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración TrazabilidadPorCodigoIT en MediChain (paso B12e).
 * <ul>
 *   <li>Trazabilidad por código (GET /api/eventos-trazabilidad/recorrido): lote, bulto o caja, con los
 *       eventos de lo que los rodea y en el orden de la cadena.</li>
 *   <li>Alcance: cada rol ve solo las entidades que ya ve (lo ajeno, 404).</li>
 *   <li>Un código de lote repetido entre laboratorios: devuelve todos los visibles, cada uno con su laboratorio.</li>
 *   <li>Filtro ?entidadId= de listarEventos y búsquedas ?codigo= (lotes, bultos) y ?serie= (cajas).</li>
 * </ul>
 */
class TrazabilidadPorCodigoIT extends IntegracionBase {

    @Test
    @DisplayName("Lote, bulto y caja: sus eventos y los de lo que los rodea, en orden de la cadena")
    void recorridoDeLoteBultoYCaja() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.NEUQUEN);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "TC-0001", 4);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        DespachoLogistico viaje = escenario.viajeEnTransito(actores.adminLaboratorio(), bulto);
        escenario.recibir(actores.adminDistribuidora(), bulto, 2);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        String laboratorio = bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE);

        // Lote: sus propios eventos (no los de sus bultos).
        JsonNode porLote = recorrido("?lote=tc-0001", sede);
        assertEquals(1, porLote.path("resultados").size());
        JsonNode resultadoLote = porLote.path("resultados").get(0);
        assertEquals("LOTE", resultadoLote.path("tipo").asString());
        assertEquals("TC-0001", resultadoLote.path("codigo").asString());
        assertEquals(medicamento.getNombreComercial(), resultadoLote.path("medicamentoNombre").asString());
        assertEquals(actores.laboratorio().getRazonSocial(), resultadoLote.path("laboratorioRazonSocial").asString());
        assertEquals(List.of("LOTE_REGISTRADO", "LOTE_LIBERADO"), tipos(resultadoLote));
        assertFalse(resultadoLote.path("truncado").asBoolean());

        // Bulto: los suyos y los de su viaje (en el orden de la cadena).
        JsonNode resultadoBulto = recorrido("?bulto=" + bulto.getCodigo(), sede).path("resultados").get(0);
        assertEquals("BULTO", resultadoBulto.path("tipo").asString());
        assertEquals("TC-0001", resultadoBulto.path("loteCodigo").asString());
        List<String> tiposBulto = tipos(resultadoBulto);
        assertEquals(List.of("BULTO_ARMADO", "VIAJE_CREADO", "VIAJE_SALIDA", "BULTO_RECIBIDO", "VIAJE_FINALIZADO"),
                tiposBulto);
        assertTrue(numeros(resultadoBulto).equals(numerosOrdenados(resultadoBulto)), "orden de la cadena");
        assertTrue(tipos(resultadoBulto).contains("VIAJE_FINALIZADO"), "el viaje " + viaje.getCodigo() + " terminó");

        // Caja del bulto: sus eventos (ninguno propio todavía), los del lote y los del bulto.
        String serie = primeraSerieDelBulto(laboratorio, lote, bulto);
        JsonNode resultadoCaja = recorrido("?gtin=" + medicamento.getGtin() + "&serie=" + serie, sede)
                .path("resultados").get(0);
        assertEquals("CAJA", resultadoCaja.path("tipo").asString());
        assertEquals(serie, resultadoCaja.path("codigo").asString());
        List<String> tiposCaja = tipos(resultadoCaja);
        assertTrue(tiposCaja.containsAll(List.of("LOTE_REGISTRADO", "LOTE_LIBERADO", "BULTO_ARMADO", "VIAJE_SALIDA",
                "BULTO_RECIBIDO")), tiposCaja.toString());
        assertTrue(numeros(resultadoCaja).equals(numerosOrdenados(resultadoCaja)));

        // El filtro ?entidadId= de listarEventos trae los mismos eventos del lote.
        JsonNode delLote = json(mockMvc.perform(get("/api/eventos-trazabilidad?entidadId=" + lote.getId() + "&sort=numero,asc")
                        .header("Authorization", sede)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, delLote.path("page").path("totalElements").asLong());
    }

    @Test
    @DisplayName("Alcance: cada rol ve solo lo que ya ve de esa entidad; lo ajeno, 404; el paciente, 403")
    void alcancePorRol() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.RIO_NEGRO);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "TC-0002", 4);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        String laboratorio = bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE);
        String distribuidora = bearer(actores.adminDistribuidora().getEmail(), EscenarioIntegracion.CLAVE);
        String farmacia = bearer(actores.adminFarmacia().getEmail(), EscenarioIntegracion.CLAVE);
        String inspector = bearer(actores.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        EscenarioIntegracion.Actores otros = escenario.actores(Provincia.RIO_NEGRO);
        String otroLaboratorio = bearer(otros.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE);

        // El laboratorio dueño y el inspector ven el lote; el resto no (todavía no salió nada hacia ellos).
        assertEquals(1, recorrido("?lote=TC-0002", laboratorio).path("resultados").size());
        assertEquals(1, recorrido("?lote=TC-0002", inspector).path("resultados").size());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?lote=TC-0002").header("Authorization", distribuidora))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?lote=TC-0002").header("Authorization", farmacia))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?lote=TC-0002").header("Authorization", otroLaboratorio))
                .andExpect(status().isNotFound());
        // El bulto sí lo ven la distribuidora y la farmacia de su circuito (desde ARMADO); otro laboratorio no.
        assertEquals(1, recorrido("?bulto=" + bulto.getCodigo(), distribuidora).path("resultados").size());
        assertEquals(1, recorrido("?bulto=" + bulto.getCodigo(), farmacia).path("resultados").size());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?bulto=" + bulto.getCodigo())
                .header("Authorization", otroLaboratorio)).andExpect(status().isNotFound());

        // Cuando el bulto sale hacia la distribuidora, empieza a ver el lote.
        escenario.viajeEnTransito(actores.adminLaboratorio(), bulto);
        assertEquals(1, recorrido("?lote=TC-0002", distribuidora).path("resultados").size());

        // Un paciente no usa esta búsqueda.
        mockMvc.perform(post("/api/registro/pacientes").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", "paciente-trazas@integracion.test",
                                "password", EscenarioIntegracion.CLAVE, "nombre", "Pía", "apellido", "Trazas",
                                "dni", "31222555")))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?lote=TC-0002").header("Authorization",
                bearer("paciente-trazas@integracion.test", EscenarioIntegracion.CLAVE))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?lote=TC-0002")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un código de lote repetido entre laboratorios: todos los visibles, con su laboratorio; laboratorioId los separa")
    void codigoDeLoteRepetido() throws Exception {
        EscenarioIntegracion.Actores a = escenario.actores(Provincia.CORRIENTES);
        EscenarioIntegracion.Actores b = escenario.actores(Provincia.CORRIENTES);
        escenario.loteLiberado(a, escenario.medicamento(a), "TC-IGUAL", 2);
        escenario.loteLiberado(b, escenario.medicamento(b), "TC-IGUAL", 2);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");

        JsonNode todos = recorrido("?lote=TC-IGUAL", sede);
        assertEquals(2, todos.path("resultados").size());
        List<String> laboratorios = new ArrayList<>();
        todos.path("resultados").forEach(r -> laboratorios.add(r.path("laboratorioRazonSocial").asString()));
        assertTrue(laboratorios.containsAll(List.of(a.laboratorio().getRazonSocial(), b.laboratorio().getRazonSocial())));

        JsonNode soloA = recorrido("?lote=TC-IGUAL&laboratorioId=" + a.laboratorio().getId(), sede);
        assertEquals(1, soloA.path("resultados").size());
        assertEquals(a.laboratorio().getRazonSocial(), soloA.path("resultados").get(0).path("laboratorioRazonSocial").asString());
        // Cada laboratorio ve solo el suyo.
        assertEquals(1, recorrido("?lote=TC-IGUAL", bearer(b.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE))
                .path("resultados").size());
    }

    @Test
    @DisplayName("Consultas mal armadas → 400; sin coincidencias → 404")
    void consultasInvalidas() throws Exception {
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        for (String consulta : List.of("", "?lote=A&bulto=B", "?lote=A&gtin=07799000001010&serie=X1",
                "?gtin=07799000001010", "?serie=X1", "?bulto=BUL-0001&laboratorioId=" + java.util.UUID.randomUUID(),
                "?lote=" + "X".repeat(51))) {
            mockMvc.perform(get("/api/eventos-trazabilidad/recorrido" + consulta).header("Authorization", sede))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?lote=NO-EXISTE").header("Authorization", sede))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/eventos-trazabilidad/recorrido?bulto=BUL-9999").header("Authorization", sede))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Búsqueda ?codigo= en lotes y bultos y ?serie= en cajas: contiene, sin mayúsculas, dentro del alcance del rol")
    void busquedaEnListados() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.MISIONES);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote uno = escenario.loteLiberado(actores, medicamento, "BU-AAA1", 3);
        escenario.loteLiberado(actores, medicamento, "BU-BBB2", 3);
        Bulto bulto = escenario.bultoArmado(actores, circuito, uno, 2);
        escenario.bultoArmado(actores, circuito, uno, 1);
        String laboratorio = bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE);
        String farmacia = bearer(actores.adminFarmacia().getEmail(), EscenarioIntegracion.CLAVE);
        String otroLaboratorio = bearer(escenario.actores(Provincia.MISIONES).adminLaboratorio().getEmail(),
                EscenarioIntegracion.CLAVE);

        assertEquals(1, total("/api/lotes?codigo=aaa", laboratorio));
        assertEquals(2, total("/api/lotes?codigo=bu-", laboratorio));
        assertEquals(2, total("/api/lotes", laboratorio));
        assertEquals(0, total("/api/lotes?codigo=aaa", otroLaboratorio), "lo ajeno no se ve");
        assertEquals(0, total("/api/lotes?codigo=%25", laboratorio), "el % se busca tal cual, no es un comodín");
        assertEquals(1, total("/api/bultos?codigo=" + bulto.getCodigo().toLowerCase(), laboratorio));
        assertEquals(2, total("/api/bultos?codigo=bul-", farmacia), "la farmacia del circuito ve los dos desde ARMADO");
        assertEquals(0, total("/api/bultos?codigo=bul-", otroLaboratorio));
        assertEquals(6, total("/api/unidades-trazables", laboratorio));
        String serie = primeraSerieDelBulto(laboratorio, uno, bulto);
        assertEquals(1, total("/api/unidades-trazables?serie=" + serie.toLowerCase(), laboratorio));
        assertEquals(3, total("/api/unidades-trazables?serie=buaaa1", laboratorio),
                "las 3 series del lote BU-AAA1 (BUAAA1S000001…)");
        mockMvc.perform(get("/api/unidades-trazables?serie=" + "x".repeat(51)).header("Authorization", laboratorio))
                .andExpect(status().isBadRequest());
    }

    // ---------- Utilidades ----------

    private JsonNode recorrido(String consulta, String token) throws Exception {
        return json(mockMvc.perform(get("/api/eventos-trazabilidad/recorrido" + consulta).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long total(String url, String token) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("page").path("totalElements").asLong();
    }

    /** Serie de una caja que quedó en el bulto (las cajas libres no tienen bulto). */
    private String primeraSerieDelBulto(String laboratorio, Lote lote, Bulto bulto) throws Exception {
        JsonNode cajas = json(mockMvc.perform(get("/api/unidades-trazables?size=100&sort=serie").header("Authorization", laboratorio))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode caja : cajas.path("content")) {
            if (caja.path("bultoId").asString().equals(bulto.getId().toString())) {
                return caja.path("serie").asString();
            }
        }
        throw new AssertionError("el bulto " + bulto.getCodigo() + " no tiene cajas visibles");
    }

    private static List<String> tipos(JsonNode resultado) {
        List<String> tipos = new ArrayList<>();
        resultado.path("eventos").forEach(e -> tipos.add(e.path("tipo").asString()));
        return tipos;
    }

    private static List<Long> numeros(JsonNode resultado) {
        List<Long> numeros = new ArrayList<>();
        resultado.path("eventos").forEach(e -> numeros.add(e.path("numero").asLong()));
        return numeros;
    }

    private static List<Long> numerosOrdenados(JsonNode resultado) {
        List<Long> numeros = numeros(resultado);
        numeros.sort(Long::compare);
        return numeros;
    }
}
