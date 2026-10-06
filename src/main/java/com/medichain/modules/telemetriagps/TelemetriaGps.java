package com.medichain.modules.telemetriagps;

import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad TelemetriaGps en MediChain.
 * Representa una lectura puntual de posición GPS reportada por un sensor
 * durante un DespachoLogistico. Cada lectura pertenece a exactamente un
 * despacho.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "telemetria_gps")
public class TelemetriaGps extends BaseEntity {

    @Column(name = "sensor_id", nullable = false, length = 100, unique = false)
    private String sensorId;

    @Column(name = "latitud", nullable = false, unique = false)
    private Double latitud;

    @Column(name = "longitud", nullable = false, unique = false)
    private Double longitud;

    @Column(name = "lugar", nullable = true, length = 255, unique = false)
    private String lugar;

    @Column(name = "fecha_hora", nullable = false, unique = false)
    private LocalDateTime fechaHora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private DespachoLogistico despacho;

    /** Constructor vacío exigido por JPA. */
    protected TelemetriaGps() {
    }

    /** Crea la posición GPS completa (no tiene setters); el Service resuelve el viaje contra la base. */
    public TelemetriaGps(String sensorId, Double latitud, Double longitud, String lugar, LocalDateTime fechaHora,
                         DespachoLogistico despacho) {
        this.lugar = lugar;
        this.despacho = despacho;
        this.sensorId = sensorId;
        this.latitud = latitud;
        this.longitud = longitud;
        this.fechaHora = fechaHora;
    }

    /** Devuelve el id del sensor que reportó la lectura. */
    public String getSensorId() {
        return sensorId;
    }

    /** Devuelve la latitud registrada. */
    public Double getLatitud() {
        return latitud;
    }

    /** Devuelve la longitud registrada. */
    public Double getLongitud() {
        return longitud;
    }

    /** Devuelve el nombre del lugar de la lectura, si se informó. */
    public String getLugar() {
        return lugar;
    }

    /** Devuelve la fecha y hora de la lectura. */
    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /** Devuelve el despacho al que pertenece la lectura. */
    public DespachoLogistico getDespacho() {
        return despacho;
    }

}
