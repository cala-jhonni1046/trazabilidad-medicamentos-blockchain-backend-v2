package com.medichain.modules.telemetriatemperatura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida TelemetriaTemperaturaResponseDTO en MediChain.
 * Devuelve al cliente los datos de la lectura, incluidos id, auditoría,
 * version y el id del despacho.
 */
public class TelemetriaTemperaturaResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private String sensorId;
    private BigDecimal temperatura;
    private Boolean fueraDeRango;
    private LocalDateTime fechaHora;
    private UUID despachoId;

    /** Constructor vacío exigido por Jackson. */
    public TelemetriaTemperaturaResponseDTO() {
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

    /** Devuelve la temperatura registrada. */
    public BigDecimal getTemperatura() {
        return temperatura;
    }

    /** Establece la temperatura registrada. */
    public void setTemperatura(BigDecimal temperatura) {
        this.temperatura = temperatura;
    }

    /** Devuelve si la lectura está fuera de rango. */
    public Boolean getFueraDeRango() {
        return fueraDeRango;
    }

    /** Establece si la lectura está fuera de rango. */
    public void setFueraDeRango(Boolean fueraDeRango) {
        this.fueraDeRango = fueraDeRango;
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
