package com.medichain.config;

import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.CadenaEstadoRepository;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
}
