package com.medichain.modules.reporteciudadano;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.enums.Provincia;
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
 * Entidad ReporteCiudadano en MediChain (código REP-0001).
 * Un paciente reporta una caja por lo que escaneó (GTIN + serie), con un
 * motivo de lista fija, la provincia donde la consiguió y una descripción
 * libre. La caja puede no existir (sospecha de falsificación); si existe,
 * queda vinculada. Un inspector de esa provincia lo toma (EN_INVESTIGACION)
 * y lo cierra con una conclusión (CERRADO). Sin setters: el estado cambia
 * por métodos de dominio (TRANSICION_INVALIDA).
 * Datos personales (R13): el paciente y la descripción quedan solo aquí,
 * nunca en la cadena de eventos.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "reportes_ciudadanos")
public class ReporteCiudadano extends BaseEntity {

    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "gtin_reportado", nullable = false, length = 14, unique = false)
    private String gtinReportado;

    @Column(name = "serie_reportada", nullable = false, length = 20, unique = false)
    private String serieReportada;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 30, unique = false)
    private MotivoReporte motivo;

    // nullable = true: texto libre del paciente; puede tener datos personales: NUNCA va a la cadena.
    @Column(name = "descripcion", nullable = true, columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoAuditoria estado;

    // nullable = true: conclusión del inspector al cerrar (el paciente no la ve; al evento, solo su hash).
    @Column(name = "conclusion", nullable = true, columnDefinition = "TEXT")
    private String conclusion;

    // Provincia que declara el paciente: dónde consiguió el medicamento (donde se investiga).
    @Enumerated(EnumType.STRING)
    @Column(name = "provincia", nullable = false, length = 30, unique = false)
    private Provincia provincia;

    @Column(name = "fecha_reporte", nullable = false, unique = false)
    private LocalDateTime fechaReporte;

    // nullable = true: solo cuando se cierra.
    @Column(name = "fecha_cierre", nullable = true, unique = false)
    private LocalDateTime fechaCierre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    // nullable = true: el inspector que lo tomó.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "investiga_id", nullable = true)
    private InspectorAnmat investiga;

    // nullable = true: la caja reportada, si GTIN + serie existen (si no, posible falsificación).
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "unidad_trazable_id", nullable = true)
    private UnidadTrazable unidadTrazable;

    /** Constructor vacío exigido por JPA. */
    protected ReporteCiudadano() {
    }

    /** Presenta el reporte: nace ABIERTO, con la fecha del momento. */
    public ReporteCiudadano(String codigo, Usuario paciente, String gtinReportado, String serieReportada,
                            MotivoReporte motivo, Provincia provincia, String descripcion,
                            UnidadTrazable unidadTrazable) {
        this.codigo = codigo;
        this.paciente = paciente;
        this.gtinReportado = gtinReportado;
        this.serieReportada = serieReportada;
        this.motivo = motivo;
        this.provincia = provincia;
        this.descripcion = descripcion;
        this.unidadTrazable = unidadTrazable;
        this.estado = EstadoAuditoria.ABIERTO;
        this.fechaReporte = LocalDateTime.now();
    }

    /** Un inspector toma el reporte: ABIERTO → EN_INVESTIGACION. */
    public void tomar(InspectorAnmat inspector) {
        if (estado != EstadoAuditoria.ABIERTO) {
            throw transicionInvalida("Un reporte en estado " + estado + " no admite la acción tomar");
        }
        this.investiga = inspector;
        this.estado = EstadoAuditoria.EN_INVESTIGACION;
    }

    /** El inspector que lo investiga lo cierra con su conclusión: EN_INVESTIGACION → CERRADO. */
    public void cerrar(InspectorAnmat inspector, String conclusion) {
        exigirEnInvestigacionPor(inspector, "cerrar");
        this.estado = EstadoAuditoria.CERRADO;
        this.conclusion = conclusion;
        this.fechaCierre = LocalDateTime.now();
    }

    /** Exige EN_INVESTIGACION y que el inspector sea quien lo tomó (para cerrar o abrir una cuarentena). */
    public void exigirEnInvestigacionPor(InspectorAnmat inspector, String accion) {
        if (estado != EstadoAuditoria.EN_INVESTIGACION || !esInvestigador(inspector)) {
            throw transicionInvalida("Para " + accion + " el reporte tiene que estar EN_INVESTIGACION y tomado por vos");
        }
    }

    /** Indica si el inspector dado es quien investiga el reporte. */
    public boolean esInvestigador(InspectorAnmat inspector) {
        return investiga != null && inspector != null && investiga.getId().equals(inspector.getId());
    }

    /** Crea la excepción TRANSICION_INVALIDA. */
    private ReglaNegocioException transicionInvalida(String mensaje) {
        return new ReglaNegocioException("TRANSICION_INVALIDA", mensaje);
    }

    /** Devuelve el código (REP-0001). */
    public String getCodigo() {
        return codigo;
    }

    /** Devuelve el GTIN reportado. */
    public String getGtinReportado() {
        return gtinReportado;
    }

    /** Devuelve la serie reportada. */
    public String getSerieReportada() {
        return serieReportada;
    }

    /** Devuelve el motivo. */
    public MotivoReporte getMotivo() {
        return motivo;
    }

    /** Devuelve la descripción libre del paciente (nunca va a eventos). */
    public String getDescripcion() {
        return descripcion;
    }

    /** Devuelve el estado. */
    public EstadoAuditoria getEstado() {
        return estado;
    }

    /** Devuelve la conclusión del inspector (no se le muestra al paciente). */
    public String getConclusion() {
        return conclusion;
    }

    /** Devuelve la provincia declarada por el paciente. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Devuelve la fecha del reporte. */
    public LocalDateTime getFechaReporte() {
        return fechaReporte;
    }

    /** Devuelve la fecha de cierre. */
    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    /** Devuelve el paciente que reportó. */
    public Usuario getPaciente() {
        return paciente;
    }

    /** Devuelve el inspector que investiga. */
    public InspectorAnmat getInvestiga() {
        return investiga;
    }

    /** Devuelve la caja reportada, si existe. */
    public UnidadTrazable getUnidadTrazable() {
        return unidadTrazable;
    }
}
