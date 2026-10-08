package com.medichain.config;

import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.CadenaEstadoRepository;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.utils.enums.Provincia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.StringJoiner;

/**
 * Inicializador InicializadorBaseDatos en MediChain.
 * ÚNICO lugar con SQL nativo de arranque: lo que ddl-auto=update no sabe
 * crear. Corre primero ({@code @Order(0)}), después de que Hibernate crea
 * las tablas y antes de DatosIniciales y DatosDemo. Todo es idempotente
 * (IF NOT EXISTS / existe la fila):
 * <ul>
 *   <li>Fila de estado de la cadena de eventos (numero 0, hash GENESIS).</li>
 *   <li>Secuencias circuito_codigo_seq (CIR-0001), bulto_codigo_seq (BUL-0001),
 *       viaje_codigo_seq (VJ-0001) y reporte_codigo_seq (REP-0001): nextval es atómico, dos altas
 *       concurrentes nunca reciben el mismo número.</li>
 *   <li>Índice único parcial: un solo circuito VIGENTE por par
 *       laboratorio–farmacia (R5), red de seguridad ante propuestas concurrentes.</li>
 *   <li>Índice único parcial: un solo anclaje en curso (PENDIENTE o ENVIADO)
 *       por red (R15), para que nunca haya dos transacciones de anclaje a la vez.</li>
 *   <li>Conversión única de inspectores_anmat.provincia: hasta el paso 9 se
 *       guardaba por posición (smallint, sin @Enumerated). ddl-auto=update no
 *       cambia el tipo de una columna existente, así que una base creada antes
 *       se convierte acá a texto, sin perder datos. Solo corre si la columna
 *       todavía es smallint.</li>
 * </ul>
 * TODO paso 10: se reemplaza por la migración inicial de Flyway.
 */
@Component
@Order(0)
public class InicializadorBaseDatos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorBaseDatos.class);

    private final JdbcTemplate jdbcTemplate;
    private final CadenaEstadoRepository cadenaEstadoRepository;

    @Autowired
    public InicializadorBaseDatos(JdbcTemplate jdbcTemplate, CadenaEstadoRepository cadenaEstadoRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.cadenaEstadoRepository = cadenaEstadoRepository;
    }

    /** Crea las secuencias, los índices parciales y la fila inicial de la cadena si faltan. */
    @Override
    @Transactional
    public void run(String... args) {
        convertirProvinciaDeInspectorATexto();
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS circuito_codigo_seq START WITH 1 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS bulto_codigo_seq START WITH 1 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS viaje_codigo_seq START WITH 1 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS reporte_codigo_seq START WITH 1 INCREMENT BY 1");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_circuito_par_vigente "
                + "ON enlaces_cuit (laboratorio_id, farmacia_id) "
                + "WHERE estado IN ('PENDIENTE_EMPRESAS', 'PENDIENTE_INSPECTOR', 'APROBADO', 'SUSPENDIDO')");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_anclaje_en_curso "
                + "ON registros_blockchain (red) WHERE estado IN ('PENDIENTE', 'ENVIADO')");
        if (!cadenaEstadoRepository.existsByNombre(CadenaEstado.PRINCIPAL)) {
            cadenaEstadoRepository.save(new CadenaEstado(CadenaEstado.PRINCIPAL, 0L, EventoTrazabilidad.GENESIS));
            log.info("Cadena de eventos inicializada (numero 0, hash GENESIS)");
        }
    }

    /**
     * Si inspectores_anmat.provincia sigue siendo smallint (posición del enum
     * Provincia), la pasa a varchar(30) con el NOMBRE de cada provincia, igual
     * que la crea Hibernate en una base nueva: quita el CHECK numérico, convierte
     * con CASE posición → nombre y agrega el CHECK con la lista de nombres. Una
     * posición fuera del enum deja NULL y la conversión falla (NOT NULL): la
     * transacción se revierte entera y la app no arranca, en lugar de inventar
     * un dato.
     */
    private void convertirProvinciaDeInspectorATexto() {
        List<String> tipo = jdbcTemplate.queryForList("SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'inspectores_anmat' "
                + "AND column_name = 'provincia'", String.class);
        if (!tipo.equals(List.of("smallint"))) {
            return;
        }
        List<String> checks = jdbcTemplate.queryForList("SELECT c.conname FROM pg_constraint c "
                + "JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                + "WHERE n.nspname = current_schema() AND t.relname = 'inspectores_anmat' AND c.contype = 'c' "
                + "AND pg_get_constraintdef(c.oid) LIKE '%provincia%'", String.class);
        for (String check : checks) {
            jdbcTemplate.execute("ALTER TABLE inspectores_anmat DROP CONSTRAINT \"" + check + "\"");
        }
        StringBuilder posicionANombre = new StringBuilder("CASE provincia");
        StringJoiner nombres = new StringJoiner(", ");
        for (Provincia provincia : Provincia.values()) {
            posicionANombre.append(" WHEN ").append(provincia.ordinal()).append(" THEN '")
                    .append(provincia.name()).append("'");
            nombres.add("'" + provincia.name() + "'");
        }
        posicionANombre.append(" END");
        jdbcTemplate.execute("ALTER TABLE inspectores_anmat ALTER COLUMN provincia TYPE varchar(30) USING ("
                + posicionANombre + ")");
        jdbcTemplate.execute("ALTER TABLE inspectores_anmat ADD CONSTRAINT inspectores_anmat_provincia_check "
                + "CHECK (provincia IN (" + nombres + "))");
        Integer inspectores = jdbcTemplate.queryForObject("SELECT count(*) FROM inspectores_anmat", Integer.class);
        log.info("inspectores_anmat.provincia convertida de posición (smallint) a nombre (texto): {} inspectores",
                inspectores);
    }
}
