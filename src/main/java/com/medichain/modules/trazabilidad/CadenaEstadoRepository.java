package com.medichain.modules.trazabilidad;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio CadenaEstadoRepository en MediChain.
 * Acceso a la fila única de estado de la cadena de eventos.
 */
@Repository
public interface CadenaEstadoRepository extends JpaRepository<CadenaEstado, UUID> {

    /** Lee el estado con bloqueo de escritura (SELECT ... FOR UPDATE) hasta el fin de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CadenaEstado> findByNombre(String nombre);

    /** Lee el estado sin bloquear (para la verificación de la cadena). */
    Optional<CadenaEstado> findFirstByNombre(String nombre);
}
