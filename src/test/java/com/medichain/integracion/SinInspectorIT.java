package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaService;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración SinInspectorIT en MediChain (paso B11c, R2 y R5).
 * Tablero de la Sede: GET /api/empresas/sin-inspector y
 * GET /api/circuitos/sin-inspector listan EXACTAMENTE lo que la Sede puede
 * asignar: pendiente, sin revisor y de una provincia (la de la farmacia, en
 * los circuitos) sin inspectores ACTIVO. Lo asignado sale de la lista; si el
 * único inspector de una provincia se da de baja, sus solicitudes vuelven a
 * aparecer. Solo la Sede los ve.
 */
class SinInspectorIT extends IntegracionBase {

    @Autowired
    private EjecutorComoUsuario ejecutor;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private EnlaceCuitService enlaceCuitService;

    @Test
    @DisplayName("Empresas sin inspector: solo las de provincias sin inspector ACTIVO; salen al asignarlas y vuelven si el inspector se da de baja")
    void empresasSinInspector() throws Exception {
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        InspectorAnmat deMendoza = escenario.inspector(Provincia.MENDOZA);
        InspectorAnmat deChaco = escenario.inspector(Provincia.CHACO);
        Empresa enFormosa = escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.FORMOSA);
        escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.MENDOZA);
        Empresa enChaco = escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.CHACO);
        ejecutor.ejecutarComo(deChaco.getUsuario(), () -> empresaService.tomar(enChaco.getId()));

        assertEquals(List.of(enFormosa.getCuit()), cuits(pagina("/api/empresas/sin-inspector", sede)),
                "solo Formosa: Mendoza y Chaco tienen inspector");

        mockMvc.perform(post("/api/empresas/" + enFormosa.getId() + "/asignar").header("Authorization", sede)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("inspectorId", deMendoza.getId().toString()))))
                .andExpect(status().isOk());
        assertEquals(List.of(), cuits(pagina("/api/empresas/sin-inspector", sede)), "asignada: sale de la lista");

        // La baja del único inspector de Chaco devuelve su solicitud tomada a la bandeja: ahora la asigna la Sede.
        mockMvc.perform(post("/api/inspectores-anmat/" + deChaco.getId() + "/baja").header("Authorization", sede))
                .andExpect(status().isOk());
        assertEquals(List.of(enChaco.getCuit()), cuits(pagina("/api/empresas/sin-inspector", sede)));

        mockMvc.perform(get("/api/empresas/sin-inspector")
                        .header("Authorization", bearer(deMendoza.getUsuario().getEmail(), EscenarioIntegracion.CLAVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Circuitos sin inspector: PENDIENTE_INSPECTOR con la farmacia en una provincia sin inspector; salen al asignarlos")
    void circuitosSinInspector() throws Exception {
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.MENDOZA);
        // Farmacia de Formosa (sin inspectores): la Sede asigna su solicitud al inspector de Mendoza, que la habilita.
        Empresa farmaciaFormosa = escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.FORMOSA);
        ejecutor.ejecutarComo(escenario.sede(), () -> empresaService.asignar(farmaciaFormosa.getId(),
                actores.inspector().getId()));
        ejecutor.ejecutarComo(actores.inspector().getUsuario(), () -> empresaService.habilitar(farmaciaFormosa.getId()));

        EnlaceCuit haciaFormosa = pendienteDeInspector(actores, farmaciaFormosa);
        pendienteDeInspector(actores, actores.farmacia());

        assertEquals(List.of(haciaFormosa.getCodigo()), codigos(pagina("/api/circuitos/sin-inspector", sede)),
                "solo el de la farmacia de Formosa: Mendoza tiene inspector");

        mockMvc.perform(post("/api/circuitos/" + haciaFormosa.getId() + "/asignar").header("Authorization", sede)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("inspectorId", actores.inspector().getId().toString()))))
                .andExpect(status().isOk());
        assertEquals(List.of(), codigos(pagina("/api/circuitos/sin-inspector", sede)));
    }

    /** El laboratorio propone el circuito hacia la farmacia y lo aceptan las dos empresas: queda PENDIENTE_INSPECTOR. */
    private EnlaceCuit pendienteDeInspector(EscenarioIntegracion.Actores actores, Empresa farmacia) {
        EnlaceCuit circuito = ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> enlaceCuitService.proponer(
                actores.distribuidora().getCuit(), farmacia.getCuit()));
        ejecutor.ejecutarComo(actores.adminDistribuidora(), () -> enlaceCuitService.aceptar(circuito.getId()));
        return ejecutor.ejecutarComo(escenario.admin(farmacia), () -> enlaceCuitService.aceptar(circuito.getId()));
    }

    private JsonNode pagina(String url, String token) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> cuits(JsonNode pagina) {
        List<String> cuits = new ArrayList<>();
        pagina.path("content").forEach(empresa -> cuits.add(empresa.path("cuit").asString()));
        return cuits;
    }

    private static List<String> codigos(JsonNode pagina) {
        List<String> codigos = new ArrayList<>();
        pagina.path("content").forEach(circuito -> codigos.add(circuito.path("codigo").asString()));
        return codigos;
    }
}
