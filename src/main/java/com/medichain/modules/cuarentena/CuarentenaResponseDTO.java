package com.medichain.modules.cuarentena;

import com.medichain.utils.enums.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de salida CuarentenaResponseDTO en MediChain.
 * Devuelve al cliente los datos de la medida sanitaria, incluidos id,
 * auditoría, version y los ids de lote/despacho/inspector/bultos.
 */
public class CuarentenaResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private AlcanceCuarentena alcance;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoMedida tipo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private MotivoBloqueo motivo;
    @Schema(nullable = true)
    private String descripcion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean automatica;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoCuarentena estado;
    @Schema(nullable = true)
    private String dictamen;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Provincia provincia;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime fechaInicio;
    @Schema(nullable = true)
    private LocalDateTime fechaFin;
    @Schema(nullable = true)
    private UUID loteId;
    @Schema(nullable = true)
    private UUID despachoId;
    @Schema(nullable = true)
    private UUID inspectorId;
    @Schema(nullable = true)
    private UUID inspectorRevisorId;
    @Schema(nullable = true)
    private String reporteOrigen;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<UUID> bultoIds = new LinkedHashSet<>();

    /** Constructor vacío exigido por Jackson. */
    public CuarentenaResponseDTO() {
    }

    /** Devuelve el id de la medida. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la medida. */
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

    /** Devuelve el alcance de la medida. */
    public AlcanceCuarentena getAlcance() {
        return alcance;
    }

    /** Establece el alcance de la medida. */
    public void setAlcance(AlcanceCuarentena alcance) {
        this.alcance = alcance;
    }

    /** Devuelve el tipo de medida vigente. */
    public TipoMedida getTipo() {
        return tipo;
    }

    /** Establece el tipo de medida vigente. */
    public void setTipo(TipoMedida tipo) {
        this.tipo = tipo;
    }

    /** Devuelve el motivo del bloqueo. */
    public MotivoBloqueo getMotivo() {
        return motivo;
    }

    /** Establece el motivo del bloqueo. */
    public void setMotivo(MotivoBloqueo motivo) {
        this.motivo = motivo;
    }

    /** Devuelve la descripción de la medida. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Establece la descripción de la medida. */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Devuelve si la medida es automática. */
    public Boolean getAutomatica() {
        return automatica;
    }

    /** Establece si la medida es automática. */
    public void setAutomatica(Boolean automatica) {
        this.automatica = automatica;
    }

    /** Devuelve el estado de la medida. */
    public EstadoCuarentena getEstado() {
        return estado;
    }

    /** Establece el estado de la medida. */
    public void setEstado(EstadoCuarentena estado) {
        this.estado = estado;
    }

    /** Devuelve el dictamen del inspector. */
    public String getDictamen() {
        return dictamen;
    }

    /** Establece el dictamen del inspector. */
    public void setDictamen(String dictamen) {
        this.dictamen = dictamen;
    }

    /** Devuelve la provincia de la medida. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia de la medida. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve la fecha de inicio de la medida. */
    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    /** Establece la fecha de inicio de la medida. */
    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    /** Devuelve la fecha de fin de la medida. */
    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    /** Establece la fecha de fin de la medida. */
    public void setFechaFin(LocalDateTime fechaFin) {
        this.fechaFin = fechaFin;
    }

    /** Devuelve el id del lote afectado. */
    public UUID getLoteId() {
        return loteId;
    }

    /** Establece el id del lote afectado. */
    public void setLoteId(UUID loteId) {
        this.loteId = loteId;
    }

    /** Devuelve el id del despacho afectado. */
    public UUID getDespachoId() {
        return despachoId;
    }

    /** Establece el id del despacho afectado. */
    public void setDespachoId(UUID despachoId) {
        this.despachoId = despachoId;
    }

    /** Devuelve el id del inspector que dictaminó. */
    public UUID getInspectorId() {
        return inspectorId;
    }

    /** Establece el id del inspector que dictaminó. */
    public void setInspectorId(UUID inspectorId) {
        this.inspectorId = inspectorId;
    }

    /** Devuelve los ids de los bultos bloqueados. */
    public Set<UUID> getBultoIds() {
        return bultoIds;
    }

    /** Establece los ids de los bultos bloqueados. */
    public void setBultoIds(Set<UUID> bultoIds) {
        this.bultoIds = bultoIds;
    }

    /** Devuelve el id del inspector revisor (quien la tomó). */
    public UUID getInspectorRevisorId() {
        return inspectorRevisorId;
    }

    /** Establece el id del inspector revisor. */
    public void setInspectorRevisorId(UUID inspectorRevisorId) {
        this.inspectorRevisorId = inspectorRevisorId;
    }

    /** Devuelve el código del reporte ciudadano que la originó, si aplica. */
    public String getReporteOrigen() {
        return reporteOrigen;
    }

    /** Establece el código del reporte de origen. */
    public void setReporteOrigen(String reporteOrigen) {
        this.reporteOrigen = reporteOrigen;
    }
}
