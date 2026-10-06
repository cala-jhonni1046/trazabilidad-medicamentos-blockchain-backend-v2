package com.medichain.modules.dispensacion;

import org.springframework.stereotype.Component;

/**
 * Mapper DispensacionMapper en MediChain.
 * Convierte la dispensación en su DTO de salida, sin lógica de negocio. El
 * DNI sale solo enmascarado (la entidad nunca tiene el completo).
 */
@Component
public class DispensacionMapper {

    /** Convierte una entidad Dispensacion en su DTO de salida. */
    public DispensacionResponseDTO toResponseDTO(Dispensacion entity) {
        if (entity == null) {
            return null;
        }
        DispensacionResponseDTO dto = new DispensacionResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setFechaHora(entity.getFechaHora());
        dto.setObraSocial(entity.getObraSocial());
        dto.setNumeroAfiliado(entity.getNumeroAfiliado());
        dto.setNumeroReceta(entity.getNumeroReceta());
        dto.setDniEnmascarado(entity.getDniEnmascarado());
        dto.setParticular(entity.getParticular());
        dto.setAnulada(entity.getAnulada());
        dto.setFechaAnulacion(entity.getFechaAnulacion());
        dto.setUnidadTrazableId(entity.getUnidadTrazable() != null ? entity.getUnidadTrazable().getId() : null);
        dto.setGtin(entity.getUnidadTrazable() != null ? entity.getUnidadTrazable().getGtin() : null);
        dto.setSerie(entity.getUnidadTrazable() != null ? entity.getUnidadTrazable().getSerie() : null);
        dto.setFarmaciaId(entity.getFarmacia() != null ? entity.getFarmacia().getId() : null);
        dto.setFarmaceuticoId(entity.getFarmaceutico() != null ? entity.getFarmaceutico().getId() : null);
        return dto;
    }
}
