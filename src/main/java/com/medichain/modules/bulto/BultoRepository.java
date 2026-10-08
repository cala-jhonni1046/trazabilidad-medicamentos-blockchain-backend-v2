package com.medichain.modules.bulto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio BultoRepository en MediChain.
 * Acceso a datos JPA para Bulto (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface BultoRepository extends JpaRepository<Bulto, UUID> {

    /** Bultos armados con lotes de un laboratorio. */
    Page<Bulto> findByLoteMedicamentoLaboratorioId(UUID laboratorioId, Pageable pageable);

    /** Bultos: todos, opcionalmente de un estado (Sede e inspectores). */
    @Query("select b from Bulto b where (:estado is null or b.estado = :estado)")
    Page<Bulto> findPorEstado(@Param("estado") EstadoBulto estado, Pageable pageable);

    /** Bultos de lotes del laboratorio dado, opcionalmente de un estado. */
    @Query("select b from Bulto b where b.lote.laboratorio.id = :empresaId and (:estado is null or b.estado = :estado)")
    Page<Bulto> findDelLaboratorioPorEstado(@Param("empresaId") UUID empresaId, @Param("estado") EstadoBulto estado,
                            Pageable pageable);

    /** Bultos de circuitos donde la empresa es la distribuidora (los ve desde ARMADO), opcionalmente de un estado. */
    @Query("select b from Bulto b where b.destino.distribuidor.id = :empresaId and (:estado is null or b.estado = :estado)")
    Page<Bulto> findDeLaDistribuidoraPorEstado(@Param("empresaId") UUID empresaId, @Param("estado") EstadoBulto estado,
                            Pageable pageable);

    /** Bultos de circuitos donde la empresa es la farmacia (los ve desde ARMADO), opcionalmente de un estado. */
    @Query("select b from Bulto b where b.destino.farmacia.id = :empresaId and (:estado is null or b.estado = :estado)")
    Page<Bulto> findDeLaFarmaciaPorEstado(@Param("empresaId") UUID empresaId, @Param("estado") EstadoBulto estado,
                            Pageable pageable);

    /** Bultos con los códigos dados (lo que se escanea al armar un viaje). */
    List<Bulto> findByCodigoIn(Collection<String> codigos);

    /** Próximo número para el código BUL-0001 (secuencia creada por la migración V1__esquema_inicial). */
    @Query(value = "select nextval('bulto_codigo_seq')", nativeQuery = true)
    Long siguienteNumeroCodigo();

    /** Bulto por su código (lo que se escanea al recibir). */
    Optional<Bulto> findByCodigo(String codigo);
}
