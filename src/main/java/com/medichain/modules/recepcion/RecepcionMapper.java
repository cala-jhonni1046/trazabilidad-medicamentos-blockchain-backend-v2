package com.medichain.modules.recepcion;

import org.springframework.stereotype.Component;

/**
 * Mapper RecepcionMapper en MediChain.
 * Convierte el acta de recepción en su DTO de salida, sin lógica de negocio.
 * El alta la arma RecepcionService.recibir.
 */
@Component
public class RecepcionMapper {

    /** Convierte una entidad Recepcion en su DTO de salida. */
    public RecepcionResponseDTO toResponseDTO(Recepcion entity) {
        if (entity == null) {
            return null;
        }
        RecepcionResponseDTO dto = new RecepcionResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setFechaHora(entity.getFechaHora());
        dto.setTemperatura(entity.getTemperatura());
        dto.setPrecintoIntacto(entity.getPrecintoIntacto());
        dto.setCantidadVerificada(entity.getCantidadVerificada());
        dto.setConforme(entity.getConforme());
        dto.setMotivoRechazo(entity.getMotivoRechazo());
        dto.setObservacion(entity.getObservacion());
        dto.setCodigoBulto(entity.getBulto() != null ? entity.getBulto().getCodigo() : null);
        dto.setBultoId(entity.getBulto() != null ? entity.getBulto().getId() : null);
        dto.setDespachoId(entity.getDespacho() != null ? entity.getDespacho().getId() : null);
        dto.setReceptoraId(entity.getReceptora() != null ? entity.getReceptora().getId() : null);
        dto.setRegistradaPorId(entity.getRegistradaPor() != null ? entity.getRegistradaPor().getId() : null);
        return dto;
    }
}
