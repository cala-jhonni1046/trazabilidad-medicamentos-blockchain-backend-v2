package com.medichain.modules.lote;

import com.medichain.modules.cuarentena.CausaBloqueo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de salida LoteResponseDTO en MediChain.
 * Devuelve al cliente los datos del lote, incluidos id, auditoría,
 * version y los ids del medicamento y del usuario que lo liberó.
 */
public class LoteResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(example = "L2026-0415", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigo;
    @Schema(example = "2026-09-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate fechaFabricacion;
    @Schema(example = "2028-09-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate fechaVencimiento;
    @Schema(example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer cantidad;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoLote estado;
    @Schema(nullable = true)
    private EstadoLote estadoPrevio;
    @Schema(nullable = true)
    private Instant fechaLiberacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID medicamentoId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID laboratorioId;
    @Schema(nullable = true)
    private UUID liberadoPorId;

    @Schema(description = "R10: bloqueado no viaja, no se recibe ni se dispensa. Se calcula en cada consulta (no se guarda).",
            example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean bloqueado;
    @Schema(description = "Causa del bloqueo (código); null si no está bloqueado", nullable = true)
    private CausaBloqueo motivoBloqueo;
    @Schema(description = "Explicación del bloqueo para mostrar; null si no está bloqueado",
            example = "el lote L2026-0415 está vencido", nullable = true)
    private String mensajeBloqueo;
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
    public Instant getFechaLiberacion() {
        return fechaLiberacion;
    }

    /** Establece la fecha de liberación del lote. */
    public void setFechaLiberacion(Instant fechaLiberacion) {
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

    /** Indica si está bloqueado (R10, calculado). */
    public boolean isBloqueado() {
        return bloqueado;
    }

    /** Establece si está bloqueado. */
    public void setBloqueado(boolean bloqueado) {
        this.bloqueado = bloqueado;
    }

    /** Devuelve la causa del bloqueo (null si no lo está). */
    public CausaBloqueo getMotivoBloqueo() {
        return motivoBloqueo;
    }

    /** Establece la causa del bloqueo. */
    public void setMotivoBloqueo(CausaBloqueo motivoBloqueo) {
        this.motivoBloqueo = motivoBloqueo;
    }

    /** Devuelve la explicación del bloqueo (null si no lo está). */
    public String getMensajeBloqueo() {
        return mensajeBloqueo;
    }

    /** Establece la explicación del bloqueo. */
    public void setMensajeBloqueo(String mensajeBloqueo) {
        this.mensajeBloqueo = mensajeBloqueo;
    }
}
