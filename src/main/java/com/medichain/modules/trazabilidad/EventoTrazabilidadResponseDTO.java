package com.medichain.modules.trazabilidad;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida EventoTrazabilidadResponseDTO en MediChain.
 * Devuelve al cliente el asiento completo de la cadena de eventos,
 * incluidos id, auditoría, version, el hash propio y el del evento
 * anterior, y los ids del actor (usuario y empresa).
 */
public class EventoTrazabilidadResponseDTO {

    private UUID id;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long version;
    private Long numero;
    private TipoEvento tipo;
    private Instant fechaHora;
    private String entidadTipo;
    private UUID entidadId;
    private String datosJson;
    private String hashAnterior;
    private String hash;
    private UUID actorUsuarioId;
    private UUID actorEmpresaId;

    /** Constructor vacío exigido por Jackson. */
    public EventoTrazabilidadResponseDTO() {
    }

    /** Devuelve el id del evento. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del evento. */
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

    /** Devuelve el número de secuencia del evento. */
    public Long getNumero() {
        return numero;
    }

    /** Establece el número de secuencia del evento. */
    public void setNumero(Long numero) {
        this.numero = numero;
    }

    /** Devuelve el tipo de evento. */
    public TipoEvento getTipo() {
        return tipo;
    }

    /** Establece el tipo de evento. */
    public void setTipo(TipoEvento tipo) {
        this.tipo = tipo;
    }

    /** Devuelve la fecha y hora del evento. */
    public Instant getFechaHora() {
        return fechaHora;
    }

    /** Establece la fecha y hora del evento. */
    public void setFechaHora(Instant fechaHora) {
        this.fechaHora = fechaHora;
    }

    /** Devuelve el tipo de entidad de negocio afectada. */
    public String getEntidadTipo() {
        return entidadTipo;
    }

    /** Establece el tipo de entidad de negocio afectada. */
    public void setEntidadTipo(String entidadTipo) {
        this.entidadTipo = entidadTipo;
    }

    /** Devuelve el id de la entidad de negocio afectada. */
    public UUID getEntidadId() {
        return entidadId;
    }

    /** Establece el id de la entidad de negocio afectada. */
    public void setEntidadId(UUID entidadId) {
        this.entidadId = entidadId;
    }

    /** Devuelve el payload adicional del evento. */
    public String getDatosJson() {
        return datosJson;
    }

    /** Establece el payload adicional del evento. */
    public void setDatosJson(String datosJson) {
        this.datosJson = datosJson;
    }

    /** Devuelve el hash del evento anterior. */
    public String getHashAnterior() {
        return hashAnterior;
    }

    /** Establece el hash del evento anterior. */
    public void setHashAnterior(String hashAnterior) {
        this.hashAnterior = hashAnterior;
    }

    /** Devuelve el hash propio del evento. */
    public String getHash() {
        return hash;
    }

    /** Establece el hash propio del evento. */
    public void setHash(String hash) {
        this.hash = hash;
    }

    /** Devuelve el id del usuario actor, o null (sistema o paciente). */
    public UUID getActorUsuarioId() {
        return actorUsuarioId;
    }

    /** Establece el id del usuario actor. */
    public void setActorUsuarioId(UUID actorUsuarioId) {
        this.actorUsuarioId = actorUsuarioId;
    }

    /** Devuelve el id de la empresa del actor, o null. */
    public UUID getActorEmpresaId() {
        return actorEmpresaId;
    }

    /** Establece el id de la empresa del actor. */
    public void setActorEmpresaId(UUID actorEmpresaId) {
        this.actorEmpresaId = actorEmpresaId;
    }
}
