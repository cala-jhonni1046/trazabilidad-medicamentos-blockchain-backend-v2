package com.medichain.modules.lote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.medichain.utils.enums.Provincia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio LoteRepository en MediChain.
 * Acceso a datos JPA para Lote (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface LoteRepository extends JpaRepository<Lote, UUID> {

    /** Lotes de los medicamentos de un laboratorio (lote.medicamento.laboratorio.id). */
    Page<Lote> findByMedicamentoLaboratorioId(UUID laboratorioId, Pageable pageable);

    /** Indica si el laboratorio ya tiene un lote con ese código (único por laboratorio). */
    boolean existsByLaboratorioIdAndCodigo(UUID laboratorioId, String codigo);

    /** Bandeja del inspector (R4): lotes BIOLÓGICOS PENDIENTE_LIBERACION de laboratorios de su provincia. */
    @Query(value = "select l from Lote l join l.medicamento m join l.laboratorio lab "
            + "where l.estado = com.medichain.modules.lote.EstadoLote.PENDIENTE_LIBERACION "
            + "and m.biologico = true and lab.provincia = :provincia",
            countQuery = "select count(l) from Lote l join l.medicamento m join l.laboratorio lab "
            + "where l.estado = com.medichain.modules.lote.EstadoLote.PENDIENTE_LIBERACION "
            + "and m.biologico = true and lab.provincia = :provincia")
    Page<Lote> findBandejaLiberacion(@Param("provincia") Provincia provincia, Pageable pageable);

    /** Lotes que ve una distribuidora: los que tienen bultos que ya salieron hacia ella (tramo 1). */
    @Query("select l from Lote l where exists (select d.id from DespachoLogistico d join d.bultos b "
            + "where b.lote.id = l.id and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.LAB_A_DISTRIBUIDOR and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) "
            + "and b.destino.distribuidor.id = :empresaId)")
    Page<Lote> findVisiblesParaDistribuidor(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Lotes que ve una farmacia: los que tienen bultos que ya salieron hacia ella (tramo 2). */
    @Query("select l from Lote l where exists (select d.id from DespachoLogistico d join d.bultos b "
            + "where b.lote.id = l.id and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.DISTRIBUIDOR_A_FARMACIA and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) "
            + "and b.destino.farmacia.id = :empresaId)")
    Page<Lote> findVisiblesParaFarmacia(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Indica si la distribuidora puede ver el lote (algún bulto suyo ya salió hacia ella). */
    @Query("select count(l) > 0 from Lote l where l.id = :loteId and exists (select d.id from DespachoLogistico d "
            + "join d.bultos b where b.lote.id = l.id and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.LAB_A_DISTRIBUIDOR and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) "
            + "and b.destino.distribuidor.id = :empresaId)")
    boolean esVisibleParaDistribuidor(@Param("loteId") UUID loteId, @Param("empresaId") UUID empresaId);

    /** Indica si la farmacia puede ver el lote (algún bulto suyo ya salió hacia ella). */
    @Query("select count(l) > 0 from Lote l where l.id = :loteId and exists (select d.id from DespachoLogistico d "
            + "join d.bultos b where b.lote.id = l.id and d.tramo = com.medichain.modules.despachologistico.TramoDespacho.DISTRIBUIDOR_A_FARMACIA and d.estado in (com.medichain.modules.despachologistico.EstadoDespacho.EN_TRANSITO, com.medichain.modules.despachologistico.EstadoDespacho.FINALIZADO, com.medichain.modules.despachologistico.EstadoDespacho.ROBADO) "
            + "and b.destino.farmacia.id = :empresaId)")
    boolean esVisibleParaFarmacia(@Param("loteId") UUID loteId, @Param("empresaId") UUID empresaId);
}
