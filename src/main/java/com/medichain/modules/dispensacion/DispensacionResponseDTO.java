package com.medichain.modules.dispensacion;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida DispensacionResponseDTO en MediChain.
 * Devuelve al cliente los datos de la dispensación, incluidos id,
 * auditoría, version y los ids de la unidad, la farmacia y el
 * farmacéutico actuante.
 */
public class DispensacionResponseDTO {

    private UUID id;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long version;
    private LocalDateTime fechaHora;
    private Boolean particular;
    private String obraSocial;
    private String numeroAfiliado;
    private String numeroReceta;
    private String dniEnmascarado;
    private Boolean anulada;
    private LocalDateTime fechaAnulacion;
    private UUID unidadTrazableId;
    private String gtin;
    private String serie;
    private UUID farmaciaId;
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
