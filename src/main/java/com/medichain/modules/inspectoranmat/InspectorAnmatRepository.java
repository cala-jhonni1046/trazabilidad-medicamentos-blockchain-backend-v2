package com.medichain.modules.inspectoranmat;

import com.medichain.utils.enums.Provincia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio InspectorAnmatRepository en MediChain.
 * Acceso a datos JPA para InspectorAnmat (CRUD y paginación heredados de
 * JpaRepository por UUID), más la búsqueda por cuenta de usuario que usa
 * el login para poner la provincia del inspector en el JWT.
 */
@Repository
public interface InspectorAnmatRepository extends JpaRepository<InspectorAnmat, UUID> {

    /** Busca el inspector asociado a la cuenta de usuario dada, si existe. */
    Optional<InspectorAnmat> findByUsuarioId(UUID usuarioId);

    /** Indica si la provincia tiene al menos un inspector en el estado dado (R2: asignación por la Sede). */
    boolean existsByProvinciaAndEstado(Provincia provincia, EstadoInspector estado);

    /** Inspectores, opcionalmente de un estado (ACTIVO o BAJA). */
    @Query("select i from InspectorAnmat i where (:estado is null or i.estado = :estado)")
    Page<InspectorAnmat> findPorEstado(@Param("estado") EstadoInspector estado, Pageable pageable);

    /** Indica si ya hay un inspector con ese legajo (único). */
    boolean existsByLegajo(String legajo);

    /** Indica si ya hay un inspector con ese DNI (único). */
    boolean existsByDni(String dni);
}
