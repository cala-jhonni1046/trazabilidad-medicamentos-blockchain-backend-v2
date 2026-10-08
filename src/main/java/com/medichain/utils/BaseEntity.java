package com.medichain.utils;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Superclase mapeada que aportan todas las entidades del dominio MediChain.
 * Centraliza el identificador, las fechas de auditoría y el control de
 * concurrencia optimista, para que cada entidad concreta solo declare sus
 * propios atributos y relaciones.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Instant en UTC (timestamptz en la base, V2__fechas_auditoria_utc), truncado a microsegundos:
    // la precisión de PostgreSQL, así el valor en memoria es el mismo que se relee de la base.
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = true)
    private Instant fechaActualizacion;

    // @Version: habilita locking optimista; Hibernate rechaza un update/delete
    // si la fila fue modificada por otra transacción desde la última lectura.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Constructor vacío exigido por JPA. */
    public BaseEntity() {
    }

    /** Devuelve el identificador único de la entidad. */
    public UUID getId() {
        return id;
    }

    /** Establece el identificador único de la entidad. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve el instante (UTC) en que se creó el registro. */
    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece el instante (UTC) de creación del registro. */
    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve el instante (UTC) de la última actualización del registro. */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece el instante (UTC) de la última actualización del registro. */
    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    /** Devuelve el número de versión usado para el locking optimista. */
    public Long getVersion() {
        return version;
    }

    /** Establece el número de versión usado para el locking optimista. */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** Completa las fechas de auditoría antes del primer INSERT. */
    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (this.fechaCreacion == null) {
            this.fechaCreacion = now;
        }
        this.fechaActualizacion = now;
    }

    /** Actualiza la fecha de auditoría antes de cada UPDATE. */
    @PreUpdate
    protected void preUpdate() {
        this.fechaActualizacion = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
