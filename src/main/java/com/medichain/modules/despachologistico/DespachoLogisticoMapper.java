package com.medichain.modules.despachologistico;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.empresa.Empresa;
import org.springframework.stereotype.Component;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Mapper DespachoLogisticoMapper en MediChain.
 * Convierte el viaje en su DTO de salida (con códigos de bultos y paradas),
 * sin lógica de negocio. La creación la hace DespachoLogisticoService.crear.
 */
@Component
public class DespachoLogisticoMapper {

    /** Convierte una entidad DespachoLogistico en su DTO de salida. */
    public DespachoLogisticoResponseDTO toResponseDTO(DespachoLogistico entity) {
        if (entity == null) {
            return null;
        }
        DespachoLogisticoResponseDTO dto = new DespachoLogisticoResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setCodigo(entity.getCodigo());
        dto.setTramo(entity.getTramo());
        dto.setPatente(entity.getPatente());
        dto.setChofer(entity.getChofer());
        dto.setFechaSalida(entity.getFechaSalida());
        dto.setFechaEstimadaEntrega(entity.getFechaEstimadaEntrega());
        dto.setEstado(entity.getEstado());
        dto.setOrigenId(entity.getOrigen() != null ? entity.getOrigen().getId() : null);
        dto.setCreadoPorId(entity.getCreadoPor() != null ? entity.getCreadoPor().getId() : null);
        dto.setBultoIds(entity.getBultos().stream().map(Bulto::getId).collect(Collectors.toCollection(LinkedHashSet::new)));
        dto.setBultoCodigos(entity.getBultos().stream().map(Bulto::getCodigo).sorted().toList());
        dto.setParadaIds(entity.paradas().stream().map(Empresa::getId).toList());
        return dto;
    }
}
