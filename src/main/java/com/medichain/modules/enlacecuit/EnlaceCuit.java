package com.medichain.modules.enlacecuit;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.Tiempo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad EnlaceCuit en MediChain (circuito, código CIR-0001).
 * Circuito laboratorio → distribuidora → farmacia. Lo propone el DT del
 * laboratorio (PENDIENTE_EMPRESAS); cuando la distribuidora y la farmacia
 * aceptan pasa a PENDIENTE_INSPECTOR; un inspector de la provincia de la
 * farmacia lo toma y lo APRUEBA o lo RECHAZA. Un APROBADO puede
 * SUSPENDERSE (a mano, o en cascada por la suspensión de una empresa).
 * Sin setters de estado: cada cambio pasa por un método de dominio que
 * valida el estado de origen (TRANSICION_INVALIDA → 409).
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "enlaces_cuit")
public class EnlaceCuit extends BaseEntity {

    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoEnlaceCuit estado;

    @Column(name = "fecha_propuesta", nullable = false, unique = false)
    private Instant fechaPropuesta;

    // nullable = true: solo tiene valor una vez que la distribuidora acepta el circuito.
    @Column(name = "fecha_aceptacion_distribuidor", nullable = true, unique = false)
    private Instant fechaAceptacionDistribuidor;

    // nullable = true: solo tiene valor una vez que la farmacia acepta el circuito.
    @Column(name = "fecha_aceptacion_farmacia", nullable = true, unique = false)
    private Instant fechaAceptacionFarmacia;

    // nullable = true: solo tiene valor una vez que el inspector aprueba el circuito.
    @Column(name = "fecha_aprobacion", nullable = true, unique = false)
    private Instant fechaAprobacion;

    // nullable = true: solo tiene valor cuando el estado es RECHAZADO.
    @Column(name = "motivo_rechazo", nullable = true, columnDefinition = "TEXT")
    private String motivoRechazo;

    // nullable = true: solo tiene valor cuando el estado es RECHAZADO.
    @Enumerated(EnumType.STRING)
    @Column(name = "rechazado_por", nullable = true, length = 20, unique = false)
    private OrigenRechazo rechazadoPor;

