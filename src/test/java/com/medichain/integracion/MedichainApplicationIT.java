package com.medichain.integracion;

import com.medichain.modules.registroblockchain.AnclajeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración MedichainApplicationIT en MediChain.
 * Reemplaza al viejo contextLoads (que necesitaba una base y variables de
 * entorno): el contexto completo arranca contra el PostgreSQL del contenedor,
 * con el esquema que crean las migraciones de Flyway (secuencias, índices
 * únicos parciales, fila de cadena_estado incluidos). Además comprueba las
 * protecciones de la suite: la base es la del contenedor (nunca la del
 * usuario), el anclaje está apagado y el perfil demo no está activo.
 * Las migraciones en sí las prueba MigracionesIT.
 */
class MedichainApplicationIT extends IntegracionBase {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Autowired
    private AnclajeProperties anclajeProperties;

    @Test
    @DisplayName("El contexto arranca contra el contenedor: la URL es la de Testcontainers, nunca la base del usuario")
    void usaLaBaseDelContenedor() throws Exception {
        try (Connection conexion = dataSource.getConnection()) {
            String url = conexion.getMetaData().getURL();
            assertEquals(POSTGRES.getJdbcUrl(), url);
            assertFalse(url.contains(":5433/"), "nunca la base del usuario (puerto 5433)");
        }
        assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("16."));
    }

    @Test
    @DisplayName("Migración V1: secuencias de códigos, índices únicos parciales y cadena en GENESIS")
    void objetosDeArranque() {
        List<String> secuencias = jdbc.queryForList(
                "SELECT sequencename FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename", String.class);
        assertTrue(secuencias.containsAll(List.of("bulto_codigo_seq", "circuito_codigo_seq", "reporte_codigo_seq",
                "viaje_codigo_seq")), secuencias.toString());

        List<String> indices = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class);
        assertTrue(indices.containsAll(List.of("ux_circuito_par_vigente", "ux_anclaje_en_curso",
                "ux_lote_laboratorio_codigo", "ux_unidad_gtin_serie", "ux_intento_verificacion_dia")), indices.toString());
        // Los dos parciales tienen su condición WHERE (si no, bloquearían también los RECHAZADO / FALLIDO).
        assertTrue(definicion("ux_circuito_par_vigente").contains("WHERE"));
        assertTrue(definicion("ux_anclaje_en_curso").contains("WHERE"));

        Map<String, Object> cadena = jdbc.queryForMap("SELECT nombre, ultimo_numero, ultimo_hash FROM cadena_estado");
        assertEquals("PRINCIPAL", cadena.get("nombre"));
        assertEquals(0L, ((Number) cadena.get("ultimo_numero")).longValue());
        assertEquals("GENESIS", cadena.get("ultimo_hash"));
    }

    @Test
    @DisplayName("Protecciones: perfil test (no demo), anclaje apagado y la Sede inicial creada por DatosIniciales")
    void configuracionDeLaSuite() {
        assertTrue(List.of(environment.getActiveProfiles()).contains("test"));
        assertFalse(List.of(environment.getActiveProfiles()).contains("demo"));
        assertFalse(anclajeProperties.isHabilitado());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE rol = 'SEDE_CENTRAL'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM empresas", Integer.class),
                "sin datos de demo");
    }

    /** Definición SQL de un índice. */
    private String definicion(String indice) {
        return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indice);
    }
}
