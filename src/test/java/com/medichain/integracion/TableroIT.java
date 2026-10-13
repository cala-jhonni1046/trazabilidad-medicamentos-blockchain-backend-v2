package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración TableroIT en MediChain (paso B12d).
 * <ul>
 *   <li>Resumen de la Sede: cada total es el que muestra el listado correspondiente (empresas,
 *       pendientes, inspectores activos, circuitos aprobados, cuarentenas activas, eventos);
 *       solo la Sede lo ve.</li>
 *   <li>Bandeja del inspector: cada contador es el total de su bandeja (las mismas consultas),
 *       solo de su provincia; solo un inspector lo ve.</li>
 * </ul>
 */
class TableroIT extends IntegracionBase {

    @Autowired
    private EjecutorComoUsuario ejecutor;

    @Autowired
    private EnlaceCuitService enlaceCuitService;

    @Test
    @DisplayName("Resumen de la Sede: cada total coincide con su listado; un inspector o una empresa → 403")
    void resumenDeLaSede() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.TUCUMAN);
        escenario.circuitoAprobado(actores);
        escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.TUCUMAN);
        Medicamento medicamento = escenario.medicamento(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "TB-0001", 3);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        String inspector = bearer(actores.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        abrirCuarentena(inspector, lote);

        JsonNode resumen = obtener("/api/tablero/resumen", sede);

        assertEquals(total("/api/empresas", sede), resumen.path("empresas").asLong());
        assertEquals(4, resumen.path("empresas").asLong(), "laboratorio, distribuidora, farmacia y la pendiente");
        assertEquals(total("/api/empresas?estado=PENDIENTE", sede), resumen.path("empresasPendientes").asLong());
        assertEquals(1, resumen.path("empresasPendientes").asLong());
        assertEquals(total("/api/inspectores-anmat?estado=ACTIVO", sede), resumen.path("inspectoresActivos").asLong());
        assertEquals(1, resumen.path("inspectoresActivos").asLong());
        assertEquals(total("/api/circuitos?estado=APROBADO", sede), resumen.path("circuitosAprobados").asLong());
        assertEquals(1, resumen.path("circuitosAprobados").asLong());
        assertEquals(total("/api/cuarentenas?estado=ACTIVA", sede), resumen.path("cuarentenasActivas").asLong());
        assertEquals(1, resumen.path("cuarentenasActivas").asLong());
        assertEquals(total("/api/eventos-trazabilidad", sede), resumen.path("eventos").asLong(),
                "el último número de la cadena es la cantidad de eventos (sin huecos)");
        assertEquals(0, resumen.path("anclajesConfirmados").asLong(), "anclaje apagado: ninguno confirmado");

        mockMvc.perform(get("/api/tablero/resumen").header("Authorization", inspector)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tablero/resumen").header("Authorization",
                bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tablero/resumen")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bandeja del inspector: cada contador es el total de su bandeja y solo de su provincia; la Sede → 403")
    void bandejaDelInspector() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.TUCUMAN);
        String inspector = bearer(actores.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");

        // Una solicitud de habilitación pendiente en su provincia y otra en otra provincia (no cuenta).
        escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.TUCUMAN);
        escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.JUJUY);
        // Un circuito que aceptaron las dos empresas y espera al inspector (la farmacia es de su provincia).
        Empresa otraFarmacia = escenario.empresaHabilitada(TipoEmpresa.FARMACIA, Provincia.TUCUMAN, actores.inspector());
        EnlaceCuit propuesto = ejecutor.ejecutarComo(actores.adminLaboratorio(),
                () -> enlaceCuitService.proponer(actores.distribuidora().getCuit(), otraFarmacia.getCuit()));
        ejecutor.ejecutarComo(actores.adminDistribuidora(), () -> enlaceCuitService.aceptar(propuesto.getId()));
        ejecutor.ejecutarComo(escenario.admin(otraFarmacia), () -> enlaceCuitService.aceptar(propuesto.getId()));
        // Una cuarentena ACTIVA que abrió el propio inspector.
        Medicamento medicamento = escenario.medicamento(actores);
        abrirCuarentena(inspector, escenario.loteLiberado(actores, medicamento, "TB-0002", 3));
        // Un reporte ABIERTO de un paciente en su provincia.
        mockMvc.perform(post("/api/registro/pacientes").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", "paciente-tablero@integracion.test",
                                "password", EscenarioIntegracion.CLAVE, "nombre", "Pía", "apellido", "Tablero",
                                "dni", "31222444")))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/reportes-ciudadanos")
                        .header("Authorization", bearer("paciente-tablero@integracion.test", EscenarioIntegracion.CLAVE))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "gtin", medicamento.getGtin(), "serie", "NOEXISTE02", "motivo", "OTRO",
                                "provincia", "TUCUMAN", "descripcion", "Duda")))).andExpect(status().isCreated());

        JsonNode bandeja = obtener("/api/tablero/bandeja-inspector", inspector);

        assertEquals(total("/api/empresas/bandeja", inspector), bandeja.path("empresas").asLong());
        assertEquals(1, bandeja.path("empresas").asLong(), "solo la de su provincia");
        assertEquals(total("/api/circuitos/bandeja", inspector), bandeja.path("circuitos").asLong());
        assertEquals(1, bandeja.path("circuitos").asLong());
        assertEquals(total("/api/lotes/bandeja-liberacion", inspector), bandeja.path("lotesBiologicos").asLong());
        assertEquals(0, bandeja.path("lotesBiologicos").asLong());
        assertEquals(total("/api/cuarentenas/bandeja", inspector), bandeja.path("cuarentenas").asLong());
        assertEquals(1, bandeja.path("cuarentenas").asLong());
        assertEquals(total("/api/reportes-ciudadanos/bandeja", inspector), bandeja.path("reportes").asLong());
        assertEquals(1, bandeja.path("reportes").asLong());

        // Un inspector de otra provincia ve solo lo suyo (aquí: la solicitud de Jujuy).
        EscenarioIntegracion.Actores otros = escenario.actores(Provincia.JUJUY);
        JsonNode deJujuy = obtener("/api/tablero/bandeja-inspector",
                bearer(otros.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE));
        assertEquals(1, deJujuy.path("empresas").asLong());
        assertEquals(0, deJujuy.path("circuitos").asLong());
        assertEquals(0, deJujuy.path("cuarentenas").asLong());
        assertEquals(0, deJujuy.path("reportes").asLong());

        mockMvc.perform(get("/api/tablero/bandeja-inspector").header("Authorization", sede)).andExpect(status().isForbidden());
    }

    // ---------- Utilidades ----------

    private void abrirCuarentena(String inspector, Lote lote) throws Exception {
        mockMvc.perform(post("/api/cuarentenas").header("Authorization", inspector)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "loteId", lote.getId().toString(), "motivo", "PREVENTIVA", "descripcion", "Control"))))
                .andExpect(status().isCreated());
    }

    private JsonNode obtener(String url, String token) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long total(String url, String token) throws Exception {
        return obtener(url + (url.contains("?") ? "&" : "?") + "size=1", token).path("page").path("totalElements").asLong();
    }
}
