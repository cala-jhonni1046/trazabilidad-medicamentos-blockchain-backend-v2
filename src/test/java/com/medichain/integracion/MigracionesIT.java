package com.medichain.integracion;

import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.utils.enums.Provincia;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración MigracionesIT en MediChain (paso 10).
 * El esquema lo crean SOLO las migraciones de Flyway; Hibernate solo lo valida.
 * <ul>
 *   <li>Archivos: nombre V&lt;n&gt;__descripcion.sql y versiones correlativas sin huecos.</li>
 *   <li>Historial: todas aplicadas con éxito, ninguna pendiente, checksums válidos.</li>
 *   <li>Aplican limpio en una base nueva, y un segundo migrate no hace nada.</li>
 *   <li>Una base con tablas y sin historial (anterior al paso 10) no se adopta.</li>
 *   <li>Hibernate valida el esquema (y falla si falta una columna).</li>
 *   <li>Deriva: el esquema de las migraciones es IGUAL al que deducen las
 *       entidades (columnas con tipo, largo y NOT NULL; PK, UNIQUE, FK y la lista
 *       de valores de cada CHECK de enum), ignorando solo los nombres. Cubre lo
 *       que ddl-auto=validate NO ve: un valor nuevo de enum sin su migración, un
 *       largo o un nullable cambiado.</li>
 *   <li>V2 conserva el instante exacto de las fechas de auditoría y no altera la
 *       cadena de hashes; la API las devuelve en UTC con Z.</li>
 *   <li>V3: cada índice ix_ está sobre una columna con clave foránea.</li>
 * </ul>
 * Las bases auxiliares se crean y se borran en el mismo contenedor.
 */
class MigracionesIT extends IntegracionBase {

    private static final Pattern NOMBRE_MIGRACION = Pattern.compile("V(\\d+)__[a-z0-9]+(_[a-z0-9]+)*\\.sql");
    private static final Pattern INSTANTE_UTC = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z");

    /** Columnas de cada tabla, normalizadas (sin nombres de restricciones). */
    private static final String SQL_COLUMNAS = "SELECT 'columna ' || table_name || '.' || column_name || ' ' || data_type"
            + " || coalesce('(' || character_maximum_length || ')', '')"
            + " || coalesce(' precision ' || numeric_precision || ',' || numeric_scale, '')"
            + " || coalesce(' fraccion ' || datetime_precision, '')"
            + " || CASE WHEN is_nullable = 'NO' THEN ' NOT NULL' ELSE '' END"
            + " || coalesce(' DEFAULT ' || column_default, '')"
            + " FROM information_schema.columns"
            + " WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'";

    /** Restricciones por tipo, tabla y columnas (y la tabla referida o los valores del CHECK), sin su nombre. */
    private static final String SQL_RESTRICCIONES = "SELECT CASE c.contype WHEN 'p' THEN 'pk ' WHEN 'u' THEN 'unique '"
            + " WHEN 'f' THEN 'fk ' WHEN 'c' THEN 'check ' ELSE c.contype::text || ' ' END"
            + " || c.conrelid::regclass::text || ' ('"
            + " || (SELECT string_agg(a.attname, ', ' ORDER BY a.attname) FROM pg_attribute a"
            + "     WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)) || ')'"
            + " || CASE c.contype WHEN 'f' THEN ' -> ' || c.confrelid::regclass::text"
            + "     WHEN 'c' THEN ' IN [' || (SELECT string_agg(m[1], ', ' ORDER BY m[1])"
            + "         FROM regexp_matches(pg_get_constraintdef(c.oid), '''([^'']*)''', 'g') m) || ']'"
            + "     ELSE '' END"
            + " FROM pg_constraint c"
            + " WHERE c.connamespace = 'public'::regnamespace AND c.conrelid::regclass::text <> 'flyway_schema_history'";

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Archivos de migración: V<n>__descripcion.sql en minúsculas y versiones 1..n sin huecos")
    void nombresYVersiones() throws IOException {
        Resource[] archivos = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*");
        Set<Integer> versiones = new TreeSet<>();
        for (Resource archivo : archivos) {
            Matcher nombre = NOMBRE_MIGRACION.matcher(archivo.getFilename());
            assertTrue(nombre.matches(), "nombre de migración inválido: " + archivo.getFilename());
            assertTrue(versiones.add(Integer.parseInt(nombre.group(1))), "versión repetida: " + archivo.getFilename());
        }
        List<Integer> esperadas = new ArrayList<>();
        for (int version = 1; version <= versiones.size(); version++) {
            esperadas.add(version);
        }
        assertEquals(esperadas, new ArrayList<>(versiones), "versiones correlativas desde 1, sin huecos");
    }

