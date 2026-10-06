package com.medichain.modules.lote;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad Lote en MediChain.
 * Representa un lote de fabricación de un Medicamento. Es el origen de la
 * trazabilidad: nace PENDIENTE_LIBERACION, un usuario lo libera, y a
 * partir de ahí se empaca en Bultos que contienen sus UnidadTrazable
 * (ambas relaciones se guardan del lado "muchos", sin colección aquí).
 * El código lo escribe el laboratorio y es único POR LABORATORIO
 * (restricción compuesta laboratorio_id + codigo): dos laboratorios pueden
 * usar el mismo código. El laboratorio se guarda desnormalizado (es el
 * del medicamento) porque PostgreSQL no admite una restricción única que
 * atraviese un join. El estado cambia solo por métodos de dominio.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "lotes", uniqueConstraints = @UniqueConstraint(name = "ux_lote_laboratorio_codigo",
        columnNames = {"laboratorio_id", "codigo"}))
public class Lote extends BaseEntity {

    @Column(name = "codigo", nullable = false, length = 12, unique = false)
    private String codigo;

    @Column(name = "fecha_fabricacion", nullable = false, unique = false)
    private LocalDate fechaFabricacion;

    @Column(name = "fecha_vencimiento", nullable = false, unique = false)
    private LocalDate fechaVencimiento;

    @Column(name = "cantidad", nullable = false, unique = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoLote estado;

    // nullable = true: solo tiene valor cuando el lote entra en CUARENTENA o RECALL,
    // para poder restaurar el estado anterior al levantar la medida.
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_previo", nullable = true, length = 30, unique = false)
    private EstadoLote estadoPrevio;

    // nullable = true: solo tiene valor una vez que el lote es liberado.
    @Column(name = "fecha_liberacion", nullable = true, unique = false)
    private LocalDateTime fechaLiberacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    // Desnormalizado: es el laboratorio del medicamento. Permite la restricción única por laboratorio.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false)
    private Empresa laboratorio;

    // nullable = true: se asigna recién cuando el lote es liberado.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "liberado_por_id", nullable = true)
    private Usuario liberadoPor;

    /** Constructor vacío exigido por JPA. */
    protected Lote() {
    }

    /**
     * Registra un lote del medicamento dado: nace PENDIENTE_LIBERACION, con
     * el laboratorio del medicamento y la cantidad de cajas (series) que trae.
     */
    public Lote(String codigo, LocalDate fechaFabricacion, LocalDate fechaVencimiento, Integer cantidad,
                Medicamento medicamento) {
        this.codigo = codigo;
        this.fechaFabricacion = fechaFabricacion;
        this.fechaVencimiento = fechaVencimiento;
        this.cantidad = cantidad;
        this.medicamento = medicamento;
        this.laboratorio = medicamento.getLaboratorio();
        this.estado = EstadoLote.PENDIENTE_LIBERACION;
    }

    /** Devuelve el código del lote. */
    public String getCodigo() {
        return codigo;
    }

    /** Devuelve la fecha de fabricación del lote. */
    public LocalDate getFechaFabricacion() {
        return fechaFabricacion;
    }

    /** Devuelve la fecha de vencimiento del lote. */
    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    /** Devuelve la cantidad de unidades del lote. */
    public Integer getCantidad() {
        return cantidad;
    }

    /** Devuelve el estado actual del lote. */
    public EstadoLote getEstado() {
        return estado;
    }

    /** Devuelve el estado previo del lote (antes de entrar en cuarentena o recall). */
    public EstadoLote getEstadoPrevio() {
        return estadoPrevio;
    }

    /** Devuelve la fecha de liberación del lote. */
    public LocalDateTime getFechaLiberacion() {
        return fechaLiberacion;
    }

    /** Devuelve el medicamento que se fabrica en este lote. */
    public Medicamento getMedicamento() {
        return medicamento;
    }

    /** Devuelve el usuario que liberó el lote, si aplica. */
    public Usuario getLiberadoPor() {
        return liberadoPor;
    }

    /** Devuelve el laboratorio dueño del lote (el del medicamento). */
    public Empresa getLaboratorio() {
        return laboratorio;
    }

    /**
     * Libera el lote (R4): PENDIENTE_LIBERACION → LIBERADO. Quién puede
     * liberarlo (DT o inspector) lo decide el Service; acá solo el estado.
     */
    public void liberar(Usuario quien) {
        if (this.estado != EstadoLote.PENDIENTE_LIBERACION) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Un lote en estado " + this.estado + " no admite la acción liberar");
        }
        this.estado = EstadoLote.LIBERADO;
        this.liberadoPor = quien;
        this.fechaLiberacion = LocalDateTime.now();
    }

    /**
     * Entra en cuarentena por una medida de alcance LOTE: LIBERADO → CUARENTENA
     * (guarda el estado previo). Un lote PENDIENTE_LIBERACION no circula: no
     * se pone en cuarentena (alcanza con no liberarlo). Uno ya en CUARENTENA
     * o RECALL tampoco (una sola medida de LOTE vigente).
     */
    public void entrarEnCuarentena() {
        exigirEstado(EstadoLote.LIBERADO, "entrar en cuarentena");
        this.estadoPrevio = this.estado;
        this.estado = EstadoLote.CUARENTENA;
    }

    /** Se levanta la medida de LOTE: CUARENTENA → el estado previo (LIBERADO). */
    public void levantarCuarentena() {
        exigirEstado(EstadoLote.CUARENTENA, "levantar la cuarentena");
        this.estado = this.estadoPrevio;
        this.estadoPrevio = null;
    }

    /** La medida de LOTE se convierte en recall: CUARENTENA → RECALL (definitivo; no hay LIBERADO → RECALL directo). */
    public void pasarARecall() {
        exigirEstado(EstadoLote.CUARENTENA, "pasar a recall");
        this.estado = EstadoLote.RECALL;
    }

    /** Lanza TRANSICION_INVALIDA si el estado actual no es el esperado. */
    private void exigirEstado(EstadoLote esperado, String accion) {
        if (this.estado != esperado) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Un lote en estado " + this.estado + " no admite la acción " + accion);
        }
    }

    /** Indica si el lote está vencido a la fecha actual. */
    public boolean estaVencido() {
        return this.fechaVencimiento != null && this.fechaVencimiento.isBefore(LocalDate.now());
    }
}
