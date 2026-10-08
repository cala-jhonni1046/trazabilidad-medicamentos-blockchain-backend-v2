package com.medichain.integracion;

import com.medichain.modules.auth.LoginRequestDTO;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración SeguridadIT en MediChain.
 * Seguridad de punta a punta con la cadena de filtros REAL (JWT firmado con
 * el secreto del perfil test, BCrypt, @PreAuthorize) y usuarios guardados en
 * PostgreSQL: login, 401 sin token o con token alterado, 403 por rol, 404 por
 * recurso de otra empresa o de otra provincia (D1), y la cuenta de un
 * inspector dado de baja: su token todavía vigente deja de servir porque el
 * filtro JWT confirma en la base que la cuenta siga activa.
 */
class SeguridadIT extends IntegracionBase {

    private static final String CLAVE_SEDE = "clave-falsa-de-la-sede";

    @Test
    @DisplayName("Login real: contraseña correcta → 200 con token; incorrecta o email inexistente → 401 genérico")
    void login() throws Exception {
        String sede = escenario.sede().getEmail();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new LoginRequestDTO(sede, CLAVE_SEDE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new LoginRequestDTO(sede, "otra-clave"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new LoginRequestDTO("nadie@integracion.test", CLAVE_SEDE))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin token → 401; token con la firma alterada → 401; token válido → 200")
    void sinTokenOAlterado() throws Exception {
        String token = token(escenario.sede().getEmail(), CLAVE_SEDE);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        mockMvc.perform(get("/api/inspectores-anmat")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inspectores-anmat").header("Authorization", "Bearer " + alterado))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inspectores-anmat").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rol sin permiso → 403: una farmacia no da de baja inspectores ni verifica la cadena")
    void rolIncorrecto() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CHACO);
        String farmacia = bearer(actores.adminFarmacia().getEmail(), EscenarioIntegracion.CLAVE);

        mockMvc.perform(post("/api/inspectores-anmat/" + actores.inspector().getId() + "/baja")
                        .header("Authorization", farmacia))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/eventos-trazabilidad/verificacion").header("Authorization", farmacia))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Recurso de otra empresa → 404 (no 403): un laboratorio no ve el lote de otro")
    void recursoDeOtraEmpresa() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CHACO);
        Lote lote = escenario.lote(actores, escenario.medicamento(actores), "SEG-0001", 2);
        Empresa otroLaboratorio = escenario.empresaHabilitada(TipoEmpresa.LABORATORIO, Provincia.CHACO,
                actores.inspector());

        mockMvc.perform(get("/api/lotes/" + lote.getId())
                        .header("Authorization", bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/lotes/" + lote.getId())
                        .header("Authorization", bearer(escenario.admin(otroLaboratorio).getEmail(), EscenarioIntegracion.CLAVE)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("D1: un inspector no toma la solicitud de una empresa de otra provincia → 404; el de la provincia sí")
    void inspectorDeOtraProvincia() throws Exception {
        InspectorAnmat deChaco = escenario.inspector(Provincia.CHACO);
        InspectorAnmat deJujuy = escenario.inspector(Provincia.JUJUY);
        Empresa pendiente = escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.CHACO);

        mockMvc.perform(post("/api/empresas/" + pendiente.getId() + "/tomar")
                        .header("Authorization", bearer(deJujuy.getUsuario().getEmail(), EscenarioIntegracion.CLAVE)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/empresas/" + pendiente.getId() + "/tomar")
                        .header("Authorization", bearer(deChaco.getUsuario().getEmail(), EscenarioIntegracion.CLAVE)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Inspector dado de baja: ya no inicia sesión y su token previo no sirve para actuar (401 del filtro)")
    void inspectorDadoDeBajaNoActua() throws Exception {
        InspectorAnmat inspector = escenario.inspector(Provincia.CHACO);
        Empresa pendiente = escenario.empresaRegistrada(TipoEmpresa.FARMACIA, Provincia.CHACO);
        String tokenPrevio = bearer(inspector.getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        darDeBaja(inspector);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new LoginRequestDTO(inspector.getUsuario().getEmail(),
                                EscenarioIntegracion.CLAVE))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/empresas/" + pendiente.getId() + "/tomar").header("Authorization", tokenPrevio))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Inspector dado de baja con token todavía válido → rechazado (401) también en rutas que solo miran el rol")
    void inspectorDadoDeBajaConTokenVigente() throws Exception {
        InspectorAnmat inspector = escenario.inspector(Provincia.CHACO);
        String tokenPrevio = bearer(inspector.getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        mockMvc.perform(get("/api/eventos-trazabilidad/verificacion").header("Authorization", tokenPrevio))
                .andExpect(status().isOk());
        darDeBaja(inspector);

        mockMvc.perform(get("/api/eventos-trazabilidad/verificacion").header("Authorization", tokenPrevio))
                .andExpect(status().isUnauthorized());
    }

    /** La Sede da de baja al inspector por la API. */
    private void darDeBaja(InspectorAnmat inspector) throws Exception {
        mockMvc.perform(post("/api/inspectores-anmat/" + inspector.getId() + "/baja")
                        .header("Authorization", bearer(escenario.sede().getEmail(), CLAVE_SEDE)))
                .andExpect(status().isOk());
    }
}
