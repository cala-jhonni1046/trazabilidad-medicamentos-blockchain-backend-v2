package com.medichain.modules.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada RegistroPacienteRequestDTO en MediChain.
 * Registro público de un paciente (POST /api/registro/pacientes).
 */
public class RegistroPacienteRequestDTO {

    @NotBlank(message = "El campo email es obligatorio")
    @Email(message = "El campo email debe tener un formato válido")
    @Size(max = 150, message = "El campo email no puede superar 150 caracteres")
    private String email;

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
    @Pattern(regexp = "\\d{8}", message = "El campo dni debe contener 8 dígitos numéricos")
    private String dni;

    /** Constructor vacío exigido por Spring/Jackson. */
    public RegistroPacienteRequestDTO() {
    }

    /** Devuelve el email. */
    public String getEmail() {
        return email;
    }

    /** Establece el email. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Devuelve la contraseña (se guarda con BCrypt). */
    public String getPassword() {
        return password;
    }

    /** Establece la contraseña (se guarda con BCrypt). */
    public void setPassword(String password) {
        this.password = password;
    }

    /** Devuelve el nombre. */
    public String getNombre() {
        return nombre;
    }

    /** Establece el nombre. */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Devuelve el apellido. */
    public String getApellido() {
        return apellido;
    }

    /** Establece el apellido. */
    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    /** Devuelve el DNI. */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI. */
    public void setDni(String dni) {
        this.dni = dni;
    }
}
