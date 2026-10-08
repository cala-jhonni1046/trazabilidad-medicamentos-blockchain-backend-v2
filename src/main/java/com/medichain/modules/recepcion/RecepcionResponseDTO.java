package com.medichain.modules.recepcion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida RecepcionResponseDTO en MediChain.
 * Devuelve al cliente los datos del acta de recepción, incluidos id,
 * auditoría, version y los ids del bulto, el despacho, la empresa
 * receptora y el usuario que la registró.
 */
public class RecepcionResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private LocalDateTime fechaHora;
    private BigDecimal temperatura;
    private Boolean precintoIntacto;
    private Integer cantidadVerificada;
    private Boolean conforme;
    private String motivoRechazo;
    private String observacion;
    private String codigoBulto;
    private UUID bultoId;
    private UUID despachoId;
    private UUID receptoraId;
    private UUID registradaPorId;

    /** Constructor vacío exigido por Jackson. */
    public RecepcionResponseDTO() {
    }

    /** Devuelve el id de la recepción. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la recepción. */
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

    /** Devuelve la fecha y hora de la recepción. */
    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /** Establece la fecha y hora de la recepción. */
    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    /** Devuelve la temperatura registrada. */
    public BigDecimal getTemperatura() {
        return temperatura;
    }

    /** Establece la temperatura registrada. */
    public void setTemperatura(BigDecimal temperatura) {
        this.temperatura = temperatura;
    }

    /** Devuelve si el precinto llegó intacto. */
    public Boolean getPrecintoIntacto() {
        return precintoIntacto;
    }

    /** Establece si el precinto llegó intacto. */
    public void setPrecintoIntacto(Boolean precintoIntacto) {
        this.precintoIntacto = precintoIntacto;
    }

    /** Devuelve la cantidad verificada. */
    public Integer getCantidadVerificada() {
        return cantidadVerificada;
    }

    /** Establece la cantidad verificada. */
    public void setCantidadVerificada(Integer cantidadVerificada) {
        this.cantidadVerificada = cantidadVerificada;
    }

    /** Devuelve si la recepción fue conforme. */
    public Boolean getConforme() {
        return conforme;
    }

    /** Establece si la recepción fue conforme. */
    public void setConforme(Boolean conforme) {
        this.conforme = conforme;
    }

    /** Devuelve el motivo de rechazo, si aplica. */
    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    /** Establece el motivo de rechazo. */
    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    /** Devuelve el id del bulto recibido. */
    public UUID getBultoId() {
        return bultoId;
    }

    /** Establece el id del bulto recibido. */
    public void setBultoId(UUID bultoId) {
        this.bultoId = bultoId;
    }

    /** Devuelve el id del despacho. */
    public UUID getDespachoId() {
        return despachoId;
    }

    /** Establece el id del despacho. */
    public void setDespachoId(UUID despachoId) {
        this.despachoId = despachoId;
    }

    /** Devuelve el id de la empresa receptora. */
    public UUID getReceptoraId() {
        return receptoraId;
    }

    /** Establece el id de la empresa receptora. */
    public void setReceptoraId(UUID receptoraId) {
        this.receptoraId = receptoraId;
    }

    /** Devuelve el id del usuario que registró la recepción. */
    public UUID getRegistradaPorId() {
        return registradaPorId;
    }

    /** Establece el id del usuario que registró la recepción. */
    public void setRegistradaPorId(UUID registradaPorId) {
        this.registradaPorId = registradaPorId;
    }

    /** Devuelve la observación libre. */
    public String getObservacion() {
        return observacion;
    }

    /** Establece la observación libre. */
    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    /** Devuelve el código del bulto. */
    public String getCodigoBulto() {
        return codigoBulto;
    }

    /** Establece el código del bulto. */
    public void setCodigoBulto(String codigoBulto) {
        this.codigoBulto = codigoBulto;
    }
}
