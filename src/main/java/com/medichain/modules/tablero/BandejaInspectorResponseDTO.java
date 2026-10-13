package com.medichain.modules.tablero;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de salida BandejaInspectorResponseDTO en MediChain.
 * Cuántos pendientes tiene el inspector en cada una de sus bandejas (las mismas
 * consultas que los listados de bandeja, así el número coincide con la lista):
 * para mostrar insignias en el menú sin pedir las cinco bandejas.
 */
public class BandejaInspectorResponseDTO {

    @Schema(description = "Solicitudes de habilitación de tu provincia sin tomar, más las que tomaste", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long empresas;
    @Schema(description = "Circuitos PENDIENTE_INSPECTOR de farmacias de tu provincia sin tomar, más los que tomaste", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long circuitos;
    @Schema(description = "Lotes biológicos PENDIENTE_LIBERACION de laboratorios de tu provincia", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long lotesBiologicos;
    @Schema(description = "Cuarentenas ACTIVA de tu provincia sin tomar, más las que tomaste", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long cuarentenas;
    @Schema(description = "Reportes ciudadanos ABIERTO de tu provincia, más los que investigás", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportes;

    /** Constructor vacío exigido por Jackson. */
    public BandejaInspectorResponseDTO() {
    }

    /** Devuelve las solicitudes de habilitación pendientes. */
    public Long getEmpresas() {
        return empresas;
    }

    /** Establece las solicitudes de habilitación pendientes. */
    public void setEmpresas(Long empresas) {
        this.empresas = empresas;
    }

    /** Devuelve los circuitos pendientes. */
    public Long getCircuitos() {
        return circuitos;
    }

    /** Establece los circuitos pendientes. */
    public void setCircuitos(Long circuitos) {
        this.circuitos = circuitos;
    }

    /** Devuelve los lotes biológicos por liberar. */
    public Long getLotesBiologicos() {
        return lotesBiologicos;
    }

    /** Establece los lotes biológicos por liberar. */
    public void setLotesBiologicos(Long lotesBiologicos) {
        this.lotesBiologicos = lotesBiologicos;
    }

    /** Devuelve las cuarentenas pendientes. */
    public Long getCuarentenas() {
        return cuarentenas;
    }

    /** Establece las cuarentenas pendientes. */
    public void setCuarentenas(Long cuarentenas) {
        this.cuarentenas = cuarentenas;
    }

    /** Devuelve los reportes pendientes. */
    public Long getReportes() {
        return reportes;
    }

    /** Establece los reportes pendientes. */
    public void setReportes(Long reportes) {
        this.reportes = reportes;
    }
}
