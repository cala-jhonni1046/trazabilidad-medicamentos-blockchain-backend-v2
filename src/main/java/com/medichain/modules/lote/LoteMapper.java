package com.medichain.modules.lote;

import com.medichain.modules.cuarentena.Bloqueo;
import org.springframework.stereotype.Component;

/**
 * Mapper LoteMapper en MediChain.
 * Convierte la entidad Lote en LoteResponseDTO, sin lógica de negocio. El
 * alta la arma LoteService.registrar (lote + cajas en una operación).
 */
@Component
public class LoteMapper {

    /** Convierte una entidad Lote en su DTO de salida, con su bloqueo R10 (null si no está bloqueado). */
    public LoteResponseDTO toResponseDTO(Lote entity, Bloqueo bloqueo) {
        if (entity == null) {
            return null;
        }
        LoteResponseDTO dto = new LoteResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setCodigo(entity.getCodigo());
        dto.setFechaFabricacion(entity.getFechaFabricacion());
        dto.setFechaVencimiento(entity.getFechaVencimiento());
        dto.setCantidad(entity.getCantidad());
        dto.setEstado(entity.getEstado());
        dto.setEstadoPrevio(entity.getEstadoPrevio());
        dto.setFechaLiberacion(entity.getFechaLiberacion());
        dto.setMedicamentoId(entity.getMedicamento() != null ? entity.getMedicamento().getId() : null);
        dto.setLaboratorioId(entity.getLaboratorio() != null ? entity.getLaboratorio().getId() : null);
        dto.setLiberadoPorId(entity.getLiberadoPor() != null ? entity.getLiberadoPor().getId() : null);
        // R10: lo calcula EvaluadorBloqueo (el mapper no consulta la base); null = no bloqueado.
        dto.setBloqueado(bloqueo != null);
        dto.setMotivoBloqueo(bloqueo != null ? bloqueo.getCausa() : null);
        dto.setMensajeBloqueo(bloqueo != null ? bloqueo.getMensaje() : null);
        return dto;
    }
}
