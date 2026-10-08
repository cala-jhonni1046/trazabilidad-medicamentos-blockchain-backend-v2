package com.medichain.integracion;

import com.medichain.config.InicializadorBaseDatos;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.registroblockchain.AnclajeProperties;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración MedichainApplicationIT en MediChain.
 * Reemplaza al viejo contextLoads (que necesitaba una base y variables de
 * entorno): el contexto completo arranca contra el PostgreSQL del contenedor
 * y deja creado lo que InicializadorBaseDatos agrega por SQL nativo
 * (secuencias, índices únicos parciales, fila de cadena_estado). Además
 * comprueba las protecciones de la suite: la base es la del contenedor
 * (nunca la del usuario), el anclaje está apagado y el perfil demo no está
 * activo; y la conversión única de inspectores_anmat.provincia (posición →
 * nombre) sobre una base creada antes del paso 9.
 */
class MedichainApplicationIT extends IntegracionBase {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Autowired
    private AnclajeProperties anclajeProperties;

    @Autowired
    private InicializadorBaseDatos inicializadorBaseDatos;

    @Autowired
    private InspectorAnmatRepository inspectorAnmatRepository;

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
    @DisplayName("InicializadorBaseDatos: secuencias de códigos, índices únicos parciales y cadena en GENESIS")
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

    @Test
    @DisplayName("Base anterior al paso 9: provincia del inspector en smallint → al arrancar pasa a texto sin perder datos")
    void convierteProvinciaGuardadaPorPosicion() {
        InspectorAnmat inspector = escenario.inspector(Provincia.MENDOZA);
        String checkDeHibernate = checkDeProvincia();
        // Deja la columna como la creaba el código anterior: posición del enum y CHECK numérico.
        StringJoiner nombreAPosicion = new StringJoiner(" ", "CASE provincia ", " END");
        for (Provincia provincia : Provincia.values()) {
            nombreAPosicion.add("WHEN '" + provincia.name() + "' THEN " + provincia.ordinal());
        }
        jdbc.execute("ALTER TABLE inspectores_anmat DROP CONSTRAINT IF EXISTS inspectores_anmat_provincia_check");
        jdbc.execute("ALTER TABLE inspectores_anmat ALTER COLUMN provincia TYPE smallint USING (" + nombreAPosicion + ")");
        jdbc.execute("ALTER TABLE inspectores_anmat ADD CONSTRAINT inspectores_anmat_provincia_check "
                + "CHECK (provincia BETWEEN 0 AND " + (Provincia.values().length - 1) + ")");
        assertEquals("smallint", tipoDeProvincia());
        assertEquals(Provincia.MENDOZA.ordinal(), jdbc.queryForObject(
                "SELECT provincia FROM inspectores_anmat", Integer.class));

        inicializadorBaseDatos.run();

        assertEquals("character varying", tipoDeProvincia());
        assertEquals("MENDOZA", jdbc.queryForObject("SELECT provincia FROM inspectores_anmat", String.class));
        assertEquals(checkDeHibernate, checkDeProvincia(), "el mismo CHECK que crea Hibernate en una base nueva");
        assertEquals(Provincia.MENDOZA, inspectorAnmatRepository.findById(inspector.getId()).orElseThrow().getProvincia());

        // Idempotente: con la columna ya en texto no hace nada.
        inicializadorBaseDatos.run();
        assertEquals("character varying", tipoDeProvincia());
        assertEquals(checkDeHibernate, checkDeProvincia());
    }

    private String tipoDeProvincia() {
        return jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'inspectores_anmat' AND column_name = 'provincia'", String.class);
    }

    private String checkDeProvincia() {
        return jdbc.queryForObject("SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                + "JOIN pg_class t ON t.oid = c.conrelid WHERE t.relname = 'inspectores_anmat' "
                + "AND c.contype = 'c' AND pg_get_constraintdef(c.oid) LIKE '%provincia%'", String.class);
    }

    /** Definición SQL de un índice. */
    private String definicion(String indice) {
        return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indice);
    }
}
