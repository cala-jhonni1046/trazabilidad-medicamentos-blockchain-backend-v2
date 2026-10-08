package com.medichain.modules.dispensacion;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida DispensacionResponseDTO en MediChain.
 * Devuelve al cliente los datos de la dispensación, incluidos id,
 * auditoría, version y los ids de la unidad, la farmacia y el
 * farmacéutico actuante.
 */
public class DispensacionResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime fechaHora;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean particular;
    @Schema(nullable = true)
    private String obraSocial;
    @Schema(nullable = true)
    private String numeroAfiliado;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String numeroReceta;
    @Schema(example = "*****006", nullable = true)
    private String dniEnmascarado;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean anulada;
    @Schema(nullable = true)
    private LocalDateTime fechaAnulacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID unidadTrazableId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String gtin;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String serie;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID farmaciaId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID farmaceuticoId;

    /** Constructor vacío exigido por Jackson. */
    public DispensacionResponseDTO() {
    }

    /** Devuelve el id de la dispensación. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la dispensación. */
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

    /** Devuelve la fecha y hora de la dispensación. */
    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /** Establece la fecha y hora de la dispensación. */
    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    /** Devuelve la obra social del paciente. */
    public String getObraSocial() {
        return obraSocial;
    }

    /** Establece la obra social del paciente. */
    public void setObraSocial(String obraSocial) {
        this.obraSocial = obraSocial;
    }

    /** Devuelve el número de afiliado. */
    public String getNumeroAfiliado() {
        return numeroAfiliado;
    }

    /** Establece el número de afiliado. */
    public void setNumeroAfiliado(String numeroAfiliado) {
        this.numeroAfiliado = numeroAfiliado;
    }

    /** Devuelve el número de receta. */
    public String getNumeroReceta() {
        return numeroReceta;
    }

    /** Establece el número de receta. */
    public void setNumeroReceta(String numeroReceta) {
        this.numeroReceta = numeroReceta;
    }

    /** Devuelve el DNI ENMASCARADO del paciente (*****006), nunca el completo (R13). */
    public String getDniEnmascarado() {
        return dniEnmascarado;
    }

    /** Establece el DNI enmascarado. */
    public void setDniEnmascarado(String dniEnmascarado) {
        this.dniEnmascarado = dniEnmascarado;
    }

    /** Devuelve si la dispensación está anulada. */
    public Boolean getAnulada() {
        return anulada;
    }

    /** Establece si la dispensación está anulada. */
    public void setAnulada(Boolean anulada) {
        this.anulada = anulada;
    }

    /** Devuelve la fecha de anulación. */
    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    /** Establece la fecha de anulación. */
    public void setFechaAnulacion(LocalDateTime fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    /** Devuelve el id de la unidad trazable dispensada. */
    public UUID getUnidadTrazableId() {
        return unidadTrazableId;
    }

    /** Establece el id de la unidad trazable dispensada. */
    public void setUnidadTrazableId(UUID unidadTrazableId) {
        this.unidadTrazableId = unidadTrazableId;
    }

    /** Devuelve el id de la farmacia. */
    public UUID getFarmaciaId() {
        return farmaciaId;
    }

    /** Establece el id de la farmacia. */
    public void setFarmaciaId(UUID farmaciaId) {
        this.farmaciaId = farmaciaId;
    }

    /** Devuelve el id del farmacéutico actuante. */
    public UUID getFarmaceuticoId() {
        return farmaceuticoId;
    }

    /** Establece el id del farmacéutico actuante. */
    public void setFarmaceuticoId(UUID farmaceuticoId) {
        this.farmaceuticoId = farmaceuticoId;
    }

    /** Devuelve si fue particular. */
    public Boolean getParticular() {
        return particular;
    }

    /** Establece si fue particular. */
    public void setParticular(Boolean particular) {
        this.particular = particular;
    }

    /** Devuelve el GTIN de la caja. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN de la caja. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve la serie de la caja. */
    public String getSerie() {
        return serie;
    }

    /** Establece la serie de la caja. */
    public void setSerie(String serie) {
        this.serie = serie;
    }
}
