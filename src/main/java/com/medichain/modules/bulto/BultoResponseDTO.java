package com.medichain.modules.bulto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida BultoResponseDTO en MediChain.
 * Devuelve al cliente los datos del bulto, incluidos id, auditoría,
 * version y los ids del lote, el destino y la ubicación actual.
 */
public class BultoResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private String codigo;
    private Integer cantidad;
    private String precinto;
    private EstadoBulto estado;
    private LocalDateTime fechaArmado;
    private UUID loteId;
    private UUID destinoId;
    private UUID ubicacionId;
    private UUID viajeActualId;

    /** Constructor vacío exigido por Jackson. */
    public BultoResponseDTO() {
    }

    /** Devuelve el id del bulto. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del bulto. */
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

    /** Devuelve el código del bulto. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece el código del bulto. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve la cantidad de unidades del bulto. */
    public Integer getCantidad() {
        return cantidad;
    }

    /** Establece la cantidad de unidades del bulto. */
    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    /** Devuelve el precinto del bulto. */
    public String getPrecinto() {
        return precinto;
    }

    /** Establece el precinto del bulto. */
    public void setPrecinto(String precinto) {
        this.precinto = precinto;
    }

    /** Devuelve el estado del bulto. */
    public EstadoBulto getEstado() {
        return estado;
    }

    /** Establece el estado del bulto. */
    public void setEstado(EstadoBulto estado) {
        this.estado = estado;
    }

    /** Devuelve la fecha de armado del bulto. */
    public LocalDateTime getFechaArmado() {
        return fechaArmado;
    }

    /** Establece la fecha de armado del bulto. */
    public void setFechaArmado(LocalDateTime fechaArmado) {
        this.fechaArmado = fechaArmado;
    }

    /** Devuelve el id del lote del bulto. */
    public UUID getLoteId() {
        return loteId;
    }

    /** Establece el id del lote del bulto. */
    public void setLoteId(UUID loteId) {
        this.loteId = loteId;
    }

    /** Devuelve el id del destino del bulto. */
    public UUID getDestinoId() {
        return destinoId;
    }

    /** Establece el id del destino del bulto. */
    public void setDestinoId(UUID destinoId) {
        this.destinoId = destinoId;
    }

    /** Devuelve el id de la empresa donde está ubicado el bulto. */
    public UUID getUbicacionId() {
        return ubicacionId;
    }

    /** Establece el id de la empresa donde está ubicado el bulto. */
    public void setUbicacionId(UUID ubicacionId) {
        this.ubicacionId = ubicacionId;
    }

    /** Devuelve el id del viaje programado o en curso. */
    public UUID getViajeActualId() {
        return viajeActualId;
    }

    /** Establece el id del viaje programado o en curso. */
    public void setViajeActualId(UUID viajeActualId) {
        this.viajeActualId = viajeActualId;
    }
}
