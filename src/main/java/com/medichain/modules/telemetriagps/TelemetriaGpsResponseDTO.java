package com.medichain.modules.telemetriagps;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida TelemetriaGpsResponseDTO en MediChain.
 * Devuelve al cliente los datos de la lectura GPS, incluidos id,
 * auditoría, version y el id del despacho.
 */
public class TelemetriaGpsResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private String sensorId;
    private Double latitud;
    private Double longitud;
    private String lugar;
    private LocalDateTime fechaHora;
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
    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /** Establece la fecha y hora de la lectura. */
    public void setFechaHora(LocalDateTime fechaHora) {
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
