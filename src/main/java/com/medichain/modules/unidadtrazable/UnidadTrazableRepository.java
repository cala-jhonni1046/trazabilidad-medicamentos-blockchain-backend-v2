package com.medichain.modules.unidadtrazable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio UnidadTrazableRepository en MediChain.
 * Acceso a datos JPA para UnidadTrazable (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface UnidadTrazableRepository extends JpaRepository<UnidadTrazable, UUID> {

    /** Unidades de los lotes de un laboratorio. */
    Page<UnidadTrazable> findByLoteMedicamentoLaboratorioId(UUID laboratorioId, Pageable pageable);

    /** Unidades que están físicamente en la empresa dada. */
    Page<UnidadTrazable> findByEmpresaActualId(UUID empresaId, Pageable pageable);

    /** Cajas de un lote, paginadas. */
    Page<UnidadTrazable> findByLoteId(UUID loteId, Pageable pageable);

    /** R3: de las series dadas, devuelve las que ya existen para ese GTIN (la serie es única por GTIN). */
    @Query("select u.serie from UnidadTrazable u where u.gtin = :gtin and u.serie in :series")
    List<String> findSeriesExistentes(@Param("gtin") String gtin, @Param("series") Collection<String> series);

    /** Cajas del lote con las series dadas (R6: deben ser de ESE lote). */
    List<UnidadTrazable> findByLoteIdAndSerieIn(UUID loteId, Collection<String> series);

    /** Primeras N cajas disponibles del lote (en el laboratorio y sin bulto), por serie. */
    List<UnidadTrazable> findByLoteIdAndEstadoAndBultoIsNullOrderBySerieAsc(UUID loteId, EstadoUnidad estado,
                                                                            Limit limite);

    /** Cajas de un bulto. */
    List<UnidadTrazable> findByBultoId(UUID bultoId);

    /** Cajas de varios bultos (salida y robo de un viaje). */
    List<UnidadTrazable> findByBultoIdIn(Collection<UUID> bultoIds);

    /**
     * Cajas que ve una distribuidora: las que tiene en su poder y las de bultos
     * de sus circuitos que ya salieron hacia ella (viaje del tramo 1 que salió).
     */
    @Query("select u from UnidadTrazable u where u.empresaActual.id = :empresaId or exists ("
            + "select d.id from DespachoLogistico d join d.bultos b where b.id = u.bulto.id "
            + "and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.LAB_A_DISTRIBUIDOR and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) and b.destino.distribuidor.id = :empresaId)")
    Page<UnidadTrazable> findVisiblesParaDistribuidor(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Cajas que ve una farmacia: las que tiene en su poder y las de bultos que ya salieron hacia ella (tramo 2). */
    @Query("select u from UnidadTrazable u where u.empresaActual.id = :empresaId or exists ("
            + "select d.id from DespachoLogistico d join d.bultos b where b.id = u.bulto.id "
            + "and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.DISTRIBUIDOR_A_FARMACIA and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) and b.destino.farmacia.id = :empresaId)")
    Page<UnidadTrazable> findVisiblesParaFarmacia(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Indica si la distribuidora puede ver la caja (en su poder o en un bulto que ya salió hacia ella). */
    @Query("select count(u) > 0 from UnidadTrazable u where u.id = :unidadId and (u.empresaActual.id = :empresaId "
            + "or exists (select d.id from DespachoLogistico d join d.bultos b where b.id = u.bulto.id "
            + "and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.LAB_A_DISTRIBUIDOR and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) and b.destino.distribuidor.id = :empresaId))")
    boolean esVisibleParaDistribuidor(@Param("unidadId") UUID unidadId, @Param("empresaId") UUID empresaId);

    /** Indica si la farmacia puede ver la caja (en su poder o en un bulto que ya salió hacia ella). */
    @Query("select count(u) > 0 from UnidadTrazable u where u.id = :unidadId and (u.empresaActual.id = :empresaId "
            + "or exists (select d.id from DespachoLogistico d join d.bultos b where b.id = u.bulto.id "
            + "and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.DISTRIBUIDOR_A_FARMACIA and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) and b.destino.farmacia.id = :empresaId))")
    boolean esVisibleParaFarmacia(@Param("unidadId") UUID unidadId, @Param("empresaId") UUID empresaId);

    /** Caja por lo que trae el DataMatrix: GTIN + serie (únicos juntos). */
    Optional<UnidadTrazable> findByGtinAndSerie(String gtin, String serie);

    /** Cantidad de cajas de un lote (para RECALL). */
    long countByLoteId(UUID loteId);
}