    @Test
    @DisplayName("Historial: todas las migraciones aplicadas con éxito, ninguna pendiente, checksums válidos; ddl-auto=validate")
    void historialCompleto() {
        MigrationInfo[] aplicadas = flyway.info().applied();
        assertTrue(aplicadas.length >= 3, "al menos V1, V2 y V3");
        for (int i = 0; i < aplicadas.length; i++) {
            assertEquals(String.valueOf(i + 1), aplicadas[i].getVersion().getVersion());
            assertEquals(MigrationState.SUCCESS, aplicadas[i].getState(), aplicadas[i].getScript());
        }
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.validateWithResult().validationSuccessful, "checksums de las migraciones aplicadas");
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    @DisplayName("Aplican limpio en una base nueva; un segundo migrate no hace nada; Hibernate valida el resultado")
    void aplicanLimpioYHibernateValida() {
        String base = "migraciones_limpias";
        String url = baseNueva(base);
        try {
            Flyway nueva = flywayPara(url).load();
            MigrateResult primera = nueva.migrate();
            assertTrue(primera.success);
            assertEquals(flyway.info().applied().length, primera.migrationsExecuted);
            assertEquals(0, nueva.migrate().migrationsExecuted, "idempotente: nada para aplicar");

            EsquemaHibernate.validar(url, POSTGRES.getUsername(), POSTGRES.getPassword(), ajustesDeLaApp());

            // Y validate sí detecta una columna faltante.
            new JdbcTemplate(fuente(url)).execute("ALTER TABLE medicamentos DROP COLUMN principio_activo");
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> EsquemaHibernate.validar(url, POSTGRES.getUsername(), POSTGRES.getPassword(), ajustesDeLaApp()));
            assertTrue(mensajes(error).contains("principio_activo"), mensajes(error));
        } finally {
            borrarBase(base);
        }
    }

    @Test
    @DisplayName("Una base con tablas y sin historial de Flyway (como una creada antes del paso 10) NO se adopta: no migra ni toca nada")
    void baseViejaSinHistorialNoSeAdopta() {
        String base = "base_vieja";
        String url = baseNueva(base);
        try {
            JdbcTemplate jdbcBase = new JdbcTemplate(fuente(url));
            jdbcBase.execute("CREATE TABLE usuarios (id uuid PRIMARY KEY, email varchar(150))");
            jdbcBase.update("INSERT INTO usuarios (id, email) VALUES (?, ?)", UUID.randomUUID(), "dato@viejo.test");

            FlywayException error = assertThrows(FlywayException.class, () -> flywayPara(url).load().migrate());

            assertTrue(error.getMessage().contains("no schema history table"), error.getMessage());
            assertEquals(1, jdbcBase.queryForObject("SELECT count(*) FROM usuarios", Integer.class), "no tocó los datos");
            assertEquals(0, jdbcBase.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"
                    + " AND tablename = 'flyway_schema_history'", Integer.class), "no creó el historial");
        } finally {
            borrarBase(base);
        }
    }

    @Test
    @DisplayName("Deriva: el esquema de las migraciones es igual al que deducen las entidades (sin contar los nombres)")
    void migracionesIgualesALasEntidades() {
        String baseMigraciones = "deriva_migraciones";
        String baseEntidades = "deriva_entidades";
        String urlMigraciones = baseNueva(baseMigraciones);
        String urlEntidades = baseNueva(baseEntidades);
        try {
            flywayPara(urlMigraciones).load().migrate();
            EsquemaHibernate.crear(urlEntidades, POSTGRES.getUsername(), POSTGRES.getPassword(), ajustesDeLaApp());

            Set<String> migraciones = catalogo(urlMigraciones);
            Set<String> entidades = catalogo(urlEntidades);
            Set<String> soloMigraciones = new TreeSet<>(migraciones);
            soloMigraciones.removeAll(entidades);
            Set<String> soloEntidades = new TreeSet<>(entidades);
            soloEntidades.removeAll(migraciones);
            assertTrue(soloMigraciones.isEmpty() && soloEntidades.isEmpty(),
                    "Las migraciones y las entidades no coinciden: falta una migración (V nueva) o una anotación.\n"
                            + "  Solo en las migraciones: " + soloMigraciones + "\n"
                            + "  Solo en las entidades:   " + soloEntidades);
            assertTrue(migraciones.size() > 300, "se compararon columnas y restricciones: " + migraciones.size());
        } finally {
            borrarBase(baseMigraciones);
            borrarBase(baseEntidades);
        }
    }

    @Test
    @DisplayName("V2: las fechas de auditoría conservan el instante exacto (aun con la sesión en hora de Argentina) y la cadena sigue íntegra")
    void v2ConservaInstantesYHashes() {
        String base = "migracion_fechas";
        String url = baseNueva(base);
        try {
            // Base en V1, con un evento real: su hash lo calcula EventoTrazabilidad, como en la app.
            flywayPara(url).target("1").load().migrate();
            JdbcTemplate jdbcBase = new JdbcTemplate(fuente(url));
            Instant fechaHora = Instant.parse("2026-10-08T12:34:56.123456Z");
            EventoTrazabilidad evento = new EventoTrazabilidad(1L, TipoEvento.ALTA_INSPECTOR, fechaHora, "Prueba",
                    UUID.randomUUID(), "{\"provincia\":\"MENDOZA\"}", null, null, EventoTrazabilidad.GENESIS);
            jdbcBase.update("INSERT INTO eventos_trazabilidad (id, numero, tipo, fecha_hora, entidad_tipo, entidad_id,"
                            + " datos_json, hash_anterior, hash, fecha_creacion, fecha_actualizacion, version)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    UUID.randomUUID(), evento.getNumero(), evento.getTipo().name(),
                    OffsetDateTime.ofInstant(fechaHora, ZoneOffset.UTC), evento.getEntidadTipo(), evento.getEntidadId(),
                    evento.getDatosJson(), evento.getHashAnterior(), evento.getHash(),
                    LocalDateTime.parse("2026-10-08T12:34:56.654321"), LocalDateTime.parse("2026-10-08T13:00:00.000001"));

            // V2 en adelante, con la sesión en otra zona horaria: AT TIME ZONE 'UTC' no depende de ella.
            flywayPara(url).initSql("SET TIME ZONE 'America/Argentina/Buenos_Aires'").load().migrate();

            Map<String, Object> fila = jdbcBase.queryForMap("SELECT * FROM eventos_trazabilidad");
            assertEquals(Instant.parse("2026-10-08T12:34:56.654321Z"), instante(fila.get("fecha_creacion")));
            assertEquals(Instant.parse("2026-10-08T13:00:00.000001Z"), instante(fila.get("fecha_actualizacion")));
            Instant fechaHoraLeida = instante(fila.get("fecha_hora"));
            assertEquals(fechaHora, fechaHoraLeida, "fecha_hora (la que entra al hash) no cambia");
            EventoTrazabilidad releido = new EventoTrazabilidad((Long) fila.get("numero"),
                    TipoEvento.valueOf((String) fila.get("tipo")), fechaHoraLeida, (String) fila.get("entidad_tipo"),
                    (UUID) fila.get("entidad_id"), (String) fila.get("datos_json"), null, null,
                    (String) fila.get("hash_anterior"));
            assertEquals(fila.get("hash"), releido.getHash(), "el hash recalculado después de V2 es el mismo");
        } finally {
            borrarBase(base);
        }
    }

    @Test
    @DisplayName("Fechas de auditoría: timestamptz en la base y en UTC con Z en la API, el mismo instante")
    void fechasDeAuditoriaEnUtc() throws Exception {
        assertEquals(List.of(), jdbc.queryForList("SELECT table_name || '.' || column_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND column_name IN ('fecha_creacion', 'fecha_actualizacion')"
                + " AND data_type <> 'timestamp with time zone'", String.class));
        InspectorAnmat inspector = escenario.inspector(Provincia.SALTA);

        String respuesta = mockMvc.perform(get("/api/inspectores-anmat/" + inspector.getId())
                        .header("Authorization", bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = json(respuesta);
        String fechaCreacion = json.get("fechaCreacion").asString();
        assertTrue(INSTANTE_UTC.matcher(fechaCreacion).matches(), "fechaCreacion en UTC con Z: " + fechaCreacion);
        assertTrue(INSTANTE_UTC.matcher(json.get("fechaActualizacion").asString()).matches(), respuesta);
        assertEquals(instante(jdbc.queryForObject("SELECT fecha_creacion FROM inspectores_anmat WHERE id = ?",
                Object.class, inspector.getId())), Instant.parse(fechaCreacion));
    }

    @Test
    @DisplayName("V3: cada índice ix_ está sobre una columna con clave foránea, sin repetir")
    void indicesSobreClavesForaneas() {
        List<String> indices = jdbc.queryForList("SELECT i.relname || ' ' || t.relname || '.' || a.attname"
                + " FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid JOIN pg_class t ON t.oid = x.indrelid"
                + " JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = x.indkey[0]"
                + " WHERE i.relname LIKE 'ix\\_%' AND x.indnatts = 1", String.class);
        assertEquals(29, indices.size(), indices.toString());
        Set<String> columnasConFk = new HashSet<>(jdbc.queryForList("SELECT c.conrelid::regclass::text || '.' || a.attname"
                + " FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]"
                + " WHERE c.contype = 'f'", String.class));
        Set<String> indexadas = new HashSet<>();
        for (String indice : indices) {
            String columna = indice.substring(indice.indexOf(' ') + 1);
            assertTrue(columnasConFk.contains(columna), "índice sobre una columna sin FK: " + indice);
            assertTrue(indexadas.add(columna), "columna indexada dos veces: " + indice);
            assertEquals("ix_" + columna.replace('.', '_'), indice.substring(0, indice.indexOf(' ')), "nombre del índice");
        }
    }

    // ---------- Utilidades ----------

    /** Ajustes de nombres con que la app construyó su EntityManagerFactory. */
    private Map<String, Object> ajustesDeLaApp() {
        return entityManagerFactory.getProperties();
    }

    /**
     * Flyway sobre otra base del contenedor con la MISMA configuración que la app
     * (application.properties: ubicación, baseline-on-migrate=false, validate-on-migrate, clean deshabilitado).
     */
    private FluentConfiguration flywayPara(String url) {
        return Flyway.configure().configuration(flyway.getConfiguration())
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Crea (o recrea) una base vacía en el contenedor y devuelve su URL JDBC. */
    private String baseNueva(String nombre) {
        borrarBase(nombre);
        jdbc.execute("CREATE DATABASE " + nombre);
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
    }

    /** Borra una base auxiliar (cerrando sus conexiones). */
    private void borrarBase(String nombre) {
        jdbc.execute("DROP DATABASE IF EXISTS " + nombre + " WITH (FORCE)");
    }

    private static DriverManagerDataSource fuente(String url) {
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Columnas y restricciones de una base, normalizadas para comparar. */
    private static Set<String> catalogo(String url) {
        JdbcTemplate jdbcBase = new JdbcTemplate(fuente(url));
        Set<String> catalogo = new TreeSet<>(jdbcBase.queryForList(SQL_COLUMNAS, String.class));
        catalogo.addAll(jdbcBase.queryForList(SQL_RESTRICCIONES, String.class));
        return catalogo;
    }

    /** Valor timestamptz leído por JDBC, como Instant. */
    private static Instant instante(Object valor) {
        if (valor instanceof OffsetDateTime fecha) {
            return fecha.toInstant();
        }
        if (valor instanceof java.sql.Timestamp fecha) {
            return fecha.toInstant();
        }
        throw new IllegalStateException("Tipo de fecha inesperado: " + valor);
    }

    /** Mensajes de toda la cadena de causas. */
    private static String mensajes(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable causa = error; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            texto.append(causa.getMessage()).append(" | ");
        }
        return texto.toString();
    }
}
