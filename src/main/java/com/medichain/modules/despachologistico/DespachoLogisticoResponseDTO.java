package com.medichain.modules.despachologistico;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de salida DespachoLogisticoResponseDTO en MediChain.
 * Devuelve al cliente los datos del despacho, incluidos id, auditoría,
 * version, los ids de origen/creadoPor y los ids de los bultos que lleva.
 */
public class DespachoLogisticoResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private String codigo;
    private TramoDespacho tramo;
    private String patente;
    private String chofer;
    private LocalDateTime fechaSalida;
    private LocalDateTime fechaEstimadaEntrega;
    private EstadoDespacho estado;
    private UUID origenId;
    private UUID creadoPorId;
    private Set<UUID> bultoIds = new LinkedHashSet<>();
    private List<String> bultoCodigos;
    private List<UUID> paradaIds;

    /** Constructor vacío exigido por Jackson. */
    public DespachoLogisticoResponseDTO() {
    }

    /** Devuelve el id del despacho. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del despacho. */
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

    /** Devuelve el código del despacho. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece el código del despacho. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve el tramo del despacho. */
    public TramoDespacho getTramo() {
        return tramo;
    }

    /** Establece el tramo del despacho. */
    public void setTramo(TramoDespacho tramo) {
        this.tramo = tramo;
    }

    /** Devuelve la patente del vehículo. */
    public String getPatente() {
        return patente;
    }

    /** Establece la patente del vehículo. */
    public void setPatente(String patente) {
        this.patente = patente;
    }

    /** Devuelve el chofer del despacho. */
    public String getChofer() {
        return chofer;
    }

    /** Establece el chofer del despacho. */
    public void setChofer(String chofer) {
        this.chofer = chofer;
    }

    /** Devuelve la fecha de salida del despacho. */
    public LocalDateTime getFechaSalida() {
        return fechaSalida;
    }

    /** Establece la fecha de salida del despacho. */
    public void setFechaSalida(LocalDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    /** Devuelve la fecha estimada de entrega. */
    public LocalDateTime getFechaEstimadaEntrega() {
        return fechaEstimadaEntrega;
    }

    /** Establece la fecha estimada de entrega. */
    public void setFechaEstimadaEntrega(LocalDateTime fechaEstimadaEntrega) {
        this.fechaEstimadaEntrega = fechaEstimadaEntrega;
    }

    /** Devuelve el estado del despacho. */
    public EstadoDespacho getEstado() {
        return estado;
    }

    /** Establece el estado del despacho. */
    public void setEstado(EstadoDespacho estado) {
        this.estado = estado;
    }

    /** Devuelve el id de la empresa de origen. */
    public UUID getOrigenId() {
        return origenId;
    }

    /** Establece el id de la empresa de origen. */
    public void setOrigenId(UUID origenId) {
        this.origenId = origenId;
    }

    /** Devuelve el id del usuario que creó el despacho. */
    public UUID getCreadoPorId() {
        return creadoPorId;
    }

    /** Establece el id del usuario que creó el despacho. */
    public void setCreadoPorId(UUID creadoPorId) {
        this.creadoPorId = creadoPorId;
    }

    /** Devuelve los ids de los bultos que lleva el despacho. */
    public Set<UUID> getBultoIds() {
        return bultoIds;
    }

    /** Establece los ids de los bultos que lleva el despacho. */
    public void setBultoIds(Set<UUID> bultoIds) {
        this.bultoIds = bultoIds;
    }

    /** Devuelve los códigos de los bultos del viaje. */
    public List<String> getBultoCodigos() {
        return bultoCodigos;
    }

    /** Establece los códigos de los bultos del viaje. */
    public void setBultoCodigos(List<String> bultoCodigos) {
        this.bultoCodigos = bultoCodigos;
    }

    /** Devuelve los ids de las paradas (distribuidora o farmacias). */
    public List<UUID> getParadaIds() {
        return paradaIds;
    }

    /** Establece los ids de las paradas. */
    public void setParadaIds(List<UUID> paradaIds) {
        this.paradaIds = paradaIds;
    }
}
