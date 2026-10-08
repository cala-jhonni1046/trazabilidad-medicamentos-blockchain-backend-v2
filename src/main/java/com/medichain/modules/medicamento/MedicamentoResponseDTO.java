package com.medichain.modules.medicamento;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida MedicamentoResponseDTO en MediChain.
 * Devuelve al cliente los datos del medicamento, incluidos id, auditoría,
 * version y el id del laboratorio que lo registró.
 */
public class MedicamentoResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(example = "07799000001010", requiredMode = Schema.RequiredMode.REQUIRED)
    private String gtin;
    @Schema(example = "Cuyafen", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombreComercial;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String principioActivo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String concentracion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String formaFarmaceutica;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentacion;
    @Schema(nullable = true)
    private BigDecimal temperaturaMinima;
    @Schema(nullable = true)
    private BigDecimal temperaturaMaxima;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean biologico;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean activo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID laboratorioId;

    /** Constructor vacío exigido por Jackson. */
    public MedicamentoResponseDTO() {
    }

    /** Devuelve el id del medicamento. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del medicamento. */
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

    /** Devuelve el GTIN del medicamento. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN del medicamento. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve el nombre comercial del medicamento. */
    public String getNombreComercial() {
        return nombreComercial;
    }

    /** Establece el nombre comercial del medicamento. */
    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    /** Devuelve el principio activo del medicamento. */
    public String getPrincipioActivo() {
        return principioActivo;
    }

    /** Establece el principio activo del medicamento. */
    public void setPrincipioActivo(String principioActivo) {
        this.principioActivo = principioActivo;
    }

    /** Devuelve la concentración del medicamento. */
    public String getConcentracion() {
        return concentracion;
    }

    /** Establece la concentración del medicamento. */
    public void setConcentracion(String concentracion) {
        this.concentracion = concentracion;
    }

    /** Devuelve la forma farmacéutica del medicamento. */
    public String getFormaFarmaceutica() {
        return formaFarmaceutica;
    }

    /** Establece la forma farmacéutica del medicamento. */
    public void setFormaFarmaceutica(String formaFarmaceutica) {
        this.formaFarmaceutica = formaFarmaceutica;
    }

    /** Devuelve la presentación del medicamento. */
    public String getPresentacion() {
        return presentacion;
    }

    /** Establece la presentación del medicamento. */
    public void setPresentacion(String presentacion) {
        this.presentacion = presentacion;
    }

    /** Devuelve la temperatura mínima de conservación. */
    public BigDecimal getTemperaturaMinima() {
        return temperaturaMinima;
    }

    /** Establece la temperatura mínima de conservación. */
    public void setTemperaturaMinima(BigDecimal temperaturaMinima) {
        this.temperaturaMinima = temperaturaMinima;
    }

    /** Devuelve la temperatura máxima de conservación. */
    public BigDecimal getTemperaturaMaxima() {
        return temperaturaMaxima;
    }

    /** Establece la temperatura máxima de conservación. */
    public void setTemperaturaMaxima(BigDecimal temperaturaMaxima) {
        this.temperaturaMaxima = temperaturaMaxima;
    }

    /** Devuelve si el medicamento es biológico. */
    public Boolean getBiologico() {
        return biologico;
    }

    /** Establece si el medicamento es biológico. */
    public void setBiologico(Boolean biologico) {
        this.biologico = biologico;
    }

    /** Devuelve si el medicamento está activo en el catálogo. */
    public Boolean getActivo() {
        return activo;
    }

    /** Establece si el medicamento está activo en el catálogo. */
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    /** Devuelve el id del laboratorio que registró el medicamento. */
    public UUID getLaboratorioId() {
        return laboratorioId;
    }

    /** Establece el id del laboratorio que registró el medicamento. */
    public void setLaboratorioId(UUID laboratorioId) {
        this.laboratorioId = laboratorioId;
    }
}
