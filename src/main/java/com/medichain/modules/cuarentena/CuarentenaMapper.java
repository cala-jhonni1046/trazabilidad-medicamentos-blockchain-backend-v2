package com.medichain.modules.cuarentena;

import com.medichain.modules.bulto.Bulto;
import org.springframework.stereotype.Component;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Mapper CuarentenaMapper en MediChain.
 * Convierte la medida sanitaria en su DTO de salida, sin lógica de negocio.
 * El alta la arma CuarentenaService (manual) o AperturaCuarentenas (automáticas).
 */
@Component
public class CuarentenaMapper {

    /** Convierte una entidad Cuarentena en su DTO de salida. */
    public CuarentenaResponseDTO toResponseDTO(Cuarentena entity) {
        if (entity == null) {
            return null;
        }
        CuarentenaResponseDTO dto = new CuarentenaResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setAlcance(entity.getAlcance());
        dto.setTipo(entity.getTipo());
        dto.setMotivo(entity.getMotivo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setAutomatica(entity.getAutomatica());
        dto.setEstado(entity.getEstado());
        dto.setDictamen(entity.getDictamen());
        dto.setProvincia(entity.getProvincia());
        dto.setFechaInicio(entity.getFechaInicio());
        dto.setFechaFin(entity.getFechaFin());
        dto.setLoteId(entity.getLote() != null ? entity.getLote().getId() : null);
        dto.setDespachoId(entity.getDespacho() != null ? entity.getDespacho().getId() : null);
        dto.setInspectorId(entity.getInspector() != null ? entity.getInspector().getId() : null);
        dto.setInspectorRevisorId(entity.getInspectorRevisor() != null ? entity.getInspectorRevisor().getId() : null);
        dto.setReporteOrigen(entity.getReporteOrigen() != null ? entity.getReporteOrigen().getCodigo() : null);
        dto.setBultoIds(entity.getBultos().stream().map(Bulto::getId).collect(Collectors.toCollection(LinkedHashSet::new)));
        return dto;
    }
}
