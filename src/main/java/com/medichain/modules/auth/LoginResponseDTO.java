package com.medichain.modules.auth;

import com.medichain.modules.usuario.RolUsuario;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida LoginResponseDTO en MediChain.
 * Resultado de un login exitoso: el JWT a enviar en el header
 * "Authorization: Bearer &lt;token&gt;" y los datos básicos del usuario.
 */
public class LoginResponseDTO {

    private String token;
    private String tipo;
    private LocalDateTime expiraEn;
    private UUID usuarioId;
    private String nombre;
    private RolUsuario rol;
    private UUID empresaId;

    /** Constructor vacío exigido por Jackson. */
    public LoginResponseDTO() {
    }

    /** Constructor completo; tipo siempre es "Bearer". */
    public LoginResponseDTO(String token, LocalDateTime expiraEn, UUID usuarioId, String nombre,
                             RolUsuario rol, UUID empresaId) {
        this.token = token;
        this.tipo = "Bearer";
        this.expiraEn = expiraEn;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.rol = rol;
        this.empresaId = empresaId;
    }

    /** Devuelve el JWT firmado. */
    public String getToken() {
        return token;
    }

    /** Establece el JWT firmado. */
    public void setToken(String token) {
        this.token = token;
    }

    /** Devuelve el tipo de token ("Bearer"). */
    public String getTipo() {
        return tipo;
    }

    /** Establece el tipo de token. */
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    /** Devuelve la fecha y hora de vencimiento del token. */
    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    /** Establece la fecha y hora de vencimiento del token. */
    public void setExpiraEn(LocalDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    /** Devuelve el id del usuario autenticado. */
    public UUID getUsuarioId() {
        return usuarioId;
    }

    /** Establece el id del usuario autenticado. */
    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** Devuelve el nombre del usuario autenticado. */
    public String getNombre() {
        return nombre;
    }

    /** Establece el nombre del usuario autenticado. */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Devuelve el rol del usuario autenticado. */
    public RolUsuario getRol() {
        return rol;
    }

    /** Establece el rol del usuario autenticado. */
    public void setRol(RolUsuario rol) {
        this.rol = rol;
    }

    /** Devuelve el id de la empresa del usuario, si trabaja en una. */
    public UUID getEmpresaId() {
        return empresaId;
    }

    /** Establece el id de la empresa del usuario. */
    public void setEmpresaId(UUID empresaId) {
        this.empresaId = empresaId;
    }
}
