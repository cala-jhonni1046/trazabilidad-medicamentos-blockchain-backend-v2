package com.medichain.modules.registroblockchain;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.CadenaEstadoRepository;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.modules.trazabilidad.EventoTrazabilidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio PasosAnclaje en MediChain (R15).
 * Los pasos de base de datos del anclaje, cada uno en su transacción
 * CORTA. ProcesoAnclaje los intercala con las llamadas a la red, que nunca
 * ocurren dentro de una transacción de base (no se retiene una conexión
 * mientras se espera a Sepolia). Bean aparte para que @Transactional
 * aplique a cada paso (como RegistroIntentos).
 * El negocio nunca espera al anclaje: cadena_estado se lee SIN bloqueo
 * (findFirstByNombre) y la tabla registros_blockchain no la toca ninguna
 * operación de negocio.
 */
@Service
public class PasosAnclaje {

    /** Estados de un anclaje que todavía no terminó. */
    public static final List<EstadoAnclaje> EN_CURSO = List.of(EstadoAnclaje.PENDIENTE, EstadoAnclaje.ENVIADO);

    /** Estados de un anclaje que llegó a la red (cuentan como cubiertos). */
    public static final List<EstadoAnclaje> EXITOSOS = List.of(EstadoAnclaje.ENVIADO, EstadoAnclaje.CONFIRMADO);

    /** Estados de un anclaje terminado. */
    public static final List<EstadoAnclaje> TERMINADOS = List.of(EstadoAnclaje.CONFIRMADO, EstadoAnclaje.FALLIDO);

    /** Primera espera después de un envío fallido; se duplica en cada intento (30 s, 1, 2, 4 min). */
    private static final Duration ESPERA_INICIAL = Duration.ofSeconds(30);

    private final RegistroBlockchainRepository repository;
    private final CadenaEstadoRepository cadenaEstadoRepository;
    private final EventoTrazabilidadRepository eventoRepository;
    private final AnclajeProperties propiedades;
    private final Clock reloj;

    @Autowired
    public PasosAnclaje(RegistroBlockchainRepository repository, CadenaEstadoRepository cadenaEstadoRepository,
                        EventoTrazabilidadRepository eventoRepository, AnclajeProperties propiedades, Clock reloj) {
        this.repository = repository;
        this.cadenaEstadoRepository = cadenaEstadoRepository;
        this.eventoRepository = eventoRepository;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Instante actual en UTC (del reloj inyectado, para poder fijarlo en los tests), en microsegundos. */
    public Instant ahora() {
        return Instant.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }

    /** El anclaje en curso (PENDIENTE o ENVIADO), si hay. */
    @Transactional(readOnly = true)
    public Optional<RegistroBlockchain> enCurso() {
        return repository.findFirstByEstadoInOrderByFechaCreacionDesc(EN_CURSO);
    }

    /** El último anclaje FALLIDO, si hay (su transacción puede seguir pendiente en la red). */
    @Transactional(readOnly = true)
    public Optional<RegistroBlockchain> ultimoFallido() {
        return repository.findFirstByEstadoOrderByFechaCreacionDesc(EstadoAnclaje.FALLIDO);
    }

    /**
     * El último anclaje terminado (CONFIRMADO o FALLIDO), si hay: si es un
     * FALLIDO determinístico, la tarea automática queda frenada.
     */
    @Transactional(readOnly = true)
    public Optional<RegistroBlockchain> ultimoTerminado() {
        return repository.findFirstByEstadoInOrderByFechaCreacionDesc(TERMINADOS);
    }

    /** Hash guardado del evento número dado, si existe. */
    @Transactional(readOnly = true)
    public Optional<String> hashDelEvento(long numero) {
        return eventoRepository.findByNumero(numero).map(EventoTrazabilidad::getHash);
    }

    /** Estado de la cadena local: último número y hash (lectura sin bloqueo). */
    @Transactional(readOnly = true)
    public CadenaEstado cadena() {
        return cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de estado de la cadena"));
    }

    /** Número del último evento de la cadena local (lectura sin bloqueo). */
    @Transactional(readOnly = true)
    public long ultimoNumeroLocal() {
        return cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL)
                .map(CadenaEstado::getUltimoNumero).orElse(0L);
    }

