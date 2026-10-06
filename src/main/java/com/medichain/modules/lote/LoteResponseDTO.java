package com.medichain.modules.lote;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida LoteResponseDTO en MediChain.
 * Devuelve al cliente los datos del lote, incluidos id, auditoría,
 * version y los ids del medicamento y del usuario que lo liberó.
 */
public class LoteResponseDTO {

    private UUID id;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long version;
    private String codigo;
    private LocalDate fechaFabricacion;
    private LocalDate fechaVencimiento;
    private Integer cantidad;
    private EstadoLote estado;
    private EstadoLote estadoPrevio;
    private LocalDateTime fechaLiberacion;
    private UUID medicamentoId;
    private UUID laboratorioId;
    private UUID liberadoPorId;

    /** Constructor vacío exigido por Jackson. */
    public LoteResponseDTO() {
    }

    /** Devuelve el id del lote. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del lote. */
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

    /** Devuelve el código del lote. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece el código del lote. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve la fecha de fabricación del lote. */
    public LocalDate getFechaFabricacion() {
        return fechaFabricacion;
    }

    /** Establece la fecha de fabricación del lote. */
    public void setFechaFabricacion(LocalDate fechaFabricacion) {
        this.fechaFabricacion = fechaFabricacion;
    }

    /** Devuelve la fecha de vencimiento del lote. */
    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    /** Establece la fecha de vencimiento del lote. */
    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    /** Devuelve la cantidad de unidades del lote. */
    public Integer getCantidad() {
        return cantidad;
    }

    /** Establece la cantidad de unidades del lote. */
    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    /** Devuelve el estado del lote. */
    public EstadoLote getEstado() {
        return estado;
    }

    /** Establece el estado del lote. */
    public void setEstado(EstadoLote estado) {
        this.estado = estado;
    }

    /** Devuelve el estado previo del lote. */
    public EstadoLote getEstadoPrevio() {
        return estadoPrevio;
    }

    /** Establece el estado previo del lote. */
    public void setEstadoPrevio(EstadoLote estadoPrevio) {
        this.estadoPrevio = estadoPrevio;
    }

    /** Devuelve la fecha de liberación del lote. */
    public LocalDateTime getFechaLiberacion() {
        return fechaLiberacion;
    }

    /** Establece la fecha de liberación del lote. */
    public void setFechaLiberacion(LocalDateTime fechaLiberacion) {
        this.fechaLiberacion = fechaLiberacion;
    }

    /** Devuelve el id del medicamento del lote. */
    public UUID getMedicamentoId() {
        return medicamentoId;
    }

    /** Establece el id del medicamento del lote. */
    public void setMedicamentoId(UUID medicamentoId) {
        this.medicamentoId = medicamentoId;
    }

    /** Devuelve el id del usuario que liberó el lote. */
    public UUID getLiberadoPorId() {
        return liberadoPorId;
    }

    /** Establece el id del usuario que liberó el lote. */
    public void setLiberadoPorId(UUID liberadoPorId) {
        this.liberadoPorId = liberadoPorId;
    }

    /** Devuelve el id del laboratorio dueño del lote. */
    public UUID getLaboratorioId() {
        return laboratorioId;
    }

    /** Establece el id del laboratorio dueño del lote. */
    public void setLaboratorioId(UUID laboratorioId) {
        this.laboratorioId = laboratorioId;
    }
}
