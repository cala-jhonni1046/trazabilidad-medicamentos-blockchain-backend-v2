package com.medichain.modules.empresa;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.Tiempo;
import com.medichain.utils.enums.Provincia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidad Empresa en MediChain.
 * Representa a un laboratorio, distribuidor o farmacia que participa del
 * circuito de trazabilidad. Nace con habilitación PENDIENTE ante ANMAT y un
 * inspector la revisa, la habilita o la rechaza.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "empresas")
public class Empresa extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30, unique = false)
    private TipoEmpresa tipo;

    @Column(name = "cuit", nullable = false, length = 13, unique = true)
    private String cuit;

    @Column(name = "razon_social", nullable = false, length = 200, unique = false)
    private String razonSocial;

    @Column(name = "gln", nullable = true, length = 13, unique = true)
    private String gln;

    @Enumerated(EnumType.STRING)
    @Column(name = "provincia", nullable = false, length = 30, unique = false)
    private Provincia provincia;

    @Column(name = "localidad", nullable = false, length = 150, unique = false)
    private String localidad;

    @Column(name = "domicilio", nullable = false, length = 250, unique = false)
    private String domicilio;

    // nullable = true: se completa cuando el inspector habilita a la empresa.
    @Column(name = "numero_habilitacion", nullable = true, length = 50, unique = false)
    private String numeroHabilitacion;

    // nullable = true: solo aplica a laboratorios/farmacias con director técnico.
    @Column(name = "director_tecnico", nullable = true, length = 200, unique = false)
    private String directorTecnico;

    // nullable = true: se completa cuando la empresa adjunta su documentación.
    @Column(name = "documento_nombre", nullable = true, length = 255, unique = false)
    private String documentoNombre;

    // nullable = true, length 64: SHA-256 (hex minúscula) del PDF presentado; también va en SOLICITUD_HABILITACION.
    @Column(name = "documento_hash", nullable = true, length = 64, unique = false)
    private String documentoHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoHabilitacion estado;

    @Column(name = "fecha_solicitud", nullable = false, unique = false)
    private Instant fechaSolicitud;

    // nullable = true: solo tiene valor una vez que el inspector habilita la empresa.
    @Column(name = "fecha_habilitacion", nullable = true, unique = false)
    private Instant fechaHabilitacion;

    // nullable = true: solo tiene valor cuando el estado es RECHAZADA.
    @Column(name = "motivo_rechazo", nullable = true, columnDefinition = "TEXT")
    private String motivoRechazo;

    // nullable = true: solo tiene valor mientras la empresa está (o estuvo) SUSPENDIDA.
    @Column(name = "motivo_suspension", nullable = true, columnDefinition = "TEXT")
    private String motivoSuspension;

    // nullable = true: se asigna cuando un inspector toma la solicitud ("revisa").
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "inspector_revisor_id", nullable = true)
    private InspectorAnmat inspectorRevisor;

    // nullable = true: se asigna cuando un inspector habilita o rechaza la solicitud.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "inspector_habilitador_id", nullable = true)
    private InspectorAnmat inspectorHabilitador;

    /** Constructor vacío exigido por JPA. */
    protected Empresa() {
    }

    /**
     * Constructor con los campos obligatorios para dar de alta la solicitud
     * de habilitación de una empresa. El estado inicial siempre es PENDIENTE
     * y la fecha de solicitud se fija en el momento de la creación.
     */
    public Empresa(TipoEmpresa tipo, String cuit, String razonSocial, Provincia provincia,
                    String localidad, String domicilio) {
        this.tipo = tipo;
        this.cuit = cuit;
        this.razonSocial = razonSocial;
        this.provincia = provincia;
        this.localidad = localidad;
        this.domicilio = domicilio;
        this.estado = EstadoHabilitacion.PENDIENTE;
        this.fechaSolicitud = Tiempo.ahora();
    }

    /** Devuelve el tipo de empresa (laboratorio, distribuidor o farmacia). */
    public TipoEmpresa getTipo() {
        return tipo;
    }

    /** Establece el tipo de empresa. */
    public void setTipo(TipoEmpresa tipo) {
        this.tipo = tipo;
    }

    /** Devuelve el CUIT de la empresa. */
    public String getCuit() {
        return cuit;
    }

    /** Establece el CUIT de la empresa. */
    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    /** Devuelve la razón social de la empresa. */
    public String getRazonSocial() {
        return razonSocial;
    }

    /** Establece la razón social de la empresa. */
    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    /** Devuelve el código GLN de la empresa. */
    public String getGln() {
        return gln;
    }

    /** Establece el código GLN de la empresa. */
    public void setGln(String gln) {
        this.gln = gln;
    }

    /** Devuelve la provincia donde opera la empresa. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia donde opera la empresa. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve la localidad donde opera la empresa. */
    public String getLocalidad() {
        return localidad;
    }

    /** Establece la localidad donde opera la empresa. */
    public void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    /** Devuelve el domicilio de la empresa. */
    public String getDomicilio() {
        return domicilio;
    }

    /** Establece el domicilio de la empresa. */
    public void setDomicilio(String domicilio) {
        this.domicilio = domicilio;
    }

    /** Devuelve el número de habilitación otorgado por ANMAT. */
    public String getNumeroHabilitacion() {
        return numeroHabilitacion;
    }

    /** Establece el número de habilitación otorgado por ANMAT. */
    public void setNumeroHabilitacion(String numeroHabilitacion) {
        this.numeroHabilitacion = numeroHabilitacion;
    }

    /** Devuelve el nombre del director técnico de la empresa. */
    public String getDirectorTecnico() {
        return directorTecnico;
    }

    /** Establece el nombre del director técnico de la empresa. */
    public void setDirectorTecnico(String directorTecnico) {
        this.directorTecnico = directorTecnico;
    }

    /** Devuelve el nombre del documento de habilitación adjuntado. */
    public String getDocumentoNombre() {
        return documentoNombre;
    }

    /** Establece el nombre del documento de habilitación adjuntado. */
    public void setDocumentoNombre(String documentoNombre) {
        this.documentoNombre = documentoNombre;
    }

    /** Devuelve el hash del documento de habilitación adjuntado. */
    public String getDocumentoHash() {
        return documentoHash;
    }

    /** Establece el hash del documento de habilitación adjuntado. */
    public void setDocumentoHash(String documentoHash) {
        this.documentoHash = documentoHash;
    }

    /** Devuelve el estado de habilitación actual. */
    public EstadoHabilitacion getEstado() {
        return estado;
    }

    /** Establece el estado de habilitación actual. */
    public void setEstado(EstadoHabilitacion estado) {
        this.estado = estado;
    }

    /** Devuelve la fecha y hora de la solicitud de habilitación. */
    public Instant getFechaSolicitud() {
        return fechaSolicitud;
    }

    /** Establece la fecha y hora de la solicitud de habilitación. */
    public void setFechaSolicitud(Instant fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    /** Devuelve la fecha y hora en que se habilitó la empresa. */
    public Instant getFechaHabilitacion() {
        return fechaHabilitacion;
    }

    /** Establece la fecha y hora en que se habilitó la empresa. */
    public void setFechaHabilitacion(Instant fechaHabilitacion) {
        this.fechaHabilitacion = fechaHabilitacion;
    }

    /** Devuelve el motivo por el que se rechazó la solicitud, si aplica. */
    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    /** Establece el motivo por el que se rechazó la solicitud. */
    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    /** Devuelve el inspector que tomó la solicitud para revisarla. */
    public InspectorAnmat getInspectorRevisor() {
        return inspectorRevisor;
    }

    /** Establece el inspector que tomó la solicitud para revisarla. */
    public void setInspectorRevisor(InspectorAnmat inspectorRevisor) {
        this.inspectorRevisor = inspectorRevisor;
    }

    /** Devuelve el inspector que habilitó o rechazó la solicitud. */
    public InspectorAnmat getInspectorHabilitador() {
        return inspectorHabilitador;
    }

    /** Establece el inspector que habilitó o rechazó la solicitud. */
    public void setInspectorHabilitador(InspectorAnmat inspectorHabilitador) {
        this.inspectorHabilitador = inspectorHabilitador;
    }

    /** Devuelve el motivo de la última suspensión, si la hubo. */
    public String getMotivoSuspension() {
        return motivoSuspension;
    }

    /** Indica si la empresa está actualmente habilitada para operar. */
    public boolean estaHabilitada() {
        return this.estado == EstadoHabilitacion.HABILITADA;
    }

    /** Indica si el inspector dado es el revisor actual de la solicitud. */
    public boolean esRevisor(InspectorAnmat inspector) {
        return inspectorRevisor != null && inspector != null
                && inspectorRevisor.getId().equals(inspector.getId());
    }

    /**
     * El inspector toma la solicitud: solo si está PENDIENTE y nadie la
     * tomó ni la tiene asignada.
     */
    public void tomar(InspectorAnmat inspector) {
        exigirPendienteSinRevisor("tomar");
        this.inspectorRevisor = inspector;
    }

    /** La Sede asigna la solicitud a un inspector: mismas condiciones que tomar. */
    public void asignar(InspectorAnmat inspector) {
        exigirPendienteSinRevisor("asignar");
        this.inspectorRevisor = inspector;
    }

    /** Devuelve la solicitud a la bandeja (sin revisor). Usado al dar de baja al inspector. */
    public void liberarRevisor() {
        exigirEstado(EstadoHabilitacion.PENDIENTE, "liberar");
        this.inspectorRevisor = null;
    }

    /** Habilita la empresa: PENDIENTE → HABILITADA, solo por su revisor. */
    public void habilitar(InspectorAnmat inspector) {
        exigirEstado(EstadoHabilitacion.PENDIENTE, "habilitar");
        exigirRevisor(inspector);
        this.estado = EstadoHabilitacion.HABILITADA;
        this.inspectorHabilitador = inspector;
        this.fechaHabilitacion = Tiempo.ahora();
    }

    /** Rechaza la solicitud: PENDIENTE → RECHAZADA, solo por su revisor y con motivo. */
    public void rechazar(InspectorAnmat inspector, String motivo) {
        exigirEstado(EstadoHabilitacion.PENDIENTE, "rechazar");
        exigirRevisor(inspector);
        this.estado = EstadoHabilitacion.RECHAZADA;
        this.inspectorHabilitador = inspector;
        this.motivoRechazo = motivo;
    }

    /** Suspende la empresa: HABILITADA → SUSPENDIDA, con motivo. */
    public void suspender(String motivo) {
        exigirEstado(EstadoHabilitacion.HABILITADA, "suspender");
        this.estado = EstadoHabilitacion.SUSPENDIDA;
        this.motivoSuspension = motivo;
    }

    /** Rehabilita la empresa: SUSPENDIDA → HABILITADA. */
    public void rehabilitar() {
        exigirEstado(EstadoHabilitacion.SUSPENDIDA, "rehabilitar");
        this.estado = EstadoHabilitacion.HABILITADA;
    }

    /** Lanza TRANSICION_INVALIDA si el estado actual no es el esperado. */
    private void exigirEstado(EstadoHabilitacion esperado, String accion) {
        if (this.estado != esperado) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Una empresa en estado " + this.estado + " no admite la acción " + accion);
        }
    }

    /** Exige PENDIENTE y sin revisor (para tomar o asignar). */
    private void exigirPendienteSinRevisor(String accion) {
        exigirEstado(EstadoHabilitacion.PENDIENTE, accion);
        if (this.inspectorRevisor != null) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "La solicitud ya fue tomada o asignada a un inspector");
        }
    }

    /** Exige que quien actúa sea el revisor (que haya tomado o tenga asignada la solicitud). */
    private void exigirRevisor(InspectorAnmat inspector) {
        if (!esRevisor(inspector)) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Primero hay que tomar la solicitud (o tenerla asignada)");
        }
    }
}
