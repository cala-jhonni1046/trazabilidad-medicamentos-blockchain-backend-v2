package com.medichain.modules.despachologistico;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio DespachoLogisticoRepository en MediChain.
 * Acceso a datos JPA para DespachoLogistico (CRUD y paginación heredados
 * de JpaRepository por UUID).
 */
@Repository
public interface DespachoLogisticoRepository extends JpaRepository<DespachoLogistico, UUID> {

    /** Despachos que origina la empresa dada. */
    Page<DespachoLogistico> findByOrigenId(UUID empresaId, Pageable pageable);

    /**
     * Viajes que ve una distribuidora: los que origina y los del tramo 1 que
     * traen bultos de sus circuitos a su depósito.
     */
    @Query("select d from DespachoLogistico d where d.origen.id = :empresaId or (d.tramo = com.medichain.modules.despachologistico.TramoDespacho.LAB_A_DISTRIBUIDOR "
            + "and exists (select b.id from DespachoLogistico d2 join d2.bultos b "
            + "where d2.id = d.id and b.destino.distribuidor.id = :empresaId))")
    Page<DespachoLogistico> findVisiblesParaDistribuidor(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Viajes que ve una farmacia: los del tramo 2 con bultos de sus circuitos (una parada en ella). */
    @Query("select d from DespachoLogistico d where d.tramo = com.medichain.modules.despachologistico.TramoDespacho.DISTRIBUIDOR_A_FARMACIA "
            + "and exists (select b.id from DespachoLogistico d2 join d2.bultos b "
            + "where d2.id = d.id and b.destino.farmacia.id = :empresaId)")
    Page<DespachoLogistico> findVisiblesParaFarmacia(@Param("empresaId") UUID empresaId, Pageable pageable);

    /** Próximo número para el código VJ-0001 (secuencia creada por InicializadorBaseDatos). */
    @Query(value = "select nextval('viaje_codigo_seq')", nativeQuery = true)
    Long siguienteNumeroCodigo();

    /** Viajes que ya salieron con el bulto dado, en orden de salida (recorrido de la verificación pública). */
    @Query("select d from DespachoLogistico d join d.bultos b where b.id = :bultoId and d.fechaSalida is not null "
            + "order by d.fechaSalida asc")
    List<DespachoLogistico> findSalidasDelBulto(@Param("bultoId") UUID bultoId);
}
