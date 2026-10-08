package com.medichain.integracion;

import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utilidad de prueba LimpiadorBase en MediChain.
 * Deja la base del contenedor como recién creada antes de cada test de
 * integración, en ~20-50 ms:
 * <ul>
 *   <li>TRUNCATE … CASCADE de TODAS las tablas del esquema public, leídas de
 *       pg_tables (una tabla nueva nunca se olvida), salvo cadena_estado y
 *       flyway_schema_history (el historial de migraciones: el esquema no se toca);</li>
 *   <li>cadena_estado vuelve a (0, GENESIS): es una fila única que crea la
 *       migración V1__esquema_inicial, no se borra;</li>
 *   <li>las secuencias de los códigos vuelven a 1 (CIR-0001, BUL-0001, VJ-0001,
 *       REP-0001 en cada test);</li>
 *   <li>se borran los PDF de la carpeta de documentos de los tests.</li>
 * </ul>
 */
public final class LimpiadorBase {

    private static final List<String> SECUENCIAS = List.of("circuito_codigo_seq", "bulto_codigo_seq",
            "viaje_codigo_seq", "reporte_codigo_seq");
    private static final Path DOCUMENTOS = Path.of("target/it-documentos");

    private LimpiadorBase() {
    }

    /** Vacía la base, reinicia la cadena y las secuencias, y borra los documentos. */
    public static void limpiar(JdbcTemplate jdbc) {
        List<String> tablas = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                + "AND tablename NOT IN ('cadena_estado', 'flyway_schema_history') ORDER BY tablename", String.class);
        if (!tablas.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tablas) + " CASCADE");
        }
        jdbc.update("UPDATE cadena_estado SET ultimo_numero = 0, ultimo_hash = 'GENESIS'");
        for (String secuencia : SECUENCIAS) {
            jdbc.execute("ALTER SEQUENCE " + secuencia + " RESTART WITH 1");
        }
        borrarDocumentos();
    }

    /** Borra los PDF que dejaron los tests anteriores. */
    private static void borrarDocumentos() {
        if (!Files.isDirectory(DOCUMENTOS)) {
            return;
        }
        try (Stream<Path> archivos = Files.list(DOCUMENTOS)) {
            for (Path archivo : archivos.toList()) {
                Files.deleteIfExists(archivo);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
