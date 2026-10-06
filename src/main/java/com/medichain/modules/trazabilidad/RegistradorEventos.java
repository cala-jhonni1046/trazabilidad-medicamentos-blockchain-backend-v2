package com.medichain.modules.trazabilidad;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.usuario.RolUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio RegistradorEventos en MediChain.
 * Único punto de alta de eventos de la cadena. Corre DENTRO de la
 * transacción de negocio que lo llama (propagation MANDATORY): si el
 * negocio hace rollback, el evento también, y nunca queda un evento sin
 * su hecho ni un hecho sin su evento. Pasos, en orden:
 * <ol>
 *   <li>Bloquea la fila CadenaEstado (SELECT ... FOR UPDATE).</li>
 *   <li>numero = ultimoNumero + 1; hashAnterior = ultimoHash.</li>
 *   <li>fechaHora = ahora, tomada DESPUÉS del bloqueo (A1), en microsegundos.</li>
 *   <li>Guarda el evento (su constructor calcula el hash).</li>
 *   <li>Avanza CadenaEstado al nuevo número y hash.</li>
 * </ol>
 * El bloqueo serializa las altas de eventos hasta el commit; es el costo
 * aceptado de tener una única cadena sin huecos.
 */
@Service
public class RegistradorEventos {

    private final CadenaEstadoRepository cadenaEstadoRepository;
    private final EventoTrazabilidadRepository eventoRepository;
    private final Clock clock;

    @Autowired
    public RegistradorEventos(CadenaEstadoRepository cadenaEstadoRepository,
                              EventoTrazabilidadRepository eventoRepository) {
        this(cadenaEstadoRepository, eventoRepository, Clock.systemUTC());
    }

    /** Constructor con reloj explícito, para tests deterministas. */
    public RegistradorEventos(CadenaEstadoRepository cadenaEstadoRepository,
                              EventoTrazabilidadRepository eventoRepository, Clock clock) {
        this.cadenaEstadoRepository = cadenaEstadoRepository;
        this.eventoRepository = eventoRepository;
        this.clock = clock;
    }

    /**
     * Registra un evento con el usuario autenticado como actor. Si el
     * actor es PACIENTE, se omite (R13: ningún dato del paciente en la cadena).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventoTrazabilidad registrar(TipoEvento tipo, String entidadTipo, UUID entidadId,
                                        Map<String, ?> datos, UsuarioAutenticado actor) {
        if (actor == null || actor.getRol() == RolUsuario.PACIENTE) {
            return registrar(tipo, entidadTipo, entidadId, datos, null, null);
        }
        return registrar(tipo, entidadTipo, entidadId, datos, actor.getUsuarioId(), actor.getEmpresaId());
    }

    /** Registra un evento con ids de actor explícitos (null en eventos del sistema). */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventoTrazabilidad registrar(TipoEvento tipo, String entidadTipo, UUID entidadId, Map<String, ?> datos,
                                        UUID actorUsuarioId, UUID actorEmpresaId) {
        CadenaEstado estado = cadenaEstadoRepository.findByNombre(CadenaEstado.PRINCIPAL)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de estado de la cadena de eventos"));
        Long numero = estado.getUltimoNumero() + 1;
        // A1: la hora se toma con el bloqueo ya adquirido, así fechaHora crece junto con numero.
        Instant fechaHora = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        EventoTrazabilidad evento = new EventoTrazabilidad(numero, tipo, fechaHora, entidadTipo, entidadId,
                JsonCanonico.escribir(datos), actorUsuarioId, actorEmpresaId, estado.getUltimoHash());
        EventoTrazabilidad guardado = eventoRepository.save(evento);
        estado.avanzar(numero, guardado.getHash());
        cadenaEstadoRepository.save(estado);
        return guardado;
    }
}
