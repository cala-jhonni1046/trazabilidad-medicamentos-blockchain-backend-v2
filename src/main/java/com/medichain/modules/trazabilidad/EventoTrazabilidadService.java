package com.medichain.modules.trazabilidad;

import com.medichain.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Servicio EventoTrazabilidadService en MediChain.
 * Solo lectura de la cadena de eventos. Las altas las hace
 * RegistradorEventos dentro de cada transacción de negocio.
 */
@Service
public class EventoTrazabilidadService {

    private final EventoTrazabilidadRepository repository;

    @Autowired
    public EventoTrazabilidadService(EventoTrazabilidadRepository repository) {
        this.repository = repository;
    }

    /** Devuelve una página de eventos de trazabilidad. */
    @Transactional(readOnly = true)
    public Page<EventoTrazabilidad> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /** Busca un evento por id o lanza ResourceNotFoundException si no existe. */
    @Transactional(readOnly = true)
    public EventoTrazabilidad getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventoTrazabilidad no encontrado con id: " + id));
    }
}
