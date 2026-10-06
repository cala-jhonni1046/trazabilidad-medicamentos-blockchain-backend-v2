package com.medichain.modules.medicamento;

import com.medichain.modules.empresa.Empresa;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Entidad Medicamento en MediChain.
 * Representa un medicamento registrado por un laboratorio, con su rango de
 * temperatura de conservación. Es el catálogo sobre el que luego se
 * fabrican los Lotes.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "medicamentos")
public class Medicamento extends BaseEntity {

    @Column(name = "gtin", nullable = false, length = 14, unique = true)
    private String gtin;

    @Column(name = "nombre_comercial", nullable = false, length = 200, unique = false)
    private String nombreComercial;

    @Column(name = "principio_activo", nullable = false, length = 200, unique = false)
    private String principioActivo;

    @Column(name = "concentracion", nullable = false, length = 100, unique = false)
    private String concentracion;

    @Column(name = "forma_farmaceutica", nullable = false, length = 100, unique = false)
    private String formaFarmaceutica;

    @Column(name = "presentacion", nullable = false, length = 100, unique = false)
    private String presentacion;

    // nullable = true: solo aplica a medicamentos con requisito de cadena de frío.
    @Column(name = "temperatura_minima", nullable = true, precision = 5, scale = 2, unique = false)
    private BigDecimal temperaturaMinima;

    // nullable = true: solo aplica a medicamentos con requisito de cadena de frío.
    @Column(name = "temperatura_maxima", nullable = true, precision = 5, scale = 2, unique = false)
    private BigDecimal temperaturaMaxima;

    @Column(name = "biologico", nullable = false, unique = false)
    private Boolean biologico;

    @Column(name = "activo", nullable = false, unique = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratorio_id", nullable = false)
    private Empresa laboratorio;

    /** Constructor vacío exigido por JPA. */
    protected Medicamento() {
    }

    /**
     * Constructor con los campos escalares obligatorios para registrar un
     * medicamento. biologico nace en false y activo en true. La relación
     * obligatoria con el laboratorio se completa después, vía setter,
     * porque el service la resuelve contra la base a partir del id que
     * llega en el DTO.
     */
    public Medicamento(String gtin, String nombreComercial, String principioActivo,
                        String concentracion, String formaFarmaceutica, String presentacion) {
        this.gtin = gtin;
        this.nombreComercial = nombreComercial;
        this.principioActivo = principioActivo;
        this.concentracion = concentracion;
        this.formaFarmaceutica = formaFarmaceutica;
        this.presentacion = presentacion;
        this.biologico = false;
        this.activo = true;
    }

    /** Devuelve el código GTIN del medicamento. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el código GTIN del medicamento. */
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

    /** Indica si el medicamento es biológico. */
    public Boolean getBiologico() {
        return biologico;
    }

    /** Establece si el medicamento es biológico. */
    public void setBiologico(Boolean biologico) {
        this.biologico = biologico;
    }

    /** Indica si el medicamento está activo en el catálogo. */
    public Boolean getActivo() {
        return activo;
    }

    /** Establece si el medicamento está activo en el catálogo. */
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    /** Devuelve el laboratorio que registró el medicamento. */
    public Empresa getLaboratorio() {
        return laboratorio;
    }

    /** Establece el laboratorio que registró el medicamento. */
    public void setLaboratorio(Empresa laboratorio) {
        this.laboratorio = laboratorio;
    }

    /**
     * Indica si la temperatura informada está dentro del rango de
     * conservación. Si no hay rango definido, se considera siempre válida.
     */
    public boolean estaEnRango(BigDecimal t) {
        if (t == null) {
            return false;
        }
        boolean okMin = temperaturaMinima == null || t.compareTo(temperaturaMinima) >= 0;
        boolean okMax = temperaturaMaxima == null || t.compareTo(temperaturaMaxima) <= 0;
        return okMin && okMax;
    }
}
