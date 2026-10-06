package com.medichain.modules.unidadtrazable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida UnidadTrazableResponseDTO en MediChain.
 * Devuelve al cliente los datos de la unidad, incluidos id, auditoría,
 * version y los ids del lote, la empresa actual y el bulto (si aplica).
 */
public class UnidadTrazableResponseDTO {

    private UUID id;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long version;
    private String serie;
    private String gtin;
    private String codigoGS1;
    private EstadoUnidad estado;
    private UUID loteId;
    private UUID empresaActualId;
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
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha de creación del registro. */
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha de última actualización del registro. */
    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha de última actualización del registro. */
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
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
