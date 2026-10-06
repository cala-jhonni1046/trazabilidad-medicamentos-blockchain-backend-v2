package com.medichain.modules.reporteciudadano;

import com.medichain.modules.usuario.RolUsuario;
import org.springframework.stereotype.Component;

/**
 * Mapper ReporteCiudadanoMapper en MediChain.
 * Convierte el reporte en su DTO de salida según QUIÉN lo lee: el paciente
 * ve su reporte, estado y fechas; inspectores y Sede ven además la
 * conclusión, el inspector y la provincia de la caja real. Sin lógica de
 * negocio; el alta la arma ReporteCiudadanoService.reportar.
 */
@Component
public class ReporteCiudadanoMapper {

    /** Convierte el reporte en su DTO de salida para el rol lector dado. */
    public ReporteCiudadanoResponseDTO toResponseDTO(ReporteCiudadano entity, RolUsuario lector) {
        if (entity == null) {
            return null;
        }
        ReporteCiudadanoResponseDTO dto = new ReporteCiudadanoResponseDTO();
        dto.setId(entity.getId());
        dto.setCodigo(entity.getCodigo());
        dto.setGtinReportado(entity.getGtinReportado());
        dto.setSerieReportada(entity.getSerieReportada());
        dto.setMotivo(entity.getMotivo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setEstado(entity.getEstado());
        dto.setProvincia(entity.getProvincia());
        dto.setFechaReporte(entity.getFechaReporte());
        dto.setFechaCierre(entity.getFechaCierre());
        dto.setCajaExiste(entity.getUnidadTrazable() != null);
        if (lector != RolUsuario.PACIENTE) {
            dto.setConclusion(entity.getConclusion());
            dto.setInvestigaId(entity.getInvestiga() != null ? entity.getInvestiga().getId() : null);
            dto.setProvinciaCaja(entity.getUnidadTrazable() != null && entity.getUnidadTrazable().getEmpresaActual() != null
                    ? entity.getUnidadTrazable().getEmpresaActual().getProvincia() : null);
        }
        return dto;
    }
}
