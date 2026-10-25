package com.medichain.integracion;

import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración AislamientoMedicamentosIT en MediChain.
 * El catálogo de medicamentos se aísla por rol: la Sede ve todo; un
 * laboratorio ve solo los suyos; una distribuidora o farmacia ve los de los
 * laboratorios con los que tiene un circuito APROBADO. Un medicamento fuera
 * del alcance responde 404 en el detalle por id. Dos cadenas independientes
 * (A y B, en provincias distintas): B no tiene circuito con las empresas de A.
 */
class AislamientoMedicamentosIT extends IntegracionBase {

    private EscenarioIntegracion.Actores a;
    private EscenarioIntegracion.Actores b;
    private Medicamento medicamentoA;
    private Medicamento medicamentoB;
    private String sede;

    @BeforeEach
    void escenario() throws Exception {
        a = escenario.actores(Provincia.TIERRA_DEL_FUEGO);
        escenario.circuitoAprobado(a);               // circuito APROBADO labA → distA → farmA
        medicamentoA = escenario.medicamento(a);
        b = escenario.actores(Provincia.SALTA);       // cadena independiente, SIN circuito aprobado
        medicamentoB = escenario.medicamento(b);
        sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
    }

    @Test
    @DisplayName("La Sede ve todo el catálogo; el laboratorio A ve solo el suyo (no el de B)")
    void sedeVeTodoLaboratorioSoloLoSuyo() throws Exception {
        List<String> catalogoSede = idsDelCatalogo(sede);
        assertTrue(catalogoSede.contains(medicamentoA.getId().toString()), "la Sede ve el de A");
        assertTrue(catalogoSede.contains(medicamentoB.getId().toString()), "la Sede ve el de B");

        List<String> catalogoLabA = idsDelCatalogo(token(a.adminLaboratorio()));
        assertTrue(catalogoLabA.contains(medicamentoA.getId().toString()), "el laboratorio A ve el suyo");
        assertFalse(catalogoLabA.contains(medicamentoB.getId().toString()), "el laboratorio A NO ve el de B");
    }

    @Test
    @DisplayName("La distribuidora y la farmacia de A ven el medicamento de A (circuito APROBADO), no el de B")
    void receptorasVenPorCircuitoAprobado() throws Exception {
        for (String token : List.of(token(a.adminDistribuidora()), token(a.adminFarmacia()))) {
            List<String> catalogo = idsDelCatalogo(token);
            assertTrue(catalogo.contains(medicamentoA.getId().toString()), "ve el de A (circuito aprobado con labA)");
            assertFalse(catalogo.contains(medicamentoB.getId().toString()), "no ve el de B (sin circuito con labB)");
        }
    }

    @Test
    @DisplayName("Una farmacia sin ningún circuito aprobado ve el catálogo vacío (no un error)")
    void sinCircuitoCatalogoVacio() throws Exception {
        // La farmacia de la cadena B está HABILITADA pero no tiene ningún circuito APROBADO.
        List<String> catalogo = idsDelCatalogo(token(b.adminFarmacia()));
        assertEquals(List.of(), catalogo, "sin circuito aprobado, el catálogo es vacío");
    }

    @Test
    @DisplayName("getById: la farmacia de A ve el medicamento de A por id, pero el de B (sin circuito) → 404")
    void getByIdFueraDeAlcance404() throws Exception {
        String farmaciaA = token(a.adminFarmacia());
        mockMvc.perform(get("/api/medicamentos/" + medicamentoA.getId()).header("Authorization", farmaciaA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/medicamentos/" + medicamentoB.getId()).header("Authorization", farmaciaA))
                .andExpect(status().isNotFound());
    }

    // ---------- Utilidades ----------

    private String token(com.medichain.modules.usuario.Usuario usuario) throws Exception {
        return bearer(usuario.getEmail(), EscenarioIntegracion.CLAVE);
    }

    private List<String> idsDelCatalogo(String token) throws Exception {
        JsonNode pagina = json(mockMvc.perform(get("/api/medicamentos?size=100").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        pagina.path("content").forEach(m -> ids.add(m.path("id").asString()));
        return ids;
    }
}
