package com.medichain.modules.unidadtrazable;

import com.medichain.modules.trazabilidad.TipoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Repositorio IntentoVerificacionRepository en MediChain.
 * Control anti-spam de SERIE_INEXISTENTE / SERIE_ROBADA en la verificación pública.
 */
@Repository
public interface IntentoVerificacionRepository extends JpaRepository<IntentoVerificacion, UUID> {

    /** Indica si ya se registró hoy ese tipo de intento para esa caja. */
    boolean existsByTipoAndGtinAndSerieAndFecha(TipoEvento tipo, String gtin, String serie, LocalDate fecha);

    /** Cuántos intentos de ese tipo se registraron en el día (tope diario global). */
    long countByTipoAndFecha(TipoEvento tipo, LocalDate fecha);
}
