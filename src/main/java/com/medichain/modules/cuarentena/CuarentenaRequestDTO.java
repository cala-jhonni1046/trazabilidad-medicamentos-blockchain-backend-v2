package com.medichain.modules.cuarentena;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO de entrada CuarentenaRequestDTO en MediChain.
 * Cuarentena MANUAL de alcance LOTE que abre un inspector de la provincia
 * del laboratorio. La provincia la deduce el servidor (la del laboratorio).
 * Opcionalmente nace de un reporte ciudadano que el inspector investiga.
 */
public class CuarentenaRequestDTO {

    @Schema(description = "Lote LIBERADO a poner en cuarentena", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo loteId es obligatorio")
    private UUID loteId;

    @Schema(description = "PREVENTIVA o DEFECTO_CALIDAD (los demás motivos los usa el sistema)", example = "DEFECTO_CALIDAD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo motivo es obligatorio")
    @MotivoManual
    private MotivoBloqueo motivo;

    @Schema(description = "Detalle libre (queda en la medida; nunca va a la cadena)")
    @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
    private String descripcion;

    @Schema(description = "Opcional: reporte ciudadano EN_INVESTIGACION, tomado por vos, cuya caja es de este lote")
    private UUID reporteId;

    /** Constructor vacío exigido por Jackson. */
    public CuarentenaRequestDTO() {
    }

    /** Devuelve el id del lote. */
    public UUID getLoteId() {
        return loteId;
    }

    /** Establece el id del lote. */
    public void setLoteId(UUID loteId) {
        this.loteId = loteId;
    }

    /** Devuelve el motivo. */
    public MotivoBloqueo getMotivo() {
        return motivo;
    }

    /** Establece el motivo. */
    public void setMotivo(MotivoBloqueo motivo) {
        this.motivo = motivo;
    }

    /** Devuelve la descripción libre. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Establece la descripción libre. */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Devuelve el reporte ciudadano que la origina. */
    public UUID getReporteId() {
        return reporteId;
    }

    /** Establece el reporte ciudadano que la origina. */
    public void setReporteId(UUID reporteId) {
        this.reporteId = reporteId;
    }
}
