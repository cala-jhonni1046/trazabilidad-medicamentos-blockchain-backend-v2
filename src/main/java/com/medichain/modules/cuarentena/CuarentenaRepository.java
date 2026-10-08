package com.medichain.modules.cuarentena;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.medichain.utils.enums.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio CuarentenaRepository en MediChain.
 * Acceso a datos JPA para Cuarentena (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface CuarentenaRepository extends JpaRepository<Cuarentena, UUID> {

    /** Cuarentenas sobre lotes de un laboratorio. */
    Page<Cuarentena> findByLoteMedicamentoLaboratorioId(UUID laboratorioId, Pageable pageable);

    /** Cuarentenas sobre despachos que origina la empresa dada. */
    Page<Cuarentena> findByDespachoOrigenId(UUID empresaId, Pageable pageable);

    /** R10: ids de los lotes dados que tienen una medida VIGENTE (ACTIVA o CONVERTIDA_EN_RECALL) de alcance LOTE. */
    @Query("select distinct c.lote.id from Cuarentena c where c.lote.id in :loteIds and c.estado in "
            + "(com.medichain.modules.cuarentena.EstadoCuarentena.ACTIVA, "
            + "com.medichain.modules.cuarentena.EstadoCuarentena.CONVERTIDA_EN_RECALL)")
    List<UUID> findLotesConMedidaVigente(@Param("loteIds") Collection<UUID> loteIds);

    /** R10: ids de los bultos dados que figuran en una medida VIGENTE (alcance DESPACHO o BULTO). */
    @Query("select distinct b.id from Cuarentena c join c.bultos b where b.id in :bultoIds and c.estado in "
            + "(com.medichain.modules.cuarentena.EstadoCuarentena.ACTIVA, "
            + "com.medichain.modules.cuarentena.EstadoCuarentena.CONVERTIDA_EN_RECALL)")
    List<UUID> findBultosConMedidaVigente(@Param("bultoIds") Collection<UUID> bultoIds);

    /**
     * Visibilidad por empresa: medidas sobre lotes de un laboratorio, sobre viajes que
     * origina la empresa, o sobre bultos de circuitos donde participa (como laboratorio,
     * distribuidora o farmacia).
     */
    @Query(value = "select distinct c from Cuarentena c left join c.lote l left join c.despacho d "
            + "left join c.bultos b left join b.destino e "
            + "where (l.laboratorio.id = :empresaId or d.origen.id = :empresaId "
            + "or e.laboratorio.id = :empresaId or e.distribuidor.id = :empresaId or e.farmacia.id = :empresaId) "
            + "and (:estado is null or c.estado = :estado)",
            countQuery = "select count(distinct c) from Cuarentena c left join c.lote l left join c.despacho d "
            + "left join c.bultos b left join b.destino e "
            + "where (l.laboratorio.id = :empresaId or d.origen.id = :empresaId "
            + "or e.laboratorio.id = :empresaId or e.distribuidor.id = :empresaId or e.farmacia.id = :empresaId) "
            + "and (:estado is null or c.estado = :estado)")
    Page<Cuarentena> findVisiblesParaEmpresa(@Param("empresaId") UUID empresaId,
                                             @Param("estado") EstadoCuarentena estado, Pageable pageable);

    /** Cuarentenas y recalls, opcionalmente de un estado (Sede e inspectores). */
    @Query("select c from Cuarentena c where (:estado is null or c.estado = :estado)")
    Page<Cuarentena> findPorEstado(@Param("estado") EstadoCuarentena estado, Pageable pageable);

    /** Bandeja del inspector: medidas ACTIVA de su provincia sin tomar, más las que tomó él. */
    @Query(value = "select c from Cuarentena c left join c.inspectorRevisor r "
            + "where c.estado = com.medichain.modules.cuarentena.EstadoCuarentena.ACTIVA "
            + "and ((c.provincia = :provincia and r is null) or r.id = :inspectorId)",
            countQuery = "select count(c) from Cuarentena c left join c.inspectorRevisor r "
            + "where c.estado = com.medichain.modules.cuarentena.EstadoCuarentena.ACTIVA "
            + "and ((c.provincia = :provincia and r is null) or r.id = :inspectorId)")
    Page<Cuarentena> findBandeja(@Param("provincia") Provincia provincia, @Param("inspectorId") UUID inspectorId,
                                 Pageable pageable);

    /** Ids de los bultos dados que figuran en una medida CONVERTIDA_EN_RECALL (retiro del mercado). */
    @Query("select distinct b.id from Cuarentena c join c.bultos b where b.id in :bultoIds and c.estado = "
            + "com.medichain.modules.cuarentena.EstadoCuarentena.CONVERTIDA_EN_RECALL")
    List<UUID> findBultosEnRecall(@Param("bultoIds") Collection<UUID> bultoIds);

    /** Medidas que nacieron del reporte ciudadano dado. */
    List<Cuarentena> findByReporteOrigenId(UUID reporteId);
}
