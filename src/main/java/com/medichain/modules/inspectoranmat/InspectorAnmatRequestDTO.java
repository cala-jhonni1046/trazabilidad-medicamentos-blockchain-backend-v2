package com.medichain.modules.inspectoranmat;

import com.medichain.utils.enums.Provincia;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada InspectorAnmatRequestDTO en MediChain.
 * Datos para dar de alta un inspector JUNTO con su cuenta de usuario
 * (rol INSPECTOR): por la regla R1 los inspectores solo se crean por
 * este endpoint, nunca por POST /api/usuarios. El usuario que lo da de
 * alta (usuarioAlta) sale del token, no del body. El estado inicial
 * (ACTIVO) y la fecha de alta los fija el servidor.
 */
public class InspectorAnmatRequestDTO {

    @NotBlank(message = "El campo legajo es obligatorio")
    @Size(max = 20, message = "El campo legajo no puede superar 20 caracteres")
    private String legajo;

    @NotBlank(message = "El campo dni es obligatorio")
    @Size(min = 8, max = 8, message = "El campo dni debe tener 8 caracteres")
    @Pattern(regexp = "\\d{8}", message = "El campo dni debe contener solo 8 dígitos numéricos")
    private String dni;

    @NotNull(message = "El campo provincia es obligatorio")
    private Provincia provincia;

    @NotBlank(message = "El campo email es obligatorio")
    @Email(message = "El campo email debe tener un formato válido")
    @Size(max = 150, message = "El campo email no puede superar 150 caracteres")
    private String email;

    // max = 72: es el límite real de BCrypt.
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank(message = "El campo nombre es obligatorio")
    @Size(max = 150, message = "El campo nombre no puede superar 150 caracteres")
    private String nombre;

    @NotBlank(message = "El campo apellido es obligatorio")
    @Size(max = 150, message = "El campo apellido no puede superar 150 caracteres")
    private String apellido;

    /** Constructor vacío exigido por Jackson. */
    public InspectorAnmatRequestDTO() {
    }

    /** Constructor con todos los campos obligatorios de alta. */
    public InspectorAnmatRequestDTO(String legajo, String dni, Provincia provincia, String email,
                                     String password, String nombre, String apellido) {
        this.legajo = legajo;
        this.dni = dni;
        this.provincia = provincia;
        this.email = email;
        this.password = password;
        this.nombre = nombre;
        this.apellido = apellido;
    }

    /** Devuelve el legajo informado. */
    public String getLegajo() {
        return legajo;
    }

    /** Establece el legajo informado. */
    public void setLegajo(String legajo) {
        this.legajo = legajo;
    }

    /** Devuelve el DNI informado. */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI informado. */
    public void setDni(String dni) {
        this.dni = dni;
    }

    /** Devuelve la provincia informada. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia informada. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve el email de la cuenta del inspector. */
    public String getEmail() {
        return email;
    }

    /** Establece el email de la cuenta del inspector. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Devuelve la contraseña en texto plano (se hashea en el Service, nunca se guarda así). */
    public String getPassword() {
        return password;
    }

    /** Establece la contraseña en texto plano. */
    public void setPassword(String password) {
        this.password = password;
    }

    /** Devuelve el nombre del inspector. */
    public String getNombre() {
        return nombre;
    }

    /** Establece el nombre del inspector. */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Devuelve el apellido del inspector. */
    public String getApellido() {
        return apellido;
    }

    /** Establece el apellido del inspector. */
    public void setApellido(String apellido) {
        this.apellido = apellido;
    }
}
