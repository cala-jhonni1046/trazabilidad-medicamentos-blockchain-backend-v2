package com.medichain.modules.reporteciudadano;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.medichain.utils.enums.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio ReporteCiudadanoRepository en MediChain.
 * Acceso a datos JPA para ReporteCiudadano (CRUD y paginación heredados
 * de JpaRepository por UUID).
 */
@Repository
public interface ReporteCiudadanoRepository extends JpaRepository<ReporteCiudadano, UUID> {

    /** Reportes presentados por el paciente dado, opcionalmente de un estado. */
    @Query("select r from ReporteCiudadano r where r.paciente.id = :pacienteId and (:estado is null or r.estado = :estado)")
    Page<ReporteCiudadano> findDelPacientePorEstado(@Param("pacienteId") UUID pacienteId,
                                                    @Param("estado") EstadoAuditoria estado, Pageable pageable);

    /** Reportes, opcionalmente de un estado (Sede e inspectores). */
    @Query("select r from ReporteCiudadano r where (:estado is null or r.estado = :estado)")
    Page<ReporteCiudadano> findPorEstado(@Param("estado") EstadoAuditoria estado, Pageable pageable);

    /** Bandeja del inspector: ABIERTO de su provincia sin tomar, más los EN_INVESTIGACION que tomó él. */
    @Query(value = "select r from ReporteCiudadano r left join r.investiga i where "
            + "(r.estado = com.medichain.modules.reporteciudadano.EstadoAuditoria.ABIERTO and r.provincia = :provincia) "
            + "or (r.estado = com.medichain.modules.reporteciudadano.EstadoAuditoria.EN_INVESTIGACION and i.id = :inspectorId)",
            countQuery = "select count(r) from ReporteCiudadano r left join r.investiga i where "
            + "(r.estado = com.medichain.modules.reporteciudadano.EstadoAuditoria.ABIERTO and r.provincia = :provincia) "
            + "or (r.estado = com.medichain.modules.reporteciudadano.EstadoAuditoria.EN_INVESTIGACION and i.id = :inspectorId)")
    Page<ReporteCiudadano> findBandeja(@Param("provincia") Provincia provincia, @Param("inspectorId") UUID inspectorId,
                                       Pageable pageable);

    /** Próximo número para el código REP-0001 (secuencia creada por la migración V1__esquema_inicial). */
    @Query(value = "select nextval('reporte_codigo_seq')", nativeQuery = true)
    Long siguienteNumeroCodigo();
}
