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

    /** Bultos de lotes del laboratorio dado. */
    Page<Bulto> findByLoteLaboratorioId(UUID laboratorioId, Pageable pageable);

    /** Bultos de circuitos donde la empresa es la distribuidora (los ve desde ARMADO). */
    Page<Bulto> findByDestinoDistribuidorId(UUID empresaId, Pageable pageable);

    /** Bultos de circuitos donde la empresa es la farmacia (los ve desde ARMADO). */
    Page<Bulto> findByDestinoFarmaciaId(UUID empresaId, Pageable pageable);

    /** Bultos con los códigos dados (lo que se escanea al armar un viaje). */
    List<Bulto> findByCodigoIn(Collection<String> codigos);

    /** Próximo número para el código BUL-0001 (secuencia creada por la migración V1__esquema_inicial). */
    @Query(value = "select nextval('bulto_codigo_seq')", nativeQuery = true)
    Long siguienteNumeroCodigo();

    /** Bulto por su código (lo que se escanea al recibir). */
    Optional<Bulto> findByCodigo(String codigo);
}
