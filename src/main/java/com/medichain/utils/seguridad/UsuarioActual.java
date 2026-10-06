package com.medichain.utils.seguridad;

import com.medichain.modules.auth.UsuarioAutenticado;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helper UsuarioActual en MediChain.
 * Devuelve el UsuarioAutenticado que JwtAuthenticationFilter dejó en el
 * SecurityContext. Es la ÚNICA fuente de la identidad, la empresa y la
 * provincia del usuario en los Services: nunca se toman del body ni de
 * parámetros. Es un @Component (y no un método estático) para poder
 * simularlo con Mockito en los tests unitarios.
 */
@Component
public class UsuarioActual {

    /**
     * Devuelve el usuario autenticado de la request en curso. Lanza
     * AuthenticationCredentialsNotFoundException (→ 401) si no hay uno.
     */
    public UsuarioAutenticado obtener() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado");
        }
        return usuario;
    }
}
