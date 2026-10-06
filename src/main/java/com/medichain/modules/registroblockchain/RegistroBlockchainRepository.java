package com.medichain.modules.registroblockchain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio RegistroBlockchainRepository en MediChain.
 * Acceso a datos de los anclajes. Un índice único parcial
 * (InicializadorBaseDatos) garantiza a lo sumo UN anclaje PENDIENTE o
 * ENVIADO por red.
 */
@Repository
public interface RegistroBlockchainRepository extends JpaRepository<RegistroBlockchain, UUID> {

    /** El anclaje en curso (PENDIENTE o ENVIADO), si hay. */
    Optional<RegistroBlockchain> findFirstByEstadoInOrderByFechaCreacionDesc(Collection<EstadoAnclaje> estados);

    /** Mayor hastaNumero entre los anclajes en los estados dados (null si no hay ninguno). */
    @Query("select max(r.hastaNumero) from RegistroBlockchain r where r.estado in :estados")
    Long maxHastaNumero(@Param("estados") Collection<EstadoAnclaje> estados);

    /** El primer anclaje (en los estados dados) que cubre el evento numero: hastaNumero ≥ numero. */
    Optional<RegistroBlockchain> findFirstByHastaNumeroGreaterThanEqualAndEstadoInOrderByHastaNumeroAsc(
            Long numero, Collection<EstadoAnclaje> estados);

    /** El último anclaje (en los estados dados), por número de evento. */
    Optional<RegistroBlockchain> findFirstByEstadoInOrderByHastaNumeroDesc(Collection<EstadoAnclaje> estados);

    /** El último anclaje en un estado, por fecha de creación (por ejemplo, el último FALLIDO). */
    Optional<RegistroBlockchain> findFirstByEstadoOrderByFechaCreacionDesc(EstadoAnclaje estado);

    /** Todos los anclajes en un estado, por número de evento (la verificación recorre los CONFIRMADO). */
    List<RegistroBlockchain> findByEstadoOrderByHastaNumeroAsc(EstadoAnclaje estado);
}
