package com.medichain.modules.dispensacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio DispensacionRepository en MediChain.
 * Acceso a datos JPA para Dispensacion (CRUD y paginación heredados de
 * JpaRepository por UUID). existsByUnidadTrazableIdAndAnuladaFalse es
 * necesario para que el service valide la regla R11 (no puede haber más
 * de una dispensación vigente por unidad).
 */
@Repository
public interface DispensacionRepository extends JpaRepository<Dispensacion, UUID> {

    /** Dispensaciones hechas por la farmacia dada. */
    Page<Dispensacion> findByFarmaciaId(UUID farmaciaId, Pageable pageable);

    /** Indica si la unidad trazable dada tiene alguna dispensación vigente (no anulada). */
    boolean existsByUnidadTrazableIdAndAnuladaFalse(UUID unidadTrazableId);
}
