package com.medichain.modules.telemetriatemperatura;

import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad TelemetriaTemperatura en MediChain.
 * Representa una lectura puntual de temperatura reportada por un sensor
 * durante un DespachoLogistico. Cada lectura pertenece a exactamente un
 * despacho.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "telemetria_temperatura")
public class TelemetriaTemperatura extends BaseEntity {

    @Column(name = "sensor_id", nullable = false, length = 100, unique = false)
    private String sensorId;

    @Column(name = "temperatura", nullable = false, precision = 5, scale = 2, unique = false)
    private BigDecimal temperatura;

    @Column(name = "fuera_de_rango", nullable = false, unique = false)
    private Boolean fueraDeRango;

    @Column(name = "fecha_hora", nullable = false, unique = false)
    private Instant fechaHora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private DespachoLogistico despacho;

    /** Constructor vacío exigido por JPA. */
    protected TelemetriaTemperatura() {
    }

    /**
     * Crea la lectura completa (no tiene setters). El Service resuelve el
     * viaje contra la base y calcula fueraDeRango (no lo decide el cliente).
     */
    public TelemetriaTemperatura(String sensorId, BigDecimal temperatura, Boolean fueraDeRango,
                                 Instant fechaHora, DespachoLogistico despacho) {
        this.despacho = despacho;
        this.sensorId = sensorId;
        this.temperatura = temperatura;
        this.fueraDeRango = fueraDeRango;
        this.fechaHora = fechaHora;
    }

    /** Devuelve el id del sensor que reportó la lectura. */
    public String getSensorId() {
        return sensorId;
    }

    /** Devuelve la temperatura registrada. */
    public BigDecimal getTemperatura() {
        return temperatura;
    }

    /** Indica si la lectura está fuera del rango permitido. */
    public Boolean getFueraDeRango() {
        return fueraDeRango;
    }

    /** Devuelve la fecha y hora de la lectura. */
    public Instant getFechaHora() {
        return fechaHora;
    }

    /** Devuelve el despacho al que pertenece la lectura. */
    public DespachoLogistico getDespacho() {
        return despacho;
    }

}
