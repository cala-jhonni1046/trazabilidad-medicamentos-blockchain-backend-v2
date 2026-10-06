package com.medichain.modules.trazabilidad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio RegistradorEventosAparte en MediChain.
 * Registra un evento en su PROPIA transacción (REQUIRES_NEW), para los
 * intentos que deben quedar en la cadena aunque la operación que los
 * detectó falle: BULTO_INEXISTENTE, BULTO_DUPLICADO, INTENTO_DUPLICADO,
 * SERIE_ROBADA y SERIE_INEXISTENTE.
 * <ul>
 *   <li>Es un bean aparte a propósito: una llamada interna saltearía el
 *       proxy y REQUIRES_NEW no tendría efecto.</li>
 *   <li>Sin deadlock: quien lo llama debe hacerlo ANTES de registrar
 *       cualquier evento propio, así la transacción externa no tiene tomado
 *       el bloqueo de cadena_estado que pide la interna.</li>
 * </ul>
 */
@Service
public class RegistradorEventosAparte {

    private final RegistradorEventos registradorEventos;

    @Autowired
    public RegistradorEventosAparte(RegistradorEventos registradorEventos) {
        this.registradorEventos = registradorEventos;
    }

    /** Registra el evento en una transacción nueva que hace commit sola. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventoTrazabilidad registrar(TipoEvento tipo, String entidadTipo, UUID entidadId, Map<String, ?> datos,
                                        UUID actorUsuarioId, UUID actorEmpresaId) {
        return registradorEventos.registrar(tipo, entidadTipo, entidadId, datos, actorUsuarioId, actorEmpresaId);
    }
}
