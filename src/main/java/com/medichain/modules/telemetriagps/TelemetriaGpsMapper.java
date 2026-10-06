package com.medichain.modules.telemetriagps;

import org.springframework.stereotype.Component;

/**
 * Mapper TelemetriaGpsMapper en MediChain.
 * Convierte entre TelemetriaGpsRequestDTO/ResponseDTO y la entidad
 * TelemetriaGps, sin lógica de negocio. La relación con el despacho no
 * se completa acá: el service la resuelve contra la base a partir del id
 * que trae el DTO.
 */
@Component
public class TelemetriaGpsMapper {

    /** Convierte una entidad TelemetriaGps en su DTO de salida. */
    public TelemetriaGpsResponseDTO toResponseDTO(TelemetriaGps entity) {
        if (entity == null) {
            return null;
        }
        TelemetriaGpsResponseDTO dto = new TelemetriaGpsResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setSensorId(entity.getSensorId());
        dto.setLatitud(entity.getLatitud());
        dto.setLongitud(entity.getLongitud());
        dto.setLugar(entity.getLugar());
        dto.setFechaHora(entity.getFechaHora());
        dto.setDespachoId(entity.getDespacho() != null ? entity.getDespacho().getId() : null);
        return dto;
    }
}
