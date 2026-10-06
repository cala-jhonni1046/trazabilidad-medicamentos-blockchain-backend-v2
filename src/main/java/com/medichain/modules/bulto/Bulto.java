package com.medichain.modules.bulto;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad Bulto en MediChain.
 * Empaque cerrado (con precinto) de cajas de UN lote LIBERADO, con un
 * circuito APROBADO de destino (R6). Las cajas guardan la relación (sin
 * colección aquí). El estado cambia solo por métodos de dominio
 * (TRANSICION_INVALIDA): ARMADO → EN_TRANSITO → EN_DEPOSITO → EN_TRANSITO
 * → RECIBIDO · EN_TRANSITO → RECHAZADO | ROBADO · ARMADO (sin viaje) →
 * DESARMADO. viajeActual es el viaje en curso (o programado) del bulto.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "bultos")
public class Bulto extends BaseEntity {

    /** Máximo de cajas por bulto. */
    public static final int MAXIMO_CAJAS = 1000;

    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "cantidad", nullable = false, unique = false)
    private Integer cantidad;

    @Column(name = "precinto", nullable = false, length = 50, unique = false)
    private String precinto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoBulto estado;

    @Column(name = "fecha_armado", nullable = false, unique = false)
    private LocalDateTime fechaArmado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    private EnlaceCuit destino;

    // nullable = true: la empresa donde está físicamente el bulto; null mientras viaja.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "ubicacion_empresa_id", nullable = true)
    private Empresa ubicacion;

    // nullable = true: el viaje programado o en curso del bulto; null cuando no está asignado a ninguno.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "viaje_actual_id", nullable = true)
    private DespachoLogistico viajeActual;

    /** Constructor vacío exigido por JPA. */
    protected Bulto() {
    }

    /**
     * Arma un bulto en el laboratorio del lote: nace ARMADO, ubicado en ese
     * laboratorio, con la cantidad de cajas que contiene y su circuito de destino.
     */
    public Bulto(String codigo, Integer cantidad, String precinto, Lote lote, EnlaceCuit destino) {
        this.codigo = codigo;
        this.cantidad = cantidad;
        this.precinto = precinto;
        this.lote = lote;
        this.destino = destino;
        this.ubicacion = lote.getLaboratorio();
        this.estado = EstadoBulto.ARMADO;
        this.fechaArmado = LocalDateTime.now();
    }

    // ---------- Métodos de dominio ----------

    /** Asigna el bulto a un viaje PROGRAMADO: debe estar ARMADO o EN_DEPOSITO y sin otro viaje. */
    public void asignarViaje(DespachoLogistico viaje) {
        if (estado != EstadoBulto.ARMADO && estado != EstadoBulto.EN_DEPOSITO) {
            throw transicionInvalida("Un bulto en estado " + estado + " no puede asignarse a un viaje");
        }
        if (viajeActual != null) {
            throw new ReglaNegocioException("R7", "El bulto " + codigo + " ya está en el viaje " + viajeActual.getCodigo());
        }
        this.viajeActual = viaje;
    }

    /** Saca el bulto de un viaje cancelado: vuelve a quedar sin viaje (sigue ARMADO o EN_DEPOSITO). */
    public void quitarViaje() {
        if (viajeActual == null || (estado != EstadoBulto.ARMADO && estado != EstadoBulto.EN_DEPOSITO)) {
            throw transicionInvalida("El bulto " + codigo + " no está en un viaje programado");
        }
        this.viajeActual = null;
    }

    /** Salida del viaje: ARMADO o EN_DEPOSITO (con viaje asignado) → EN_TRANSITO; deja de estar en una empresa. */
    public void salir() {
        if ((estado != EstadoBulto.ARMADO && estado != EstadoBulto.EN_DEPOSITO) || viajeActual == null) {
            throw transicionInvalida("Un bulto en estado " + estado + " no puede salir de viaje");
        }
        this.estado = EstadoBulto.EN_TRANSITO;
        this.ubicacion = null;
    }

    /** Robo o extravío del viaje (R14): EN_TRANSITO → ROBADO. */
    public void robar() {
        if (estado != EstadoBulto.EN_TRANSITO) {
            throw transicionInvalida("Un bulto en estado " + estado + " no puede reportarse robado");
        }
        this.estado = EstadoBulto.ROBADO;
    }

    /**
     * Recepción conforme (R8). Tramo 1 → EN_DEPOSITO en la distribuidora;
     * tramo 2 → RECIBIDO en la farmacia. En ambos casos deja de tener viaje
     * en curso (el historial queda en despacho_bulto).
     */
    public void recibir(Empresa receptora) {
        exigirEnTransito("recibir");
        this.estado = viajeActual.getTramo() == TramoDespacho.LAB_A_DISTRIBUIDOR
                ? EstadoBulto.EN_DEPOSITO
                : EstadoBulto.RECIBIDO;
        this.ubicacion = receptora;
        this.viajeActual = null;
    }

    /** Recepción NO conforme (R8): EN_TRANSITO → RECHAZADO, en poder de la receptora. */
    public void rechazar(Empresa receptora) {
        exigirEnTransito("rechazar");
        this.estado = EstadoBulto.RECHAZADO;
        this.ubicacion = receptora;
        this.viajeActual = null;
    }

    /**
     * Aceptación por dictamen (7f): un inspector levantó la cuarentena BULTO
     * de un rechazo que fue SOLO por precinto roto. RECHAZADO → EN_DEPOSITO
     * (si venía del tramo 1) o RECIBIDO (tramo 2), en la empresa que ya lo tiene.
     */
    public void aceptarPorDictamen(TramoDespacho tramo) {
        if (estado != EstadoBulto.RECHAZADO) {
            throw transicionInvalida("Solo se acepta por dictamen un bulto RECHAZADO (estado " + estado + ")");
        }
        this.estado = tramo == TramoDespacho.LAB_A_DISTRIBUIDOR ? EstadoBulto.EN_DEPOSITO : EstadoBulto.RECIBIDO;
    }

    /** Exige EN_TRANSITO con un viaje en curso. */
    private void exigirEnTransito(String accion) {
        if (estado != EstadoBulto.EN_TRANSITO || viajeActual == null) {
            throw transicionInvalida("Un bulto en estado " + estado + " no admite la acción " + accion);
        }
    }

    /** Desarma el bulto: solo ARMADO y sin viaje asignado → DESARMADO (las cajas las libera el Service). */
    public void desarmar() {
        if (estado != EstadoBulto.ARMADO || viajeActual != null) {
            throw transicionInvalida("Solo se desarma un bulto ARMADO que no está asignado a un viaje");
        }
        this.estado = EstadoBulto.DESARMADO;
    }

    /**
     * Devuelve la empresa a la que va el bulto ahora:
     * ARMADO → la distribuidora del circuito; EN_DEPOSITO → la farmacia;
     * EN_TRANSITO → la distribuidora (tramo 1) o la farmacia (tramo 2);
     * estados terminales → null.
     */
    public Empresa destinoActual() {
        if (destino == null || estado == null) {
            return null;
        }
        return switch (estado) {
            case ARMADO -> destino.getDistribuidor();
            case EN_DEPOSITO -> destino.getFarmacia();
            case EN_TRANSITO -> {
                if (viajeActual == null || viajeActual.getTramo() == null) {
                    yield null;
                }
                yield viajeActual.getTramo() == TramoDespacho.LAB_A_DISTRIBUIDOR
                        ? destino.getDistribuidor()
                        : destino.getFarmacia();
            }
            case RECIBIDO, RECHAZADO, ROBADO, DESARMADO -> null;
        };
    }

    /** Crea la excepción TRANSICION_INVALIDA. */
    private ReglaNegocioException transicionInvalida(String mensaje) {
        return new ReglaNegocioException("TRANSICION_INVALIDA", mensaje);
    }

    // ---------- Getters (sin setters: el estado cambia por los métodos de dominio) ----------

    /** Devuelve el código del bulto (BUL-0001). */
    public String getCodigo() {
        return codigo;
    }

    /** Devuelve la cantidad de cajas del bulto. */
    public Integer getCantidad() {
        return cantidad;
    }

    /** Devuelve el número de precinto. */
    public String getPrecinto() {
        return precinto;
    }

    /** Devuelve el estado del bulto. */
    public EstadoBulto getEstado() {
        return estado;
    }

    /** Devuelve la fecha de armado. */
    public LocalDateTime getFechaArmado() {
        return fechaArmado;
    }

    /** Devuelve el lote de las cajas del bulto. */
    public Lote getLote() {
        return lote;
    }

    /** Devuelve el circuito de destino. */
    public EnlaceCuit getDestino() {
        return destino;
    }

    /** Devuelve la empresa donde está el bulto (null si viaja). */
    public Empresa getUbicacion() {
        return ubicacion;
    }

    /** Devuelve el viaje programado o en curso (null si no tiene). */
    public DespachoLogistico getViajeActual() {
        return viajeActual;
    }
}
