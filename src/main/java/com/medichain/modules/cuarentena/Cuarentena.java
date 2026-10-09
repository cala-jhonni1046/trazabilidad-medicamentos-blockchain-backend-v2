package com.medichain.modules.cuarentena;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.reporteciudadano.ReporteCiudadano;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.Tiempo;
import com.medichain.utils.enums.Provincia;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Entidad Cuarentena en MediChain.
 * Medida sanitaria sobre un Lote (manual, de un inspector), sobre bultos
 * de un viaje (DESPACHO: ruptura de frío o robo) o sobre un bulto (BULTO:
 * recepción rechazada). Nace ACTIVA; un inspector de su provincia la toma y
 * dictamina (R12): la levanta o la convierte en recall. Sin setters.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "cuarentenas")
public class Cuarentena extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false, length = 30, unique = false)
    private AlcanceCuarentena alcance;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30, unique = false)
    private TipoMedida tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 30, unique = false)
    private MotivoBloqueo motivo;

    // nullable = true: texto libre opcional que amplía el motivo.
    @Column(name = "descripcion", nullable = true, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "automatica", nullable = false, unique = false)
    private Boolean automatica;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoCuarentena estado;

    // nullable = true: solo tiene valor una vez que un inspector dictamina (levanta o convierte en recall).
    @Column(name = "dictamen", nullable = true, columnDefinition = "TEXT")
    private String dictamen;

    @Enumerated(EnumType.STRING)
    @Column(name = "provincia", nullable = false, length = 30, unique = false)
    private Provincia provincia;

    @Column(name = "fecha_inicio", nullable = false, unique = false)
    private Instant fechaInicio;

    // nullable = true: solo tiene valor una vez que la medida se levanta o se convierte en recall.
    @Column(name = "fecha_fin", nullable = true, unique = false)
    private Instant fechaFin;

    // nullable = true: cardinalidad "0..1"; solo tiene valor cuando alcance = LOTE.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "lote_id", nullable = true)
    private Lote lote;

    // nullable = true: cardinalidad "0..1"; solo tiene valor cuando alcance = DESPACHO.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "despacho_id", nullable = true)
    private DespachoLogistico despacho;

    // nullable = true: cardinalidad "0..1"; se asigna cuando un inspector dictamina.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "inspector_id", nullable = true)
    private InspectorAnmat inspector;

    // nullable = true: el inspector que tomó la medida para dictaminarla (el que abre una manual la toma).
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "inspector_revisor_id", nullable = true)
    private InspectorAnmat inspectorRevisor;

    // nullable = true: el reporte ciudadano que originó la medida, si nació de uno.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reporte_origen_id", nullable = true)
    private ReporteCiudadano reporteOrigen;

    // @ManyToMany: los bultos bloqueados por esta medida (alcance BULTO, o DESPACHO: los afectados del viaje).
    // Lado dueño de la relación; se materializa en la tabla intermedia "cuarentena_bulto".
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "cuarentena_bulto",
            joinColumns = @JoinColumn(name = "cuarentena_id"),
            inverseJoinColumns = @JoinColumn(name = "bulto_id")
    )
    private Set<Bulto> bultos = new LinkedHashSet<>();

    /** Constructor vacío exigido por JPA. */
    protected Cuarentena() {
    }

    /**
     * Constructor base: nace ACTIVA, de tipo CUARENTENA, con la fecha del
     * momento. Privado: se crea por las fábricas manualDeLote,
     * automaticaDeDespacho y automaticaDeBulto, que fijan alcance y objeto.
     */
    private Cuarentena(AlcanceCuarentena alcance, MotivoBloqueo motivo, Provincia provincia) {
        this.alcance = alcance;
        this.motivo = motivo;
        this.provincia = provincia;
        this.tipo = TipoMedida.CUARENTENA;
        this.estado = EstadoCuarentena.ACTIVA;
        this.automatica = false;
        this.fechaInicio = Tiempo.ahora();
    }

    /** Devuelve el alcance de la medida. */
    public AlcanceCuarentena getAlcance() {
        return alcance;
    }

    /** Devuelve el tipo de medida vigente. */
    public TipoMedida getTipo() {
        return tipo;
    }

    /** Devuelve el motivo del bloqueo. */
    public MotivoBloqueo getMotivo() {
        return motivo;
    }

    /** Devuelve la descripción ampliada del motivo. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Indica si la medida se generó automáticamente. */
    public Boolean getAutomatica() {
        return automatica;
    }

    /** Devuelve el estado actual de la medida. */
    public EstadoCuarentena getEstado() {
        return estado;
    }

    /** Devuelve el dictamen del inspector, si aplica. */
    public String getDictamen() {
        return dictamen;
    }

    /** Devuelve la provincia donde se dictó la medida. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Devuelve la fecha de inicio de la medida. */
    public Instant getFechaInicio() {
        return fechaInicio;
    }

    /** Devuelve la fecha de fin de la medida, si aplica. */
    public Instant getFechaFin() {
        return fechaFin;
    }

    /** Devuelve el lote afectado, si el alcance es LOTE. */
    public Lote getLote() {
        return lote;
    }

    /** Devuelve el despacho afectado, si el alcance es DESPACHO. */
    public DespachoLogistico getDespacho() {
        return despacho;
    }

    /** Devuelve el inspector que dictaminó la medida, si aplica. */
    public InspectorAnmat getInspector() {
        return inspector;
    }

    /** Devuelve el conjunto de bultos bloqueados, si el alcance es BULTO. */
    public Set<Bulto> getBultos() {
        return bultos;
    }

    /**
     * Cuarentena MANUAL de alcance LOTE: la abre un inspector de la provincia
     * del laboratorio (solo motivos PREVENTIVA o DEFECTO_CALIDAD), que queda
     * como revisor. Puede nacer de un reporte ciudadano.
     */
    public static Cuarentena manualDeLote(Lote lote, MotivoBloqueo motivo, String descripcion,
                                          InspectorAnmat inspector, ReporteCiudadano reporteOrigen) {
        Cuarentena cuarentena = new Cuarentena(AlcanceCuarentena.LOTE, motivo, lote.getLaboratorio().getProvincia());
        cuarentena.lote = lote;
        cuarentena.descripcion = descripcion;
        cuarentena.inspectorRevisor = inspector;
        cuarentena.reporteOrigen = reporteOrigen;
        return cuarentena;
    }

    /**
     * Cuarentena automática de alcance DESPACHO (R9 ruptura de frío, R14 robo):
     * la abre el sistema sobre los bultos afectados de un viaje. La provincia
     * es la del laboratorio de los lotes (el que dictamina un eventual recall).
     */
    public static Cuarentena automaticaDeDespacho(MotivoBloqueo motivo, Provincia provincia,
                                                  DespachoLogistico despacho, Collection<Bulto> bultos) {
        Cuarentena cuarentena = new Cuarentena(AlcanceCuarentena.DESPACHO, motivo, provincia);
        cuarentena.automatica = true;
        cuarentena.despacho = despacho;
        cuarentena.bultos.addAll(bultos);
        return cuarentena;
    }

    /**
     * Cuarentena automática de alcance BULTO (R8): la abre el sistema al
     * rechazar una recepción, sobre ese bulto, motivo RECHAZO_RECEPCION.
     */
    public static Cuarentena automaticaDeBulto(Provincia provincia, Bulto bulto) {
        Cuarentena cuarentena = new Cuarentena(AlcanceCuarentena.BULTO, MotivoBloqueo.RECHAZO_RECEPCION, provincia);
        cuarentena.automatica = true;
        cuarentena.bultos.add(bulto);
        return cuarentena;
    }

    /** Indica si la medida está vigente (bloquea): ACTIVA o CONVERTIDA_EN_RECALL. */
    public boolean estaVigente() {
        return estado == EstadoCuarentena.ACTIVA || estado == EstadoCuarentena.CONVERTIDA_EN_RECALL;
    }

    /** Un inspector toma la medida para dictaminarla: ACTIVA y sin revisor (R12). */
    public void tomar(InspectorAnmat inspector) {
        exigirActiva("tomar");
        if (inspectorRevisor != null) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "La medida ya fue tomada por un inspector");
        }
        this.inspectorRevisor = inspector;
    }

    /** Indica si el inspector dado es el revisor de la medida. */
    public boolean esRevisor(InspectorAnmat inspector) {
        return inspectorRevisor != null && inspector != null && inspectorRevisor.getId().equals(inspector.getId());
    }

    /**
     * Levanta la medida (R12): ACTIVA → LEVANTADA, solo por su revisor y con
     * fundamento. Nunca tras ruptura de frío (R9) ni robo (R14): solo cabe
     * recall. La restricción de los rechazos de recepción (R8) la valida el
     * Service, que conoce los motivos del rechazo.
     */
    public void levantar(InspectorAnmat inspector, String dictamen) {
        exigirActiva("levantar");
        exigirRevisor(inspector);
        if (motivo == MotivoBloqueo.RUPTURA_FRIO) {
            throw new ReglaNegocioException("R9", "Tras una ruptura de frío no se levanta la cuarentena: solo cabe recall");
        }
        if (motivo == MotivoBloqueo.ROBO) {
            throw new ReglaNegocioException("R14", "Una medida por robo o extravío no se levanta: solo cabe recall");
        }
        this.estado = EstadoCuarentena.LEVANTADA;
        this.inspector = inspector;
        this.dictamen = dictamen;
        this.fechaFin = Tiempo.ahora();
    }

    /**
     * Convierte la medida en recall (R12): ACTIVA → CONVERTIDA_EN_RECALL, solo
     * por su revisor y con fundamento. Lo que bloquea queda bloqueado para
     * siempre (si es de LOTE, el Service pasa el lote a RECALL).
     */
    public void convertirEnRecall(InspectorAnmat inspector, String dictamen) {
        exigirActiva("convertir en recall");
        exigirRevisor(inspector);
        this.tipo = TipoMedida.RECALL;
        this.estado = EstadoCuarentena.CONVERTIDA_EN_RECALL;
        this.inspector = inspector;
        this.dictamen = dictamen;
        this.fechaFin = Tiempo.ahora();
    }

    /** Exige que quien dictamina sea el revisor (que haya tomado la medida). */
    private void exigirRevisor(InspectorAnmat inspector) {
        if (!esRevisor(inspector)) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "Primero hay que tomar la medida para dictaminarla");
        }
    }

    /** Devuelve el inspector revisor (quien la tomó). */
    public InspectorAnmat getInspectorRevisor() {
        return inspectorRevisor;
    }

    /** Devuelve el reporte ciudadano que originó la medida, si aplica. */
    public ReporteCiudadano getReporteOrigen() {
        return reporteOrigen;
    }

    /** Lanza TRANSICION_INVALIDA si la medida no está ACTIVA. */
    private void exigirActiva(String accion) {
        if (estado != EstadoCuarentena.ACTIVA) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Una medida en estado " + estado + " no admite la acción " + accion);
        }
    }
}
