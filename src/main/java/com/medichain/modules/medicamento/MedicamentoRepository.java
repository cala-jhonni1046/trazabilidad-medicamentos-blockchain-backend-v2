package com.medichain.modules.medicamento;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio MedicamentoRepository en MediChain.
 * Acceso a datos JPA para Medicamento (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface MedicamentoRepository extends JpaRepository<Medicamento, UUID> {

    /** Medicamento por GTIN (normalizado, 14 dígitos). */
    Optional<Medicamento> findByGtin(String gtin);

    /** Indica si ya hay un medicamento con ese GTIN (el GTIN es único en el sistema). */
    boolean existsByGtin(String gtin);
}
