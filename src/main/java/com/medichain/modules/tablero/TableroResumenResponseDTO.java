package com.medichain.modules.tablero;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de salida TableroResumenResponseDTO en MediChain.
 * Los totales del tablero de la Sede en una sola respuesta: así el frontend no
 * pide cada listado para leer su cantidad. Son totales de todo el sistema (no
 * dependen de la provincia).
 */
public class TableroResumenResponseDTO {

    @Schema(description = "Empresas registradas, en cualquier estado", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long empresas;
    @Schema(description = "Empresas con la solicitud de habilitación PENDIENTE", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long empresasPendientes;
    @Schema(description = "Inspectores ACTIVO", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long inspectoresActivos;
    @Schema(description = "Circuitos APROBADO", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long circuitosAprobados;
    @Schema(description = "Cuarentenas y recalls en estado ACTIVA", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long cuarentenasActivas;
    @Schema(description = "Eventos en la cadena (el último número: la numeración no tiene huecos)", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long eventos;
    @Schema(description = "Anclajes en Sepolia CONFIRMADO (con sus 3 confirmaciones)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long anclajesConfirmados;

    /** Constructor vacío exigido por Jackson. */
    public TableroResumenResponseDTO() {
    }

    /** Devuelve la cantidad de empresas. */
    public Long getEmpresas() {
        return empresas;
    }

    /** Establece la cantidad de empresas. */
    public void setEmpresas(Long empresas) {
        this.empresas = empresas;
    }

    /** Devuelve la cantidad de empresas pendientes. */
    public Long getEmpresasPendientes() {
        return empresasPendientes;
    }

    /** Establece la cantidad de empresas pendientes. */
    public void setEmpresasPendientes(Long empresasPendientes) {
        this.empresasPendientes = empresasPendientes;
    }

    /** Devuelve la cantidad de inspectores activos. */
    public Long getInspectoresActivos() {
        return inspectoresActivos;
    }

    /** Establece la cantidad de inspectores activos. */
    public void setInspectoresActivos(Long inspectoresActivos) {
        this.inspectoresActivos = inspectoresActivos;
    }

    /** Devuelve la cantidad de circuitos aprobados. */
    public Long getCircuitosAprobados() {
        return circuitosAprobados;
    }

    /** Establece la cantidad de circuitos aprobados. */
    public void setCircuitosAprobados(Long circuitosAprobados) {
        this.circuitosAprobados = circuitosAprobados;
    }

    /** Devuelve la cantidad de cuarentenas activas. */
    public Long getCuarentenasActivas() {
        return cuarentenasActivas;
    }

    /** Establece la cantidad de cuarentenas activas. */
    public void setCuarentenasActivas(Long cuarentenasActivas) {
        this.cuarentenasActivas = cuarentenasActivas;
    }

    /** Devuelve la cantidad de eventos de la cadena. */
    public Long getEventos() {
        return eventos;
    }

    /** Establece la cantidad de eventos de la cadena. */
    public void setEventos(Long eventos) {
        this.eventos = eventos;
    }

    /** Devuelve la cantidad de anclajes confirmados. */
    public Long getAnclajesConfirmados() {
        return anclajesConfirmados;
    }

    /** Establece la cantidad de anclajes confirmados. */
    public void setAnclajesConfirmados(Long anclajesConfirmados) {
        this.anclajesConfirmados = anclajesConfirmados;
    }
}
