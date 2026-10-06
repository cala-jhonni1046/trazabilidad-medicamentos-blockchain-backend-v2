package com.medichain.modules.inspectoranmat;

import org.springframework.stereotype.Component;

/**
 * Mapper InspectorAnmatMapper en MediChain.
 * Convierte entre InspectorAnmatRequestDTO/InspectorAnmatResponseDTO y la
 * entidad InspectorAnmat, sin lógica de negocio. Las relaciones con Usuario
 * (obligatorias) no se completan acá: el service las resuelve contra la
 * base a partir de los ids que trae el DTO.
 */
@Component
public class InspectorAnmatMapper {

    /** Convierte el DTO de alta en una entidad InspectorAnmat nueva (sin relaciones aún). */
    public InspectorAnmat toEntity(InspectorAnmatRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return new InspectorAnmat(dto.getLegajo(), dto.getDni(), dto.getProvincia());
    }

    /** Convierte una entidad InspectorAnmat en su DTO de salida. */
    public InspectorAnmatResponseDTO toResponseDTO(InspectorAnmat entity) {
        if (entity == null) {
            return null;
        }
        InspectorAnmatResponseDTO dto = new InspectorAnmatResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setLegajo(entity.getLegajo());
        dto.setDni(entity.getDni());
        dto.setProvincia(entity.getProvincia());
        dto.setEstado(entity.getEstado());
        dto.setFechaAlta(entity.getFechaAlta());
        dto.setFechaBaja(entity.getFechaBaja());
        dto.setUsuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null);
        dto.setUsuarioAltaId(entity.getUsuarioAlta() != null ? entity.getUsuarioAlta().getId() : null);
        return dto;
    }
}
