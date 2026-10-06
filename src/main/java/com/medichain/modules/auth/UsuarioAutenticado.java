package com.medichain.modules.auth;

import com.medichain.modules.usuario.RolUsuario;
import java.util.UUID;

/**
 * Principal UsuarioAutenticado en MediChain.
 * Datos del usuario que viajan en el JWT y que JwtAuthenticationFilter
 * deja en el SecurityContext en cada request. No consulta la base: todo
 * sale de los claims del token.
 */
public class UsuarioAutenticado {

    private final UUID usuarioId;
    private final String email;
    private final RolUsuario rol;
    private final UUID empresaId;
    private final String provincia;

    /** Crea el principal con los datos extraídos del token. */
    public UsuarioAutenticado(UUID usuarioId, String email, RolUsuario rol, UUID empresaId, String provincia) {
        this.usuarioId = usuarioId;
        this.email = email;
        this.rol = rol;
        this.empresaId = empresaId;
        this.provincia = provincia;
    }

    /** Devuelve el id del usuario (claim "sub"). */
    public UUID getUsuarioId() {
        return usuarioId;
    }

    /** Devuelve el email del usuario. */
    public String getEmail() {
        return email;
    }

    /** Devuelve el rol del usuario. */
    public RolUsuario getRol() {
        return rol;
    }

    /** Devuelve el id de la empresa del usuario, o null si no trabaja en una. */
    public UUID getEmpresaId() {
        return empresaId;
    }

    /** Devuelve la provincia del inspector, o null si no es inspector. */
    public String getProvincia() {
        return provincia;
    }
}
