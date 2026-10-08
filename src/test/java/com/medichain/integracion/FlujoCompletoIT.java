package com.medichain.integracion;

import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.VerificacionCadenaResponseDTO;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración FlujoCompletoIT en MediChain.
 * El recorrido completo de una caja, del laboratorio al paciente, contra
 * PostgreSQL real:
 * <ol>
 *   <li>la farmacia se registra por la API pública (multipart con su PDF) y
 *       el inspector de su provincia la habilita;</li>
 *   <li>circuito aprobado, lote registrado y liberado, bulto, viaje del tramo 1
 *       con recepción en la distribuidora, viaje del tramo 2 con recepción en
 *       la farmacia (con el fixture, por los services reales);</li>
 *   <li>la farmacia dispensa una caja por la API;</li>
 *   <li>la verificación pública (sin token) la muestra YA_DISPENSADA con su
 *       recorrido y sin datos de la dispensación (R13); otra caja, APTA;</li>
 *   <li>la cadena queda íntegra y con los eventos de cada paso.</li>
 * </ol>
 */
class FlujoCompletoIT extends IntegracionBase {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private VerificadorCadena verificadorCadena;

    @Test
    @DisplayName("Del registro de la farmacia a la verificación pública de una caja dispensada; cadena íntegra")
    void recorridoCompleto() throws Exception {
        InspectorAnmat inspector = escenario.inspector(Provincia.CORDOBA);
        Empresa laboratorio = escenario.empresaHabilitada(TipoEmpresa.LABORATORIO, Provincia.CORDOBA, inspector);
        Empresa distribuidora = escenario.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR, Provincia.CORDOBA, inspector);

        // 1. Registro público de la farmacia (multipart con el PDF) y habilitación.
        String cuit = EscenarioIntegracion.cuitValido(90_000);
        byte[] pdf = "%PDF-1.4\n% habilitacion de la farmacia\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/registro/empresas")
                        .file(new MockMultipartFile("documento", "habilitacion.pdf", "application/pdf", pdf))
                        .param("tipo", "FARMACIA").param("cuit", cuit).param("razonSocial", "Farmacia del Flujo")
                        .param("gln", EscenarioIntegracion.gs1Valido("779911090000")).param("provincia", "CORDOBA")
                        .param("localidad", "Córdoba").param("domicilio", "Av. Colón 1000")
                        .param("numeroHabilitacion", "HAB-FLUJO").param("directorTecnico", "Farm. Flujo")
                        .param("adminEmail", "farmacia-flujo@integracion.test")
                        .param("adminPassword", EscenarioIntegracion.CLAVE).param("adminNombre", "Ana")
                        .param("adminApellido", "Flujo").param("adminDni", "29000111"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identificador").value(cuit))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        Empresa farmacia = empresaRepository.findByCuit(cuit).orElseThrow();
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)),
                farmacia.getDocumentoHash());
        assertTrue(Files.exists(Path.of("target/it-documentos", farmacia.getDocumentoHash() + ".pdf")));
        escenario.habilitar(farmacia, inspector);

        // 2. Circuito, lote, bulto y los dos tramos con sus recepciones.
        EscenarioIntegracion.Actores actores = new EscenarioIntegracion.Actores(inspector, laboratorio,
                escenario.admin(laboratorio), distribuidora, escenario.admin(distribuidora), farmacia,
                escenario.usuario("farmacia-flujo@integracion.test"));
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "FLUJO-01", 3);
        escenario.cajasEnFarmacia(actores, circuito, lote, 3);
        List<String> series = jdbc.queryForList("SELECT serie FROM unidades_trazables WHERE lote_id = ? ORDER BY serie",
                String.class, lote.getId());
        assertEquals(List.of("EN_STOCK"), jdbc.queryForList(
                "SELECT DISTINCT estado FROM unidades_trazables WHERE lote_id = ?", String.class, lote.getId()));
        assertEquals(List.of("FINALIZADO"), jdbc.queryForList(
                "SELECT DISTINCT estado FROM despachos_logisticos", String.class));

        // 3. Dispensación por la API.
        Map<String, Object> pedido = Map.of("gtin", medicamento.getGtin(), "serie", series.get(0),
                "particular", false, "obraSocial", "OSDE-FLUJO", "numeroAfiliado", "AF-123",
                "numeroReceta", "REC-FLUJO-1", "dni", "30111222");
        mockMvc.perform(post("/api/dispensaciones")
                        .header("Authorization", bearer("farmacia-flujo@integracion.test", EscenarioIntegracion.CLAVE))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(pedido)))
                .andExpect(status().isCreated());

        // 4. Verificación pública, sin token.
        String respuesta = mockMvc.perform(get("/api/verificacion")
                        .param("gtin", medicamento.getGtin()).param("serie", series.get(0)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode verificacion = json(respuesta);
        assertEquals("YA_DISPENSADA", verificacion.get("estado").asString());
        assertEquals("FLUJO-01", verificacion.get("lote").asString());
        assertTrue(verificacion.get("recorrido").size() >= 3, "recorrido resumido: " + respuesta);
        for (String privado : List.of("OSDE-FLUJO", "AF-123", "REC-FLUJO-1", "30111222", "*****222")) {
            assertFalse(respuesta.contains(privado), "la verificación pública no muestra " + privado);
        }
        mockMvc.perform(get("/api/verificacion").param("gtin", medicamento.getGtin()).param("serie", series.get(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APTA"));

        // 5. Cadena íntegra, con un evento por cada paso del recorrido.
        VerificacionCadenaResponseDTO cadena = verificadorCadena.verificar();
        assertTrue(cadena.isIntegra(), cadena.getMotivo());
        List<String> tipos = jdbc.queryForList("SELECT DISTINCT tipo FROM eventos_trazabilidad", String.class);
        assertTrue(tipos.containsAll(List.of("ALTA_INSPECTOR", "SOLICITUD_HABILITACION", "SOLICITUD_TOMADA",
                "HABILITACION_APROBADA", "MEDICAMENTO_REGISTRADO", "CIRCUITO_PROPUESTO", "CIRCUITO_ACEPTADO",
                "CIRCUITO_TOMADO", "CIRCUITO_APROBADO", "LOTE_REGISTRADO", "LOTE_LIBERADO", "BULTO_ARMADO",
                "VIAJE_CREADO", "VIAJE_SALIDA", "BULTO_RECIBIDO", "VIAJE_FINALIZADO", "DISPENSACION")), tipos.toString());
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM eventos_trazabilidad WHERE tipo = 'BULTO_RECIBIDO'", Integer.class),
                "una recepción por tramo");
        String dispensacion = jdbc.queryForObject(
                "SELECT datos_json FROM eventos_trazabilidad WHERE tipo = 'DISPENSACION'", String.class);
        assertNotNull(dispensacion);
        assertFalse(dispensacion.contains("OSDE-FLUJO") || dispensacion.contains("REC-FLUJO-1"), dispensacion);
    }
}
