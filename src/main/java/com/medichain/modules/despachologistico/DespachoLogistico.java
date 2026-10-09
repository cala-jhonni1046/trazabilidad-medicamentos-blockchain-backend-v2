package com.medichain.modules.despachologistico;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.Tiempo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Entidad DespachoLogistico en MediChain (viaje, código VJ-0001, ruta /api/viajes).
 * Un camión con uno o más bultos, en el tramo laboratorio → distribuidora
 * o distribuidora → farmacias (R7). Nace PROGRAMADO con sus bultos (no se
 * agregan ni se sacan después); PROGRAMADO → EN_TRANSITO (salida) →
 * FINALIZADO (7e) · EN_TRANSITO → ROBADO · PROGRAMADO → CANCELADO.
 * Patente y chofer son datos personales del viaje: nunca van a eventos.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "despachos_logisticos")
public class DespachoLogistico extends BaseEntity {

    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tramo", nullable = false, length = 30, unique = false)
    private TramoDespacho tramo;

    @Column(name = "patente", nullable = false, length = 10, unique = false)
    private String patente;

    @Column(name = "chofer", nullable = false, length = 200, unique = false)
    private String chofer;

    // nullable = true: solo tiene valor una vez que se registra la salida.
    @Column(name = "fecha_salida", nullable = true, unique = false)
    private Instant fechaSalida;

    @Column(name = "fecha_estimada_entrega", nullable = false, unique = false)
    private Instant fechaEstimadaEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoDespacho estado;

    // nullable = true: motivo de la cancelación o del robo/extravío (al evento va solo su hash).
    @Column(name = "motivo_cierre", nullable = true, columnDefinition = "TEXT")
    private String motivoCierre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    private Empresa origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    // @ManyToMany: los bultos que lleva este viaje (un bulto viaja en el tramo 1 y en el tramo 2).
    // Lado dueño de la relación; se materializa en la tabla intermedia "despacho_bulto".
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "despacho_bulto",
            joinColumns = @JoinColumn(name = "despacho_id"),
            inverseJoinColumns = @JoinColumn(name = "bulto_id")
    )
    private Set<Bulto> bultos = new LinkedHashSet<>();

    /** Constructor vacío exigido por JPA. */
    protected DespachoLogistico() {
    }

    /** Programa un viaje: nace PROGRAMADO, con origen y creador; los bultos se agregan al crearlo. */
    public DespachoLogistico(String codigo, TramoDespacho tramo, String patente, String chofer,
                             Instant fechaEstimadaEntrega, Empresa origen, Usuario creadoPor) {
        this.codigo = codigo;
        this.tramo = tramo;
        this.patente = patente;
        this.chofer = chofer;
        this.fechaEstimadaEntrega = fechaEstimadaEntrega;
        this.origen = origen;
        this.creadoPor = creadoPor;
        this.estado = EstadoDespacho.PROGRAMADO;
    }

    // ---------- Métodos de dominio ----------

    /** Agrega un bulto al viaje y lo asigna (solo mientras se crea, en PROGRAMADO). */
    public void agregarBulto(Bulto bulto) {
        exigirEstado(EstadoDespacho.PROGRAMADO, "agregar bultos");
        bulto.asignarViaje(this);
        this.bultos.add(bulto);
    }

    /** Salida: PROGRAMADO → EN_TRANSITO, con fecha de salida (los bultos y cajas los mueve el Service). */
    public void registrarSalida() {
        exigirEstado(EstadoDespacho.PROGRAMADO, "salida");
        this.estado = EstadoDespacho.EN_TRANSITO;
        this.fechaSalida = Tiempo.ahora();
    }

    /** Cancela un viaje que no salió: PROGRAMADO → CANCELADO; sus bultos quedan sin viaje. */
    public void cancelar(String motivo) {
        exigirEstado(EstadoDespacho.PROGRAMADO, "cancelar");
        this.estado = EstadoDespacho.CANCELADO;
        this.motivoCierre = motivo;
        for (Bulto bulto : bultos) {
            bulto.quitarViaje();
        }
    }

    /** Robo o extravío (R14): EN_TRANSITO → ROBADO (bultos, cajas y cuarentena los maneja el Service). */
    public void robar(String motivo) {
        exigirEstado(EstadoDespacho.EN_TRANSITO, "reportar robo");
        this.estado = EstadoDespacho.ROBADO;
        this.motivoCierre = motivo;
    }

    /**
     * Paradas del viaje: la distribuidora (tramo 1) o las farmacias (tramo 2)
     * de los circuitos de sus bultos, sin duplicados.
     */
    public List<Empresa> paradas() {
        Set<Empresa> resultado = new LinkedHashSet<>();
        for (Bulto bulto : bultos) {
            if (bulto.getDestino() == null) {
                continue;
            }
            Empresa parada = this.tramo == TramoDespacho.DISTRIBUIDOR_A_FARMACIA
                    ? bulto.getDestino().getFarmacia()
                    : bulto.getDestino().getDistribuidor();
            if (parada != null) {
                resultado.add(parada);
            }
        }
        return new ArrayList<>(resultado);
    }

    /**
     * Finaliza el viaje si corresponde (R7): EN_TRANSITO y ninguno de sus
     * bultos sigue en curso con este viaje (todos se recibieron —en depósito
     * o en farmacia— o se rechazaron). Devuelve true si lo finalizó.
     */
    public boolean finalizarSiCorresponde() {
        if (estado != EstadoDespacho.EN_TRANSITO || bultos.isEmpty()) {
            return false;
        }
        boolean quedanEnCurso = bultos.stream().anyMatch(b -> b.getViajeActual() != null
                && b.getViajeActual().getId() != null && b.getViajeActual().getId().equals(getId()));
        if (quedanEnCurso) {
            return false;
        }
        this.estado = EstadoDespacho.FINALIZADO;
        return true;
    }

    /** Lanza TRANSICION_INVALIDA si el estado actual no es el esperado. */
    private void exigirEstado(EstadoDespacho esperado, String accion) {
        if (this.estado != esperado) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Un viaje en estado " + this.estado + " no admite la acción " + accion);
        }
    }

    // ---------- Getters (sin setters: el estado cambia por los métodos de dominio) ----------

    /** Devuelve el código del viaje (VJ-0001). */
    public String getCodigo() {
        return codigo;
    }

    /** Devuelve el tramo del viaje. */
    public TramoDespacho getTramo() {
        return tramo;
    }

    /** Devuelve la patente (dato del viaje, nunca va a eventos). */
    public String getPatente() {
        return patente;
    }

    /** Devuelve el chofer (dato personal, nunca va a eventos). */
    public String getChofer() {
        return chofer;
    }

    /** Devuelve la fecha de salida. */
    public Instant getFechaSalida() {
        return fechaSalida;
    }

    /** Devuelve la fecha estimada de entrega. */
    public Instant getFechaEstimadaEntrega() {
        return fechaEstimadaEntrega;
    }

    /** Devuelve el estado del viaje. */
    public EstadoDespacho getEstado() {
        return estado;
    }

    /** Devuelve el motivo de la cancelación o del robo. */
    public String getMotivoCierre() {
        return motivoCierre;
    }

    /** Devuelve la empresa origen. */
    public Empresa getOrigen() {
        return origen;
    }

    /** Devuelve el usuario que creó el viaje. */
    public Usuario getCreadoPor() {
        return creadoPor;
    }

    /** Devuelve los bultos del viaje (solo lectura). */
    public Set<Bulto> getBultos() {
        return Collections.unmodifiableSet(bultos);
    }
}
