package com.medichain.modules.unidadtrazable;

import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * Entidad IntentoVerificacion en MediChain.
 * Control anti-spam de la verificación pública: como mucho UN evento
 * SERIE_INEXISTENTE o SERIE_ROBADA por (tipo, GTIN, serie) por día UTC
 * (restricción única), así nadie puede llenar la cadena repitiendo una
 * consulta. No guarda nada de quien consulta (ni IP: es un dato personal).
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "intento_verificacion", uniqueConstraints = @UniqueConstraint(name = "ux_intento_verificacion_dia",
        columnNames = {"tipo", "gtin", "serie", "fecha"}))
public class IntentoVerificacion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, unique = false)
    private TipoEvento tipo;

    @Column(name = "gtin", nullable = false, length = 14, unique = false)
    private String gtin;

    @Column(name = "serie", nullable = false, length = 20, unique = false)
    private String serie;

    // Día UTC del intento.
    @Column(name = "fecha", nullable = false, unique = false)
    private LocalDate fecha;

    /** Constructor vacío exigido por JPA. */
    protected IntentoVerificacion() {
    }

    /** Registra el intento del día. */
    public IntentoVerificacion(TipoEvento tipo, String gtin, String serie, LocalDate fecha) {
        this.tipo = tipo;
        this.gtin = gtin;
        this.serie = serie;
        this.fecha = fecha;
    }

    /** Devuelve el tipo de evento (SERIE_INEXISTENTE o SERIE_ROBADA). */
    public TipoEvento getTipo() {
        return tipo;
    }

    /** Devuelve el GTIN consultado. */
    public String getGtin() {
        return gtin;
    }

    /** Devuelve la serie consultada. */
    public String getSerie() {
        return serie;
    }

    /** Devuelve el día UTC. */
    public LocalDate getFecha() {
        return fecha;
    }
}
