package com.medichain.modules.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada LoginRequestDTO en MediChain.
 * Credenciales que envía el cliente a POST /api/auth/login.
 */
public class LoginRequestDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe tener un formato válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /** Constructor vacío exigido por Jackson. */
    public LoginRequestDTO() {
    }

    /** Constructor con email y contraseña. */
    public LoginRequestDTO(String email, String password) {
        this.email = email;
        this.password = password;
    }

    /** Devuelve el email informado. */
    public String getEmail() {
        return email;
    }

    /** Establece el email informado. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Devuelve la contraseña en texto plano informada (solo se compara con el hash, nunca se guarda). */
    public String getPassword() {
        return password;
    }

    /** Establece la contraseña en texto plano informada. */
    public void setPassword(String password) {
        this.password = password;
    }
}
