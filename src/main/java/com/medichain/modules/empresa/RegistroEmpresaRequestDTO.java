package com.medichain.modules.empresa;

import com.medichain.utils.enums.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import com.medichain.utils.validacion.Cuit;
import com.medichain.utils.validacion.Gln;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO de entrada RegistroEmpresaRequestDTO en MediChain.
 * Registro público de una empresa y su administrador inicial en una sola
 * operación (POST /api/registro/empresas, multipart/form-data: cada campo
 * es un campo del formulario y "documento" es el PDF de habilitación).
 */
@DirectorTecnicoSoloLaboratorio
public class RegistroEmpresaRequestDTO {

    @Schema(description = "Tipo de empresa", example = "FARMACIA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo tipo es obligatorio")
    private TipoEmpresa tipo;

    @Schema(description = "CUIT de la empresa, con o sin guiones (se valida el dígito verificador)", example = "30-71000006-5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo cuit es obligatorio")
    @Cuit
    private String cuit;

    @Schema(description = "Razón social", example = "Farmacia del Parque", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo razonSocial es obligatorio")
    @Size(max = 200, message = "El campo razonSocial no puede superar 200 caracteres")
    private String razonSocial;

    @Schema(description = "GLN de 13 dígitos (GS1, se valida el dígito verificador)", example = "7799000000068", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo gln es obligatorio")
    @Gln
    private String gln;

    @Schema(description = "Provincia: define qué inspectores reciben la solicitud", example = "MENDOZA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo provincia es obligatorio")
    private Provincia provincia;

    @Schema(description = "Localidad", example = "Mendoza", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo localidad es obligatorio")
    @Size(max = 150, message = "El campo localidad no puede superar 150 caracteres")
    private String localidad;

    @Schema(description = "Domicilio", example = "Av. San Martín 1200", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo domicilio es obligatorio")
    @Size(max = 250, message = "El campo domicilio no puede superar 250 caracteres")
    private String domicilio;

    @Schema(description = "Número de habilitación provincial (opcional)", example = "HAB-MZA-005")
    @Size(max = 50, message = "El campo numeroHabilitacion no puede superar 50 caracteres")
    private String numeroHabilitacion;

    @Schema(description = "Nombre del director técnico de la empresa, como figura en la habilitación (opcional, solo texto)", example = "Farm. Laura Gómez")
    @Size(max = 200, message = "El campo directorTecnico no puede superar 200 caracteres")
    private String directorTecnico;

    @Schema(description = "PDF de habilitación (archivo PDF real, hasta 5 MB)", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El documento de habilitación (PDF) es obligatorio")
    private MultipartFile documento;

    @Schema(description = "Email del administrador inicial: con él hace login", example = "admin@farmacia-delparque.demo", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo email es obligatorio")
    @Email(message = "El campo email debe tener un formato válido")
    @Size(max = 150, message = "El campo email no puede superar 150 caracteres")
    private String adminEmail;

    @Schema(description = "Contraseña del administrador inicial (8 a 72 caracteres)", example = "ClaveSegura123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String adminPassword;

    @Schema(description = "Nombre del administrador inicial", example = "Laura", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo nombre es obligatorio")
    @Size(max = 150, message = "El campo nombre no puede superar 150 caracteres")
    private String adminNombre;

    @Schema(description = "Apellido del administrador inicial", example = "Gómez", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo apellido es obligatorio")
    @Size(max = 150, message = "El campo apellido no puede superar 150 caracteres")
    private String adminApellido;

    @Schema(description = "DNI del administrador inicial: exactamente 8 dígitos, sin puntos", example = "30111007", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo dni es obligatorio")
    @Pattern(regexp = "\\d{8}", message = "El campo dni debe contener 8 dígitos numéricos")
    private String adminDni;

    @Schema(description = "Marcar true solo si tipo = LABORATORIO y el admin es además director técnico; en otro tipo dejar vacío o false (si no, 400)", example = "false")
    private Boolean adminEsDirectorTecnico;

    /** Constructor vacío exigido por Spring/Jackson. */
    public RegistroEmpresaRequestDTO() {
    }

    /** Devuelve el tipo de empresa. */
    public TipoEmpresa getTipo() {
        return tipo;
    }

    /** Establece el tipo de empresa. */
    public void setTipo(TipoEmpresa tipo) {
        this.tipo = tipo;
    }

    /** Devuelve el CUIT. */
    public String getCuit() {
        return cuit;
    }

    /** Establece el CUIT. */
    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    /** Devuelve la razón social. */
    public String getRazonSocial() {
        return razonSocial;
    }

    /** Establece la razón social. */
    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    /** Devuelve el GLN. */
    public String getGln() {
        return gln;
    }

    /** Establece el GLN. */
    public void setGln(String gln) {
        this.gln = gln;
    }

    /** Devuelve la provincia. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve la localidad. */
    public String getLocalidad() {
        return localidad;
    }

    /** Establece la localidad. */
    public void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    /** Devuelve el domicilio. */
    public String getDomicilio() {
        return domicilio;
    }

    /** Establece el domicilio. */
    public void setDomicilio(String domicilio) {
        this.domicilio = domicilio;
    }

    /** Devuelve el número de habilitación. */
    public String getNumeroHabilitacion() {
        return numeroHabilitacion;
    }

    /** Establece el número de habilitación. */
    public void setNumeroHabilitacion(String numeroHabilitacion) {
        this.numeroHabilitacion = numeroHabilitacion;
    }

    /** Devuelve el nombre del director técnico. */
    public String getDirectorTecnico() {
        return directorTecnico;
    }

    /** Establece el nombre del director técnico. */
    public void setDirectorTecnico(String directorTecnico) {
        this.directorTecnico = directorTecnico;
    }

    /** Devuelve el PDF de habilitación. */
    public MultipartFile getDocumento() {
        return documento;
    }

    /** Establece el PDF de habilitación. */
    public void setDocumento(MultipartFile documento) {
        this.documento = documento;
    }

    /** Devuelve el email del administrador inicial. */
    public String getAdminEmail() {
        return adminEmail;
    }

    /** Establece el email del administrador inicial. */
    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    /** Devuelve la contraseña del administrador inicial. */
    public String getAdminPassword() {
        return adminPassword;
    }

    /** Establece la contraseña del administrador inicial. */
    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    /** Devuelve el nombre del administrador inicial. */
    public String getAdminNombre() {
        return adminNombre;
    }

    /** Establece el nombre del administrador inicial. */
    public void setAdminNombre(String adminNombre) {
        this.adminNombre = adminNombre;
    }

    /** Devuelve el apellido del administrador inicial. */
    public String getAdminApellido() {
        return adminApellido;
    }

    /** Establece el apellido del administrador inicial. */
    public void setAdminApellido(String adminApellido) {
        this.adminApellido = adminApellido;
    }

    /** Devuelve el DNI del administrador inicial. */
    public String getAdminDni() {
        return adminDni;
    }

    /** Establece el DNI del administrador inicial. */
    public void setAdminDni(String adminDni) {
        this.adminDni = adminDni;
    }

    /** Devuelve si el administrador inicial es además director técnico (solo LABORATORIO). */
    public Boolean getAdminEsDirectorTecnico() {
        return adminEsDirectorTecnico;
    }

    /** Establece si el administrador inicial es además director técnico (solo LABORATORIO). */
    public void setAdminEsDirectorTecnico(Boolean adminEsDirectorTecnico) {
        this.adminEsDirectorTecnico = adminEsDirectorTecnico;
    }
}
