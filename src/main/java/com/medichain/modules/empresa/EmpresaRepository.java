package com.medichain.modules.empresa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.medichain.utils.enums.Provincia;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio EmpresaRepository en MediChain.
 * Acceso a datos JPA para Empresa (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {

    /** Devuelve una página de empresas en el estado dado (LAB/DIST/FARM ven solo las HABILITADA). */
    Page<Empresa> findByEstado(EstadoHabilitacion estado, Pageable pageable);

    /** Indica si ya existe una empresa con el CUIT dado (formato XX-XXXXXXXX-X). */
    boolean existsByCuit(String cuit);

    /** Indica si ya existe una empresa con el GLN dado. */
    boolean existsByGln(String gln);

    /**
     * Solicitudes que la Sede tiene que asignar (R2): PENDIENTE, sin revisor y
     * de una provincia SIN inspectores ACTIVO. Es exactamente lo que acepta
     * EmpresaService.asignar.
     */
    @Query(value = "select e from Empresa e where e.estado = com.medichain.modules.empresa.EstadoHabilitacion.PENDIENTE "
            + "and e.inspectorRevisor is null and not exists (select i.id from InspectorAnmat i "
            + "where i.provincia = e.provincia and i.estado = com.medichain.modules.inspectoranmat.EstadoInspector.ACTIVO)",
            countQuery = "select count(e) from Empresa e where e.estado = com.medichain.modules.empresa.EstadoHabilitacion.PENDIENTE "
            + "and e.inspectorRevisor is null and not exists (select i.id from InspectorAnmat i "
            + "where i.provincia = e.provincia and i.estado = com.medichain.modules.inspectoranmat.EstadoInspector.ACTIVO)")
    Page<Empresa> findSinInspector(Pageable pageable);

    /** Empresas, opcionalmente de un estado (Sede e inspectores ven todas). */
    @Query("select e from Empresa e where (:estado is null or e.estado = :estado)")
    Page<Empresa> findPorEstado(@Param("estado") EstadoHabilitacion estado, Pageable pageable);

    /**
     * Bandeja del inspector: solicitudes PENDIENTE de su provincia que
     * nadie tomó, más las que él tomó o le asignaron (de cualquier provincia).
     */
    @Query(value = "select e from Empresa e left join e.inspectorRevisor r "
            + "where e.estado = com.medichain.modules.empresa.EstadoHabilitacion.PENDIENTE "
            + "and ((e.provincia = :provincia and r is null) or r.id = :inspectorId)",
            countQuery = "select count(e) from Empresa e left join e.inspectorRevisor r "
            + "where e.estado = com.medichain.modules.empresa.EstadoHabilitacion.PENDIENTE "
            + "and ((e.provincia = :provincia and r is null) or r.id = :inspectorId)")
    Page<Empresa> findBandeja(@Param("provincia") Provincia provincia, @Param("inspectorId") UUID inspectorId,
                              Pageable pageable);

    /** Solicitudes en el estado dado que tiene tomadas o asignadas el inspector. */
    List<Empresa> findByEstadoAndInspectorRevisorId(EstadoHabilitacion estado, UUID inspectorId);

    /** Busca una empresa por CUIT exacto (formato normalizado XX-XXXXXXXX-X). */
    Optional<Empresa> findByCuit(String cuit);
}
