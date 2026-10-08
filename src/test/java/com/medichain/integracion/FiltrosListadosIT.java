package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración FiltrosListadosIT en MediChain (paso B11b, punto C).
 * <ul>
 *   <li>Filtro opcional ?estado= en bultos, empresas, circuitos, inspectores,
 *       lotes, cuarentenas y reportes: trae solo ese estado y DENTRO del alcance
 *       de cada rol (lo ajeno sigue sin verse; un estado inválido → 400).</li>
 *   <li>Temperatura de un viaje (GET /api/viajes/{id}/telemetria-temperatura y
 *       los listados de telemetría): la ven la Sede, los inspectores, la empresa
 *       origen y la receptora de ese tramo (distribuidora en el tramo 1, farmacia
 *       en el tramo 2); otra empresa → 404. El GPS sigue solo para el origen.</li>
 * </ul>
 */
class FiltrosListadosIT extends IntegracionBase {

    @Autowired
    private EjecutorComoUsuario ejecutor;

    @Autowired
    private EnlaceCuitService enlaceCuitService;

    @Test
    @DisplayName("Bultos por estado: cada filtro trae solo ese estado y solo los del alcance; estado inválido → 400")
    void bultosPorEstado() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CATAMARCA);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "FL-0001", 4);
        Bulto armado = escenario.bultoArmado(actores, circuito, lote, 2);
        Bulto enViaje = escenario.bultoArmado(actores, circuito, lote, 2);
        escenario.viajeEnTransito(actores.adminLaboratorio(), enViaje);
        String laboratorio = token(actores.adminLaboratorio());

        assertEquals(List.of(armado.getCodigo()), codigos(pagina("/api/bultos?estado=ARMADO", laboratorio)));
        assertEquals(List.of(enViaje.getCodigo()), codigos(pagina("/api/bultos?estado=EN_TRANSITO", laboratorio)));
        assertEquals(2, total(pagina("/api/bultos", laboratorio)), "sin filtro, todos los del alcance");
        assertEquals(1, total(pagina("/api/bultos?estado=ARMADO", token(actores.adminFarmacia()))),
                "la farmacia del circuito los ve desde ARMADO");

        EscenarioIntegracion.Actores otros = escenario.actores(Provincia.CATAMARCA);
        assertEquals(0, total(pagina("/api/bultos?estado=ARMADO", token(otros.adminFarmacia()))), "lo ajeno no se ve");
        mockMvc.perform(get("/api/bultos?estado=INEXISTENTE").header("Authorization", laboratorio))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Empresas, circuitos, inspectores, lotes, cuarentenas y reportes por estado (con el alcance de cada rol)")
    void otrosListadosPorEstado() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.LA_RIOJA);
        Medicamento medicamento = escenario.medicamento(actores);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        String laboratorio = token(actores.adminLaboratorio());

        // Empresas: la Sede filtra por cualquier estado; una empresa solo ve las HABILITADA.
        escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.LA_RIOJA);
        assertEquals(1, total(pagina("/api/empresas?estado=PENDIENTE", sede)));
        assertEquals(3, total(pagina("/api/empresas?estado=HABILITADA", sede)));
        assertEquals(0, total(pagina("/api/empresas?estado=PENDIENTE", token(actores.adminFarmacia()))));

        // Circuitos: uno APROBADO y otro recién propuesto (PENDIENTE_EMPRESAS) con otra farmacia.
        escenario.circuitoAprobado(actores);
        var otraFarmacia = escenario.empresaHabilitada(TipoEmpresa.FARMACIA, Provincia.LA_RIOJA, actores.inspector());
        ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> enlaceCuitService.proponer(
                actores.distribuidora().getCuit(), otraFarmacia.getCuit()));
        assertEquals(1, total(pagina("/api/circuitos?estado=APROBADO", sede)));
        assertEquals(1, total(pagina("/api/circuitos?estado=PENDIENTE_EMPRESAS", laboratorio)));
        assertEquals(0, total(pagina("/api/circuitos?estado=PENDIENTE_EMPRESAS", token(actores.adminFarmacia()))),
                "la propuesta es con otra farmacia");

        // Inspectores: uno dado de baja.
        InspectorAnmat otro = escenario.inspector(Provincia.LA_RIOJA);
        mockMvc.perform(post("/api/inspectores-anmat/" + otro.getId() + "/baja").header("Authorization", sede))
                .andExpect(status().isOk());
        assertEquals(1, total(pagina("/api/inspectores-anmat?estado=BAJA", sede)));
        assertEquals(1, total(pagina("/api/inspectores-anmat?estado=ACTIVO", sede)));

        // Lotes: uno sin liberar y otro liberado.
        escenario.lote(actores, medicamento, "FL-PEND", 2);
        Lote liberado = escenario.loteLiberado(actores, medicamento, "FL-LIB", 2);
        assertEquals(1, total(pagina("/api/lotes?estado=PENDIENTE_LIBERACION", laboratorio)));
        assertEquals(1, total(pagina("/api/lotes?estado=LIBERADO", laboratorio)));

        // Cuarentenas: una ACTIVA sobre el lote liberado, vista por su laboratorio.
        String inspector = token(actores.inspector().getUsuario());
        mockMvc.perform(post("/api/cuarentenas").header("Authorization", inspector)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "loteId", liberado.getId().toString(), "motivo", "PREVENTIVA", "descripcion", "Control"))))
                .andExpect(status().isCreated());
        assertEquals(1, total(pagina("/api/cuarentenas?estado=ACTIVA", laboratorio)));
        assertEquals(0, total(pagina("/api/cuarentenas?estado=LEVANTADA", laboratorio)));

        // Reportes: el paciente ve los suyos, filtrados.
        mockMvc.perform(post("/api/registro/pacientes").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", "paciente-filtros@integracion.test",
                                "password", EscenarioIntegracion.CLAVE, "nombre", "Pía", "apellido", "Filtros",
                                "dni", "31222333"))))
                .andExpect(status().isCreated());
        String paciente = bearer("paciente-filtros@integracion.test", EscenarioIntegracion.CLAVE);
        mockMvc.perform(post("/api/reportes-ciudadanos").header("Authorization", paciente)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "gtin", medicamento.getGtin(), "serie", "NOEXISTE01", "motivo", "OTRO",
                                "provincia", "LA_RIOJA", "descripcion", "Duda"))))
                .andExpect(status().isCreated());
        assertEquals(1, total(pagina("/api/reportes-ciudadanos?estado=ABIERTO", paciente)));
        assertEquals(0, total(pagina("/api/reportes-ciudadanos?estado=CERRADO", paciente)));
    }

    @Test
    @DisplayName("Temperatura de un viaje: origen, receptora del tramo, Sede e inspectores sí; otra empresa 404; el GPS sigue solo para el origen")
    void temperaturaDelViaje() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.JUJUY);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "FL-TEMP", 2);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        String laboratorio = token(actores.adminLaboratorio());
        String distribuidora = token(actores.adminDistribuidora());
        String farmacia = token(actores.adminFarmacia());
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        String otraFarmacia = token(escenario.actores(Provincia.JUJUY).adminFarmacia());

        // Tramo 1: laboratorio → distribuidora. Lectura de temperatura y de GPS del origen.
        DespachoLogistico tramo1 = escenario.viajeEnTransito(actores.adminLaboratorio(), bulto);
        leerTemperatura(laboratorio, tramo1, "20.5");
        leerGps(laboratorio, tramo1);
        String temperaturas1 = "/api/viajes/" + tramo1.getId() + "/telemetria-temperatura";
        assertEquals(1, total(pagina(temperaturas1, laboratorio)), "el origen");
        assertEquals(1, total(pagina(temperaturas1, distribuidora)), "la receptora del tramo 1");
        assertEquals(1, total(pagina(temperaturas1, sede)), "la Sede");
        mockMvc.perform(get(temperaturas1).header("Authorization", farmacia)).andExpect(status().isNotFound());
        mockMvc.perform(get(temperaturas1).header("Authorization", otraFarmacia)).andExpect(status().isNotFound());
        assertEquals(0, total(pagina("/api/telemetria-gps", distribuidora)), "el GPS no cambió: solo el origen");
        assertEquals(1, total(pagina("/api/telemetria-gps", laboratorio)));

        // Tramo 2: distribuidora → farmacia. La farmacia receptora ve la temperatura (también en el listado y el detalle).
        escenario.recibir(actores.adminDistribuidora(), bulto, 2);
        DespachoLogistico tramo2 = escenario.viajeEnTransito(actores.adminDistribuidora(), bulto);
        String lectura = leerTemperatura(distribuidora, tramo2, "21");
        assertEquals(1, total(pagina("/api/viajes/" + tramo2.getId() + "/telemetria-temperatura", farmacia)));
        assertEquals(1, total(pagina("/api/telemetria-temperatura", farmacia)), "solo la del tramo que recibe");
        mockMvc.perform(get("/api/telemetria-temperatura/" + lectura).header("Authorization", farmacia))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/telemetria-temperatura/" + lectura).header("Authorization", otraFarmacia))
                .andExpect(status().isNotFound());
        assertEquals(0, total(pagina("/api/telemetria-temperatura", otraFarmacia)));
    }

    // ---------- Utilidades ----------

    /** Lectura de temperatura enviada por la empresa origen (viaje EN_TRANSITO); devuelve su id. */
    private String leerTemperatura(String origen, DespachoLogistico viaje, String grados) throws Exception {
        String cuerpo = JSON.writeValueAsString(Map.of("sensorId", "SENSOR-IT", "temperatura", grados,
                "fechaHora", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                "despachoId", viaje.getId().toString()));
        String respuesta = mockMvc.perform(post("/api/telemetria-temperatura").header("Authorization", origen)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json(respuesta).path("id").asString();
    }

    /** Lectura de GPS enviada por la empresa origen. */
    private void leerGps(String origen, DespachoLogistico viaje) throws Exception {
        String cuerpo = JSON.writeValueAsString(Map.of("sensorId", "GPS-IT", "latitud", -24.18, "longitud", -65.3,
                "lugar", "Ruta 9", "fechaHora", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                "despachoId", viaje.getId().toString()));
        mockMvc.perform(post("/api/telemetria-gps").header("Authorization", origen)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
    }

    private String token(com.medichain.modules.usuario.Usuario usuario) throws Exception {
        return bearer(usuario.getEmail(), EscenarioIntegracion.CLAVE);
    }

    private JsonNode pagina(String url, String token) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static long total(JsonNode pagina) {
        return pagina.path("page").path("totalElements").asLong();
    }

    private static List<String> codigos(JsonNode pagina) {
        List<String> codigos = new ArrayList<>();
        pagina.path("content").forEach(elemento -> codigos.add(elemento.path("codigo").asString()));
        return codigos;
    }
}