    // nullable = true: motivo de la última suspensión manual.
    @Column(name = "motivo_suspension", nullable = true, columnDefinition = "TEXT")
    private String motivoSuspension;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false)
    private Empresa laboratorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distribuidor_id", nullable = false)
    private Empresa distribuidor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmacia_id", nullable = false)
    private Empresa farmacia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propuesto_por_id", nullable = false)
    private Usuario propuestoPor;

    // nullable = true: el inspector que tomó (o al que la Sede asignó) el circuito.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "inspector_revisor_id", nullable = true)
    private InspectorAnmat inspectorRevisor;

    // nullable = true: se asigna cuando un inspector aprueba o rechaza el circuito.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "inspector_aprobador_id", nullable = true)
    private InspectorAnmat inspectorAprobador;

    // true solo si el circuito quedó SUSPENDIDO por la suspensión de una de sus empresas:
    // al rehabilitarla, vuelven a APROBADO únicamente estos.
    @Column(name = "suspendido_por_empresa", nullable = false, unique = false)
    private Boolean suspendidoPorEmpresa;

    /** Constructor vacío exigido por JPA. */
    protected EnlaceCuit() {
    }

    /**
     * Propone un circuito: código generado por la secuencia, las tres
     * empresas y el DT que lo propone. Nace PENDIENTE_EMPRESAS.
     */
    public EnlaceCuit(String codigo, Empresa laboratorio, Empresa distribuidor, Empresa farmacia,
                      Usuario propuestoPor) {
        this.codigo = codigo;
        this.laboratorio = laboratorio;
        this.distribuidor = distribuidor;
        this.farmacia = farmacia;
        this.propuestoPor = propuestoPor;
        this.estado = EstadoEnlaceCuit.PENDIENTE_EMPRESAS;
        this.fechaPropuesta = Tiempo.ahora();
        this.suspendidoPorEmpresa = false;
    }

    // ---------- Métodos de dominio ----------

    /**
     * La empresa dada (distribuidora o farmacia del circuito) acepta su
     * parte. Devuelve true si con esto aceptaron las dos y el circuito
     * pasó a PENDIENTE_INSPECTOR.
     */
    public boolean aceptar(Empresa empresa) {
        exigirEstado(EstadoEnlaceCuit.PENDIENTE_EMPRESAS, "aceptar");
        if (esDistribuidor(empresa)) {
            if (fechaAceptacionDistribuidor != null) {
                throw transicionInvalida("La distribuidora ya aceptó este circuito");
            }
            this.fechaAceptacionDistribuidor = Tiempo.ahora();
        } else if (esFarmacia(empresa)) {
            if (fechaAceptacionFarmacia != null) {
                throw transicionInvalida("La farmacia ya aceptó este circuito");
            }
            this.fechaAceptacionFarmacia = Tiempo.ahora();
        } else {
            throw new IllegalArgumentException("La empresa no participa del circuito");
        }
        if (fechaAceptacionDistribuidor != null && fechaAceptacionFarmacia != null) {
            this.estado = EstadoEnlaceCuit.PENDIENTE_INSPECTOR;
            return true;
        }
        return false;
    }

    /** La distribuidora o la farmacia rechazan el circuito: PENDIENTE_EMPRESAS → RECHAZADO. */
    public void rechazarPorEmpresa(Empresa empresa, String motivo) {
        exigirEstado(EstadoEnlaceCuit.PENDIENTE_EMPRESAS, "rechazar");
        if (esDistribuidor(empresa)) {
            this.rechazadoPor = OrigenRechazo.DISTRIBUIDOR;
        } else if (esFarmacia(empresa)) {
            this.rechazadoPor = OrigenRechazo.FARMACIA;
        } else {
            throw new IllegalArgumentException("La empresa no participa del circuito");
        }
        this.estado = EstadoEnlaceCuit.RECHAZADO;
        this.motivoRechazo = motivo;
    }

    /** Un inspector toma el circuito: PENDIENTE_INSPECTOR sin revisor. */
    public void tomar(InspectorAnmat inspector) {
        exigirPendienteInspectorSinRevisor("tomar");
        this.inspectorRevisor = inspector;
    }

    /** La Sede asigna el circuito a un inspector: mismas condiciones que tomar. */
    public void asignar(InspectorAnmat inspector) {
        exigirPendienteInspectorSinRevisor("asignar");
        this.inspectorRevisor = inspector;
    }

    /** El revisor aprueba: PENDIENTE_INSPECTOR → APROBADO. */
    public void aprobar(InspectorAnmat inspector) {
        exigirEstado(EstadoEnlaceCuit.PENDIENTE_INSPECTOR, "aprobar");
        exigirRevisor(inspector);
        this.estado = EstadoEnlaceCuit.APROBADO;
        this.inspectorAprobador = inspector;
        this.fechaAprobacion = Tiempo.ahora();
    }

    /** El revisor rechaza (definitivo): PENDIENTE_INSPECTOR → RECHAZADO. */
    public void rechazarPorInspector(InspectorAnmat inspector, String motivo) {
        exigirEstado(EstadoEnlaceCuit.PENDIENTE_INSPECTOR, "rechazar");
        exigirRevisor(inspector);
        this.estado = EstadoEnlaceCuit.RECHAZADO;
        this.rechazadoPor = OrigenRechazo.INSPECTOR;
        this.inspectorAprobador = inspector;
        this.motivoRechazo = motivo;
    }

    /** Suspensión manual (inspector o Sede): APROBADO → SUSPENDIDO, con motivo. */
    public void suspenderManual(String motivo) {
        exigirEstado(EstadoEnlaceCuit.APROBADO, "suspender");
        this.estado = EstadoEnlaceCuit.SUSPENDIDO;
        this.suspendidoPorEmpresa = false;
        this.motivoSuspension = motivo;
    }

    /**
     * Rehabilitación manual: solo de un circuito suspendido a mano (los de
     * cascada los rehabilita la empresa) y con las tres empresas HABILITADA.
     */
    public void rehabilitarManual() {
        exigirEstado(EstadoEnlaceCuit.SUSPENDIDO, "rehabilitar");
        if (Boolean.TRUE.equals(suspendidoPorEmpresa)) {
            throw transicionInvalida("El circuito está suspendido por la suspensión de una empresa: "
                    + "vuelve solo cuando se rehabilita esa empresa");
        }
        exigirTresHabilitadas();
        this.estado = EstadoEnlaceCuit.APROBADO;
    }

    /** Suspende el circuito por la suspensión de una de sus empresas: APROBADO → SUSPENDIDO. */
    public void suspenderPorEmpresa() {
        exigirEstado(EstadoEnlaceCuit.APROBADO, "suspender");
        this.estado = EstadoEnlaceCuit.SUSPENDIDO;
        this.suspendidoPorEmpresa = true;
    }

    /**
     * Rehabilita un circuito suspendido por cascada: SUSPENDIDO → APROBADO,
     * solo si las tres empresas están HABILITADA.
     */
    public void rehabilitarPorEmpresa() {
        if (this.estado != EstadoEnlaceCuit.SUSPENDIDO || !Boolean.TRUE.equals(this.suspendidoPorEmpresa)) {
            throw transicionInvalida("Solo se rehabilitan circuitos suspendidos por la suspensión de una empresa");
        }
        exigirTresHabilitadas();
        this.estado = EstadoEnlaceCuit.APROBADO;
        this.suspendidoPorEmpresa = false;
    }

    /** Lanza R5 si alguna de las tres empresas no está HABILITADA (pudo cambiar desde la propuesta). */
    public void exigirTresHabilitadas() {
        if (!puedeRehabilitarse()) {
            throw new ReglaNegocioException("R5", "Las tres empresas del circuito deben estar HABILITADA");
        }
    }

    /** Indica si las tres empresas del circuito están HABILITADA. */
    public boolean puedeRehabilitarse() {
        return laboratorio.estaHabilitada() && distribuidor.estaHabilitada() && farmacia.estaHabilitada();
    }

    /** Indica si el circuito está APROBADO. */
    public boolean estaAprobado() {
        return this.estado == EstadoEnlaceCuit.APROBADO;
    }

    /** Indica si la empresa dada es la distribuidora del circuito. */
    public boolean esDistribuidor(Empresa empresa) {
        return mismaEmpresa(distribuidor, empresa);
    }

    /** Indica si la empresa dada es la farmacia del circuito. */
    public boolean esFarmacia(Empresa empresa) {
        return mismaEmpresa(farmacia, empresa);
    }

    /** Indica si el inspector dado es el revisor actual. */
    public boolean esRevisor(InspectorAnmat inspector) {
        return inspectorRevisor != null && inspector != null
                && inspectorRevisor.getId().equals(inspector.getId());
    }

    /** Compara dos empresas por id. */
    private boolean mismaEmpresa(Empresa propia, Empresa otra) {
        UUID otraId = otra != null ? otra.getId() : null;
        return propia != null && otraId != null && otraId.equals(propia.getId());
    }

    /** Lanza TRANSICION_INVALIDA si el estado actual no es el esperado. */
    private void exigirEstado(EstadoEnlaceCuit esperado, String accion) {
        if (this.estado != esperado) {
            throw transicionInvalida("Un circuito en estado " + this.estado + " no admite la acción " + accion);
        }
    }

    /** Exige PENDIENTE_INSPECTOR y sin revisor (para tomar o asignar). */
    private void exigirPendienteInspectorSinRevisor(String accion) {
        exigirEstado(EstadoEnlaceCuit.PENDIENTE_INSPECTOR, accion);
        if (this.inspectorRevisor != null) {
            throw transicionInvalida("El circuito ya fue tomado o asignado a un inspector");
        }
    }

    /** Exige que quien actúa sea el revisor. */
    private void exigirRevisor(InspectorAnmat inspector) {
        if (!esRevisor(inspector)) {
            throw transicionInvalida("Primero hay que tomar el circuito (o tenerlo asignado)");
        }
    }

    /** Crea la excepción TRANSICION_INVALIDA con el mensaje dado. */
    private ReglaNegocioException transicionInvalida(String mensaje) {
        return new ReglaNegocioException("TRANSICION_INVALIDA", mensaje);
    }

    // ---------- Getters (sin setters: el estado cambia solo por los métodos de dominio) ----------

    /** Devuelve el código legible del circuito (CIR-0001). */
    public String getCodigo() {
        return codigo;
    }

    /** Devuelve el estado del circuito. */
    public EstadoEnlaceCuit getEstado() {
        return estado;
    }

    /** Devuelve la fecha de la propuesta. */
    public Instant getFechaPropuesta() {
        return fechaPropuesta;
    }

    /** Devuelve la fecha de aceptación de la distribuidora. */
    public Instant getFechaAceptacionDistribuidor() {
        return fechaAceptacionDistribuidor;
    }

    /** Devuelve la fecha de aceptación de la farmacia. */
    public Instant getFechaAceptacionFarmacia() {
        return fechaAceptacionFarmacia;
    }

    /** Devuelve la fecha de aprobación. */
    public Instant getFechaAprobacion() {
        return fechaAprobacion;
    }

    /** Devuelve el motivo del rechazo. */
    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    /** Devuelve quién rechazó el circuito. */
    public OrigenRechazo getRechazadoPor() {
        return rechazadoPor;
    }

    /** Devuelve el motivo de la última suspensión manual. */
    public String getMotivoSuspension() {
        return motivoSuspension;
    }

    /** Devuelve el laboratorio. */
    public Empresa getLaboratorio() {
        return laboratorio;
    }

    /** Devuelve la distribuidora. */
    public Empresa getDistribuidor() {
        return distribuidor;
    }

    /** Devuelve la farmacia. */
    public Empresa getFarmacia() {
        return farmacia;
    }

    /** Devuelve el DT que propuso el circuito. */
    public Usuario getPropuestoPor() {
        return propuestoPor;
    }

    /** Devuelve el inspector revisor (el que lo tomó o al que se asignó). */
    public InspectorAnmat getInspectorRevisor() {
        return inspectorRevisor;
    }

    /** Devuelve el inspector que aprobó o rechazó. */
    public InspectorAnmat getInspectorAprobador() {
        return inspectorAprobador;
    }

    /** Indica si el circuito está suspendido por la suspensión de una de sus empresas. */
    public Boolean getSuspendidoPorEmpresa() {
        return suspendidoPorEmpresa;
    }
}
