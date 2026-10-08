package com.medichain.modules.enlacecuit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida EnlaceCuitResponseDTO en MediChain.
 * Devuelve al cliente el estado completo del circuito, incluidos id,
 * auditoría, version y los ids de las empresas, el usuario proponente y
 * el inspector aprobador.
 */
public class EnlaceCuitResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private String codigo;
    private EstadoEnlaceCuit estado;
    private LocalDateTime fechaPropuesta;
    private LocalDateTime fechaAceptacionDistribuidor;
    private LocalDateTime fechaAceptacionFarmacia;
    private LocalDateTime fechaAprobacion;
    private String motivoRechazo;
    private UUID laboratorioId;
    private UUID distribuidorId;
    private UUID farmaciaId;
    private UUID propuestoPorId;
    private UUID inspectorAprobadorId;
    private UUID inspectorRevisorId;
    private OrigenRechazo rechazadoPor;
    private String motivoSuspension;
    private Boolean suspendidoPorEmpresa;

    /** Constructor vacío exigido por Jackson. */
    public EnlaceCuitResponseDTO() {
    }

    /** Devuelve el id del circuito. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del circuito. */
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

    /** Devuelve el código del circuito. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece el código del circuito. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve el estado del circuito. */
    public EstadoEnlaceCuit getEstado() {
        return estado;
    }

    /** Establece el estado del circuito. */
    public void setEstado(EstadoEnlaceCuit estado) {
        this.estado = estado;
    }

    /** Devuelve la fecha de propuesta del circuito. */
    public LocalDateTime getFechaPropuesta() {
        return fechaPropuesta;
    }

    /** Establece la fecha de propuesta del circuito. */
    public void setFechaPropuesta(LocalDateTime fechaPropuesta) {
        this.fechaPropuesta = fechaPropuesta;
    }

    /** Devuelve la fecha de aceptación del distribuidor. */
    public LocalDateTime getFechaAceptacionDistribuidor() {
        return fechaAceptacionDistribuidor;
    }

    /** Establece la fecha de aceptación del distribuidor. */
    public void setFechaAceptacionDistribuidor(LocalDateTime fechaAceptacionDistribuidor) {
        this.fechaAceptacionDistribuidor = fechaAceptacionDistribuidor;
    }

    /** Devuelve la fecha de aceptación de la farmacia. */
    public LocalDateTime getFechaAceptacionFarmacia() {
        return fechaAceptacionFarmacia;
    }

    /** Establece la fecha de aceptación de la farmacia. */
    public void setFechaAceptacionFarmacia(LocalDateTime fechaAceptacionFarmacia) {
        this.fechaAceptacionFarmacia = fechaAceptacionFarmacia;
    }

    /** Devuelve la fecha de aprobación del circuito. */
    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    /** Establece la fecha de aprobación del circuito. */
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) {
        this.fechaAprobacion = fechaAprobacion;
    }

    /** Devuelve el motivo de rechazo, si aplica. */
    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    /** Establece el motivo de rechazo. */
    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    /** Devuelve el id del laboratorio. */
    public UUID getLaboratorioId() {
        return laboratorioId;
    }

    /** Establece el id del laboratorio. */
    public void setLaboratorioId(UUID laboratorioId) {
        this.laboratorioId = laboratorioId;
    }

    /** Devuelve el id del distribuidor. */
    public UUID getDistribuidorId() {
        return distribuidorId;
    }

    /** Establece el id del distribuidor. */
    public void setDistribuidorId(UUID distribuidorId) {
        this.distribuidorId = distribuidorId;
    }

    /** Devuelve el id de la farmacia. */
    public UUID getFarmaciaId() {
        return farmaciaId;
    }

    /** Establece el id de la farmacia. */
    public void setFarmaciaId(UUID farmaciaId) {
        this.farmaciaId = farmaciaId;
    }

    /** Devuelve el id del usuario que propuso el circuito. */
    public UUID getPropuestoPorId() {
        return propuestoPorId;
    }

    /** Establece el id del usuario que propuso el circuito. */
    public void setPropuestoPorId(UUID propuestoPorId) {
        this.propuestoPorId = propuestoPorId;
    }

    /** Devuelve el id del inspector aprobador. */
    public UUID getInspectorAprobadorId() {
        return inspectorAprobadorId;
    }

    /** Establece el id del inspector aprobador. */
    public void setInspectorAprobadorId(UUID inspectorAprobadorId) {
        this.inspectorAprobadorId = inspectorAprobadorId;
    }

    /** Devuelve el id del inspector revisor. */
    public UUID getInspectorRevisorId() {
        return inspectorRevisorId;
    }

    /** Establece el id del inspector revisor. */
    public void setInspectorRevisorId(UUID inspectorRevisorId) {
        this.inspectorRevisorId = inspectorRevisorId;
    }

    /** Devuelve quién rechazó el circuito. */
    public OrigenRechazo getRechazadoPor() {
        return rechazadoPor;
    }

    /** Establece quién rechazó el circuito. */
    public void setRechazadoPor(OrigenRechazo rechazadoPor) {
        this.rechazadoPor = rechazadoPor;
    }

    /** Devuelve el motivo de la última suspensión manual. */
    public String getMotivoSuspension() {
        return motivoSuspension;
    }

    /** Establece el motivo de la última suspensión manual. */
    public void setMotivoSuspension(String motivoSuspension) {
        this.motivoSuspension = motivoSuspension;
    }

    /** Devuelve si está suspendido por la suspensión de una empresa. */
    public Boolean getSuspendidoPorEmpresa() {
        return suspendidoPorEmpresa;
    }

    /** Establece si está suspendido por la suspensión de una empresa. */
    public void setSuspendidoPorEmpresa(Boolean suspendidoPorEmpresa) {
        this.suspendidoPorEmpresa = suspendidoPorEmpresa;
    }
}
