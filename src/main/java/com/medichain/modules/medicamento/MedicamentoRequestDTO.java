package com.medichain.modules.medicamento;

import com.medichain.utils.validacion.Gtin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de entrada MedicamentoRequestDTO en MediChain.
 * Transporta los datos que se envían para registrar un medicamento.
 * biologico nace en false y activo en true salvo que se informen; el
 * rango de temperatura es opcional (solo aplica a los que requieren
 * cadena de frío).
 */
public class MedicamentoRequestDTO {

    @NotBlank(message = "El campo gtin es obligatorio")
    @Gtin
    private String gtin;

    @NotBlank(message = "El campo nombreComercial es obligatorio")
    @Size(max = 200, message = "El campo nombreComercial no puede superar 200 caracteres")
    private String nombreComercial;

    @NotBlank(message = "El campo principioActivo es obligatorio")
    @Size(max = 200, message = "El campo principioActivo no puede superar 200 caracteres")
    private String principioActivo;

    @NotBlank(message = "El campo concentracion es obligatorio")
    @Size(max = 100, message = "El campo concentracion no puede superar 100 caracteres")
    private String concentracion;

    @NotBlank(message = "El campo formaFarmaceutica es obligatorio")
    @Size(max = 100, message = "El campo formaFarmaceutica no puede superar 100 caracteres")
    private String formaFarmaceutica;

    @NotBlank(message = "El campo presentacion es obligatorio")
    @Size(max = 100, message = "El campo presentacion no puede superar 100 caracteres")
    private String presentacion;

    @DecimalMin(value = "-99.99", message = "El campo temperaturaMinima no puede ser menor a -99.99")
    @DecimalMax(value = "999.99", message = "El campo temperaturaMinima no puede ser mayor a 999.99")
    private BigDecimal temperaturaMinima;

    @DecimalMin(value = "-99.99", message = "El campo temperaturaMaxima no puede ser menor a -99.99")
    @DecimalMax(value = "999.99", message = "El campo temperaturaMaxima no puede ser mayor a 999.99")
    private BigDecimal temperaturaMaxima;

    private Boolean biologico;

    /** Constructor vacío exigido por Jackson. */
    public MedicamentoRequestDTO() {
    }

    /** Constructor con los campos obligatorios de alta. */
    public MedicamentoRequestDTO(String gtin, String nombreComercial, String principioActivo,
                                  String concentracion, String formaFarmaceutica, String presentacion) {
        this.gtin = gtin;
        this.nombreComercial = nombreComercial;
        this.principioActivo = principioActivo;
        this.concentracion = concentracion;
        this.formaFarmaceutica = formaFarmaceutica;
        this.presentacion = presentacion;
    }

    /** Devuelve el GTIN informado. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN informado. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve el nombre comercial informado. */
    public String getNombreComercial() {
        return nombreComercial;
    }

    /** Establece el nombre comercial informado. */
    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    /** Devuelve el principio activo informado. */
    public String getPrincipioActivo() {
        return principioActivo;
    }

    /** Establece el principio activo informado. */
    public void setPrincipioActivo(String principioActivo) {
        this.principioActivo = principioActivo;
    }

    /** Devuelve la concentración informada. */
    public String getConcentracion() {
        return concentracion;
    }

    /** Establece la concentración informada. */
    public void setConcentracion(String concentracion) {
        this.concentracion = concentracion;
    }

    /** Devuelve la forma farmacéutica informada. */
    public String getFormaFarmaceutica() {
        return formaFarmaceutica;
    }

    /** Establece la forma farmacéutica informada. */
    public void setFormaFarmaceutica(String formaFarmaceutica) {
        this.formaFarmaceutica = formaFarmaceutica;
    }

    /** Devuelve la presentación informada. */
    public String getPresentacion() {
        return presentacion;
    }

    /** Establece la presentación informada. */
    public void setPresentacion(String presentacion) {
        this.presentacion = presentacion;
    }

    /** Devuelve la temperatura mínima informada. */
    public BigDecimal getTemperaturaMinima() {
        return temperaturaMinima;
    }

    /** Establece la temperatura mínima informada. */
    public void setTemperaturaMinima(BigDecimal temperaturaMinima) {
        this.temperaturaMinima = temperaturaMinima;
    }

    /** Devuelve la temperatura máxima informada. */
    public BigDecimal getTemperaturaMaxima() {
        return temperaturaMaxima;
    }

    /** Establece la temperatura máxima informada. */
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
}
