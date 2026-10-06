package com.medichain.modules.bulto;

import org.springframework.stereotype.Component;

/**
 * Mapper BultoMapper en MediChain.
 * Convierte la entidad Bulto en su DTO de salida, sin lógica de negocio.
 * El armado lo hace BultoService.armar.
 */
@Component
public class BultoMapper {

    /** Convierte una entidad Bulto en su DTO de salida. */
    public BultoResponseDTO toResponseDTO(Bulto entity) {
        if (entity == null) {
            return null;
        }
        BultoResponseDTO dto = new BultoResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setCodigo(entity.getCodigo());
        dto.setCantidad(entity.getCantidad());
        dto.setPrecinto(entity.getPrecinto());
        dto.setEstado(entity.getEstado());
        dto.setFechaArmado(entity.getFechaArmado());
        dto.setLoteId(entity.getLote() != null ? entity.getLote().getId() : null);
        dto.setDestinoId(entity.getDestino() != null ? entity.getDestino().getId() : null);
        dto.setUbicacionId(entity.getUbicacion() != null ? entity.getUbicacion().getId() : null);
        dto.setViajeActualId(entity.getViajeActual() != null ? entity.getViajeActual().getId() : null);
        return dto;
    }
}
