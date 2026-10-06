package com.medichain.modules.unidadtrazable;

import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio RegistroIntentosVerificacion en MediChain.
 * Registra SERIE_INEXISTENTE / SERIE_ROBADA de la verificación pública en
 * su PROPIA transacción (REQUIRES_NEW; la verificación es de solo lectura)
 * y con protección contra quien quiera llenar la cadena a propósito:
 * <ul>
 *   <li>Un solo evento por (tipo, GTIN, serie) por día UTC (tabla
 *       intento_verificacion con restricción única).</li>
 *   <li>Tope diario global de SERIE_INEXISTENTE ({@value #TOPE_DIARIO_INEXISTENTES});
 *       pasado el tope, solo se loguea (sin GTIN ni serie completos).</li>
 * </ul>
 * TODO: rate limiting por IP (Bucket4j) en /api/verificacion; la IP no se guarda.
 */
@Service
public class RegistroIntentosVerificacion {

    /** Máximo de eventos SERIE_INEXISTENTE por día. */
    public static final int TOPE_DIARIO_INEXISTENTES = 500;

    private static final Logger log = LoggerFactory.getLogger(RegistroIntentosVerificacion.class);

    private final IntentoVerificacionRepository repository;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public RegistroIntentosVerificacion(IntentoVerificacionRepository repository,
                                        RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.registradorEventos = registradorEventos;
    }

    /**
     * Registra el evento si es el primero del día para esa caja y no se pasó
     * el tope. Devuelve true si lo registró. El actor es siempre "sistema"
     * (la consulta es pública).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarSiCorresponde(TipoEvento tipo, String gtin, String serie, String entidadTipo,
                                          UUID entidadId, Map<String, ?> datos) {
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        if (repository.existsByTipoAndGtinAndSerieAndFecha(tipo, gtin, serie, hoy)) {
            return false;
        }
        if (tipo == TipoEvento.SERIE_INEXISTENTE && repository.countByTipoAndFecha(tipo, hoy) >= TOPE_DIARIO_INEXISTENTES) {
            log.warn("Tope diario de SERIE_INEXISTENTE alcanzado: no se registran más eventos hoy");
            return false;
        }
        repository.saveAndFlush(new IntentoVerificacion(tipo, gtin, serie, hoy));
        registradorEventos.registrar(tipo, entidadTipo, entidadId, datos, null, null);
        return true;
    }
}
