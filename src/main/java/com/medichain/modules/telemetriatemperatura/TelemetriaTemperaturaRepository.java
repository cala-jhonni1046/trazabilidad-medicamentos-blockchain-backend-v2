package com.medichain.modules.telemetriatemperatura;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio TelemetriaTemperaturaRepository en MediChain.
 * Acceso a datos JPA para TelemetriaTemperatura (CRUD y paginación
 * heredados de JpaRepository por UUID).
 */
@Repository
public interface TelemetriaTemperaturaRepository extends JpaRepository<TelemetriaTemperatura, UUID> {

    /**
     * Lecturas que ve una empresa: las de los viajes que origina y las de los
     * viajes en los que es la receptora de ese tramo (la distribuidora en el
     * tramo 1, la farmacia en el tramo 2), igual que DespachoLogistico.paradas().
     */
    @Query("select t from TelemetriaTemperatura t where t.despacho.origen.id = :empresaId "
            + "or exists (select b.id from DespachoLogistico d join d.bultos b where d.id = t.despacho.id and ("
            + "(d.tramo = com.medichain.modules.despachologistico.TramoDespacho.LAB_A_DISTRIBUIDOR "
            + "and b.destino.distribuidor.id = :empresaId) "
            + "or (d.tramo = com.medichain.modules.despachologistico.TramoDespacho.DISTRIBUIDOR_A_FARMACIA "
            + "and b.destino.farmacia.id = :empresaId)))")
    Page<TelemetriaTemperatura> findVisiblesParaEmpresa(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Lecturas de un viaje, para su gráfico de temperatura. */
    @Query("select t from TelemetriaTemperatura t where t.despacho.id = :despachoId")
    Page<TelemetriaTemperatura> findDelViaje(@Param("despachoId") UUID despachoId, Pageable pageable);

    /** Todas las lecturas de un viaje (para el resumen de VIAJE_FINALIZADO). */
    List<TelemetriaTemperatura> findByDespachoId(UUID despachoId);
}
