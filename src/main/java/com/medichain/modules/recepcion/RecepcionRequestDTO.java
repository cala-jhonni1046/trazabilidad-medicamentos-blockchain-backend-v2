package com.medichain.modules.recepcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO de entrada RecepcionRequestDTO en MediChain (R8).
 * Lo que verifica quien recibe al escanear el código del bulto. El viaje,
 * la fecha, la receptora y si es conforme los resuelve el servidor (no el
 * cliente).
 */
public class RecepcionRequestDTO {

    @Schema(description = "Código del bulto escaneado", example = "BUL-0002", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo codigoBulto es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9-]{1,20}", message = "El código de bulto admite hasta 20 letras, dígitos o guiones")
    private String codigoBulto;

    @Schema(description = "¿El precinto llegó intacto?", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo precintoIntacto es obligatorio")
    private Boolean precintoIntacto;

    @Schema(description = "Cantidad de cajas contadas al recibir", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo cantidadVerificada es obligatorio")
    @Min(value = 0, message = "La cantidad verificada no puede ser negativa")
    private Integer cantidadVerificada;

    @Schema(description = "Temperatura del bulto al llegar (°C)", example = "21.5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo temperatura es obligatorio")
    @DecimalMin(value = "-99.99", message = "La temperatura no puede ser menor a -99.99")
    @DecimalMax(value = "999.99", message = "La temperatura no puede ser mayor a 999.99")
    private BigDecimal temperatura;

    @Schema(description = "Observación libre (opcional; se guarda en el acta y nunca va a la cadena)")
    @Size(max = 2000, message = "La observación no puede superar 2000 caracteres")
    private String observacion;

    /** Constructor vacío exigido por Jackson. */
    public RecepcionRequestDTO() {
    }

    /** Devuelve el código del bulto. */
    public String getCodigoBulto() {
        return codigoBulto;
    }

    /** Establece el código del bulto. */
    public void setCodigoBulto(String codigoBulto) {
        this.codigoBulto = codigoBulto;
    }

    /** Indica si el precinto llegó intacto. */
    public Boolean getPrecintoIntacto() {
        return precintoIntacto;
    }

    /** Establece si el precinto llegó intacto. */
    public void setPrecintoIntacto(Boolean precintoIntacto) {
        this.precintoIntacto = precintoIntacto;
    }

    /** Devuelve la cantidad verificada. */
    public Integer getCantidadVerificada() {
        return cantidadVerificada;
    }

    /** Establece la cantidad verificada. */
    public void setCantidadVerificada(Integer cantidadVerificada) {
        this.cantidadVerificada = cantidadVerificada;
    }

    /** Devuelve la temperatura de llegada. */
    public BigDecimal getTemperatura() {
        return temperatura;
    }

    /** Establece la temperatura de llegada. */
    public void setTemperatura(BigDecimal temperatura) {
        this.temperatura = temperatura;
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
