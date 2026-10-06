package com.medichain.modules.reporteciudadano;

import com.medichain.utils.enums.Provincia;
import com.medichain.utils.validacion.Gtin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada ReporteCiudadanoRequestDTO en MediChain.
 * El paciente reporta lo que escaneó (GTIN + serie), con un motivo de lista
 * fija, la provincia donde consiguió el medicamento y una descripción libre
 * opcional. El código REP-0001 lo genera el servidor.
 */
public class ReporteCiudadanoRequestDTO {

    @Schema(description = "GTIN escaneado de la caja (DataMatrix (01))", example = "07799000001010", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo gtin es obligatorio")
    @Gtin
    private String gtin;

    @Schema(description = "Serie escaneada (DataMatrix (21)); puede no existir (sospecha de falsificación)", example = "FALSA00099", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo serie es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9]{1,20}", message = "La serie es alfanumérica de hasta 20 caracteres")
    private String serie;

    @Schema(description = "Motivo del reporte", example = "SOSPECHA_FALSIFICACION", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo motivo es obligatorio")
    private MotivoReporte motivo;

    @Schema(description = "Provincia donde conseguiste el medicamento (ahí se investiga)", example = "MENDOZA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo provincia es obligatorio")
    private Provincia provincia;

    @Schema(description = "Qué pasó (opcional). Queda solo en tu reporte, nunca en la cadena de eventos.")
    @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
    private String descripcion;

    /** Constructor vacío exigido por Jackson. */
    public ReporteCiudadanoRequestDTO() {
    }

    /** Devuelve el GTIN escaneado. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN escaneado. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve la serie escaneada. */
    public String getSerie() {
        return serie;
    }

    /** Establece la serie escaneada. */
    public void setSerie(String serie) {
        this.serie = serie;
    }

    /** Devuelve el motivo. */
    public MotivoReporte getMotivo() {
        return motivo;
    }

    /** Establece el motivo. */
    public void setMotivo(MotivoReporte motivo) {
        this.motivo = motivo;
    }

    /** Devuelve la provincia donde se consiguió el medicamento. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia donde se consiguió el medicamento. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve la descripción libre. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Establece la descripción libre. */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
