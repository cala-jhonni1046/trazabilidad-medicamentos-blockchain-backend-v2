package com.medichain.modules.enlacecuit;

import org.springframework.stereotype.Component;

/**
 * Mapper EnlaceCuitMapper en MediChain.
 * Convierte la entidad EnlaceCuit en su DTO de salida, sin lógica de
 * negocio (de las relaciones solo expone los ids). La propuesta la arma
 * EnlaceCuitService.proponer a partir de los CUIT.
 */
@Component
public class EnlaceCuitMapper {

    /** Convierte una entidad EnlaceCuit en su DTO de salida. */
    public EnlaceCuitResponseDTO toResponseDTO(EnlaceCuit entity) {
        if (entity == null) {
            return null;
        }
        EnlaceCuitResponseDTO dto = new EnlaceCuitResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setCodigo(entity.getCodigo());
        dto.setEstado(entity.getEstado());
        dto.setFechaPropuesta(entity.getFechaPropuesta());
        dto.setFechaAceptacionDistribuidor(entity.getFechaAceptacionDistribuidor());
        dto.setFechaAceptacionFarmacia(entity.getFechaAceptacionFarmacia());
        dto.setFechaAprobacion(entity.getFechaAprobacion());
        dto.setMotivoRechazo(entity.getMotivoRechazo());
        dto.setLaboratorioId(entity.getLaboratorio() != null ? entity.getLaboratorio().getId() : null);
        dto.setDistribuidorId(entity.getDistribuidor() != null ? entity.getDistribuidor().getId() : null);
        dto.setFarmaciaId(entity.getFarmacia() != null ? entity.getFarmacia().getId() : null);
        dto.setPropuestoPorId(entity.getPropuestoPor() != null ? entity.getPropuestoPor().getId() : null);
        dto.setInspectorAprobadorId(entity.getInspectorAprobador() != null ? entity.getInspectorAprobador().getId() : null);
        dto.setInspectorRevisorId(entity.getInspectorRevisor() != null ? entity.getInspectorRevisor().getId() : null);
        dto.setRechazadoPor(entity.getRechazadoPor());
        dto.setMotivoSuspension(entity.getMotivoSuspension());
        dto.setSuspendidoPorEmpresa(entity.getSuspendidoPorEmpresa());
        return dto;
    }
}
