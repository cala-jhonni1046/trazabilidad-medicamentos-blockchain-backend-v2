package com.medichain.modules.telemetriagps;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida TelemetriaGpsResponseDTO en MediChain.
 * Devuelve al cliente los datos de la lectura GPS, incluidos id,
 * auditoría, version y el id del despacho.
 */
public class TelemetriaGpsResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String sensorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Double latitud;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Double longitud;
    @Schema(nullable = true)
    private String lugar;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaHora;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID despachoId;

    /** Constructor vacío exigido por Jackson. */
    public TelemetriaGpsResponseDTO() {
    }

    /** Devuelve el id de la lectura. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la lectura. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve la fecha de creación del registro. */
    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha de creación del registro. */
    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha de última actualización del registro. */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha de última actualización del registro. */
    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    /** Devuelve la versión usada para el locking optimista. */
    public Long getVersion() {
        return version;
    }

    /** Establece la versión usada para el locking optimista. */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** Devuelve el id del sensor. */
    public String getSensorId() {
        return sensorId;
    }

    /** Establece el id del sensor. */
    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    /** Devuelve la latitud registrada. */
    public Double getLatitud() {
        return latitud;
    }

    /** Establece la latitud registrada. */
    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    /** Devuelve la longitud registrada. */
    public Double getLongitud() {
        return longitud;
    }

    /** Establece la longitud registrada. */
    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    /** Devuelve el lugar de la lectura. */
    public String getLugar() {
        return lugar;
    }

    /** Establece el lugar de la lectura. */
    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    /** Devuelve la fecha y hora de la lectura. */
    public Instant getFechaHora() {
        return fechaHora;
    }

    /** Establece la fecha y hora de la lectura. */
    public void setFechaHora(Instant fechaHora) {
        this.fechaHora = fechaHora;
    }

    /** Devuelve el id del despacho de la lectura. */
    public UUID getDespachoId() {
        return despachoId;
    }

    /** Establece el id del despacho de la lectura. */
    public void setDespachoId(UUID despachoId) {
        this.despachoId = despachoId;
    }
}
