package com.medichain.modules.telemetriatemperatura;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de entrada TelemetriaTemperaturaRequestDTO en MediChain.
 * Transporta la lectura que reporta un sensor de temperatura durante un
 * despacho.
 */
public class TelemetriaTemperaturaRequestDTO {

    @NotBlank(message = "El campo sensorId es obligatorio")
    @Size(max = 100, message = "El campo sensorId no puede superar 100 caracteres")
    @Schema(description = "Identificador del sensor", example = "SENSOR-CAMION-01")
    private String sensorId;

    @NotNull(message = "El campo temperatura es obligatorio")
    @DecimalMin(value = "-99.99", message = "El campo temperatura no puede ser menor a -99.99")
    @DecimalMax(value = "999.99", message = "El campo temperatura no puede ser mayor a 999.99")
    @Schema(description = "Temperatura medida (°C)", example = "5.5")
    private BigDecimal temperatura;

    @Schema(description = "Momento de la lectura. Con offset obligatorio (ISO-8601, ej. -03:00); sin offset → 400", example = "2026-12-01T18:00:00-03:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo fechaHora es obligatorio")
    private OffsetDateTime fechaHora;

    // Campo 'despachoId': UUID del despacho al que pertenece la lectura (obligatorio).
    @NotNull(message = "El campo despachoId es obligatorio")
    @Schema(description = "Viaje EN_TRANSITO de tu empresa")
    private UUID despachoId;

    /** Constructor vacío exigido por Jackson. */
    public TelemetriaTemperaturaRequestDTO() {
    }

    /** Constructor con todos los campos obligatorios de alta. */
    public TelemetriaTemperaturaRequestDTO(String sensorId, BigDecimal temperatura,
                                            OffsetDateTime fechaHora, UUID despachoId) {
        this.sensorId = sensorId;
        this.temperatura = temperatura;
        this.fechaHora = fechaHora;
        this.despachoId = despachoId;
    }

    /** Devuelve el id de sensor informado. */
    public String getSensorId() {
        return sensorId;
    }

    /** Establece el id de sensor informado. */
    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    /** Devuelve la temperatura informada. */
    public BigDecimal getTemperatura() {
        return temperatura;
    }

    /** Establece la temperatura informada. */
    public void setTemperatura(BigDecimal temperatura) {
        this.temperatura = temperatura;
    }

    /** Devuelve la fecha y hora informada. */
    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    /** Establece la fecha y hora informada. */
    public void setFechaHora(OffsetDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    /** Devuelve el id del despacho informado. */
    public UUID getDespachoId() {
        return despachoId;
    }

    /** Establece el id del despacho informado. */
    public void setDespachoId(UUID despachoId) {
        this.despachoId = despachoId;
    }
}
