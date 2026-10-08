package com.medichain.modules.unidadtrazable;

import com.medichain.modules.cuarentena.Bloqueo;
import org.springframework.stereotype.Component;

/**
 * Mapper UnidadTrazableMapper en MediChain.
 * Convierte la entidad UnidadTrazable en su DTO de salida (con GTIN, serie
 * y código GS1), sin lógica de negocio.
 */
@Component
public class UnidadTrazableMapper {

    /** Convierte una entidad UnidadTrazable en su DTO de salida, con su bloqueo R10 (null si no está bloqueado). */
    public UnidadTrazableResponseDTO toResponseDTO(UnidadTrazable entity, Bloqueo bloqueo) {
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
        // R10: lo calcula EvaluadorBloqueo (el mapper no consulta la base); null = no bloqueado.
        dto.setBloqueado(bloqueo != null);
        dto.setMotivoBloqueo(bloqueo != null ? bloqueo.getCausa() : null);
        dto.setMensajeBloqueo(bloqueo != null ? bloqueo.getMensaje() : null);
        return dto;
    }
}
