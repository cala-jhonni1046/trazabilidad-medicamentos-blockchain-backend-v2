package com.medichain.modules.unidadtrazable;

import org.springframework.stereotype.Component;

/**
 * Mapper UnidadTrazableMapper en MediChain.
 * Convierte la entidad UnidadTrazable en su DTO de salida (con GTIN, serie
 * y código GS1), sin lógica de negocio.
 */
@Component
public class UnidadTrazableMapper {

    /** Convierte una entidad UnidadTrazable en su DTO de salida. */
    public UnidadTrazableResponseDTO toResponseDTO(UnidadTrazable entity) {
        if (entity == null) {
            return null;
        }
        UnidadTrazableResponseDTO dto = new UnidadTrazableResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setSerie(entity.getSerie());
        dto.setGtin(entity.getGtin());
        dto.setCodigoGS1(entity.codigoGS1());
        dto.setEstado(entity.getEstado());
        dto.setLoteId(entity.getLote() != null ? entity.getLote().getId() : null);
        dto.setEmpresaActualId(entity.getEmpresaActual() != null ? entity.getEmpresaActual().getId() : null);
        dto.setBultoId(entity.getBulto() != null ? entity.getBulto().getId() : null);
        return dto;
    }
}
