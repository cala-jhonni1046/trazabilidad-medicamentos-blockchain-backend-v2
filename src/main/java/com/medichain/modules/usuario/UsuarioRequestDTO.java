package com.medichain.modules.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO de entrada UsuarioRequestDTO en MediChain.
 * Transporta los datos que se envían para dar de alta un usuario. La cuenta
 * nace activa; esAdminEmpresa y esDirectorTecnico son opcionales (por
 * defecto false); ultimoLogin no se informa por API, lo fija el propio
 * sistema de autenticación (fuera de alcance en esta etapa).
 */
public class UsuarioRequestDTO {

    @NotBlank(message = "El campo email es obligatorio")
    @Email(message = "El campo email debe tener un formato válido")
    @Size(max = 150, message = "El campo email no puede superar 150 caracteres")
    private String email;

    // max = 72: es el límite real de BCrypt (ignora silenciosamente lo que exceda 72 bytes de entrada).
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank(message = "El campo nombre es obligatorio")
    @Size(max = 150, message = "El campo nombre no puede superar 150 caracteres")
    private String nombre;

    @NotBlank(message = "El campo apellido es obligatorio")
    @Size(max = 150, message = "El campo apellido no puede superar 150 caracteres")
    private String apellido;

    @NotBlank(message = "El campo dni es obligatorio")
    @Size(min = 8, max = 8, message = "El campo dni debe tener 8 caracteres")
    @Pattern(regexp = "\\d{8}", message = "El campo dni debe contener solo 8 dígitos numéricos")
    private String dni;

    @NotNull(message = "El campo rol es obligatorio")
    private RolUsuario rol;

    private Boolean esAdminEmpresa;

    private Boolean esDirectorTecnico;

    /** Constructor vacío exigido por Jackson. */
    public UsuarioRequestDTO() {
    }

    /** Constructor con los campos obligatorios de alta. */
    public UsuarioRequestDTO(String email, String password, String nombre, String apellido,
                              String dni, RolUsuario rol) {
        this.email = email;
        this.password = password;
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.rol = rol;
    }

    /** Devuelve el email informado. */
    public String getEmail() {
        return email;
    }

    /** Establece el email informado. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Devuelve la contraseña en texto plano informada (se hashea en el Service, nunca se persiste así). */
    public String getPassword() {
        return password;
    }

    /** Establece la contraseña en texto plano informada. */
    public void setPassword(String password) {
        this.password = password;
    }

    /** Devuelve el nombre informado. */
    public String getNombre() {
        return nombre;
    }

    /** Establece el nombre informado. */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Devuelve el apellido informado. */
    public String getApellido() {
        return apellido;
    }

    /** Establece el apellido informado. */
    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    /** Devuelve el DNI informado. */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI informado. */
    public void setDni(String dni) {
        this.dni = dni;
    }

    /** Devuelve el rol informado. */
    public RolUsuario getRol() {
        return rol;
    }

    /** Establece el rol informado. */
    public void setRol(RolUsuario rol) {
        this.rol = rol;
    }

    /** Devuelve si el usuario administra la cuenta de su empresa. */
    public Boolean getEsAdminEmpresa() {
        return esAdminEmpresa;
    }

    /** Establece si el usuario administra la cuenta de su empresa. */
    public void setEsAdminEmpresa(Boolean esAdminEmpresa) {
        this.esAdminEmpresa = esAdminEmpresa;
    }

    /** Devuelve si el usuario es el director técnico de su empresa. */
    public Boolean getEsDirectorTecnico() {
        return esDirectorTecnico;
    }

    /** Establece si el usuario es el director técnico de su empresa. */
    public void setEsDirectorTecnico(Boolean esDirectorTecnico) {
        this.esDirectorTecnico = esDirectorTecnico;
    }
}