    /**
     * El anclaje que correspondería abrir ahora, SIN guardarlo (para estimar su
     * costo antes de decidir): PENDIENTE del último evento si hay eventos sin
     * cubrir. Lo ya cubierto es el mayor entre el último anclaje exitoso local
     * y el último número del contrato (ultimoAnclado). Vacío si no hubo eventos
     * nuevos. Lee cadena_estado SIN bloqueo.
     */
    @Transactional(readOnly = true)
    public Optional<RegistroBlockchain> candidato(long ultimoAnclado) {
        CadenaEstado cadena = cadena();
        Long ultimoExitoso = repository.maxHastaNumero(EXITOSOS);
        long cubierto = Math.max(ultimoExitoso == null ? 0L : ultimoExitoso, ultimoAnclado);
        if (cadena.getUltimoNumero() <= cubierto) {
            return Optional.empty();
        }
        return Optional.of(new RegistroBlockchain(cubierto + 1, cadena.getUltimoNumero(), cadena.getUltimoHash(),
                propiedades.getRed(), propiedades.getContrato()));
    }

    /**
     * Abre (guarda) el anclaje PENDIENTE del candidato actual. Vacío si ya hay
     * un anclaje en curso o si no hubo eventos nuevos.
     */
    @Transactional
    public Optional<RegistroBlockchain> crearPendiente(long ultimoAnclado) {
        if (repository.findFirstByEstadoInOrderByFechaCreacionDesc(EN_CURSO).isPresent()) {
            return Optional.empty();
        }
        return candidato(ultimoAnclado).map(repository::save);
    }

    /** Suma un intento de envío. */
    @Transactional
    public RegistroBlockchain iniciarIntento(UUID id) {
        RegistroBlockchain registro = buscar(id);
        registro.iniciarIntento();
        return repository.save(registro);
    }

    /** Guarda la transacción firmada antes de transmitirla. */
    @Transactional
    public RegistroBlockchain registrarTransaccion(UUID id, TransaccionFirmada transaccion) {
        RegistroBlockchain registro = buscar(id);
        registro.registrarTransaccion(transaccion);
        return repository.save(registro);
    }

    /** PENDIENTE → ENVIADO. */
    @Transactional
    public RegistroBlockchain marcarEnviado(UUID id) {
        RegistroBlockchain registro = buscar(id);
        registro.marcarEnviado(ahora());
        return repository.save(registro);
    }

    /** ENVIADO → ENVIADO con la transacción de reemplazo. */
    @Transactional
    public RegistroBlockchain registrarReemplazo(UUID id, TransaccionFirmada transaccion) {
        RegistroBlockchain registro = buscar(id);
        registro.registrarReemplazo(transaccion, ahora());
        return repository.save(registro);
    }

    /** Un intento falló: espera creciente (30 s, 1, 2, 4 min) o FALLIDO al llegar a max-intentos. */
    @Transactional
    public RegistroBlockchain registrarFallo(UUID id, String error) {
        RegistroBlockchain registro = buscar(id);
        long factor = 1L << Math.min(Math.max(registro.getIntentos() - 1, 0), 10);
        registro.registrarFallo(error, ahora().plus(ESPERA_INICIAL.multipliedBy(factor)), propiedades.getMaxIntentos());
        return repository.save(registro);
    }

    /** La transacción está en un bloque: confirmaciones = último bloque − bloque de la transacción. */
    @Transactional
    public RegistroBlockchain registrarInclusion(UUID id, ReciboTransaccion recibo, long ultimoBloque) {
        RegistroBlockchain registro = buscar(id);
        int confirmaciones = (int) Math.min(Integer.MAX_VALUE, ultimoBloque - recibo.getBloque());
        registro.registrarInclusion(recibo, confirmaciones, propiedades.getConfirmaciones(), ahora());
        return repository.save(registro);
    }

    /** El recibo desapareció (reorganización): vuelve a esperar la inclusión. */
    @Transactional
    public RegistroBlockchain registrarSinInclusion(UUID id) {
        RegistroBlockchain registro = buscar(id);
        registro.registrarSinInclusion();
        return repository.save(registro);
    }

    /** → FALLIDO con el motivo y su causa. */
    @Transactional
    public RegistroBlockchain marcarFallido(UUID id, String error, CausaFallo causa) {
        RegistroBlockchain registro = buscar(id);
        registro.marcarFallido(error, causa);
        return repository.save(registro);
    }

    /** Busca el anclaje por id o lanza ResourceNotFoundException. */
    private RegistroBlockchain buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegistroBlockchain no encontrado con id: " + id));
    }
}
