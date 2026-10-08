package com.medichain.modules.enlacecuit;

import com.medichain.utils.enums.Provincia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio EnlaceCuitRepository en MediChain.
 * Acceso a datos de los circuitos: listados por empresa, bandejas,
 * control del par vigente (R5) y el próximo número de código.
 */
@Repository
public interface EnlaceCuitRepository extends JpaRepository<EnlaceCuit, UUID> {

    /** Estados en que un circuito cuenta como VIGENTE (RECHAZADO no cuenta). */
    List<EstadoEnlaceCuit> ESTADOS_VIGENTES = List.of(EstadoEnlaceCuit.PENDIENTE_EMPRESAS,
            EstadoEnlaceCuit.PENDIENTE_INSPECTOR, EstadoEnlaceCuit.APROBADO, EstadoEnlaceCuit.SUSPENDIDO);

    /** Circuitos donde la empresa es el laboratorio. */
    Page<EnlaceCuit> findByLaboratorioId(UUID empresaId, Pageable pageable);

    /** Circuitos donde la empresa es la distribuidora. */
    Page<EnlaceCuit> findByDistribuidorId(UUID empresaId, Pageable pageable);

    /** Circuitos donde la empresa es la farmacia. */
    Page<EnlaceCuit> findByFarmaciaId(UUID empresaId, Pageable pageable);

    /** Circuitos en el estado dado donde la empresa participa en cualquiera de los tres roles. */
    @Query("select e from EnlaceCuit e where e.estado = :estado and "
            + "(e.laboratorio.id = :empresaId or e.distribuidor.id = :empresaId or e.farmacia.id = :empresaId)")
    List<EnlaceCuit> findByEstadoYEmpresa(@Param("estado") EstadoEnlaceCuit estado, @Param("empresaId") UUID empresaId);

    /** R5: indica si ya hay un circuito en alguno de los estados dados para el par laboratorio–farmacia. */
    boolean existsByLaboratorioIdAndFarmaciaIdAndEstadoIn(UUID laboratorioId, UUID farmaciaId,
                                                          Collection<EstadoEnlaceCuit> estados);

    /**
     * Pendientes de aceptación de una empresa: PENDIENTE_EMPRESAS donde es
     * la distribuidora y todavía no aceptó, o la farmacia y todavía no aceptó.
     */
    @Query(value = "select e from EnlaceCuit e where e.estado = com.medichain.modules.enlacecuit.EstadoEnlaceCuit.PENDIENTE_EMPRESAS "
            + "and ((e.distribuidor.id = :empresaId and e.fechaAceptacionDistribuidor is null) "
            + "or (e.farmacia.id = :empresaId and e.fechaAceptacionFarmacia is null))",
            countQuery = "select count(e) from EnlaceCuit e where e.estado = com.medichain.modules.enlacecuit.EstadoEnlaceCuit.PENDIENTE_EMPRESAS "
            + "and ((e.distribuidor.id = :empresaId and e.fechaAceptacionDistribuidor is null) "
            + "or (e.farmacia.id = :empresaId and e.fechaAceptacionFarmacia is null))")
    Page<EnlaceCuit> findPendientesDeAceptacion(@Param("empresaId") UUID empresaId, Pageable pageable);

    /**
     * Bandeja del inspector: SOLO PENDIENTE_INSPECTOR, de farmacias de su
     * provincia sin revisor, más los que él tomó o le asignaron.
     */
    @Query(value = "select e from EnlaceCuit e join e.farmacia f left join e.inspectorRevisor r "
            + "where e.estado = com.medichain.modules.enlacecuit.EstadoEnlaceCuit.PENDIENTE_INSPECTOR "
            + "and ((f.provincia = :provincia and r is null) or r.id = :inspectorId)",
            countQuery = "select count(e) from EnlaceCuit e join e.farmacia f left join e.inspectorRevisor r "
            + "where e.estado = com.medichain.modules.enlacecuit.EstadoEnlaceCuit.PENDIENTE_INSPECTOR "
            + "and ((f.provincia = :provincia and r is null) or r.id = :inspectorId)")
    Page<EnlaceCuit> findBandeja(@Param("provincia") Provincia provincia, @Param("inspectorId") UUID inspectorId,
                                 Pageable pageable);

    /** Próximo número para el código CIR-0001 (secuencia creada por la migración V1__esquema_inicial). */
    @Query(value = "select nextval('circuito_codigo_seq')", nativeQuery = true)
    Long siguienteNumeroCodigo();
}
