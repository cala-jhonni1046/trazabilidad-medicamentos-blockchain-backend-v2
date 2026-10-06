package com.medichain.utils;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
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

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = true)
    private LocalDateTime fechaActualizacion;

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

    /** Devuelve la fecha y hora en que se creó el registro. */
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha y hora de creación del registro. */
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha y hora de la última actualización del registro. */
    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha y hora de la última actualización del registro. */
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
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
        LocalDateTime now = LocalDateTime.now();
        if (this.fechaCreacion == null) {
            this.fechaCreacion = now;
        }
        this.fechaActualizacion = now;
    }

    /** Actualiza la fecha de auditoría antes de cada UPDATE. */
    @PreUpdate
    protected void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
