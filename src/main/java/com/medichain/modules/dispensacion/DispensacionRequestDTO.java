package com.medichain.modules.dispensacion;

import com.medichain.utils.validacion.Gtin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada DispensacionRequestDTO en MediChain (R11, R13).
 * La caja se identifica por lo que trae el DataMatrix: GTIN + serie.
 * Cobertura: particular, u obra social + número de afiliado. DNI opcional:
 * se valida, se enmascara y el completo se descarta (nunca se guarda ni se
 * devuelve). Este DTO no tiene toString(): no se puede loguear el DNI por
 * accidente.
 */
@DispensacionRequestValida
public class DispensacionRequestDTO {

    @Schema(description = "GTIN de la caja (DataMatrix (01))", example = "07799000001010", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo gtin es obligatorio")
    @Gtin
    private String gtin;

    @Schema(description = "Serie de la caja (DataMatrix (21))", example = "L20260001S000011", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo serie es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9]{1,20}", message = "La serie es alfanumérica de hasta 20 caracteres")
    private String serie;

    @Schema(description = "true si el paciente no usa obra social", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo particular es obligatorio")
    private Boolean particular;

    @Schema(description = "Obra social (obligatoria si no es particular)", example = "OSEP")
    @Size(max = 200, message = "El campo obraSocial no puede superar 200 caracteres")
    private String obraSocial;

    @Schema(description = "Número de afiliado (obligatorio si no es particular)", example = "AF-123456")
    @Size(max = 50, message = "El campo numeroAfiliado no puede superar 50 caracteres")
    private String numeroAfiliado;

    @Schema(description = "Número de receta", example = "REC-2026-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo numeroReceta es obligatorio")
    @Size(max = 100, message = "El campo numeroReceta no puede superar 100 caracteres")
    private String numeroReceta;

    @Schema(description = "DNI del paciente, opcional (7 u 8 dígitos). Se guarda SOLO enmascarado: *****006", example = "30111006")
    @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos, sin puntos")
    private String dni;

    /** Constructor vacío exigido por Jackson. */
    public DispensacionRequestDTO() {
    }

    /** Devuelve el GTIN. */
    public String getGtin() {
        return gtin;
    }

    /** Establece el GTIN. */
    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    /** Devuelve la serie. */
    public String getSerie() {
        return serie;
    }

    /** Establece la serie. */
    public void setSerie(String serie) {
        this.serie = serie;
    }

    /** Indica si es particular. */
    public Boolean getParticular() {
        return particular;
    }

    /** Establece si es particular. */
    public void setParticular(Boolean particular) {
        this.particular = particular;
    }

    /** Devuelve la obra social. */
    public String getObraSocial() {
        return obraSocial;
    }

    /** Establece la obra social. */
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

    /** Devuelve el DNI informado (solo para enmascararlo; nunca se persiste). */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI informado. */
    public void setDni(String dni) {
        this.dni = dni;
    }
}
