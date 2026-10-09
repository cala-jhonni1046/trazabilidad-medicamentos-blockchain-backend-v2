package com.medichain.modules.telemetriagps;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de entrada TelemetriaGpsRequestDTO en MediChain.
 * Transporta la lectura que reporta un sensor GPS durante un despacho.
 */
public class TelemetriaGpsRequestDTO {

    @NotBlank(message = "El campo sensorId es obligatorio")
    @Size(max = 100, message = "El campo sensorId no puede superar 100 caracteres")
    private String sensorId;

    @NotNull(message = "El campo latitud es obligatorio")
    @DecimalMin(value = "-90.0", message = "La latitud debe ser mayor o igual a -90")
    @DecimalMax(value = "90.0", message = "La latitud debe ser menor o igual a 90")
    private Double latitud;

    @NotNull(message = "El campo longitud es obligatorio")
    @DecimalMin(value = "-180.0", message = "La longitud debe ser mayor o igual a -180")
    @DecimalMax(value = "180.0", message = "La longitud debe ser menor o igual a 180")
    private Double longitud;

    @Size(max = 255, message = "El campo lugar no puede superar 255 caracteres")
    private String lugar;

    @Schema(description = "Momento de la lectura. Con offset obligatorio (ISO-8601, ej. -03:00); sin offset → 400", example = "2026-12-01T18:00:00-03:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo fechaHora es obligatorio")
    private OffsetDateTime fechaHora;

    // Campo 'despachoId': UUID del despacho al que pertenece la lectura (obligatorio).
    @NotNull(message = "El campo despachoId es obligatorio")
    private UUID despachoId;

    /** Constructor vacío exigido por Jackson. */
    public TelemetriaGpsRequestDTO() {
    }

    /** Constructor con todos los campos obligatorios de alta. */
    public TelemetriaGpsRequestDTO(String sensorId, Double latitud, Double longitud,
                                    OffsetDateTime fechaHora, UUID despachoId) {
        this.sensorId = sensorId;
        this.latitud = latitud;
        this.longitud = longitud;
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

    /** Devuelve la latitud informada. */
    public Double getLatitud() {
        return latitud;
    }

    /** Establece la latitud informada. */
    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    /** Devuelve la longitud informada. */
    public Double getLongitud() {
        return longitud;
    }

    /** Establece la longitud informada. */
    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    /** Devuelve el lugar informado. */
    public String getLugar() {
        return lugar;
    }

    /** Establece el lugar informado. */
    public void setLugar(String lugar) {
        this.lugar = lugar;
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
