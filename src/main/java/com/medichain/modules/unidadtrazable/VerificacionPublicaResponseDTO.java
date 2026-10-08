package com.medichain.modules.unidadtrazable;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO de salida VerificacionPublicaResponseDTO en MediChain (R13).
 * Lo que ve cualquiera (sin token) al verificar una caja por GTIN + serie:
 * producto, laboratorio, lote, vencimiento, un estado simple con su mensaje
 * y el recorrido resumido. NUNCA datos del paciente ni de la dispensación
 * (ni fecha, ni farmacia que dispensó, ni receta).
 */
public class VerificacionPublicaResponseDTO {

    @Schema(example = "07799000001010", requiredMode = Schema.RequiredMode.REQUIRED)
    private String gtin;
    @Schema(example = "L20260415S000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serie;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoVerificacion estado;
    @Schema(example = "Caja apta: recorrido completo y sin medidas sanitarias", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mensaje;
    @Schema(nullable = true)
    private String producto;
    @Schema(nullable = true)
    private String principioActivo;
    @Schema(nullable = true)
    private String concentracion;
    @Schema(nullable = true)
    private String presentacion;
    @Schema(nullable = true)
    private String laboratorio;
    @Schema(nullable = true)
    private String lote;
    @Schema(nullable = true)
    private LocalDate vencimiento;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<EtapaRecorridoDTO> recorrido = new ArrayList<>();
    @Schema(nullable = true)
    private AnclajeDTO anclaje;

    /** Constructor vacío exigido por Jackson. */
    public VerificacionPublicaResponseDTO() {
    }

    /** Devuelve el GTIN consultado. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN consultado. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve la serie consultada. */
    public String getSerie() {
        return serie;
    }

    /** Establece la serie consultada. */
    public void setSerie(String serie) {
        this.serie = serie;
    }

    /** Devuelve el estado de la caja para el paciente. */
    public EstadoVerificacion getEstado() {
        return estado;
    }

    /** Establece el estado de la caja para el paciente. */
    public void setEstado(EstadoVerificacion estado) {
        this.estado = estado;
    }

    /** Devuelve el mensaje para el paciente. */
    public String getMensaje() {
        return mensaje;
    }

    /** Establece el mensaje para el paciente. */
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    /** Devuelve el nombre comercial. */
    public String getProducto() {
        return producto;
    }

    /** Establece el nombre comercial. */
    public void setProducto(String producto) {
        this.producto = producto;
    }

    /** Devuelve el principio activo. */
    public String getPrincipioActivo() {
        return principioActivo;
    }

    /** Establece el principio activo. */
    public void setPrincipioActivo(String principioActivo) {
        this.principioActivo = principioActivo;
    }

    /** Devuelve la concentración. */
    public String getConcentracion() {
        return concentracion;
    }

    /** Establece la concentración. */
    public void setConcentracion(String concentracion) {
        this.concentracion = concentracion;
    }

    /** Devuelve la presentación. */
    public String getPresentacion() {
        return presentacion;
    }

    /** Establece la presentación. */
    public void setPresentacion(String presentacion) {
        this.presentacion = presentacion;
    }

    /** Devuelve la razón social del laboratorio. */
    public String getLaboratorio() {
        return laboratorio;
    }

    /** Establece la razón social del laboratorio. */
    public void setLaboratorio(String laboratorio) {
        this.laboratorio = laboratorio;
    }

    /** Devuelve el código del lote. */
    public String getLote() {
        return lote;
    }

    /** Establece el código del lote. */
    public void setLote(String lote) {
        this.lote = lote;
    }

    /** Devuelve la fecha de vencimiento del lote. */
    public LocalDate getVencimiento() {
        return vencimiento;
    }

    /** Establece la fecha de vencimiento del lote. */
    public void setVencimiento(LocalDate vencimiento) {
        this.vencimiento = vencimiento;
    }

    /** Devuelve el recorrido resumido. */
    public List<EtapaRecorridoDTO> getRecorrido() {
        return recorrido;
    }

    /** Establece el recorrido resumido. */
    public void setRecorrido(List<EtapaRecorridoDTO> recorrido) {
        this.recorrido = recorrido;
    }

    /** Devuelve el anclaje en blockchain (paso 8). */
    public AnclajeDTO getAnclaje() {
        return anclaje;
    }

    /** Establece el anclaje en blockchain (paso 8). */
    public void setAnclaje(AnclajeDTO anclaje) {
        this.anclaje = anclaje;
    }
}
