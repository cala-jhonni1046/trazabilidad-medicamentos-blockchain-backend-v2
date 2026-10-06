package com.medichain.modules.recepcion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repositorio RecepcionRepository en MediChain.
 * Acceso a datos JPA para Recepcion (CRUD y paginación heredados de
 * JpaRepository por UUID).
 */
@Repository
public interface RecepcionRepository extends JpaRepository<Recepcion, UUID> {

    /** Actas de recepción registradas por la empresa receptora dada. */
    Page<Recepcion> findByReceptoraId(UUID empresaId, Pageable pageable);

    /** Indica si la empresa ya registró una recepción de ese bulto (para BULTO_DUPLICADO). */
    boolean existsByBultoIdAndReceptoraId(UUID bultoId, UUID receptoraId);

    /** Recepciones de un bulto, en orden (recorrido de la verificación pública). */
    List<Recepcion> findByBultoIdOrderByFechaHoraAsc(UUID bultoId);

    /** Recepciones de un viaje (para contar recibidos y rechazados al finalizarlo). */
    List<Recepcion> findByDespachoId(UUID despachoId);

    /** Recepciones que ve una empresa: las que registró, las de viajes que originó y las de bultos de sus lotes. */
    @Query("select r from Recepcion r where r.receptora.id = :empresaId or r.despacho.origen.id = :empresaId "
            + "or r.bulto.lote.laboratorio.id = :empresaId")
    Page<Recepcion> findVisiblesParaEmpresa(@Param("empresaId") UUID empresaId, Pageable pageable);
}
