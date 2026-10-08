package com.medichain.modules.bulto;

import com.medichain.modules.cuarentena.Bloqueo;
import org.springframework.stereotype.Component;

/**
 * Mapper BultoMapper en MediChain.
 * Convierte la entidad Bulto en su DTO de salida, sin lógica de negocio.
 * El armado lo hace BultoService.armar.
 */
@Component
public class BultoMapper {

    /** Convierte una entidad Bulto en su DTO de salida, con su bloqueo R10 (null si no está bloqueado). */
    public BultoResponseDTO toResponseDTO(Bulto entity, Bloqueo bloqueo) {
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
        // R10: lo calcula EvaluadorBloqueo (el mapper no consulta la base); null = no bloqueado.
        dto.setBloqueado(bloqueo != null);
        dto.setMotivoBloqueo(bloqueo != null ? bloqueo.getCausa() : null);
        dto.setMensajeBloqueo(bloqueo != null ? bloqueo.getMensaje() : null);
        return dto;
    }
}
