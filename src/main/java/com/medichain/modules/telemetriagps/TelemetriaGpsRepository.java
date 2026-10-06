package com.medichain.modules.telemetriagps;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio TelemetriaGpsRepository en MediChain.
 * Acceso a datos JPA para TelemetriaGps (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface TelemetriaGpsRepository extends JpaRepository<TelemetriaGps, UUID> {

    /** Lecturas de los despachos que origina la empresa dada. */
    Page<TelemetriaGps> findByDespachoOrigenId(UUID empresaId, Pageable pageable);
}
