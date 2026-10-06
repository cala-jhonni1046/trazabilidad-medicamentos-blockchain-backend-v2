package com.medichain.modules.telemetriatemperatura;

import org.springframework.stereotype.Component;

/**
 * Mapper TelemetriaTemperaturaMapper en MediChain.
 * Convierte entre TelemetriaTemperaturaRequestDTO/ResponseDTO y la
 * entidad TelemetriaTemperatura, sin lógica de negocio. La relación con
 * el despacho no se completa acá: el service la resuelve contra la base
 * a partir del id que trae el DTO.
 */
@Component
public class TelemetriaTemperaturaMapper {

    /** Convierte una entidad TelemetriaTemperatura en su DTO de salida. */
    public TelemetriaTemperaturaResponseDTO toResponseDTO(TelemetriaTemperatura entity) {
        if (entity == null) {
            return null;
        }
        TelemetriaTemperaturaResponseDTO dto = new TelemetriaTemperaturaResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setSensorId(entity.getSensorId());
        dto.setTemperatura(entity.getTemperatura());
        dto.setFueraDeRango(entity.getFueraDeRango());
        dto.setFechaHora(entity.getFechaHora());
        dto.setDespachoId(entity.getDespacho() != null ? entity.getDespacho().getId() : null);
        return dto;
    }
}
