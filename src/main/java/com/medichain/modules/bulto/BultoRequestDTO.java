package com.medichain.modules.bulto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada BultoRequestDTO en MediChain.
 * Armado de un bulto (R6): circuito APROBADO del laboratorio, lote
 * LIBERADO, precinto y las cajas. Las cajas se eligen de una de dos formas
 * (exactamente una): la lista de series escaneadas (el lote fija el GTIN)
 * o la cantidad, y el servidor toma las primeras cajas disponibles del lote
 * por orden de serie. Máximo 1.000 cajas por bulto. Código y cantidad los
 * fija el servidor.
 */
@BultoRequestValido
public class BultoRequestDTO {

    @Schema(description = "Circuito APROBADO de tu laboratorio (fija la farmacia de destino)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo circuitoId es obligatorio")
    private UUID circuitoId;

    @Schema(description = "Lote LIBERADO de tu laboratorio: todas las cajas del bulto son de este lote",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo loteId es obligatorio")
    private UUID loteId;

    @Schema(description = "Número de precinto (letras, dígitos o guion, hasta 30)", example = "PRE-000123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo precinto es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9-]{1,30}", message = "El precinto admite hasta 30 letras, dígitos o guiones")
    private String precinto;

    @Schema(description = "Forma A: series de las cajas (del lote indicado). No enviar junto con cantidad.",
            example = "[\"L20260001S000011\", \"L20260001S000012\"]")
    @Size(min = 1, max = Bulto.MAXIMO_CAJAS, message = "La lista de series debe tener entre 1 y 1000 elementos")
    private List<String> series;

    @Schema(description = "Forma B: cantidad de cajas; el servidor toma las primeras disponibles del lote. No enviar junto con series.",
            example = "10")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Max(value = Bulto.MAXIMO_CAJAS, message = "Un bulto no puede tener más de 1000 cajas")
    private Integer cantidad;

    /** Constructor vacío exigido por Jackson. */
    public BultoRequestDTO() {
    }

    /** Devuelve el id del circuito. */
    public UUID getCircuitoId() {
        return circuitoId;
    }

    /** Establece el id del circuito. */
    public void setCircuitoId(UUID circuitoId) {
        this.circuitoId = circuitoId;
    }

    /** Devuelve el id del lote. */
    public UUID getLoteId() {
        return loteId;
    }

    /** Establece el id del lote. */
    public void setLoteId(UUID loteId) {
        this.loteId = loteId;
    }

    /** Devuelve el precinto. */
    public String getPrecinto() {
        return precinto;
    }

    /** Establece el precinto. */
    public void setPrecinto(String precinto) {
        this.precinto = precinto;
    }

    /** Devuelve las series (forma A). */
    public List<String> getSeries() {
        return series;
    }

    /** Establece las series (forma A). */
    public void setSeries(List<String> series) {
        this.series = series;
    }

    /** Devuelve la cantidad (forma B). */
    public Integer getCantidad() {
        return cantidad;
    }

    /** Establece la cantidad (forma B). */
    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
