package com.medichain.modules.trazabilidad;

import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test unitario JsonCanonicoTest en MediChain.
 * Comprueba que el JSON canónico sea determinista y que el hash de un
 * evento fijo coincida con un valor calculado FUERA de Java (sha256sum),
 * para detectar cualquier cambio accidental en el formato de la cadena.
 */
class JsonCanonicoTest {

    private static final UUID ACTOR_USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_EMPRESA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENTIDAD = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Evento fijo usado en el test de hash obligatorio. */
    private EventoTrazabilidad eventoFijo() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", "L2026-0001");
        datos.put("cantidad", 100);
        datos.put("temperatura", new BigDecimal("5.50"));
        return new EventoTrazabilidad(1L, TipoEvento.LOTE_REGISTRADO,
                Instant.parse("2026-01-15T12:30:45.123456789Z"), "Lote", ENTIDAD,
                JsonCanonico.escribir(datos), ACTOR_USUARIO, ACTOR_EMPRESA, EventoTrazabilidad.GENESIS);
    }

    @Test
    @DisplayName("OBLIGATORIO: el hash de un evento fijo coincide con el valor esperado (calculado con sha256sum)")
    void hashFijoContraValorEsperado() {
        EventoTrazabilidad evento = eventoFijo();

        assertEquals("{\"actorEmpresaId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"actorUsuarioId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"datos\":{\"cantidad\":100,\"codigo\":\"L2026-0001\",\"temperatura\":\"5.5\"},"
                + "\"entidadId\":\"33333333-3333-3333-3333-333333333333\",\"entidadTipo\":\"Lote\","
                + "\"fechaHora\":\"2026-01-15T12:30:45.123456Z\",\"hashAnterior\":\"GENESIS\","
                + "\"numero\":1,\"tipo\":\"LOTE_REGISTRADO\"}", evento.contenidoCanonico());
        assertEquals("f5e87fa18e853876df0b3cd904484f13fa3049bfed831ee4df0e071943390e19", evento.getHash());
    }

    @Test
    @DisplayName("Mismos datos en distinto orden de inserción → mismo JSON")
    void ordenDeInsercionNoImporta() {
        Map<String, Object> uno = new LinkedHashMap<>();
        uno.put("b", 2);
        uno.put("a", "x");
        Map<String, Object> dos = new LinkedHashMap<>();
        dos.put("a", "x");
        dos.put("b", 2);

        assertEquals(JsonCanonico.escribir(uno), JsonCanonico.escribir(dos));
        assertEquals("{\"a\":\"x\",\"b\":2}", JsonCanonico.escribir(uno));
    }

    @Test
    @DisplayName("Omite nulls y formatea fechas, decimales, enums, UUID y listas")
    void formatosDeValores() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nulo", null);
        datos.put("fecha", LocalDate.of(2028, 1, 5));
        datos.put("decimal", new BigDecimal("30.00"));
        datos.put("provincia", Provincia.MENDOZA);
        datos.put("id", UUID.fromString("AAAAAAAA-0000-0000-0000-000000000000"));
        datos.put("lista", List.of(1L, true));
        datos.put("texto", "di\"jo\n");

        assertEquals("{\"decimal\":\"30\",\"fecha\":\"2028-01-05\",\"id\":\"aaaaaaaa-0000-0000-0000-000000000000\","
                + "\"lista\":[1,true],\"provincia\":\"MENDOZA\",\"texto\":\"di\\\"jo\\n\"}", JsonCanonico.escribir(datos));
    }

    @Test
    @DisplayName("Rechaza tipos no previstos (nada de entidades enteras)")
    void rechazaTiposNoPrevistos() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("x", new Object());
        assertThrows(IllegalArgumentException.class, () -> JsonCanonico.escribir(datos));
    }
}
