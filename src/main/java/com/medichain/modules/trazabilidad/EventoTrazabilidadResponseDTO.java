package com.medichain.modules.trazabilidad;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida EventoTrazabilidadResponseDTO en MediChain.
 * Devuelve al cliente el asiento completo de la cadena de eventos,
 * incluidos id, auditoría, version, el hash propio y el del evento
 * anterior, y los ids del actor (usuario y empresa).
 */
public class EventoTrazabilidadResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long numero;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoEvento tipo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaHora;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String entidadTipo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID entidadId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String datosJson;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String hashAnterior;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String hash;
    @Schema(nullable = true)
    private UUID actorUsuarioId;
    @Schema(nullable = true)
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
