package com.medichain.modules.empresa;

import org.springframework.stereotype.Component;

/**
 * Mapper EmpresaMapper en MediChain.
 * Convierte la entidad Empresa en EmpresaResponseDTO (el alta llega por
 * RegistroEmpresaRequestDTO y la arma EmpresaService.registrar),
 * sin lógica de negocio. De los inspectores solo expone los ids.
 */
@Component
public class EmpresaMapper {

    /** Convierte una entidad Empresa en su DTO de salida. */
    public EmpresaResponseDTO toResponseDTO(Empresa entity) {
        if (entity == null) {
            return null;
        }
        EmpresaResponseDTO dto = new EmpresaResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setTipo(entity.getTipo());
        dto.setCuit(entity.getCuit());
        dto.setRazonSocial(entity.getRazonSocial());
        dto.setGln(entity.getGln());
        dto.setProvincia(entity.getProvincia());
        dto.setLocalidad(entity.getLocalidad());
        dto.setDomicilio(entity.getDomicilio());
        dto.setNumeroHabilitacion(entity.getNumeroHabilitacion());
        dto.setDirectorTecnico(entity.getDirectorTecnico());
        dto.setDocumentoNombre(entity.getDocumentoNombre());
        dto.setDocumentoHash(entity.getDocumentoHash());
        dto.setEstado(entity.getEstado());
        dto.setFechaSolicitud(entity.getFechaSolicitud());
        dto.setFechaHabilitacion(entity.getFechaHabilitacion());
        dto.setMotivoRechazo(entity.getMotivoRechazo());
        dto.setMotivoSuspension(entity.getMotivoSuspension());
        dto.setInspectorRevisorId(entity.getInspectorRevisor() != null ? entity.getInspectorRevisor().getId() : null);
        dto.setInspectorHabilitadorId(entity.getInspectorHabilitador() != null ? entity.getInspectorHabilitador().getId() : null);
        return dto;
    }

    /** Convierte una entidad Empresa en su resumen (búsqueda por CUIT para armar circuitos). */
    public EmpresaResumenDTO toResumenDTO(Empresa entity) {
        if (entity == null) {
            return null;
        }
        EmpresaResumenDTO dto = new EmpresaResumenDTO();
        dto.setId(entity.getId());
        dto.setCuit(entity.getCuit());
        dto.setRazonSocial(entity.getRazonSocial());
        dto.setTipo(entity.getTipo());
        dto.setProvincia(entity.getProvincia());
        return dto;
    }
}
