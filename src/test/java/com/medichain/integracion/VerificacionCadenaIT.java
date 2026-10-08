package com.medichain.integracion;

import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.VerificacionCadenaResponseDTO;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración VerificacionCadenaIT en MediChain (R15).
 * Verificación de la cadena contra lo que PostgreSQL devuelve de verdad:
 * <ul>
 *   <li>Ida y vuelta: los hashes calculados al guardar se vuelven a obtener al
 *       leer (fechaHora en microsegundos, UUID, BigDecimal y fechas en el JSON
 *       canónico). Un desajuste de precisión rompería toda la cadena.</li>
 *   <li>Alteraciones hechas con SQL directo sobre la base (lo que haría quien
 *       tiene acceso a la base): contenido cambiado, fecha corrida un
 *       microsegundo, evento borrado en el medio o al final.</li>
 * </ul>
 * La verificación contra la blockchain no se prueba acá (anclaje apagado:
 * NO_DISPONIBLE); la cubren VerificadorAnclajesTest y scripts/simular-ataque.py.
 */
class VerificacionCadenaIT extends IntegracionBase {

    @Autowired
    private VerificadorCadena verificadorCadena;

    private long eventos;

    /** Escenario real con varios tipos de evento: altas, habilitaciones, medicamento, lote y liberación. */
    @BeforeEach
    void escenario() {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CORDOBA);
        Medicamento medicamento = escenario.medicamento(actores);
        escenario.loteLiberado(actores, medicamento, "VC-0001", 5);
        eventos = jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad", Long.class);
    }

    @Test
    @DisplayName("Ida y vuelta por PostgreSQL: la cadena da íntegra (endpoint real como Sede; blockchain NO_DISPONIBLE)")
    void cadenaIntegraTrasLeerDeLaBase() throws Exception {
        assertTrue(eventos >= 10, "el escenario genera varios eventos: " + eventos);

        String respuesta = mockMvc.perform(get("/api/eventos-trazabilidad/verificacion")
                        .header("Authorization", bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode verificacion = json(respuesta);
        assertTrue(verificacion.get("integra").asBoolean(), respuesta);
        assertEquals(eventos, verificacion.get("eventosVerificados").asLong());
        assertEquals("NO_DISPONIBLE", verificacion.get("blockchain").get("estado").asString());
    }

    @Test
    @DisplayName("Contenido alterado con SQL en el evento 3 → no íntegra, primerNumeroRoto 3")
    void contenidoAlterado() {
        jdbc.update("UPDATE eventos_trazabilidad SET datos_json = replace(datos_json, '\"', '\" ') WHERE numero = 3");

        VerificacionCadenaResponseDTO verificacion = verificadorCadena.verificar();

        assertFalse(verificacion.isIntegra());
        assertEquals(3L, verificacion.getPrimerNumeroRoto());
        assertEquals(2, verificacion.getEventosVerificados());
        assertTrue(verificacion.getMotivo().contains("alterado"), verificacion.getMotivo());
    }

    @Test
    @DisplayName("fechaHora corrida UN microsegundo → no íntegra (la precisión se conserva en la base y entra al hash)")
    void fechaCorridaUnMicrosegundo() {
        jdbc.update("UPDATE eventos_trazabilidad SET fecha_hora = fecha_hora + interval '1 microsecond' "
                + "WHERE numero = 4");

        VerificacionCadenaResponseDTO verificacion = verificadorCadena.verificar();

        assertFalse(verificacion.isIntegra());
        assertEquals(4L, verificacion.getPrimerNumeroRoto());
    }

    @Test
    @DisplayName("Evento del medio borrado → hueco detectado en ese número")
    void eventoDelMedioBorrado() {
        jdbc.update("DELETE FROM eventos_trazabilidad WHERE numero = 5");

        VerificacionCadenaResponseDTO verificacion = verificadorCadena.verificar();

        assertFalse(verificacion.isIntegra());
        assertEquals(5L, verificacion.getPrimerNumeroRoto());
        assertTrue(verificacion.getMotivo().startsWith("Falta el evento 5"), verificacion.getMotivo());
    }

    @Test
    @DisplayName("Último evento borrado → la cadena local no tiene hueco, pero no coincide con cadena_estado")
    void ultimoEventoBorrado() {
        jdbc.update("DELETE FROM eventos_trazabilidad WHERE numero = ?", eventos);

        VerificacionCadenaResponseDTO verificacion = verificadorCadena.verificar();

        assertFalse(verificacion.isIntegra());
        assertEquals(eventos, verificacion.getPrimerNumeroRoto());
        assertTrue(verificacion.getMotivo().contains("no coincide"), verificacion.getMotivo());
    }
}
