package com.medichain.modules.trazabilidad;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio EventoTrazabilidadRepository en MediChain.
 * Acceso a datos de EventoTrazabilidad. El número y el hash del próximo
 * evento NO se leen de acá sino de CadenaEstado (con bloqueo), para
 * evitar números duplicados entre transacciones concurrentes.
 */
@Repository
public interface EventoTrazabilidadRepository extends JpaRepository<EventoTrazabilidad, UUID> {

    /** Devuelve el siguiente bloque de eventos con número mayor al dado, en orden ascendente. */
    List<EventoTrazabilidad> findByNumeroGreaterThanOrderByNumeroAsc(Long numero, Limit limite);

    /** Evento por número (el anclaje compara su hash con el del contrato). */
    Optional<EventoTrazabilidad> findByNumero(Long numero);

    /** Eventos con esos números (la verificación contra la blockchain, en bloques de 500). */
    List<EventoTrazabilidad> findByNumeroIn(Collection<Long> numeros);

    /**
     * Número del último evento de los tipos dados sobre esas entidades
     * (null si no hay): la verificación pública busca el último hito del
     * recorrido de una caja para mostrar el anclaje que lo cubre.
     */
    @Query("select max(e.numero) from EventoTrazabilidad e where e.entidadId in :ids and e.tipo in :tipos")
    Long ultimoNumeroDe(@Param("ids") Collection<UUID> ids, @Param("tipos") Collection<TipoEvento> tipos);
}
