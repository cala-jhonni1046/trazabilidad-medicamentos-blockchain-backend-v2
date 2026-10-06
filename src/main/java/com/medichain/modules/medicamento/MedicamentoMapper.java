package com.medichain.modules.medicamento;

import org.springframework.stereotype.Component;

/**
 * Mapper MedicamentoMapper en MediChain.
 * Convierte entre MedicamentoRequestDTO/MedicamentoResponseDTO y la
 * entidad Medicamento, sin lógica de negocio. La relación con el
 * laboratorio no se completa acá: el service la resuelve contra la base
 * a partir del id que trae el DTO.
 */
@Component
public class MedicamentoMapper {

    /** Convierte el DTO de alta en una entidad Medicamento nueva (sin relación aún). */
    public Medicamento toEntity(MedicamentoRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        Medicamento entity = new Medicamento(dto.getGtin(), dto.getNombreComercial(), dto.getPrincipioActivo(),
                dto.getConcentracion(), dto.getFormaFarmaceutica(), dto.getPresentacion());
        entity.setTemperaturaMinima(dto.getTemperaturaMinima());
        entity.setTemperaturaMaxima(dto.getTemperaturaMaxima());
        if (dto.getBiologico() != null) {
            entity.setBiologico(dto.getBiologico());
        }
        return entity;
    }

    /** Convierte una entidad Medicamento en su DTO de salida. */
    public MedicamentoResponseDTO toResponseDTO(Medicamento entity) {
        if (entity == null) {
            return null;
        }
        MedicamentoResponseDTO dto = new MedicamentoResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setGtin(entity.getGtin());
        dto.setNombreComercial(entity.getNombreComercial());
        dto.setPrincipioActivo(entity.getPrincipioActivo());
        dto.setConcentracion(entity.getConcentracion());
        dto.setFormaFarmaceutica(entity.getFormaFarmaceutica());
        dto.setPresentacion(entity.getPresentacion());
        dto.setTemperaturaMinima(entity.getTemperaturaMinima());
        dto.setTemperaturaMaxima(entity.getTemperaturaMaxima());
        dto.setBiologico(entity.getBiologico());
        dto.setActivo(entity.getActivo());
        dto.setLaboratorioId(entity.getLaboratorio() != null ? entity.getLaboratorio().getId() : null);
        return dto;
    }
}
