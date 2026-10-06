package com.medichain.modules.telemetriatemperatura;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio TelemetriaTemperaturaRepository en MediChain.
 * Acceso a datos JPA para TelemetriaTemperatura (CRUD y paginación
 * heredados de JpaRepository por UUID).
 */
@Repository
public interface TelemetriaTemperaturaRepository extends JpaRepository<TelemetriaTemperatura, UUID> {

    /** Lecturas de los despachos que origina la empresa dada. */
    Page<TelemetriaTemperatura> findByDespachoOrigenId(UUID empresaId, Pageable pageable);

    /** Todas las lecturas de un viaje (para el resumen de VIAJE_FINALIZADO). */
    List<TelemetriaTemperatura> findByDespachoId(UUID despachoId);
}
