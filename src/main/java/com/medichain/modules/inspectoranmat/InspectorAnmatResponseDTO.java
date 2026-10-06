package com.medichain.modules.inspectoranmat;

import com.medichain.utils.enums.Provincia;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida InspectorAnmatResponseDTO en MediChain.
 * Devuelve al cliente los datos del inspector, incluidos id, auditoría,
 * version y los ids de sus relaciones con Usuario.
 */
public class InspectorAnmatResponseDTO {

    private UUID id;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long version;
    private String legajo;
    private String dni;
    private Provincia provincia;
    private EstadoInspector estado;
    private LocalDateTime fechaAlta;
    private LocalDateTime fechaBaja;
    private UUID usuarioId;
    private UUID usuarioAltaId;

    /** Constructor vacío exigido por Jackson. */
    public InspectorAnmatResponseDTO() {
    }

    /** Devuelve el id del inspector. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del inspector. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve la fecha de creación del registro. */
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha de creación del registro. */
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha de última actualización del registro. */
    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha de última actualización del registro. */
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
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

    /** Devuelve el legajo del inspector. */
    public String getLegajo() {
        return legajo;
    }

    /** Establece el legajo del inspector. */
    public void setLegajo(String legajo) {
        this.legajo = legajo;
    }

    /** Devuelve el DNI del inspector. */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI del inspector. */
    public void setDni(String dni) {
        this.dni = dni;
    }

    /** Devuelve la provincia donde actúa el inspector. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia donde actúa el inspector. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve el estado administrativo del inspector. */
    public EstadoInspector getEstado() {
        return estado;
    }

    /** Establece el estado administrativo del inspector. */
    public void setEstado(EstadoInspector estado) {
        this.estado = estado;
    }

    /** Devuelve la fecha de alta del inspector. */
    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    /** Establece la fecha de alta del inspector. */
    public void setFechaAlta(LocalDateTime fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    /** Devuelve la fecha de baja del inspector, si aplica. */
    public LocalDateTime getFechaBaja() {
        return fechaBaja;
    }

    /** Establece la fecha de baja del inspector. */
    public void setFechaBaja(LocalDateTime fechaBaja) {
        this.fechaBaja = fechaBaja;
    }

    /** Devuelve el id de la cuenta de usuario del inspector. */
    public UUID getUsuarioId() {
        return usuarioId;
    }

    /** Establece el id de la cuenta de usuario del inspector. */
    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** Devuelve el id del usuario que dio de alta al inspector. */
    public UUID getUsuarioAltaId() {
        return usuarioAltaId;
    }

    /** Establece el id del usuario que dio de alta al inspector. */
    public void setUsuarioAltaId(UUID usuarioAltaId) {
        this.usuarioAltaId = usuarioAltaId;
    }
}
