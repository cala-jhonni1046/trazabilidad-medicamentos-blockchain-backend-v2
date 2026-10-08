package com.medichain.modules.unidadtrazable;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida UnidadTrazableResponseDTO en MediChain.
 * Devuelve al cliente los datos de la unidad, incluidos id, auditoría,
 * version y los ids del lote, la empresa actual y el bulto (si aplica).
 */
public class UnidadTrazableResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(example = "L20260415S000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serie;
    @Schema(example = "07799000001010", requiredMode = Schema.RequiredMode.REQUIRED)
    private String gtin;
    @Schema(example = "(01)07799000001010(21)L20260415S000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigoGS1;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoUnidad estado;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID loteId;
    @Schema(nullable = true)
    private UUID empresaActualId;
    @Schema(nullable = true)
    private UUID bultoId;

    /** Constructor vacío exigido por Jackson. */
    public UnidadTrazableResponseDTO() {
    }

    /** Devuelve el id de la unidad. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la unidad. */
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

    /** Devuelve la serie de la unidad. */
    public String getSerie() {
        return serie;
    }

    /** Establece la serie de la unidad. */
    public void setSerie(String serie) {
        this.serie = serie;
    }

    /** Devuelve el estado de la unidad. */
    public EstadoUnidad getEstado() {
        return estado;
    }

    /** Establece el estado de la unidad. */
    public void setEstado(EstadoUnidad estado) {
        this.estado = estado;
    }

    /** Devuelve el id del lote de la unidad. */
    public UUID getLoteId() {
        return loteId;
    }

    /** Establece el id del lote de la unidad. */
    public void setLoteId(UUID loteId) {
        this.loteId = loteId;
    }

    /** Devuelve el id de la empresa donde está la unidad. */
    public UUID getEmpresaActualId() {
        return empresaActualId;
    }

    /** Establece el id de la empresa donde está la unidad. */
    public void setEmpresaActualId(UUID empresaActualId) {
        this.empresaActualId = empresaActualId;
    }

    /** Devuelve el id del bulto que contiene a la unidad, si aplica. */
    public UUID getBultoId() {
        return bultoId;
    }

    /** Establece el id del bulto que contiene a la unidad. */
    public void setBultoId(UUID bultoId) {
        this.bultoId = bultoId;
    }

    /** Devuelve el GTIN del medicamento (junto con la serie identifica la caja). */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve el código GS1 de la caja: (01) GTIN (21) serie. */
    public String getCodigoGS1() {
        return codigoGS1;
    }

    /** Establece el código GS1 de la caja. */
    public void setCodigoGS1(String codigoGS1) {
        this.codigoGS1 = codigoGS1;
    }
}
