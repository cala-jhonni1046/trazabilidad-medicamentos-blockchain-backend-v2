package com.medichain.modules.unidadtrazable;

import com.medichain.utils.validacion.Gtin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada DevolucionRequestDTO en MediChain (R14).
 * La farmacia devuelve una caja de su stock: la identifica por GTIN + serie
 * (lo que escanea), con un motivo de lista fija y una observación opcional.
 */
public class DevolucionRequestDTO {

    @Schema(description = "GTIN de la caja", example = "07799000001010", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo gtin es obligatorio")
    @Gtin
    private String gtin;

    @Schema(description = "Serie de la caja", example = "L20260001S000012", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo serie es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9]{1,20}", message = "La serie es alfanumérica de hasta 20 caracteres")
    private String serie;

    @Schema(description = "Motivo de la devolución", example = "DANADA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo motivo es obligatorio")
    private MotivoDevolucion motivo;

    @Schema(description = "Observación libre (opcional; al evento va solo su hash)")
    @Size(max = 1000, message = "La observación no puede superar 1000 caracteres")
    private String observacion;

    /** Constructor vacío exigido por Jackson. */
    public DevolucionRequestDTO() {
    }

    /** Devuelve el GTIN. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve la serie. */
    public String getSerie() {
        return serie;
    }

    /** Establece la serie. */
    public void setSerie(String serie) {
        this.serie = serie;
    }

    /** Devuelve el motivo. */
    public MotivoDevolucion getMotivo() {
        return motivo;
    }

    /** Establece el motivo. */
    public void setMotivo(MotivoDevolucion motivo) {
        this.motivo = motivo;
    }

    /** Devuelve la observación. */
    public String getObservacion() {
        return observacion;
    }

    /** Establece la observación. */
    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
