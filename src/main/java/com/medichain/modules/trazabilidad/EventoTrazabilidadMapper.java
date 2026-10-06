package com.medichain.modules.trazabilidad;

import org.springframework.stereotype.Component;

/**
 * Mapper EventoTrazabilidadMapper en MediChain.
 * Convierte la entidad EventoTrazabilidad en su DTO de salida, sin
 * lógica de negocio (solo lectura: este módulo no expone alta por API).
 */
@Component
public class EventoTrazabilidadMapper {

    /** Convierte una entidad EventoTrazabilidad en su DTO de salida. */
    public EventoTrazabilidadResponseDTO toResponseDTO(EventoTrazabilidad entity) {
        if (entity == null) {
            return null;
        }
        EventoTrazabilidadResponseDTO dto = new EventoTrazabilidadResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setNumero(entity.getNumero());
        dto.setTipo(entity.getTipo());
        dto.setFechaHora(entity.getFechaHora());
        dto.setEntidadTipo(entity.getEntidadTipo());
        dto.setEntidadId(entity.getEntidadId());
        dto.setDatosJson(entity.getDatosJson());
        dto.setHashAnterior(entity.getHashAnterior());
        dto.setHash(entity.getHash());
        dto.setActorUsuarioId(entity.getActorUsuarioId());
        dto.setActorEmpresaId(entity.getActorEmpresaId());
        return dto;
    }
}
